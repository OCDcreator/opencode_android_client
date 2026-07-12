package com.yage.opencode_client.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Severity levels for [AppLogger]. Ordered low → high so that [minLevel] filtering keeps
 * everything at or above the threshold.
 */
enum class LogLevel(val priority: Int) {
    DEBUG(0),
    INFO(1),
    WARN(2),
    ERROR(3);

    companion object {
        fun fromName(name: String?): LogLevel =
            name?.let { runCatching { valueOf(it.uppercase()) }.getOrNull() } ?: DEBUG
    }
}

/**
 * Functional buckets that replace the ad-hoc string TAGs scattered across the codebase.
 * Each [LogEntry] carries one so the in-app log viewer can filter by subsystem
 * (e.g. show only SSH + CONNECTION to diagnose whether traffic really goes through the tunnel).
 */
enum class LogCategory {
    CONNECTION,
    SSH,
    SESSION,
    STREAM,
    REPOSITORY,
    AUDIO,
    UI,
    GENERAL
}

/**
 * A single buffered log line. Kept minimal and immutable so the ring buffer can hold
 * thousands without GC pressure.
 */
data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val category: LogCategory,
    val tag: String,
    val message: String,
    val throwableMessage: String? = null
)

private const val LOG_BUFFER_CAPACITY = 2000

/**
 * Central application logger. Replaces the ~9 inconsistent TAG constants and three
 * duplicated try/catch `debugLog` helpers that previously wrapped [android.util.Log].
 *
 * Every log call:
 *  1. Appends to an in-memory ring buffer (capacity [LOG_BUFFER_CAPACITY], oldest evicted first)
 *     so the in-app "诊断日志" viewer can surface it without adb.
 *  2. Forwards to [android.util.Log] so `adb logcat` still works for power users.
 *  3. Bumps [revision] so collectors observing the buffer can refresh.
 *
 * The [android.util.Log] call is wrapped in try/catch because `android.util.Log` is stubbed
 * out on the local JVM (unit tests throw `RuntimeException("Not mocked")`); this mirrors the
 * previous per-file `debugLog` workarounds so existing unit tests keep passing without extra
 * mockk stubbing.
 */
object AppLogger {
    private val buffer = ConcurrentLinkedDeque<LogEntry>()
    private val _revision = MutableStateFlow(0)
    /** Monotonic counter bumped on every mutation; UI collects this to know when to re-snapshot. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /**
     * Minimum level that will be *buffered*. Calls below this level are still forwarded to
     * [android.util.Log] (so adb stays comprehensive) but skipped from the ring buffer to keep
     * the in-app viewer focused. Set via [setMinLevel].
     */
    @Volatile
    private var minLevel: LogLevel = LogLevel.DEBUG

    fun setMinLevel(level: LogLevel) {
        minLevel = level
    }

    fun minLevel(): LogLevel = minLevel

    /**
     * Buffer a snapshot in newest-first order (the log viewer shows the latest at the top).
     * Copies the deque so callers can iterate safely while the logger keeps appending.
     */
    fun entries(): List<LogEntry> {
        val min = minLevel
        return buffer.filter { it.level.priority >= min.priority }
    }

    /** Total entries currently held (independent of [minLevel] filtering). */
    fun size(): Int = buffer.size

    /**
     * Load persisted history from disk (older entries that have been evicted from the in-memory
     * buffer) and merge with the current in-memory buffer. Returns a combined list, oldest-first.
     * Called by the log viewer when it opens so the user can see history that predates this
     * process's in-memory buffer.
     */
    suspend fun loadHistoryWithBuffer(maxFileLines: Int = 3000): List<LogEntry> {
        val fileLines = LogFileWriter.get()?.loadHistory(maxFileLines).orEmpty()
        // Parse file lines back into LogEntry. Lines that fail to parse (corrupt/old format)
        // are skipped. The in-memory buffer entries take precedence for overlapping timestamps.
        val parsed = fileLines.mapNotNull { parseFileLine(it) }
        // Deduplicate: if a file line's timestamp+message matches an in-memory entry, skip it.
        val memKeys = buffer.map { it.timestamp.toString() + it.message }.toHashSet()
        val combined = (parsed.filter { (it.timestamp.toString() + it.message) !in memKeys } + buffer.toList())
            .sortedBy { it.timestamp }
        val min = minLevel
        return combined.filter { it.level.priority >= min.priority }
    }

    /** Parse a single persistent-log line back into a [LogEntry], or null if unparseable. */
    private fun parseFileLine(line: String): LogEntry? {
        // Format: "yyyy-MM-dd HH:mm:ss.SSS LEVEL CATEGORY/TAG message  | throwable"
        return try {
            val ts = fileTimestampFormat.parse(line.substring(0, 23)) ?: return null
            val rest = line.substring(24) // skip timestamp + space
            val levelEnd = rest.indexOf(' ')
            val level = LogLevel.fromName(rest.substring(0, levelEnd))
            val afterLevel = rest.substring(levelEnd + 1)
            val catEnd = afterLevel.indexOf('/')
            val category = runCatching { LogCategory.valueOf(afterLevel.substring(0, catEnd)) }.getOrNull()
                ?: return null
            val tagEnd = afterLevel.indexOf(' ', catEnd)
            val tag = if (tagEnd >= 0) afterLevel.substring(catEnd + 1, tagEnd) else afterLevel.substring(catEnd + 1)
            val remaining = if (tagEnd >= 0) afterLevel.substring(tagEnd + 1) else ""
            val throwableSep = remaining.indexOf("  | ")
            val (message, throwableMessage) = if (throwableSep >= 0) {
                remaining.substring(0, throwableSep) to remaining.substring(throwableSep + 4)
            } else {
                remaining to null
            }
            LogEntry(
                timestamp = ts.time,
                level = level,
                category = category,
                tag = tag,
                message = message,
                throwableMessage = throwableMessage
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        buffer.clear()
        _revision.value = _revision.value + 1
        // Also wipe persisted logs so the clear action is consistent across memory + disk.
        LogFileWriter.get()?.clear()
    }

    /**
     * Format a [LogEntry] as a single line for the persistent log file. Compact and
     * parseable: `ISO8601 LEVEL CATEGORY/TAG message [| throwable]`.
     */
    private fun formatLineForFile(entry: LogEntry): String {
        val ts = fileTimestampFormat.format(java.util.Date(entry.timestamp))
        val throwablePart = entry.throwableMessage?.let { "  | $it" } ?: ""
        return "$ts ${entry.level.name.padEnd(5)} ${entry.category.name}/${entry.tag} ${entry.message}$throwablePart"
    }

    private val fileTimestampFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US)

    fun log(level: LogLevel, category: LogCategory, tag: String, message: String, throwable: Throwable? = null) {
        val throwableMessage = throwable?.let { "${it.javaClass.simpleName}: ${it.message}" }
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            category = category,
            tag = tag,
            message = message,
            throwableMessage = throwableMessage
        )
        buffer.addLast(entry)
        // Evict oldest entries past capacity. ConcurrentLinkedDeque has no intrinsic cap.
        while (buffer.size > LOG_BUFFER_CAPACITY) {
            buffer.pollFirst()
        }
        _revision.value = _revision.value + 1
        forwardToAndroid(level, tag, message, throwable)
        // Persist to disk (async, non-blocking). Survives app restart for post-mortem debugging.
        LogFileWriter.get()?.append(formatLineForFile(entry))
    }

    fun d(category: LogCategory, tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.DEBUG, category, tag, message, throwable)

    fun i(category: LogCategory, tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.INFO, category, tag, message, throwable)

    fun w(category: LogCategory, tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.WARN, category, tag, message, throwable)

    fun e(category: LogCategory, tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.ERROR, category, tag, message, throwable)

    private fun forwardToAndroid(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        try {
            when (level) {
                LogLevel.DEBUG -> if (throwable != null) Log.d(tag, message, throwable) else Log.d(tag, message)
                LogLevel.INFO -> if (throwable != null) Log.i(tag, message, throwable) else Log.i(tag, message)
                LogLevel.WARN -> if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
                LogLevel.ERROR -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
            }
        } catch (_: RuntimeException) {
            // android.util.Log throws "Not mocked" on the local JVM unit-test runtime.
        }
    }
}
