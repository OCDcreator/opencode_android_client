package com.yage.opencode_client.ui

import android.util.Log
import com.yage.opencode_client.BuildConfig
import com.yage.opencode_client.data.model.Message
import java.util.concurrent.ConcurrentHashMap

internal object StreamDebugLogger {
    private const val TAG = "StreamDebug"
    private const val PROGRESS_LOG_INTERVAL_MS = 350L
    private const val PROGRESS_LOG_CHAR_STEP = 120
    private const val UI_LOG_INTERVAL_MS = 350L

    private data class StreamTrace(
        val sessionId: String,
        val messageId: String,
        val partId: String,
        val partType: String,
        val totalChars: Int = 0,
        val chunkCount: Int = 0,
        val lastLoggedChars: Int = 0,
        val lastLoggedAt: Long = 0L
    )

    private data class UiTrace(
        val messageCount: Int = 0,
        val streamingParts: Int = 0,
        val streamingChars: Int = 0,
        val lastLoggedAt: Long = 0L
    )

    private val streamTraces = ConcurrentHashMap<String, StreamTrace>()
    private val uiTraces = ConcurrentHashMap<String, UiTrace>()

    fun logSendRequested(
        sessionId: String,
        textLength: Int,
        agent: String,
        model: Message.ModelInfo?
    ) {
        if (!BuildConfig.DEBUG) return
        val modelLabel = model?.let { "${it.providerId}/${it.modelId}" } ?: "default"
        Log.d(TAG, "send.request session=$sessionId chars=$textLength agent=$agent model=$modelLabel")
    }

    fun logSendAccepted(sessionId: String) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, "send.accepted session=$sessionId")
    }

    fun logSendFailed(sessionId: String, error: Throwable) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, "send.failed session=$sessionId error=${errorMessageOrFallback(error, "unknown error")}")
    }

    fun logMessageRefreshScheduled(sessionId: String, reason: String, resetLimit: Boolean) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, "messages.refresh.schedule session=$sessionId reason=$reason resetLimit=$resetLimit")
    }

    fun logMessagesLoaded(
        sessionId: String,
        messageCount: Int,
        limit: Int,
        isCurrentSession: Boolean
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            TAG,
            "messages.loaded session=$sessionId count=$messageCount limit=$limit current=$isCurrentSession"
        )
    }

    fun logMessageCreated(sessionId: String, isCurrentSession: Boolean) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, "sse.message.created session=$sessionId current=$isCurrentSession")
    }

    fun logStreamDelta(
        sessionId: String,
        messageId: String,
        partId: String,
        partType: String,
        deltaLength: Int
    ) {
        if (!BuildConfig.DEBUG) return
        val now = System.currentTimeMillis()
        val key = streamKey(sessionId, messageId, partId)
        val previous = streamTraces[key]
            ?: StreamTrace(
                sessionId = sessionId,
                messageId = messageId,
                partId = partId,
                partType = partType
            )
        val updated = previous.copy(
            // After SSE fix, deltaLength is the full accumulated text length (not an increment).
            // Track current length directly; chunkCount still counts events.
            totalChars = deltaLength,
            chunkCount = previous.chunkCount + 1
        )
        val shouldLog = previous.chunkCount == 0 ||
            updated.totalChars - previous.lastLoggedChars >= PROGRESS_LOG_CHAR_STEP ||
            now - previous.lastLoggedAt >= PROGRESS_LOG_INTERVAL_MS
        if (shouldLog) {
            Log.d(
                TAG,
                "stream.delta session=$sessionId message=$messageId part=$partId type=$partType chunks=${updated.chunkCount} chars=${updated.totalChars}"
            )
            streamTraces[key] = updated.copy(
                lastLoggedChars = updated.totalChars,
                lastLoggedAt = now
            )
        } else {
            streamTraces[key] = updated
        }
    }

    fun logStreamCompleted(sessionId: String, reason: String, messageId: String? = null, partId: String? = null) {
        if (!BuildConfig.DEBUG) return
        if (messageId != null && partId != null) {
            val key = streamKey(sessionId, messageId, partId)
            val trace = streamTraces.remove(key)
            if (trace != null) {
                Log.d(
                    TAG,
                    "stream.finish session=$sessionId message=$messageId part=$partId type=${trace.partType} chunks=${trace.chunkCount} chars=${trace.totalChars} reason=$reason"
                )
            } else {
                Log.d(TAG, "stream.finish session=$sessionId message=$messageId part=$partId reason=$reason")
            }
            return
        }
        val matching = streamTraces.entries
            .filter { it.value.sessionId == sessionId }
            .map { it.key to it.value }
        var finished = 0
        matching.forEach { (key, trace) ->
            finished += 1
            Log.d(
                TAG,
                "stream.finish session=$sessionId message=${trace.messageId} part=${trace.partId} type=${trace.partType} chunks=${trace.chunkCount} chars=${trace.totalChars} reason=$reason"
            )
            streamTraces.remove(key)
        }
        if (finished == 0) {
            Log.d(TAG, "stream.finish session=$sessionId reason=$reason active=0")
        }
        uiTraces.remove(sessionId)
    }

    fun logUiSnapshot(
        sessionId: String,
        messageCount: Int,
        streamingParts: Int,
        streamingChars: Int,
        hasStreamingReasoning: Boolean,
        shouldAutoScroll: Boolean
    ) {
        if (!BuildConfig.DEBUG) return
        if (streamingParts <= 0 && !hasStreamingReasoning) {
            uiTraces.remove(sessionId)
            return
        }
        val now = System.currentTimeMillis()
        val previous = uiTraces[sessionId]
        val shrank = previous != null && streamingChars < previous.streamingChars
        val shouldLog = previous == null ||
            shrank ||
            messageCount != previous.messageCount ||
            streamingParts != previous.streamingParts ||
            now - previous.lastLoggedAt >= UI_LOG_INTERVAL_MS
        if (!shouldLog) return
        val trend = when {
            previous == null -> "start"
            shrank -> "shrink"
            streamingChars > previous.streamingChars -> "grow"
            streamingChars == previous.streamingChars -> "steady"
            else -> "shift"
        }
        Log.d(
            TAG,
            "ui.stream session=$sessionId messages=$messageCount parts=$streamingParts chars=$streamingChars reasoning=$hasStreamingReasoning autoScroll=$shouldAutoScroll trend=$trend"
        )
        uiTraces[sessionId] = UiTrace(
            messageCount = messageCount,
            streamingParts = streamingParts,
            streamingChars = streamingChars,
            lastLoggedAt = now
        )
    }

    private fun streamKey(sessionId: String, messageId: String, partId: String): String {
        return "$sessionId:$messageId:$partId"
    }
}
