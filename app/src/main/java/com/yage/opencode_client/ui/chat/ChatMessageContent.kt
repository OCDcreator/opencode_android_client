package com.yage.opencode_client.ui.chat

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.yage.opencode_client.data.model.MessageWithParts
import com.yage.opencode_client.data.model.Part
import com.yage.opencode_client.data.model.PartTokenInfo
import com.yage.opencode_client.data.model.TodoItem
import com.yage.opencode_client.data.repository.OpenCodeRepository
import com.yage.opencode_client.ui.theme.ToolWritePatchBackgroundDark
import com.yage.opencode_client.ui.theme.markdownTypographyCompact
import com.yage.opencode_client.ui.theme.uiScaled
import com.yage.opencode_client.ui.util.DataUriImageTransformer
import com.yage.opencode_client.ui.util.HttpImageHolder
import com.yage.opencode_client.ui.util.MarkdownImageResolver
import androidx.compose.ui.res.stringResource
import com.yage.opencode_client.R
import com.yage.opencode_client.ui.StreamDebugLogger
import kotlinx.coroutines.flow.collect

@Composable
internal fun ChatMessageList(
    currentSessionId: String?,
    messages: List<MessageWithParts>,
    streamingPartTexts: Map<String, String>,
    streamingReasoningPart: Part?,
    isLoading: Boolean,
    messageLimit: Int,
    repository: OpenCodeRepository,
    workspaceDirectory: String?,
    onLoadMore: () -> Unit,
    onFileClick: (String) -> Unit,
    onForkFromMessage: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val layoutInfo = listState.layoutInfo
    var shouldAutoScroll by remember { mutableStateOf(true) }
    val contentVersion = remember(messages, streamingPartTexts, streamingReasoningPart, isLoading) {
        messages.size +
            messages.sumOf { it.parts.size } +
            streamingPartTexts.hashCode() +
            (if (streamingReasoningPart != null) 1 else 0) +
            (if (isLoading) 1 else 0)
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 24
        }.collect { atBottom ->
            shouldAutoScroll = atBottom
        }
    }

    LaunchedEffect(contentVersion) {
        if (shouldAutoScroll && (messages.isNotEmpty() || streamingReasoningPart != null)) {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(
        currentSessionId,
        messages.size,
        streamingPartTexts,
        streamingReasoningPart,
        shouldAutoScroll
    ) {
        val sessionId = currentSessionId ?: return@LaunchedEffect
        StreamDebugLogger.logUiSnapshot(
            sessionId = sessionId,
            messageCount = messages.size,
            streamingParts = streamingPartTexts.size,
            streamingChars = streamingPartTexts.values.sumOf { it.length },
            hasStreamingReasoning = streamingReasoningPart != null,
            shouldAutoScroll = shouldAutoScroll
        )
    }

    // remember keys prevent stale-closure: isLoading/messages/messageLimit are plain values, not State.
    // reverseLayout=true: highest index = visual top (oldest). lastVisible >= total-3 fires there.
    val shouldLoadMore = remember(isLoading, messages.size, messageLimit) {
        derivedStateOf {
            if (isLoading || messages.isEmpty()) return@derivedStateOf false
            if (messages.size < messageLimit) return@derivedStateOf false
            val visible = layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) return@derivedStateOf false
            val total = layoutInfo.totalItemsCount
            val lastVisible = visible.maxOfOrNull { it.index } ?: return@derivedStateOf false
            lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        reverseLayout = true,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp.uiScaled())
    ) {
        if (streamingReasoningPart != null) {
            val streamingKey = "${streamingReasoningPart.messageId}:${streamingReasoningPart.id}"
            val streamingText = streamingPartTexts[streamingKey] ?: ""
            item(key = "streaming-reasoning") {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp.uiScaled(), vertical = 4.dp.uiScaled())) {
                    ReasoningCard(
                        text = streamingText,
                        title = streamingReasoningPart.toolReason,
                        isStreaming = true
                    )
                }
            }
        }
        val reversedMessages = messages.reversed()
        items(reversedMessages.mapIndexed { index, msg -> index to msg }, key = { it.second.info.id }) { (index, message) ->
            MessageRow(
                message = message,
                allMessages = reversedMessages,
                messageIndex = index,
                streamingPartTexts = streamingPartTexts,
                repository = repository,
                workspaceDirectory = workspaceDirectory,
                onFileClick = onFileClick,
                onForkFromMessage = onForkFromMessage
            )
        }
        if (isLoading && messages.size >= messageLimit) {
            item(key = "load-more-indicator") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp.uiScaled()),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp.uiScaled()))
                }
            }
        }
        if (!isLoading && messages.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp.uiScaled()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_messages),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageRow(
    message: MessageWithParts,
    allMessages: List<MessageWithParts>,
    messageIndex: Int,
    streamingPartTexts: Map<String, String>,
    repository: OpenCodeRepository,
    workspaceDirectory: String?,
    onFileClick: (String) -> Unit,
    onForkFromMessage: (String) -> Unit
) {
    val isUser = message.info.isUser
    val showModelInfo = remember(message.info.id, messageIndex, allMessages.size) {
        if (isUser) false
        else {
            val nextMsg = allMessages.getOrNull(messageIndex + 1)
            nextMsg == null || nextMsg.info.isUser ||
                message.info.parentId == null || message.info.parentId != nextMsg.info.parentId
        }
    }
    val showStepFinish = remember(message.info.id, messageIndex, allMessages.size) {
        if (isUser) true
        else {
            val prevMsg = allMessages.getOrNull(messageIndex - 1)
            val nextMsg = allMessages.getOrNull(messageIndex + 1)
            val hasSameParentAsPrev = prevMsg != null && !prevMsg.info.isUser &&
                message.info.parentId != null && message.info.parentId == prevMsg.info.parentId
            val hasSameParentAsNext = nextMsg != null && !nextMsg.info.isUser &&
                message.info.parentId != null && message.info.parentId == nextMsg.info.parentId
            !hasSameParentAsPrev && !hasSameParentAsNext || !hasSameParentAsNext
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp.uiScaled(), vertical = 4.dp.uiScaled())) {
        var i = 0
        while (i < message.parts.size) {
            val part = message.parts[i]
            if (!showStepFinish && part.isStepFinish) {
                i += 1
                continue
            }
            val streamingText = streamingPartTexts["${message.info.id}:${part.id}"]
            val isToolLike = part.isTool || (part.isPatch && part.filePathsForNavigationFiltered.isNotEmpty())
            if (isToolLike) {
                val run = mutableListOf<Part>()
                var j = i
                while (j < message.parts.size) {
                    val p = message.parts[j]
                    if (p.isTool || (p.isPatch && p.filePathsForNavigationFiltered.isNotEmpty())) {
                        run.add(p)
                        j++
                    } else break
                }
                run.chunked(2).forEach { chunk ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp.uiScaled())
                    ) {
                        chunk.forEach { p ->
                            PartView(
                                part = p,
                                isUser = isUser,
                                streamingTextOverride = streamingPartTexts["${message.info.id}:${p.id}"],
                                repository = repository,
                                workspaceDirectory = workspaceDirectory,
                                onFileClick = onFileClick,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (chunk.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
                i = j
            } else {
                PartView(
                    part = part,
                    isUser = isUser,
                    streamingTextOverride = streamingText,
                    repository = repository,
                    workspaceDirectory = workspaceDirectory,
                    onFileClick = onFileClick,
                    modifier = Modifier.fillMaxWidth()
                )
                i += 1
            }
        }
        if (!isUser && showModelInfo) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp.uiScaled(), top = 2.dp.uiScaled()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                message.info.resolvedModel?.let { model ->
                    Text(
                        text = "${model.providerId}/${model.modelId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Box {
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp.uiScaled())
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_options_cd),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp.uiScaled())
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.fork_from_here)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.CallSplit,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showMenu = false
                                onForkFromMessage(message.info.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PartView(
    part: Part,
    isUser: Boolean,
    streamingTextOverride: String?,
    repository: OpenCodeRepository,
    workspaceDirectory: String?,
    onFileClick: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    when {
        part.isText -> TextPart(
            text = streamingTextOverride ?: part.text ?: "",
            isUser = isUser,
            modifier = modifier,
            repository = repository,
            workspaceDirectory = workspaceDirectory
        )
        part.isReasoning -> ReasoningCard(streamingTextOverride ?: part.text ?: "", part.toolReason, false, modifier)
        part.isTool -> ToolCard(
            toolName = part.tool ?: "",
            status = part.stateDisplay,
            reason = part.toolReason,
            inputSummary = part.toolInputSummary,
            output = part.toolOutput,
            filePaths = part.filePathsForNavigationFiltered,
            todos = part.toolTodos,
            onFileClick = onFileClick,
            modifier = modifier
        )
        part.isPatch && part.filePathsForNavigationFiltered.isNotEmpty() -> PatchCard(part.filePathsForNavigationFiltered, onFileClick, modifier)
        part.isSubtask -> SubtaskCard(
            agentName = part.agent ?: "unknown",
            description = part.description,
            prompt = part.prompt,
            modifier = modifier
        )
        part.isStepFinish -> StepDivider(
            reason = part.reason,
            cost = part.cost,
            tokens = part.tokens,
            modifier = modifier
        )
        part.isFile -> FileAttachmentCard(
            filename = part.filename,
            mime = part.mime,
            modifier = modifier
        )
        part.isAgentPart -> AgentChip(part.agent ?: "unknown", modifier)
        part.isCompaction -> CompactionIndicator(modifier)
        // step-start, retry, snapshot: intentionally not rendered
    }
}

@Composable
private fun TextPart(
    text: String,
    isUser: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    repository: OpenCodeRepository? = null,
    workspaceDirectory: String? = null
) {
    val innerModifier = modifier.padding(12.dp.uiScaled())
    if (isUser) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp.uiScaled()),
            modifier = modifier
        ) {
            SelectionContainer {
                Text(
                    text = text,
                    modifier = Modifier.padding(12.dp.uiScaled()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        if (repository != null) {
            ResolvedMarkdownText(
                text = text,
                repository = repository,
                workspaceDirectory = workspaceDirectory,
                modifier = innerModifier
            )
        } else {
            SelectionContainer {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    Markdown(content = text, typography = markdownTypographyCompact(), modifier = innerModifier, imageTransformer = DataUriImageTransformer)
                }
            }
        }
    }
}

@Composable
private fun ResolvedMarkdownText(
    text: String,
    repository: OpenCodeRepository,
    workspaceDirectory: String?,
    modifier: Modifier = Modifier
) {
    var resolvedText by remember(text, workspaceDirectory) { mutableStateOf<String?>(null) }

    LaunchedEffect(text, workspaceDirectory, repository) {
        resolvedText = null
        resolvedText = MarkdownImageResolver.resolveImages(
            text = text,
            workspaceDirectory = workspaceDirectory,
            fetchContent = { path -> repository.getFileContent(path).getOrThrow() }
        )
        val finalText = resolvedText ?: text
        val httpsUrls = """!\[[^\]]*\]\((https?://[^)]+)\)""".toRegex().findAll(finalText).map { it.groupValues[1] }.toList().distinct()
        for (url in httpsUrls) {
            HttpImageHolder.prefetch(url)
        }
    }

    SelectionContainer {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Markdown(
                content = resolvedText ?: text,
                typography = markdownTypographyCompact(),
                modifier = modifier,
                imageTransformer = DataUriImageTransformer
            )
        }
    }
}

@Composable
private fun ReasoningCard(
    text: String,
    title: String?,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    var expanded by remember { mutableStateOf(isStreaming) }

    LaunchedEffect(isStreaming) {
        if (isStreaming) expanded = true
    }

    Card(
        modifier = modifier.padding(vertical = 4.dp.uiScaled()),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp.uiScaled()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(20.dp.uiScaled()))
                Spacer(modifier = Modifier.width(8.dp.uiScaled()))
                Text(title ?: stringResource(R.string.thinking), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.weight(1f))
                if (!isStreaming) {
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(24.dp.uiScaled())) {
                        Icon(
                            if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.ChevronRight,
                            contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                            modifier = Modifier.size(20.dp.uiScaled())
                        )
                    }
                }
            }
            if ((expanded || isStreaming) && text.isNotBlank()) {
                SelectionContainer {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                        Markdown(
                            content = text,
                            typography = markdownTypographyCompact(),
                            modifier = Modifier.padding(horizontal = 12.dp.uiScaled(), vertical = 8.dp.uiScaled()),
                            imageTransformer = DataUriImageTransformer
                        )
                    }
                }
            }
        }
    }
}

private const val MAX_OUTPUT_DISPLAY_CHARS = 2000

@Composable
private fun ToolCard(
    toolName: String,
    status: String?,
    reason: String?,
    inputSummary: String?,
    output: String?,
    filePaths: List<String>,
    todos: List<TodoItem> = emptyList(),
    onFileClick: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val isRunning = status == "running"
    var expanded by remember { mutableStateOf(isRunning) }
    val firstFile = filePaths.firstOrNull()
    val isWriteOrPatch = toolName == "write" || toolName == "patch" || toolName.contains("write")
    val isDark = isSystemInDarkTheme()
    val cardColor = if (isWriteOrPatch && isDark) ToolWritePatchBackgroundDark else MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor = if (isWriteOrPatch && !isDark) MaterialTheme.colorScheme.primary else LocalContentColor.current

    Card(modifier = modifier.padding(vertical = 4.dp.uiScaled()), colors = CardDefaults.cardColors(containerColor = cardColor)) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column(modifier = Modifier.padding(12.dp.uiScaled())) {
                // Header row
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp.uiScaled()), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp.uiScaled()))
                    }
                    Spacer(modifier = Modifier.width(8.dp.uiScaled()))
                    Text(text = toolName.ifEmpty { reason ?: stringResource(R.string.tool) }, style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.weight(1f))
                    if (firstFile != null) {
                        IconButton(onClick = { onFileClick(firstFile) }, modifier = Modifier.size(28.dp.uiScaled())) {
                            Icon(Icons.Default.OpenInNew, contentDescription = stringResource(R.string.show_in_files_cd), modifier = Modifier.size(18.dp.uiScaled()))
                        }
                    }
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(24.dp.uiScaled())) {
                        Icon(
                            if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.ChevronRight,
                            contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                            modifier = Modifier.size(20.dp.uiScaled())
                        )
                    }
                }

                if (expanded) {
                    // Input summary (command, path, etc.)
                    if (!inputSummary.isNullOrBlank()) {
                        Spacer(modifier = Modifier.size(8.dp.uiScaled()))
                        Text(
                            text = inputSummary,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Tool output
                    if (!output.isNullOrBlank()) {
                        Spacer(modifier = Modifier.size(6.dp.uiScaled()))
                        val displayOutput = if (output.length > MAX_OUTPUT_DISPLAY_CHARS) {
                            output.take(MAX_OUTPUT_DISPLAY_CHARS) + "…"
                        } else {
                            output
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(4.dp.uiScaled()),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = displayOutput,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp.uiScaled(), vertical = 6.dp.uiScaled())
                            )
                        }
                    }

                    // Todos
                    if (todos.isNotEmpty()) {
                        Spacer(modifier = Modifier.size(8.dp.uiScaled()))
                        todos.forEach { todo ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (todo.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp.uiScaled()),
                                    tint = if (todo.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.width(8.dp.uiScaled()))
                                Text(
                                    text = todo.content,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else null
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                if (todo.priority != "medium") {
                                    Text(text = todo.priority, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }

                    // File paths
                    if (filePaths.isNotEmpty()) {
                        Spacer(modifier = Modifier.size(8.dp.uiScaled()))
                        filePaths.forEach { path ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                Text(
                                    text = path,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onFileClick(path) }, modifier = Modifier.size(28.dp.uiScaled())) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = stringResource(R.string.show_in_files_cd), modifier = Modifier.size(18.dp.uiScaled()))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PatchCard(
    filePaths: List<String>,
    onFileClick: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val isDark = isSystemInDarkTheme()
    val cardColor = if (isDark) ToolWritePatchBackgroundDark else MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor = if (!isDark) MaterialTheme.colorScheme.primary else LocalContentColor.current

    Card(modifier = modifier.padding(vertical = 4.dp.uiScaled()), colors = CardDefaults.cardColors(containerColor = cardColor)) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column(modifier = Modifier.padding(12.dp.uiScaled())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp.uiScaled()))
                    Spacer(modifier = Modifier.width(8.dp.uiScaled()))
                    Text(stringResource(R.string.patch), style = MaterialTheme.typography.labelLarge)
                }
                Spacer(modifier = Modifier.size(8.dp.uiScaled()))
                filePaths.forEach { path ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(
                            path,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onFileClick(path) }, modifier = Modifier.size(28.dp.uiScaled())) {
                            Icon(Icons.Default.OpenInNew, contentDescription = stringResource(R.string.show_in_files_cd), modifier = Modifier.size(18.dp.uiScaled()))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtaskCard(
    agentName: String,
    description: String?,
    prompt: String?,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Card(
        modifier = modifier.padding(vertical = 4.dp.uiScaled()),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp.uiScaled())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.CallSplit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp.uiScaled()),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.width(8.dp.uiScaled()))
                Text(
                    text = description ?: "Sub-agent task",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp.uiScaled()))
                Surface(
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp.uiScaled())
                ) {
                    Text(
                        text = "@$agentName",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp.uiScaled(), vertical = 2.dp.uiScaled()),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            if (!prompt.isNullOrBlank()) {
                Spacer(modifier = Modifier.size(6.dp.uiScaled()))
                Text(
                    text = prompt.take(200) + if (prompt.length > 200) "\u2026" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )
            }
        }
    }
}

@Composable
private fun StepDivider(
    reason: String?,
    cost: Double?,
    tokens: PartTokenInfo?,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Row(
        modifier = modifier.padding(vertical = 2.dp.uiScaled()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            thickness = 1.dp
        )
        if (cost != null && cost > 0) {
            Text(
                text = "\$${String.format("%.4f", cost)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 8.dp.uiScaled())
            )
        }
        if (tokens != null) {
            val total = tokens.total ?: (tokens.input ?: 0) + (tokens.output ?: 0) + (tokens.reasoning ?: 0)
            if (total > 0) {
                Text(
                    text = "${total}t",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            thickness = 1.dp
        )
    }
}

@Composable
private fun FileAttachmentCard(
    filename: String?,
    mime: String?,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Surface(
        modifier = modifier.padding(vertical = 2.dp.uiScaled()),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp.uiScaled())
    ) {
        Row(
            modifier = Modifier.padding(8.dp.uiScaled()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(16.dp.uiScaled()),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.width(8.dp.uiScaled()))
            Column {
                Text(
                    text = filename ?: "Attachment",
                    style = MaterialTheme.typography.bodySmall
                )
                if (mime != null) {
                    Text(
                        text = mime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentChip(
    agentName: String,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Surface(
        modifier = modifier.padding(vertical = 2.dp.uiScaled()),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(4.dp.uiScaled())
    ) {
        Text(
            text = "\u2192 @$agentName",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp.uiScaled(), vertical = 4.dp.uiScaled())
        )
    }
}

@Composable
private fun CompactionIndicator(
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Text(
        text = "Context compressed",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = modifier.padding(vertical = 2.dp.uiScaled())
    )
}
