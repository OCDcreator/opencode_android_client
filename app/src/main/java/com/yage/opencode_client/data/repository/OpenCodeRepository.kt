package com.yage.opencode_client.data.repository

import com.yage.opencode_client.data.api.*
import com.yage.opencode_client.data.model.*
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import java.util.Base64
import com.yage.opencode_client.util.AppLogger
import com.yage.opencode_client.util.LogCategory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenCodeRepository @Inject constructor() {
    private var baseUrl: String = DEFAULT_SERVER
    private var username: String? = null
    private var password: String? = null
    private var workingDirectory: String? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false  // Omit null fields - server rejects model: null
        encodeDefaults = true  // Include type in parts - server needs discriminator
    }

    private var okHttpClient: OkHttpClient = buildOkHttpClient()
    private var retrofit: Retrofit = buildRetrofit()
    private var api: OpenCodeApi = retrofit.create(OpenCodeApi::class.java)
    private var sseClient: SSEClient = SSEClient(okHttpClient)

    private fun buildOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .apply {
                        val u = username
                        val p = password
                        if (u != null && p != null) {
                            val credential = "$u:$p"
                            val encoded = Base64.getEncoder().encodeToString(credential.toByteArray())
                            header("Authorization", "Basic $encoded")
                        }
                    }
                    .build()
                chain.proceed(request)
            }
            // Retry 502/503 (frp tunnel briefly disconnected) with backoff. These are transient
            // gateway errors — the tunnel usually reconnects within 1-2 seconds, so a quick retry
            // succeeds without surfacing the error to the user. Only applied to safe GET/HEAD
            // requests and limited to 2 retries to avoid amplifying load.
            .addInterceptor { chain ->
                val request = chain.request()
                val isSafeToRetry = request.method == "GET" || request.method == "HEAD"
                if (!isSafeToRetry) {
                    chain.proceed(request)
                } else {
                    var attempt = 0
                    val maxRetries = 2
                    var response = chain.proceed(request)
                    while ((response.code == 502 || response.code == 503) && attempt < maxRetries) {
                        response.close()
                        attempt++
                        // Backoff: 500ms, then 1000ms — gives the frp tunnel time to reconnect.
                        Thread.sleep(500L * attempt)
                        response = chain.proceed(request)
                    }
                    response
                }
            }
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private fun buildRetrofit(): Retrofit {
        val url = if (baseUrl.startsWith("http")) baseUrl else "http://$baseUrl"
        return Retrofit.Builder()
            .baseUrl(url.trimEnd('/') + "/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Synchronized
    private fun rebuildClients() {
        okHttpClient = buildOkHttpClient()
        retrofit = buildRetrofit()
        api = retrofit.create(OpenCodeApi::class.java)
        sseClient = SSEClient(okHttpClient)
    }

    @Synchronized
    fun configure(
        baseUrl: String,
        username: String? = null,
        password: String? = null,
        workingDirectory: String? = null
    ) {
        this.baseUrl = baseUrl
        this.username = username
        this.password = password
        this.workingDirectory = workingDirectory?.trim()?.takeIf { it.isNotEmpty() }
        rebuildClients()
    }

    private fun effectiveDirectory(directory: String? = null): String? {
        return directory?.trim()?.takeIf { it.isNotEmpty() }
            ?: workingDirectory?.trim()?.takeIf { it.isNotEmpty() }
    }

    suspend fun checkHealth(): Result<HealthResponse> = runCatching { api.getHealth() }

    suspend fun getSessions(
        limit: Int? = null,
        directory: String? = null,
        ignoreDirectoryFilter: Boolean = false
    ): Result<List<Session>> {
        val filterDir = if (ignoreDirectoryFilter) null else effectiveDirectory(directory)
        debugLog("getSessions limit=$limit filterDir=$filterDir ignoreFilter=$ignoreDirectoryFilter")
        return runCatching {
            // Use the experimental/session endpoint — it uses listGlobal which
            // queries across all projects (no project_id isolation). The web UI
            // uses this same endpoint. Supports roots=true to hide sub-agent sessions.
            //
            // Do NOT forward the working directory as a query param: listGlobal treats
            // a present `directory` param as an EXACT string match on the session's
            // stored directory column (session.ts listGlobal), which yields 0 results
            // unless the client's working dir is byte-for-byte identical to the
            // dir the session was created in. Instead we fetch all sessions (no
            // directory param) and prefix-filter client-side below, so selecting
            // /a/b also shows sessions created under /a/b/any-project.
            val sessions = api.getSessionsExperimental(
                directory = null,
                roots = "true",
                limit = limit
            )
            val filtered = filterByDirectory(sessions, filterDir)
            debugLog("getSessions returned count=${filtered.size}/${sessions.size}")
            filtered
        }.onFailure { error ->
            debugLog("getSessions failed limit=$limit", error)
        }
    }

    /**
     * Keep only sessions whose stored directory equals [filterDir] or lives under it.
     * Null/empty [filterDir] returns all sessions. Exposed so callers can reuse the
     * same rule (e.g. to compute a "filtered out" count for empty-state hints).
     */
    fun filterByDirectory(sessions: List<Session>, filterDir: String?): List<Session> {
        if (filterDir == null) return sessions
        // Normalize both sides so a trailing slash doesn't hide matches:
        // /a/b should match sessions stored as /a/b and /a/b/project.
        val base = filterDir.trimEnd('/').trimStart('/')
        if (base.isEmpty()) return sessions
        return sessions.filter { session ->
            val dir = session.directory?.trimEnd('/')?.trimStart('/') ?: return@filter false
            dir == base || dir.startsWith("$base/")
        }
    }

    /** Check if the 400 error is the known V2 archived-timestamp serialization bug */
    private fun isV2SerializationError(e: retrofit2.HttpException): Boolean {
        val body = e.response()?.errorBody()?.string() ?: return false
        return body.contains("DateTime.Utc") && body.contains("archived")
    }

    suspend fun createSession(title: String? = null, directory: String? = null): Result<Session> = runCatching {
        api.createSession(effectiveDirectory(directory), CreateSessionRequest(title = title))
    }

    suspend fun updateSession(sessionId: String, title: String, directory: String? = null): Result<Session> = runCatching {
        api.updateSession(sessionId, effectiveDirectory(directory), UpdateSessionRequest(title = title))
    }

    suspend fun updateSessionArchived(sessionId: String, archived: Long, directory: String? = null): Result<Session> = runCatching {
        api.updateSession(sessionId, effectiveDirectory(directory), UpdateSessionRequest(time = UpdateSessionTimeRequest(archived = archived)))
    }

    suspend fun deleteSession(sessionId: String, directory: String? = null): Result<Unit> = runCatching {
        api.deleteSession(sessionId, effectiveDirectory(directory))
    }

    suspend fun getSessionStatus(): Result<Map<String, SessionStatus>> = runCatching {
        api.getSessionStatus(effectiveDirectory()).entries
    }

    suspend fun getMessages(
        sessionId: String,
        limit: Int? = null,
        directory: String? = null
    ): Result<List<MessageWithParts>> =
        runCatching { api.getMessages(sessionId, effectiveDirectory(directory), limit) }

    suspend fun sendMessage(
        sessionId: String,
        text: String,
        agent: String = "build",
        model: Message.ModelInfo? = null,
        directory: String? = null,
        imageParts: List<PromptRequest.PartInput.File> = emptyList()
    ): Result<Unit> = runCatching {
        val parts = mutableListOf<PromptRequest.PartInput>()
        if (text.isNotEmpty()) {
            parts.add(PromptRequest.PartInput.Text(text = text))
        }
        parts.addAll(imageParts)
        require(parts.isNotEmpty()) { "Message must contain text or images" }
        val request = PromptRequest(
            parts = parts,
            agent = agent,
            model = model?.let { PromptRequest.ModelInput(it.providerId, it.modelId) }
        )
        val response = api.promptAsync(sessionId, effectiveDirectory(directory), request)
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string() ?: response.message()
            throw Exception("Send failed ${response.code()}: $errorBody")
        }
    }

    suspend fun abortSession(sessionId: String, directory: String? = null): Result<Unit> = runCatching {
        api.abortSession(sessionId, effectiveDirectory(directory))
    }

    suspend fun forkSession(sessionId: String, messageId: String? = null, directory: String? = null): Result<Session> = runCatching {
        api.forkSession(sessionId, effectiveDirectory(directory), ForkSessionRequest(messageId))
    }

    suspend fun revertSession(sessionId: String, messageId: String, partId: String? = null): Result<Session> = runCatching {
        api.revertSession(sessionId, RevertSessionRequest(messageId, partId))
    }

    suspend fun getPendingPermissions(): Result<List<PermissionRequest>> = runCatching {
        api.getPendingPermissions(effectiveDirectory())
    }

    suspend fun respondPermission(
        sessionId: String,
        permissionId: String,
        response: PermissionResponse,
        directory: String? = null
    ): Result<Unit> = runCatching {
        api.respondPermission(sessionId, permissionId, effectiveDirectory(directory), PermissionResponseRequest(response.value))
    }

    suspend fun getPendingQuestions(): Result<List<QuestionRequest>> = runCatching {
        api.getPendingQuestions(effectiveDirectory())
    }

    suspend fun replyQuestion(requestId: String, answers: List<List<String>>): Result<Unit> = runCatching {
        val response = api.replyQuestion(requestId, QuestionReplyRequest(answers), effectiveDirectory())
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string() ?: response.message()
            throw Exception("Reply failed ${response.code()}: $errorBody")
        }
    }

    suspend fun rejectQuestion(requestId: String): Result<Unit> = runCatching {
        val response = api.rejectQuestion(requestId, effectiveDirectory())
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string() ?: response.message()
            throw Exception("Reject failed ${response.code()}: $errorBody")
        }
    }

    suspend fun getProviders(): Result<ProvidersResponse> = runCatching { api.getProviders(effectiveDirectory()) }

    suspend fun getAgents(): Result<List<AgentInfo>> = runCatching { api.getAgents(effectiveDirectory()) }

    suspend fun getSessionDiff(sessionId: String, directory: String? = null): Result<List<FileDiff>> = runCatching {
        api.getSessionDiff(sessionId, effectiveDirectory(directory))
    }

    suspend fun getSessionTodos(sessionId: String, directory: String? = null): Result<List<TodoItem>> = runCatching {
        api.getSessionTodos(sessionId, effectiveDirectory(directory))
    }

    suspend fun getFileTree(path: String? = null, directory: String? = null): Result<List<FileNode>> = runCatching {
        api.getFileTree(effectiveDirectory(directory), path ?: "")
    }

    suspend fun getFileContent(path: String, directory: String? = null): Result<FileContent> = runCatching {
        api.getFileContent(effectiveDirectory(directory), path)
    }

    suspend fun getFileStatus(): Result<List<FileStatusEntry>> = runCatching {
        api.getFileStatus(effectiveDirectory())
    }

    suspend fun findFile(query: String, limit: Int = 50, directory: String? = null): Result<List<String>> = runCatching {
        api.findFile(effectiveDirectory(directory), query, limit)
    }

    fun connectSSE(): Flow<Result<SSEEvent>> = sseClient.connect(baseUrl, username, password)

    private fun debugLog(message: String, throwable: Throwable? = null) {
        AppLogger.d(LogCategory.REPOSITORY, TAG, message, throwable)
    }

    companion object {
        private const val TAG = "OpenCodeRepository"
        const val DEFAULT_SERVER = "http://localhost:4096"

        /** Max safe limit for V2 API before server serialization bug (numeric archived timestamp) */
        private const val V2_SAFE_LIMIT = 10
    }
}
