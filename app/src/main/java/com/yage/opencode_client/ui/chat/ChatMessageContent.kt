package com.yage.opencode_client.ui.chat

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.yage.opencode_client.data.model.MessageWithParts
import com.yage.opencode_client.data.model.Part
import com.yage.opencode_client.data.model.PartTokenInfo
import com.yage.opencode_client.data.model.TodoItem
import com.yage.opencode_client.data.repository.OpenCodeRepository
import com.yage.opencode_client.ui.theme.ToolWritePatchBackgroundDark
import com.yage.opencode_client.ui.theme.markdownComponentsWithScrollableTable
import com.yage.opencode_client.ui.theme.markdownTypographyCompact
import com.yage.opencode_client.ui.theme.uiScaled
import com.yage.opencode_client.ui.util.DataUriImageTransformer
import com.yage.opencode_client.ui.util.HttpImageHolder
import com.yage.opencode_client.ui.util.MarkdownImageResolver
import androidx.compose.ui.res.stringResource
import com.yage.opencode_client.R
import com.yage.opencode_client.ui.StreamDebugLogger
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

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
    // Per-session scroll state so each session remembers its position when you switch away and back.
    val listStates = remember { mutableStateMapOf<String, LazyListState>() }
    val listState = currentSessionId?.let { id ->
        listStates.getOrPut(id) { LazyListState() }
    } ?: rememberLazyListState()
    val layoutInfo = listState.layoutInfo

    // Synchronously derived "is the list scrolled to the bottom?" flag.
    // A derivedStateOf reads layoutInfo directly, so there is no async gap between "at bottom"
    // detection and the scroll decision that used to race during streaming (GH jump-to-top bug).
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= info.totalItemsCount - 1 &&
                last.offset + last.size <= info.viewportSize.height + 24
        }
    }

    // Track which sessions have been scrolled at least once so that first open falls back to the
    // newest message while later visits restore the remembered position.
    val initializedSessions = remember { mutableStateMapOf<String, Boolean>() }

    // Scroll triggers. bottomAnchor changes only when item/part counts change (not per streaming
    // token); streamingTrigger fires on every delta so streaming stays pinned to the bottom.
    val bottomAnchor = remember(messages, streamingReasoningPart) {
        messages.size to messages.lastOrNull()?.parts?.size
    }
    val streamingTrigger = remember(streamingPartTexts) { streamingPartTexts.hashCode() }

    // Stick to bottom when already there. Instant scrollToItem (not animated): during streaming the
    // trigger fires many times per second and an animation never gets to finish.
    LaunchedEffect(atBottom, bottomAnchor) {
        if (atBottom && (messages.isNotEmpty() || streamingReasoningPart != null)) {
            val idx = listState.layoutInfo.totalItemsCount - 1
            if (idx >= 0) listState.scrollToItem(idx)
        }
    }
    LaunchedEffect(streamingTrigger) {
        if (atBottom && messages.isNotEmpty()) {
            val idx = listState.layoutInfo.totalItemsCount - 1
            if (idx >= 0) listState.scrollToItem(idx)
        }
    }

    // First open of a session: no position to remember yet, so fall back to the newest message.
    LaunchedEffect(currentSessionId, isLoading, messages.isEmpty()) {
        val id = currentSessionId ?: return@LaunchedEffect
        if (!isLoading && messages.isNotEmpty() && initializedSessions[id] != true) {
            val idx = listState.layoutInfo.totalItemsCount - 1
            if (idx >= 0) {
                listState.scrollToItem(idx)
                initializedSessions[id] = true
            }
        }
    }

    LaunchedEffect(
        currentSessionId,
        messages.size,
        streamingPartTexts,
        streamingReasoningPart,
        atBottom
    ) {
        val sessionId = currentSessionId ?: return@LaunchedEffect
        StreamDebugLogger.logUiSnapshot(
            sessionId = sessionId,
            messageCount = messages.size,
            streamingParts = streamingPartTexts.size,
            streamingChars = streamingPartTexts.values.sumOf { it.length },
            hasStreamingReasoning = streamingReasoningPart != null,
            shouldAutoScroll = atBottom
        )
    }

    // Normal layout: load more when near the top (oldest messages)
    val shouldLoadMore = remember(isLoading, messages.size, messageLimit) {
        derivedStateOf {
            if (isLoading || messages.isEmpty()) return@derivedStateOf false
            if (messages.size < messageLimit) return@derivedStateOf false
            val visible = layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) return@derivedStateOf false
            val firstVisible = visible.firstOrNull()?.index ?: return@derivedStateOf false
            firstVisible <= 2
        }
    }
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) onLoadMore()
    }

    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp.uiScaled())
        ) {
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
        items(messages.mapIndexed { index, msg -> index to msg }, key = { it.second.info.id }) { (index, message) ->
            MessageRow(
                message = message,
                allMessages = messages,
                messageIndex = index,
                streamingPartTexts = streamingPartTexts,
                repository = repository,
                workspaceDirectory = workspaceDirectory,
                onFileClick = onFileClick,
                onForkFromMessage = onForkFromMessage
            )
        }
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
        }

        if (!atBottom && messages.isNotEmpty()) {
            Surface(
                onClick = {
                    val idx = listState.layoutInfo.totalItemsCount - 1
                    if (idx >= 0) scope.launch { listState.scrollToItem(idx) }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp.uiScaled()),
                shape = RoundedCornerShape(20.dp.uiScaled()),
                tonalElevation = 3.dp,
                shadowElevation = 3.dp
            ) {
                Text(
                    text = "↓ 回到底部",
                    modifier = Modifier.padding(horizontal = 16.dp.uiScaled(), vertical = 8.dp.uiScaled()),
                    style = MaterialTheme.typography.bodyMedium
                )
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
    val isAssistant = message.info.isAssistant
    val newerMsg = allMessages.getOrNull(messageIndex + 1)
    val hasNewerSameParent = isAssistant &&
        message.info.parentId != null &&
        newerMsg != null &&
        newerMsg.info.isAssistant &&
        newerMsg.info.parentId == message.info.parentId
    val showModelInfo = !isUser && !hasNewerSameParent
    val showStepFinish = !isAssistant || !hasNewerSameParent
    val expandedTools = remember(message.info.id) { mutableStateMapOf<String, Boolean>() as androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean> }

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
                // Layout: expanded = full width, collapsed = half width side-by-side
                var pendingCollapsed: Part? = null
                for (p in run) {
                    val key = "${message.info.id}:${p.id}"
                    val expanded = if (p.isTool) {
                        expandedTools[key] ?: (p.stateDisplay == "running")
                    } else {
                        true
                    }
                    if (expanded) {
                        pendingCollapsed?.let { first ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp.uiScaled())
                            ) {
                                PartView(
                                    part = first,
                                    isUser = isUser,
                                    streamingTextOverride = streamingPartTexts["${message.info.id}:${first.id}"],
                                    repository = repository,
                                    workspaceDirectory = workspaceDirectory,
                                    onFileClick = onFileClick,
                                    toolExpanded = false,
                                    onToolExpandedChange = { expandedTools["${message.info.id}:${first.id}"] = it },
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            pendingCollapsed = null
                        }
                        PartView(
                            part = p,
                            isUser = isUser,
                            streamingTextOverride = streamingPartTexts["${message.info.id}:${p.id}"],
                            repository = repository,
                            workspaceDirectory = workspaceDirectory,
                            onFileClick = onFileClick,
                            toolExpanded = true,
                            onToolExpandedChange = { expandedTools[key] = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        val first = pendingCollapsed
                        if (first == null) {
                            pendingCollapsed = p
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp.uiScaled())
                            ) {
                                PartView(
                                    part = first,
                                    isUser = isUser,
                                    streamingTextOverride = streamingPartTexts["${message.info.id}:${first.id}"],
                                    repository = repository,
                                    workspaceDirectory = workspaceDirectory,
                                    onFileClick = onFileClick,
                                    toolExpanded = false,
                                    onToolExpandedChange = { expandedTools["${message.info.id}:${first.id}"] = it },
                                    modifier = Modifier.weight(1f)
                                )
                                PartView(
                                    part = p,
                                    isUser = isUser,
                                    streamingTextOverride = streamingPartTexts["${message.info.id}:${p.id}"],
                                    repository = repository,
                                    workspaceDirectory = workspaceDirectory,
                                    onFileClick = onFileClick,
                                    toolExpanded = false,
                                    onToolExpandedChange = { expandedTools[key] = it },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            pendingCollapsed = null
                        }
                    }
                }
                pendingCollapsed?.let { first ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp.uiScaled())
                    ) {
                        PartView(
                            part = first,
                            isUser = isUser,
                            streamingTextOverride = streamingPartTexts["${message.info.id}:${first.id}"],
                            repository = repository,
                            workspaceDirectory = workspaceDirectory,
                            onFileClick = onFileClick,
                            toolExpanded = false,
                            onToolExpandedChange = { expandedTools["${message.info.id}:${first.id}"] = it },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
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
        if (isAssistant && showModelInfo) {
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
    modifier: Modifier = Modifier.fillMaxWidth(),
    toolExpanded: Boolean = false,
    onToolExpandedChange: (Boolean) -> Unit = {}
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
            expanded = toolExpanded,
            onExpandedChange = onToolExpandedChange,
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
        var expanded by remember { mutableStateOf(false) }
        var overflows by remember { mutableStateOf(false) }
        val maxHeight = 160.dp.uiScaled()
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp.uiScaled()),
            modifier = modifier
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (expanded) Modifier else Modifier.heightIn(max = maxHeight))
                        .clipToBounds()
                ) {
                    SelectionContainer {
                        Text(
                            text = text,
                            modifier = Modifier.padding(12.dp.uiScaled()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            onTextLayout = { result ->
                                if (!expanded) overflows = result.hasVisualOverflow
                            }
                        )
                    }
                }
                if (overflows) {
                    Text(
                        text = stringResource(if (expanded) R.string.collapse_message else R.string.expand_message),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 12.dp.uiScaled(), vertical = 2.dp.uiScaled())
                    )
                }
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
                    Markdown(content = text, typography = markdownTypographyCompact(), components = markdownComponentsWithScrollableTable(), modifier = innerModifier, imageTransformer = DataUriImageTransformer)
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
                components = markdownComponentsWithScrollableTable(),
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
                            components = markdownComponentsWithScrollableTable(),
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
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val isRunning = status == "running"
    val firstFile = filePaths.firstOrNull()
    val isWriteOrPatch = toolName == "write" || toolName == "patch" || toolName.contains("write")
    // Read-only tools (read, grep, glob, list, webfetch, task, todoread, ...) get a neutral gray
    // icon tint so write/patch tools — the ones that actually change the workspace — stand out.
    val isReadOnlyTool = ToolCardClassifier.readOnlyToolPrefixes.any { toolName.startsWith(it) }
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
                        Icon(
                            Icons.Default.Build,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp.uiScaled()),
                            tint = if (isReadOnlyTool) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp.uiScaled()))
                    Text(text = toolName.ifEmpty { reason ?: stringResource(R.string.tool) }, style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.weight(1f))
                    if (firstFile != null) {
                        IconButton(onClick = { onFileClick(firstFile) }, modifier = Modifier.size(28.dp.uiScaled())) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.show_in_files_cd), modifier = Modifier.size(18.dp.uiScaled()))
                        }
                    }
                    IconButton(onClick = { onExpandedChange(!expanded) }, modifier = Modifier.size(24.dp.uiScaled())) {
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
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.show_in_files_cd), modifier = Modifier.size(18.dp.uiScaled()))
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
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.show_in_files_cd), modifier = Modifier.size(18.dp.uiScaled()))
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
