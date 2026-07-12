package com.yage.opencode_client.ui.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.yage.opencode_client.R
import com.yage.opencode_client.util.AppLogger
import com.yage.opencode_client.util.LogCategory
import com.yage.opencode_client.util.LogEntry
import com.yage.opencode_client.util.LogLevel
import com.yage.opencode_client.ui.theme.uiScaled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LEVEL_COLORS: Map<LogLevel, Color> = mapOf(
    LogLevel.DEBUG to Color(0xFF6B7280),
    LogLevel.INFO to Color(0xFF2563EB),
    LogLevel.WARN to Color(0xFFD97706),
    LogLevel.ERROR to Color(0xFFDC2626)
)

private val ALL_LEVELS = LogLevel.values().toList()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogViewerDialog(
    logVersion: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    var snapshot by remember { mutableStateOf(emptyList<LogEntry>()) }
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var selectedCategory by remember { mutableStateOf<LogCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var copyStatus by remember { mutableStateOf<Int?>(null) } // R.string id for transient toast-like text
    val categoriesPresent = remember(snapshot) {
        snapshot.map { it.category }.distinct().sortedBy { it.ordinal }
    }

    // On first open, load the full persisted history (disk + in-memory buffer merged) so the
    // user can see logs that predate this process's in-memory buffer. This is a one-time IO load.
    LaunchedEffect(Unit) {
        snapshot = AppLogger.loadHistoryWithBuffer()
    }

    // On subsequent revision bumps (new logs arrive while viewer is open), just re-read the
    // in-memory buffer — cheaper than re-parsing the whole file every time.
    LaunchedEffect(logVersion) {
        if (snapshot.isNotEmpty()) {
            snapshot = AppLogger.entries()
        }
    }

    // Filtered view derived from snapshot + active filters. Newest-first for log-viewer UX.
    // Recomputed only when inputs change.
    val filtered = remember(snapshot, selectedLevel, selectedCategory, searchQuery) {
        val query = searchQuery.trim()
        snapshot.filter { entry ->
            (selectedLevel == null || entry.level == selectedLevel) &&
                (selectedCategory == null || entry.category == selectedCategory) &&
                (query.isEmpty() ||
                    entry.message.contains(query, ignoreCase = true) ||
                    entry.tag.contains(query, ignoreCase = true) ||
                    (entry.throwableMessage?.contains(query, ignoreCase = true) == true))
        }.reversed()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.logging_viewer_title))
                            Text(
                                stringResource(R.string.logging_entry_count_format, filtered.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            scope.launch {
                                val text = formatEntriesForExport(filtered, timeFormat)
                                copyStatus = withContext(Dispatchers.IO) {
                                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("logs", text))
                                    R.string.logging_copied
                                }
                            }
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.logging_copy))
                        }
                        IconButton(onClick = {
                            scope.launch {
                                val result = exportLogsToFile(context, filtered, timeFormat)
                                copyStatus = result
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.logging_export))
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Filter row: level chips + category chips (only categories that appear in the buffer)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp.uiScaled(), vertical = 4.dp.uiScaled()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp.uiScaled()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp.uiScaled())
                    )
                    FilterChip(
                        selected = selectedLevel == null,
                        onClick = { selectedLevel = null },
                        label = { Text(stringResource(R.string.logging_filter_all_levels)) }
                    )
                    ALL_LEVELS.forEach { level ->
                        FilterChip(
                            selected = selectedLevel == level,
                            onClick = { selectedLevel = if (selectedLevel == level) null else level },
                            label = { Text(levelLabel(level)) }
                        )
                    }
                }
                if (categoriesPresent.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp.uiScaled(), vertical = 4.dp.uiScaled()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp.uiScaled()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text(stringResource(R.string.logging_filter_all_levels)) }
                        )
                        categoriesPresent.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = {
                                    selectedCategory = if (selectedCategory == category) null else category
                                },
                                label = { Text(categoryLabel(category)) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.logging_search_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp.uiScaled(), vertical = 4.dp.uiScaled())
                )
                HorizontalDivider()

                copyStatus?.let { statusResId ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(statusResId),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp.uiScaled(), vertical = 6.dp.uiScaled())
                        )
                    }
                }

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.logging_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(filtered) { entry ->
                            LogEntryRow(entry = entry, timeFormat = timeFormat)
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry, timeFormat: SimpleDateFormat) {
    val levelColor = LEVEL_COLORS[entry.level] ?: MaterialTheme.colorScheme.onSurface
    SelectionContainer {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp.uiScaled(), vertical = 6.dp.uiScaled()),
            verticalAlignment = Alignment.Top
        ) {
            // Level color stripe
            Box(
                modifier = Modifier
                    .width(3.dp.uiScaled())
                    .height(36.dp.uiScaled())
                    .background(levelColor, shape = RoundedCornerShape(2.dp.uiScaled()))
            )
            Spacer(modifier = Modifier.width(8.dp.uiScaled()))
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp.uiScaled()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeFormat.format(Date(entry.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontFamily = FontFamily.Monospace
                    )
                    Surface(
                        color = levelColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp.uiScaled())
                    ) {
                        Text(
                            text = entry.level.name.take(1),
                            style = MaterialTheme.typography.labelSmall,
                            color = levelColor,
                            modifier = Modifier.padding(horizontal = 4.dp.uiScaled(), vertical = 1.dp.uiScaled())
                        )
                    }
                    Text(
                        text = "[${entry.tag}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp.uiScaled()))
                Text(
                    text = entry.message,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
                entry.throwableMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(2.dp.uiScaled()))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun levelLabel(level: LogLevel): String = when (level) {
    LogLevel.DEBUG -> stringResource(R.string.logging_level_debug)
    LogLevel.INFO -> stringResource(R.string.logging_level_info)
    LogLevel.WARN -> stringResource(R.string.logging_level_warn)
    LogLevel.ERROR -> stringResource(R.string.logging_level_error)
}

@Composable
private fun categoryLabel(category: LogCategory): String = when (category) {
    LogCategory.CONNECTION -> stringResource(R.string.logging_category_connection)
    LogCategory.SSH -> stringResource(R.string.logging_category_ssh)
    LogCategory.SESSION -> stringResource(R.string.logging_category_session)
    LogCategory.STREAM -> stringResource(R.string.logging_category_stream)
    LogCategory.REPOSITORY -> stringResource(R.string.logging_category_repository)
    LogCategory.AUDIO -> stringResource(R.string.logging_category_audio)
    LogCategory.UI -> stringResource(R.string.logging_category_ui)
    LogCategory.GENERAL -> stringResource(R.string.logging_category_general)
}

private fun formatEntriesForExport(
    entries: List<LogEntry>,
    timeFormat: SimpleDateFormat
): String {
    val sb = StringBuilder()
    sb.appendLine("OpenCode Android Client — diagnostic log")
    sb.appendLine("Exported: ${timeFormat.format(Date())}  entries=${entries.size}")
    sb.appendLine("----")
    entries.forEach { entry ->
        sb.append(timeFormat.format(Date(entry.timestamp)))
        sb.append(' ')
        sb.append(entry.level.name.padEnd(5))
        sb.append(" [")
        sb.append(entry.category.name)
        sb.append('/')
        sb.append(entry.tag)
        sb.append("] ")
        sb.append(entry.message)
        entry.throwableMessage?.let {
            sb.append("  | ")
            sb.append(it)
        }
        sb.append('\n')
    }
    return sb.toString()
}

/**
 * Write the filtered log snapshot to a file under the app's shared cache dir and fire an
 * ACTION_SEND share intent. Reuses the FileProvider already declared for image sharing
 * (the cache-path covers any file type). Returns a string resource id for user feedback.
 */
private suspend fun exportLogsToFile(
    context: android.content.Context,
    entries: List<LogEntry>,
    timeFormat: SimpleDateFormat
): Int = withContext(Dispatchers.IO) {
    try {
        val text = formatEntriesForExport(entries, timeFormat)
        val sharedDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(sharedDir, "opencode_log_$stamp.txt")
        file.writeText(text)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "opencode_log_$stamp.txt")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.logging_export))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        R.string.logging_shared
    } catch (e: Exception) {
        AppLogger.e(LogCategory.UI, "LogViewer", "Failed to export logs", e)
        R.string.logging_share_error
    }
}
