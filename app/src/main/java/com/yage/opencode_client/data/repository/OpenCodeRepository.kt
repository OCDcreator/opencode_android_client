package com.yage.opencode_client.data.repository

import android.util.Log
import com.yage.opencode_client.data.api.*
import com.yage.opencode_client.data.model.*
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import java.util.Base64
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

    suspend fun getSessions(limit: Int? = null, directory: String? = null): Result<List<Session>> {
        val resolvedDirectory = effectiveDirectory(directory)
        debugLog("getSessions directory=$resolvedDirectory limit=$limit")
        return runCatching {
            // Use the experimental/session endpoint — it uses listGlobal which
            // queries across all projects (no project_id isolation). The web UI
            // uses this same endpoint. Supports roots=true to hide sub-agent sessions.
            val sessions = api.getSessionsExperimental(
                directory = resolvedDirectory,
                roots = "true",
                limit = limit
            )
            debugLog("getSessions returned count=${sessions.size}")
            sessions
        }.onFailure { error ->
            debugLog("getSessions failed directory=$resolvedDirectory limit=$limit", error)
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
        api.updateSession(sessionId, effectiveDirectory(directory), UpdateSessionRequest(title))
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
        try {
            if (throwable == null) Log.d(TAG, message) else Log.d(TAG, message, throwable)
        } catch (_: RuntimeException) {
            // android.util.Log is not mocked in local JVM unit tests.
        }
    }

    companion object {
        private const val TAG = "OpenCodeRepository"
        const val DEFAULT_SERVER = "http://localhost:4096"

        /** Max safe limit for V2 API before server serialization bug (numeric archived timestamp) */
        private const val V2_SAFE_LIMIT = 10
    }
}
