package com.yage.opencode_client.ui

import com.yage.opencode_client.data.api.PromptRequest
import com.yage.opencode_client.data.model.Message
import com.yage.opencode_client.data.model.ProvidersResponse
import com.yage.opencode_client.data.repository.OpenCodeRepository
import com.yage.opencode_client.util.AppLogger
import com.yage.opencode_client.util.LogCategory
import com.yage.opencode_client.util.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun launchLoadSessions(
    scope: CoroutineScope,
    repository: OpenCodeRepository,
    state: MutableStateFlow<AppState>,
    onSelectSession: (String) -> Unit,
    onLoadSessionStatus: () -> Unit,
    onLoadMessages: (String) -> Unit
) {
    scope.launch {
        val limit = MainViewModelTimings.sessionPageSize
        val ignoreDir = state.value.showAllSessions
        AppLogger.d(LogCategory.SESSION, SESSION_ACTIONS_TAG, "loadSessions start limit=$limit ignoreDir=$ignoreDir")
        state.update {
            it.copy(
                loadedSessionLimit = limit,
                hasMoreSessions = true,
                isLoadingMoreSessions = false
            )
        }
        repository.getSessions(limit, ignoreDirectoryFilter = ignoreDir)
            .onSuccess { sessions ->
                AppLogger.d(LogCategory.SESSION, SESSION_ACTIONS_TAG, "loadSessions success count=${sessions.size}")
                // When filtering by directory, fetch the unfiltered count once so the
                // empty state can hint "N sessions exist under other directories".
                val totalCount = if (!ignoreDir && sessions.isEmpty()) {
                    repository.getSessions(limit, ignoreDirectoryFilter = true)
                        .getOrNull()?.size ?: 0
                } else {
                    sessions.size
                }
                state.update {
                    it.copy(
                        sessions = sessions,
                        hasMoreSessions = sessions.size >= limit,
                        isLoadingMoreSessions = false,
                        totalSessionCount = totalCount
                    )
                }
                val currentId = state.value.currentSessionId
                val hasCurrentSession = currentId != null && sessions.any { it.id == currentId }
                when {
                    currentId == null && sessions.isNotEmpty() -> onSelectSession(sessions.first().id)
                    hasCurrentSession -> {
                        onLoadSessionStatus()
                        onLoadMessages(currentId!!)
                    }
                    sessions.isNotEmpty() -> {
                        onSelectSession(sessions.first().id)
                    }
                    else -> {
                        state.update { it.copy(currentSessionId = null, messages = emptyList()) }
                    }
                }
            }
            .onFailure { error ->
                AppLogger.d(LogCategory.SESSION, SESSION_ACTIONS_TAG, "loadSessions failed", error)
                state.update {
                    it.copy(
                        isLoadingMoreSessions = false,
                        error = "Failed to load sessions: ${errorMessageOrFallback(error, "unknown error")}"
                    )
                }
            }
    }
}

internal fun launchLoadMoreSessions(
    scope: CoroutineScope,
    repository: OpenCodeRepository,
    state: MutableStateFlow<AppState>,
    onSelectSession: (String) -> Unit
) {
    var nextLimit = 0
    var shouldLaunch = false
    state.update { current ->
        if (!current.hasMoreSessions || current.isLoadingMoreSessions) {
            current
        } else {
            nextLimit = nextSessionFetchLimit(current.loadedSessionLimit)
            shouldLaunch = true
            current.copy(isLoadingMoreSessions = true)
        }
    }
    if (!shouldLaunch) return
    val ignoreDir = state.value.showAllSessions
    scope.launch {
        AppLogger.d(LogCategory.SESSION, SESSION_ACTIONS_TAG, "loadMoreSessions start limit=$nextLimit ignoreDir=$ignoreDir")
        repository.getSessions(nextLimit, ignoreDirectoryFilter = ignoreDir)
            .onSuccess { sessions ->
                AppLogger.d(LogCategory.SESSION, SESSION_ACTIONS_TAG, "loadMoreSessions success count=${sessions.size}")
                if (state.value.loadedSessionLimit > nextLimit) {
                    state.update { it.copy(isLoadingMoreSessions = false) }
                    return@onSuccess
                }
                state.update {
                    it.copy(
                        sessions = sessions,
                        loadedSessionLimit = nextLimit,
                        hasMoreSessions = sessions.size >= nextLimit,
                        isLoadingMoreSessions = false
                    )
                }
                val currentId = state.value.currentSessionId
                val hasCurrentSession = currentId != null && sessions.any { it.id == currentId }
                when {
                    currentId == null && sessions.isNotEmpty() -> onSelectSession(sessions.first().id)
                    hasCurrentSession -> Unit
                    sessions.isNotEmpty() -> onSelectSession(sessions.first().id)
                    else -> state.update { it.copy(currentSessionId = null, messages = emptyList()) }
                }
            }
            .onFailure { error ->
                AppLogger.d(LogCategory.SESSION, SESSION_ACTIONS_TAG, "loadMoreSessions failed", error)
                state.update {
                    it.copy(
                        isLoadingMoreSessions = false,
                        error = "Failed to load more sessions: ${errorMessageOrFallback(error, "unknown error")}"
                    )
                }
            }
    }
}

private const val SESSION_ACTIONS_TAG = "MainViewModelSessions"

internal fun launchLoadSessionStatus(
    scope: CoroutineScope,
    repository: OpenCodeRepository,
    state: MutableStateFlow<AppState>
) {
    scope.launch {
        repository.getSessionStatus()
            .onSuccess { statuses ->
                state.update { it.copy(sessionStatuses = statuses) }
            }
            .onFailure { error ->
                reportNonFatalIssue("MainViewModel", "Failed to load session status", error)
            }
    }
}

internal fun selectSessionState(
    state: MutableStateFlow<AppState>,
    settingsManager: SettingsManager,
    sessionId: String
) {
    val oldSessionId = state.value.currentSessionId
    val currentInputText = state.value.inputText
    if (oldSessionId != null) {
        settingsManager.setDraftText(oldSessionId, currentInputText)
    }

    settingsManager.currentSessionId = sessionId
    val restoredDraft = settingsManager.getDraftText(sessionId)
    state.update {
        it.copy(
            currentSessionId = sessionId,
            messages = emptyList(),
            messageLimit = 30,
            inputText = restoredDraft
        )
    }
}

internal fun launchLoadMessages(
    scope: CoroutineScope,
    repository: OpenCodeRepository,
    state: MutableStateFlow<AppState>,
    sessionId: String,
    resetLimit: Boolean = true,
    settingsManager: SettingsManager? = null
) {
    scope.launch {
        state.update { it.copy(isLoadingMessages = true) }
        val limit = if (resetLimit) 30 else state.value.messageLimit
        repository.getMessages(sessionId, limit)
            .onSuccess { messages ->
                StreamDebugLogger.logMessagesLoaded(
                    sessionId = sessionId,
                    messageCount = messages.size,
                    limit = limit,
                    isCurrentSession = sessionId == state.value.currentSessionId
                )
                if (sessionId == state.value.currentSessionId) {
                    val currentModels = state.value.availableModels
                    val lastAssistant = messages.lastOrNull { it.info.isAssistant }
                    val inferredModelIndex = lastAssistant?.info?.resolvedModel?.let { model ->
                        currentModels.indexOfFirst {
                            it.providerId == model.providerId && it.modelId == model.modelId
                        }.takeIf { it >= 0 }
                    }
                    val inferredAgentName = lastAssistant?.info?.agent
                    val modelKey = settingsManager?.getModelForSession(sessionId)
                    val modelIndex = modelKey?.let { key ->
                        currentModels.indexOfFirst { "${it.providerId}/${it.modelId}" == key }
                            .takeIf { it >= 0 }
                    } ?: inferredModelIndex
                    val agentName = settingsManager?.getAgentForSession(sessionId) ?: inferredAgentName
                    state.update {
                        it.copy(
                            messages = messages,
                            messageLimit = limit,
                            isLoadingMessages = false,
                            selectedModelIndex = modelIndex ?: it.selectedModelIndex,
                            selectedAgentName = agentName ?: it.selectedAgentName
                        )
                    }
                } else {
                    state.update { it.copy(isLoadingMessages = false) }
                }
            }
            .onFailure { error ->
                if (sessionId == state.value.currentSessionId) {
                    state.update {
                        it.copy(
                            isLoadingMessages = false,
                            error = "Failed to load messages: ${errorMessageOrFallback(error, "unknown error")}"
                        )
                    }
                } else {
                    state.update { it.copy(isLoadingMessages = false) }
                }
            }

        // Best-effort: load session todos after messages (matches iOS behavior).
        // Fails silently when the endpoint isn't available (e.g. test mocks).
        try {
            repository.getSessionTodos(sessionId)
                .onSuccess { todos ->
                    state.update { it.copy(sessionTodos = it.sessionTodos + (sessionId to todos)) }
                }
        } catch (_: Exception) {
        }
    }
}

internal fun launchLoadMessagesWithRetry(
    scope: CoroutineScope,
    sessionId: String,
    state: MutableStateFlow<AppState>,
    resetLimit: Boolean = true,
    onLoadMessages: (String, Boolean) -> Unit
) {
    scope.launch {
        delay(MainViewModelTimings.messageRetryDelayMs)
        if (sessionId == state.value.currentSessionId) {
            onLoadMessages(sessionId, resetLimit)
        }
    }
}

internal fun launchLoadMoreMessages(
    scope: CoroutineScope,
    repository: OpenCodeRepository,
    state: MutableStateFlow<AppState>,
    sessionId: String
) {
    if (state.value.isLoadingMessages) return
    val newLimit = state.value.messageLimit + 30
    scope.launch {
        state.update { it.copy(isLoadingMessages = true) }
        repository.getMessages(sessionId, newLimit)
            .onSuccess { messages ->
                if (sessionId == state.value.currentSessionId) {
                    state.update {
                        it.copy(
                            messages = messages,
                            messageLimit = newLimit,
                            isLoadingMessages = false
                        )
                    }
                } else {
                    state.update { it.copy(isLoadingMessages = false) }
                }
            }
            .onFailure {
                if (sessionId == state.value.currentSessionId) {
                    reportNonFatalIssue("MainViewModel", "Failed to load more messages")
                    state.update { it.copy(isLoadingMessages = false) }
                }
            }
    }
}

internal fun launchLoadProviders(
    scope: CoroutineScope,
    repository: OpenCodeRepository,
    state: MutableStateFlow<AppState>,
    settingsManager: SettingsManager,
    onNonFatalError: (String, Throwable?) -> Unit
) {
    scope.launch {
        repository.getProviders()
            .onSuccess { providers ->
                val modelOptions = buildModelOptionsFromProviders(providers)
                val savedKey = settingsManager.selectedModelKey
                val newModelIndex = resolveModelIndex(modelOptions, savedKey, providers)
                state.update {
                    it.copy(
                        providers = providers,
                        availableModels = modelOptions,
                        selectedModelIndex = newModelIndex
                    )
                }
            }
            .onFailure { error ->
                onNonFatalError("Failed to load providers", error)
            }
    }
}

internal fun buildModelOptionsFromProviders(providers: ProvidersResponse): List<AppState.ModelOption> {
    val options = providers.providers.flatMap { provider ->
        provider.models.values.map { model ->
            AppState.ModelOption(
                displayName = model.name ?: model.id,
                providerId = provider.id,
                modelId = model.id
            )
        }
    }
    if (options.isEmpty()) return ModelPresets.list
    return options.sortedBy { it.displayName.lowercase() }
}

internal fun resolveModelIndex(
    models: List<AppState.ModelOption>,
    savedKey: String,
    providers: ProvidersResponse
): Int {
    if (savedKey.isNotEmpty()) {
        val idx = models.indexOfFirst { "${it.providerId}/${it.modelId}" == savedKey }
        if (idx >= 0) return idx
    }
    providers.default?.let { default ->
        return models.indexOfFirst {
            it.providerId == default.providerId && it.modelId == default.modelId
        }.takeIf { it >= 0 } ?: 0
    }
    return 0
}

internal fun launchCreateSession(
    scope: CoroutineScope,
    repository: OpenCodeRepository,
    state: MutableStateFlow<AppState>,
    title: String?,
    onSelectSession: (String) -> Unit
) {
    scope.launch {
        repository.createSession(title)
            .onSuccess { session ->
                state.update { it.copy(sessions = upsertSession(it.sessions, session)) }
                onSelectSession(session.id)
            }
            .onFailure { error ->
                state.update { it.copy(error = "Failed to create session: ${errorMessageOrFallback(error, "unknown error")}") }
            }
    }
}

internal fun launchForkSession(
    scope: CoroutineScope,
    repository: OpenCodeRepository,
    state: MutableStateFlow<AppState>,
    sessionId: String,
    messageId: String?,
    onSelectSession: (String) -> Unit
) {
    scope.launch {
        repository.forkSession(sessionId, messageId)
            .onSuccess { session ->
                state.update { it.copy(sessions = upsertSession(it.sessions, session)) }
                onSelectSession(session.id)
            }
            .onFailure { error ->
                state.update { it.copy(error = "Failed to fork session: ${errorMessageOrFallback(error, "unknown error")}") }
            }
    }
}

internal fun launchUpdateSessionTitle(
    scope: CoroutineScope,
    repository: OpenCodeRepository,
    state: MutableStateFlow<AppState>,
    sessionId: String,
    title: String
) {
    scope.launch {
        repository.updateSession(sessionId, title)
            .onSuccess { updated ->
                state.update {
                    it.copy(sessions = it.sessions.map { session -> if (session.id == sessionId) updated else session })
                }
            }
            .onFailure { error ->
                state.update { it.copy(error = "Failed to update session: ${errorMessageOrFallback(error, "unknown error")}") }
            }
    }
}

internal fun launchSetSessionArchived(
    scope: CoroutineScope,
    repository: OpenCodeRepository,
    state: MutableStateFlow<AppState>,
    sessionId: String,
    archived: Boolean
) {
    scope.launch {
        val archivedValue = if (archived) System.currentTimeMillis() else -1L
        val ids = sessionSubtreeIds(state.value.sessions, sessionId, parentFirst = !archived)
        for (id in ids) {
            repository.updateSessionArchived(id, archivedValue)
                .onSuccess { updated ->
                    state.update { current ->
                        current.copy(sessions = current.sessions.map { session -> if (session.id == id) updated else session })
                    }
                }
                .onFailure { error ->
                    state.update {
                        it.copy(error = "Failed to ${if (archived) "archive" else "restore"} session: ${errorMessageOrFallback(error, "unknown error")}")
                    }
                    return@launch
                }
        }
    }
}

private fun sessionSubtreeIds(
    sessions: List<com.yage.opencode_client.data.model.Session>,
    rootId: String,
    parentFirst: Boolean
): List<String> {
    val childrenByParent = sessions.groupBy { it.parentId }
    fun collect(id: String): List<String> {
        val children = childrenByParent[id].orEmpty().flatMap { collect(it.id) }
        return if (parentFirst) listOf(id) + children else children + id
    }
    return collect(rootId)
}

internal fun launchDeleteSession(
    scope: CoroutineScope,
    repository: OpenCodeRepository,
    state: MutableStateFlow<AppState>,
    sessionId: String,
    onSelectSession: (String) -> Unit
) {
    scope.launch {
        repository.deleteSession(sessionId)
            .onSuccess {
                val newSessions = state.value.sessions.filter { it.id != sessionId }
                state.update { it.copy(sessions = newSessions) }
                if (state.value.currentSessionId == sessionId) {
                    val newCurrent = newSessions.firstOrNull()?.id
                    if (newCurrent != null) {
                        onSelectSession(newCurrent)
                    } else {
                        state.update { it.copy(currentSessionId = null, messages = emptyList()) }
                    }
                }
            }
            .onFailure { error ->
                state.update { it.copy(error = "Failed to delete session: ${errorMessageOrFallback(error, "unknown error")}") }
            }
    }
}

internal fun buildSelectedModel(state: AppState): Message.ModelInfo? {
    val selectedModel = state.availableModels.getOrNull(state.selectedModelIndex)
    return selectedModel?.let {
        Message.ModelInfo(it.providerId, it.modelId)
    } ?: state.providers?.default?.let {
        Message.ModelInfo(it.providerId, it.modelId)
    }
}

internal fun launchSendMessage(
    scope: CoroutineScope,
    repository: OpenCodeRepository,
    state: MutableStateFlow<AppState>,
    sessionId: String,
    text: String,
    agent: String,
    model: Message.ModelInfo?,
    imageParts: List<PromptRequest.PartInput.File> = emptyList(),
    onRefreshMessages: (String, Boolean) -> Unit,
    onSuccess: (() -> Unit)? = null,
    onComplete: (() -> Unit)? = null
) {
    scope.launch {
        repository.sendMessage(sessionId, text, agent, model, imageParts = imageParts)
            .onSuccess {
                StreamDebugLogger.logSendAccepted(sessionId)
                state.update {
                    it.copy(
                        inputText = "",
                        error = null,
                        sessionStatuses = it.sessionStatuses + (sessionId to com.yage.opencode_client.data.model.SessionStatus(type = "busy"))
                    )
                }
                onSuccess?.invoke()
                StreamDebugLogger.logMessageRefreshScheduled(sessionId, "send.accepted", true)
                onRefreshMessages(sessionId, true)
                launch {
                    delay(MainViewModelTimings.messageRefreshDelayMs)
                    StreamDebugLogger.logMessageRefreshScheduled(sessionId, "send.delayed_refresh", false)
                    onRefreshMessages(sessionId, false)
                }
            }
            .onFailure { error ->
                StreamDebugLogger.logSendFailed(sessionId, error)
                state.update { it.copy(error = errorMessageOrFallback(error, "Failed to send message")) }
            }
        onComplete?.invoke()
    }
}
