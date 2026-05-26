package com.yage.opencode_client.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val id: String,
    val slug: String? = null,
    @SerialName("projectID") val projectId: String? = null,
    val directory: String? = null,
    @SerialName("parentID") val parentId: String? = null,
    val title: String? = null,
    val version: String? = null,
    val time: TimeInfo? = null,
    val share: ShareInfo? = null,
    val summary: SummaryInfo? = null,
    val cost: Double? = null
) {
    /** Display name for UI: title, or last path segment of directory, or id */
    val displayName: String
        get() = title ?: directory?.split("/")?.filter { it.isNotEmpty() }?.lastOrNull() ?: id

    @Serializable
    data class TimeInfo(
        val created: Long? = null,
        val updated: Long? = null,
        val archived: Long? = null
    )

    @Serializable
    data class ShareInfo(
        val url: String? = null
    )

    @Serializable
    data class SummaryInfo(
        val additions: Int? = null,
        val deletions: Int? = null,
        val files: Int? = null
    )
}

@Serializable
data class SessionStatus(
    val type: String,
    val attempt: Int? = null,
    val message: String? = null,
    val next: Long? = null
) {
    val isIdle: Boolean get() = type == "idle"
    val isBusy: Boolean get() = type == "busy"
    val isRetry: Boolean get() = type == "retry"
}

/** V2 API paginated response wrapper */
@Serializable
data class SessionsResponse(
    val items: List<Session> = emptyList(),
    val cursor: SessionCursor? = null
)

@Serializable
data class SessionCursor(
    val previous: String? = null,
    val next: String? = null
)
