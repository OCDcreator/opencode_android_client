package com.yage.opencode_client.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent log file writer with daily rotation.
 *
 * Directory layout (under [Context.cacheDir]):
 * ```
 * logs/
 *   20260712/
 *     opencode_1.log   ← current day, fills up to [MAX_FILE_BYTES] then rolls to _2
 *     opencode_2.log
 *     ...
 *     opencode_5.log   ← when all 5 fill, oldest entries are dropped (ring within a day)
 *   20260711/
 *     ...              ← cleaned up once older than [RETENTION_DAYS]
 * ```
 *
 * Design parameters (per product decision):
 * - [MAX_FILES_PER_DAY] = 5, [MAX_FILE_BYTES] = 5 MB → up to 25 MB/day
 * - [RETENTION_DAYS] = 7 → at most ~175 MB, but typical usage is far less
 *
 * Writes are serialized through a buffered [Channel] consumed by a single IO coroutine, so the
 * hot logging path (every `AppLogger.log` call) only does a non-blocking `trySend`.
 */
class LogFileWriter private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channel = Channel<String>(capacity = Channel.BUFFERED)
    @Volatile private var writerJob: Job? = null
    @Volatile private var enabled = false

    // Current-day writer state — guarded by the single consumer coroutine, no locks needed.
    private var currentDay: String = ""
    private var currentFileIndex: Int = 1
    private var currentFile: File? = null

    fun start() {
        if (enabled) return
        enabled = true
        pruneOldDays()
        writerJob = scope.launch {
            for (line in channel) {
                try {
                    writeLine(line)
                } catch (e: Exception) {
                    // Swallow — logging must never crash the app. Try android log as last resort.
                    try { Log.e(TAG, "LogFileWriter write failed", e) } catch (_: RuntimeException) {}
                }
            }
        }
    }

    fun stop() {
        enabled = false
        writerJob?.cancel()
        writerJob = null
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }

    /**
     * Enqueue a single formatted log line for async disk write. Non-blocking; if the channel
     * is full the line is dropped (better to lose a log line than block the caller).
     */
    fun append(line: String) {
        if (!enabled) return
        channel.trySend(line)
    }

    /**
     * Load all persisted log lines (across all retained days), oldest-first, up to [maxLines].
     * Used by the in-app viewer to show history that predates the in-memory buffer.
     */
    suspend fun loadHistory(maxLines: Int = MAX_HISTORY_LOAD): List<String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val root = logsRoot()
        if (!root.exists()) return@withContext emptyList()
        val dayDirs = root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
        val result = ArrayList<String>(maxLines.coerceAtMost(5000))
        for (dayDir in dayDirs) {
            val files = (1..MAX_FILES_PER_DAY).mapNotNull { File(dayDir, fileName(it)).takeIf { f -> f.exists() } }
            for (file in files) {
                try {
                    file.useLines { lines ->
                        for (line in lines) {
                            result.add(line)
                            if (result.size >= maxLines) return@useLines
                        }
                    }
                } catch (_: Exception) {
                    // Skip unreadable files
                }
                if (result.size >= maxLines) break
            }
            if (result.size >= maxLines) break
        }
        // Keep only the last maxLines (most recent)
        if (result.size > maxLines) result.subList(result.size - maxLines, result.size).toList() else result
    }

    /** Delete all persisted logs (called by the "clear logs" action). Fire-and-forget on IO. */
    fun clear() {
        scope.launch {
            try {
                currentFile = null
                currentDay = ""
                currentFileIndex = 1
                logsRoot().deleteRecursively()
            } catch (_: Exception) {
                // Best effort
            }
        }
    }

    // --- internals ---

    private fun writeLine(line: String) {
        val today = dayFormat.format(Date())
        // Day rollover: switch to a new day directory and prune old ones.
        if (today != currentDay) {
            currentDay = today
            currentFileIndex = 1
            currentFile = ensureFile(today, 1)
            pruneOldDays()
            return writeLine(line) // recurse once with fresh state
        }
        var file = currentFile ?: run {
            currentFile = ensureFile(today, currentFileIndex)
            currentFile!!
        }
        // Roll to next file if current one exceeded the size cap.
        if (file.length() >= MAX_FILE_BYTES) {
            if (currentFileIndex >= MAX_FILES_PER_DAY) {
                // All files for today are full: wrap around to file 1 (overwrite oldest of the day).
                currentFileIndex = 1
            } else {
                currentFileIndex += 1
            }
            file = ensureFile(today, currentFileIndex)
            // On wrap-around, truncate to start fresh.
            if (currentFileIndex == 1 && file.length() >= MAX_FILE_BYTES) {
                file.writeText("")
            }
            currentFile = file
        }
        file.appendText(line + "\n")
    }

    private fun ensureFile(day: String, index: Int): File {
        val dayDir = File(logsRoot(), day).apply { mkdirs() }
        val f = File(dayDir, fileName(index))
        if (!f.exists()) f.createNewFile()
        return f
    }

    private fun pruneOldDays() {
        val root = logsRoot()
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: return
        val cutoff = System.currentTimeMillis() - RETENTION_DAYS * DAY_MS
        for (dir in dirs) {
            try {
                val time = dayFormat.parse(dir.name)?.time ?: continue
                if (time < cutoff) dir.deleteRecursively()
            } catch (_: Exception) {
                // Unparseable dir name — leave it
            }
        }
    }

    private fun logsRoot(): File = File(context.cacheDir, LOG_DIR_NAME)

    private fun fileName(index: Int): String = "opencode_$index.log"

    companion object {
        private const val TAG = "LogFileWriter"
        private const val LOG_DIR_NAME = "logs"
        /** Per product decision: 5 files × 5 MB per day. */
        const val MAX_FILES_PER_DAY = 5
        const val MAX_FILE_BYTES: Long = 5L * 1024 * 1024
        /** Keep the last N days of log directories. */
        const val RETENTION_DAYS = 7
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val MAX_HISTORY_LOAD = 5000

        private val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.US)

        @Volatile
        private var instance: LogFileWriter? = null

        /**
         * Initialize the global writer. Safe to call multiple times; only the first call with a
         * non-null context takes effect. Called from [com.yage.opencode_client.OpenCodeApp].
         */
        fun init(context: Context) {
            if (instance != null) return
            synchronized(this) {
                if (instance == null) {
                    instance = LogFileWriter(context.applicationContext).also { it.start() }
                }
            }
        }

        fun get(): LogFileWriter? = instance
    }
}
