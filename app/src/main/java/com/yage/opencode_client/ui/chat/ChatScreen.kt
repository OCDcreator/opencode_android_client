package com.yage.opencode_client.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yage.opencode_client.R
import com.yage.opencode_client.data.audio.AIBuildersAudioClient
import com.yage.opencode_client.data.model.MessageWithParts
import com.yage.opencode_client.data.model.Part
import com.yage.opencode_client.data.model.SessionStatus
import com.yage.opencode_client.ui.MainViewModel
import com.yage.opencode_client.ui.files.FilesScreen
import com.yage.opencode_client.ui.files.WorkspaceMarkdownLinkResolver
import com.yage.opencode_client.ui.theme.uiScaled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: MainViewModel,
    onNavigateToFiles: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    showSettingsButton: Boolean = true,
    showNewSessionInTopBar: Boolean = true,
    showSessionListInTopBar: Boolean = true
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val aiBuilderToken = AIBuildersAudioClient.sanitizeBearerToken(viewModel.getAIBuilderSettings().token)
    val micPermissionDeniedMessage = stringResource(R.string.mic_permission_denied)
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.toggleRecording()
        } else {
            viewModel.setSpeechError(micPermissionDeniedMessage)
        }
    }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        uris.forEach { uri ->
            viewModel.addImage(uri, context.contentResolver)
        }
    }

    // Workspace link preview: clicking a markdown link in an assistant message opens a
    // full-screen Dialog hosting FilesScreen pointed at the resolved path.
    var previewRequest by remember { mutableStateOf<WorkspacePreviewRequest?>(null) }
    var linkError by remember { mutableStateOf<String?>(null) }

    fun handleAssistantMarkdownLink(href: String) {
        val workspaceDirectory = state.currentSession?.directory
        when (val resolution = WorkspaceMarkdownLinkResolver.resolve(href, workspaceDirectory)) {
            is WorkspaceMarkdownLinkResolver.Resolution.External -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(resolution.url))
                runCatching { context.startActivity(intent) }
                    .onFailure { linkError = it.message ?: "Could not open link" }
            }
            is WorkspaceMarkdownLinkResolver.Resolution.Preview -> {
                previewRequest = WorkspacePreviewRequest(
                    path = resolution.path,
                    sessionId = state.currentSessionId,
                    workspaceDirectory = workspaceDirectory.orEmpty()
                )
            }
            WorkspaceMarkdownLinkResolver.Resolution.Ignored -> Unit
            is WorkspaceMarkdownLinkResolver.Resolution.Rejected -> linkError = resolution.message
        }
    }

    var cachedContextUsage by remember(state.currentSessionId) { mutableStateOf(state.contextUsage) }
    state.contextUsage?.let { cachedContextUsage = it }
    var showContextUsageSheet by rememberSaveable { mutableStateOf(false) }

    // Derive a live agent-activity status string + start timestamp for the elapsed timer.
    val currentActivity = remember(
        state.currentSessionId,
        state.currentSessionStatus,
        state.messages,
        state.streamingReasoningPart,
        state.streamingPartTexts,
        state.sessionSendTimestamps,
    ) {
        currentSessionActivity(
            sessionId = state.currentSessionId,
            status = state.currentSessionStatus,
            messages = state.messages,
            streamingReasoningPart = state.streamingReasoningPart,
            streamingPartTexts = state.streamingPartTexts,
            sendTimestamp = state.currentSessionId?.let { state.sessionSendTimestamps[it] },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChatTopBar(
            state = ChatTopBarState(
                sessions = state.sessions,
                currentSessionId = state.currentSessionId,
                sessionStatuses = state.sessionStatuses,
                hasMoreSessions = state.hasMoreSessions,
                isLoadingMoreSessions = state.isLoadingMoreSessions,
                totalSessionCount = state.totalSessionCount,
                showAllSessions = state.showAllSessions,
                expandedSessionIds = state.expandedSessionIds,
                agents = state.visibleAgents,
                selectedAgent = state.selectedAgentName,
                availableModels = state.availableModels,
                selectedModelIndex = state.selectedModelIndex,
                providers = state.providers?.providers ?: emptyList(),
                contextUsage = cachedContextUsage,
                sessionTodos = state.currentSessionId?.let { state.sessionTodos[it] } ?: emptyList(),
                showSettingsButton = showSettingsButton,
                showNewSessionInTopBar = showNewSessionInTopBar,
                showSessionListInTopBar = showSessionListInTopBar
            ),
            actions = ChatTopBarActions(
                onSelectSession = viewModel::selectSession,
                onCreateSession = viewModel::createSession,
                onDeleteSession = viewModel::deleteSession,
                onLoadMoreSessions = viewModel::loadMoreSessions,
                onToggleSessionExpanded = viewModel::toggleSessionExpanded,
                onShowAllSessions = viewModel::setShowAllSessions,
                onSelectAgent = viewModel::selectAgent,
                onSelectModel = viewModel::selectModel,
                onOpenContextUsage = { showContextUsageSheet = true },
                onNavigateToSettings = onNavigateToSettings,
                onRenameSession = { title ->
                    state.currentSessionId?.let { sessionId ->
                        viewModel.updateSessionTitle(sessionId, title)
                    }
                }
            )
        )

        Box(modifier = Modifier.weight(1f)) {
            if (state.currentSessionId == null) {
                ChatEmptyState(
                    isConnected = state.isConnected,
                    onConnect = { viewModel.testConnection() }
                )
            } else {
                ChatMessageList(
                    currentSessionId = state.currentSessionId,
                    messages = state.messages,
                    streamingPartTexts = state.streamingPartTexts,
                    streamingReasoningPart = state.streamingReasoningPart,
                    isLoading = state.isLoadingMessages,
                    messageLimit = state.messageLimit,
                    repository = viewModel.repository,
                    workspaceDirectory = state.currentSession?.directory,
                    onLoadMore = { viewModel.loadMoreMessages() },
                    onFileClick = onNavigateToFiles,
                    onMarkdownLinkClick = ::handleAssistantMarkdownLink,
                    onForkFromMessage = { messageId ->
                        state.currentSessionId?.let { sessionId ->
                            viewModel.forkSession(sessionId, messageId)
                        }
                    }
                )
            }

            state.error?.let { error ->
                Snackbar(
                    modifier = Modifier.padding(16.dp.uiScaled()),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text(stringResource(R.string.dismiss))
                        }
                    }
                ) {
                    Text(error)
                }
            }

            linkError?.let { error ->
                Snackbar(
                    modifier = Modifier.padding(16.dp.uiScaled()),
                    action = {
                        TextButton(onClick = { linkError = null }) {
                            Text(stringResource(R.string.dismiss))
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }

        previewRequest?.let { request ->
            // Auto-dismiss if the user switches sessions while the preview is open.
            if (request.sessionId != state.currentSessionId ||
                request.workspaceDirectory != state.currentSession?.directory
            ) {
                androidx.compose.runtime.LaunchedEffect(request, state.currentSessionId, state.currentSession?.directory) {
                    previewRequest = null
                    linkError = "Workspace changed; reopen the link from the current session."
                }
            } else {
                Dialog(
                    onDismissRequest = { previewRequest = null },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        FilesScreen(
                            pathToShow = request.path,
                            sessionDirectory = request.workspaceDirectory,
                            onCloseFile = { previewRequest = null }
                        )
                    }
                }
            }
        }

        if (state.currentSessionId != null) {
            ChatInputBar(
                text = state.inputText,
                isBusy = state.isCurrentSessionBusy,
                isRecording = state.isRecording,
                isTranscribing = state.isTranscribing,
                isSpeechConfigured = state.aiBuilderConnectionOK && aiBuilderToken.isNotEmpty(),
                hideMicIcon = state.hideMicIcon,
                pendingImages = state.pendingImages,
                agentActivityText = if (state.isCurrentSessionBusy) currentActivity?.text else null,
                agentStartedAtMillis = if (state.isCurrentSessionBusy) currentActivity?.startedAtMillis else null,
                onTextChange = viewModel::setInputText,
                onSend = { viewModel.sendMessage() },
                onAbort = { viewModel.abortSession() },
                onToggleRecording = {
                    if (state.isRecording) {
                        viewModel.toggleRecording()
                    } else {
                        val hasRecordAudioPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasRecordAudioPermission) {
                            viewModel.toggleRecording()
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                onPickImage = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemoveImage = viewModel::removeImage
            )
        }

        state.speechError?.let { speechError ->
            AlertDialog(
                onDismissRequest = { viewModel.clearSpeechError() },
                title = { Text(stringResource(R.string.speech_recognition)) },
                text = { Text(speechError) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearSpeechError() }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }

        if (showContextUsageSheet && cachedContextUsage != null) {
            ContextUsageBottomSheet(
                usage = cachedContextUsage!!,
                onDismiss = { showContextUsageSheet = false }
            )
        }

        state.pendingPermissions.firstOrNull()?.let { permission ->
            ChatPermissionCard(
                permission = permission,
                onRespond = { response ->
                    viewModel.respondPermission(permission.sessionId, permission.id, response)
                }
            )
        }

        state.pendingQuestions
            .filter { it.sessionId == state.currentSessionId }
            .firstOrNull()
            ?.let { question ->
                Dialog(
                    onDismissRequest = {},
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false,
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .imePadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp.uiScaled())
                    ) {
                        QuestionCardView(
                            question = question,
                            onReply = { answers, onError -> viewModel.replyQuestion(question.id, answers, onError) },
                            onReject = { viewModel.rejectQuestion(question.id) }
                        )
                    }
                }
            }
    }
}

private data class WorkspacePreviewRequest(
    val path: String,
    val sessionId: String?,
    val workspaceDirectory: String
)

private data class CurrentSessionActivity(
    val text: String,
    val startedAtMillis: Long?,
)

private fun currentSessionActivity(
    sessionId: String?,
    status: SessionStatus?,
    messages: List<MessageWithParts>,
    streamingReasoningPart: Part?,
    streamingPartTexts: Map<String, String>,
    sendTimestamp: Long?,
): CurrentSessionActivity? {
    val sid = sessionId ?: return null
    val startedAt = messages.lastOrNull { it.info.sessionId == sid && it.info.isUser }?.info?.time?.created
        ?: sendTimestamp
    val text = bestSessionActivityText(sid, status, messages, streamingReasoningPart, streamingPartTexts)
    return CurrentSessionActivity(text = text, startedAtMillis = startedAt)
}

private fun bestSessionActivityText(
    sessionId: String,
    status: SessionStatus?,
    messages: List<MessageWithParts>,
    streamingReasoningPart: Part?,
    streamingPartTexts: Map<String, String>,
): String {
    status?.message?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

    messages.asReversed().forEach { message ->
        if (message.info.sessionId != sessionId) return@forEach
        message.parts.asReversed().firstOrNull { it.isTool && it.stateDisplay == "running" }
            ?.let { part -> formatStatusFromPart(part)?.let { return it } }
    }

    if (streamingReasoningPart?.sessionId == sessionId) {
        val key = "${streamingReasoningPart.messageId}:${streamingReasoningPart.id}"
        return formatThinkingFromReasoningText(streamingPartTexts[key].orEmpty())
    }

    messages.asReversed().forEach { message ->
        if (message.info.sessionId != sessionId) return@forEach
        message.parts.asReversed().firstOrNull()?.let { part ->
            formatStatusFromPart(part)?.let { return it }
        }
    }

    return status?.takeIf { it.isRetry }?.let { "Retrying" } ?: "Thinking"
}

private fun formatStatusFromPart(part: Part): String? {
    if (part.isTool) {
        val base = when (part.tool) {
            "task" -> "Delegating"
            "todowrite", "todoread" -> "Planning"
            "read" -> "Gathering context"
            "list", "grep", "glob" -> "Searching codebase"
            "webfetch" -> "Searching web"
            "edit", "write" -> "Making edits"
            "bash" -> "Running commands"
            else -> null
        }
        val topic = (part.toolReason ?: part.toolInputSummary)?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            base != null && topic != null -> "$base - $topic"
            base != null -> base
            else -> null
        }
    }

    if (part.isReasoning) return formatThinkingFromReasoningText(part.text.orEmpty())
    if (part.isText) return "Gathering thoughts"
    return null
}

private fun formatThinkingFromReasoningText(text: String): String {
    val topic = Regex("^\\*\\*([^*]+)\\*\\*").find(text.trim())?.groupValues?.getOrNull(1)?.trim()
    return if (!topic.isNullOrEmpty()) "Thinking - $topic" else "Thinking"
}
