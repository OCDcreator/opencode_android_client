package com.yage.opencode_client.ui

import com.yage.opencode_client.data.model.SessionStatus
import com.yage.opencode_client.data.model.SSEEvent
import com.yage.opencode_client.data.model.TodoItem
import com.yage.opencode_client.data.repository.OpenCodeRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun launchBusyPolling(
    scope: CoroutineScope,
    state: MutableStateFlow<AppState>,
    onLoadMessages: (String, Boolean) -> Unit
): Job {
    return scope.launch {
        while (true) {
            delay(MainViewModelTimings.busyPollingIntervalMs)
            val sessionId = state.value.currentSessionId ?: continue
            if (state.value.isLoadingMessages) continue
            if (!state.value.isCurrentSessionBusy) continue
            onLoadMessages(sessionId, false)
        }
    }
}

internal fun launchSseCollection(
    scope: CoroutineScope,
    repository: OpenCodeRepository,
    state: MutableStateFlow<AppState>,
    onEvent: (SSEEvent) -> Unit
): Job {
    return scope.launch {
        repository.connectSSE()
            .catch { error ->
                state.update { it.copy(error = "SSE Error: ${error.message}") }
            }
            .collect { result ->
                result.onSuccess { event -> onEvent(event) }
                    .onFailure { error ->
                        state.update { it.copy(error = "SSE Error: ${error.message}") }
                    }
            }
    }
}

internal fun handleIncomingSseEvent(
    state: MutableStateFlow<AppState>,
    event: SSEEvent,
    onRefreshMessages: (String, Boolean) -> Unit,
    onLoadPendingPermissions: () -> Unit,
    onNonFatalIssue: (String) -> Unit
) {
    when (event.payload.type) {
        "session.created" -> {
            val created = parseSessionCreatedEvent(event)
            if (created != null) {
                state.update { it.copy(sessions = upsertSession(it.sessions, created.session)) }
            } else {
                onNonFatalIssue("Ignoring invalid session.created payload")
            }
        }
        "session.updated" -> {
            val updated = parseSessionUpdatedEvent(event)
            if (updated != null) {
                state.update { it.copy(sessions = upsertSession(it.sessions, updated)) }
            } else {
                onNonFatalIssue("Ignoring invalid session.updated payload")
            }
        }
        "session.status" -> {
            val statusEvent = parseSessionStatusEvent(event)
            if (statusEvent != null) {
                state.update {
                    it.copy(
                        sessionStatuses = it.sessionStatuses + (statusEvent.sessionId to statusEvent.status)
                    )
                }
                if (statusEvent.sessionId == state.value.currentSessionId && !statusEvent.status.isBusy) {
                    StreamDebugLogger.logStreamCompleted(statusEvent.sessionId, "session.idle")
                    state.update {
                        it.copy(
                            streamingPartTexts = emptyMap(),
                            streamingReasoningPart = null
                        )
                    }
                    StreamDebugLogger.logMessageRefreshScheduled(statusEvent.sessionId, "session.idle", false)
                    onRefreshMessages(statusEvent.sessionId, false)
                }
            } else {
                onNonFatalIssue("Ignoring invalid session.status payload")
            }
        }
        "message.created" -> {
            val sessionId = event.payload.getString("sessionID")
            if (sessionId != null) {
                StreamDebugLogger.logMessageCreated(sessionId, sessionId == state.value.currentSessionId)
            }
            if (sessionId != null && sessionId == state.value.currentSessionId) {
                StreamDebugLogger.logMessageRefreshScheduled(sessionId, "message.created", true)
                onRefreshMessages(sessionId, true)
            }
        }
        "message.part.updated" -> {
            val deltaEvent = parseMessagePartDeltaEvent(event) ?: return
            if (deltaEvent.sessionId == state.value.currentSessionId) {
                if (
                    deltaEvent.messageId != null &&
                    deltaEvent.partId != null &&
                    !deltaEvent.delta.isNullOrBlank()
                ) {
                    StreamDebugLogger.logStreamDelta(
                        sessionId = deltaEvent.sessionId,
                        messageId = deltaEvent.messageId,
                        partId = deltaEvent.partId,
                        partType = deltaEvent.partType,
                        deltaLength = deltaEvent.delta.length
                    )
                    val key = "${deltaEvent.messageId}:${deltaEvent.partId}"
                    // part.text is the server's full accumulated text — replace, not append
                    state.update {
                        it.copy(
                            streamingPartTexts = it.streamingPartTexts + (key to deltaEvent.delta),
                            streamingReasoningPart = reasoningPartOrNull(
                                partType = deltaEvent.partType,
                                partId = deltaEvent.partId,
                                messageId = deltaEvent.messageId,
                                sessionId = deltaEvent.sessionId
                            ) ?: it.streamingReasoningPart
                        )
                    }
                } else {
                    StreamDebugLogger.logStreamCompleted(deltaEvent.sessionId, "message.part.completed")
                    state.update {
                        it.copy(streamingPartTexts = emptyMap(), streamingReasoningPart = null)
                    }
                    StreamDebugLogger.logMessageRefreshScheduled(deltaEvent.sessionId, "message.part.completed", false)
                    onRefreshMessages(deltaEvent.sessionId, false)
                }
            }
        }
        "permission.asked" -> {
            onLoadPendingPermissions()
        }
        "question.asked" -> {
            if (!isEventForCurrentDirectory(event, state.value.workingDirectory)) return
            val question = parseQuestionAskedEvent(event)
            if (question != null) {
                state.update { currentState ->
                    val existing = currentState.pendingQuestions.any { it.id == question.id }
                    if (!existing) {
                        currentState.copy(pendingQuestions = currentState.pendingQuestions + question)
                    } else {
                        currentState
                    }
                }
            } else {
                onNonFatalIssue("Ignoring invalid question.asked payload")
            }
        }
        "question.replied", "question.rejected" -> {
            if (!isEventForCurrentDirectory(event, state.value.workingDirectory)) return
            val requestId = event.payload.getString("requestID") 
                ?: event.payload.getString("id")
            if (requestId != null) {
                state.update { currentState ->
                    currentState.copy(
                        pendingQuestions = currentState.pendingQuestions.filter { it.id != requestId }
                    )
                }
            }
        }
        "session.error" -> {
            val sessionId = event.payload.getString("sessionID")
            val errorObj = event.payload.properties?.get("error") as? JsonObject
            val errorName = (errorObj?.get("name") as? JsonPrimitive)?.content
            val errorData = errorObj?.get("data") as? JsonObject
            val errorMessage = (errorData?.get("message") as? JsonPrimitive)?.content
            val displayMessage = when {
                errorMessage != null && errorName != null -> "$errorName: $errorMessage"
                errorMessage != null -> errorMessage
                errorName != null -> "Session error: $errorName"
                else -> "Session error"
            }
            val isCurrentSession = sessionId == null || sessionId == state.value.currentSessionId
            state.update {
                it.copy(
                    error = displayMessage,
                    streamingPartTexts = if (isCurrentSession) emptyMap() else it.streamingPartTexts,
                    streamingReasoningPart = if (isCurrentSession) null else it.streamingReasoningPart,
                    sessionStatuses = if (isCurrentSession && sessionId != null) {
                        it.sessionStatuses + (sessionId to SessionStatus(type = "idle"))
                    } else {
                        it.sessionStatuses
                    }
                )
            }
        }
        "todo.updated" -> {
            val sessionId = event.payload.getString("sessionID") ?: return
            val todosArray = event.payload.properties?.get("todos") as? JsonArray ?: return
            val todos = try {
                Json.decodeFromJsonElement<List<TodoItem>>(todosArray)
            } catch (_: Exception) {
                return
            }
            state.update { it.copy(sessionTodos = it.sessionTodos + (sessionId to todos)) }
        }
    }
}
