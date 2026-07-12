package com.yage.opencode_client.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yage.opencode_client.data.api.PromptRequest
import com.yage.opencode_client.data.audio.AudioRecorderManager
import com.yage.opencode_client.data.image.ImageAttachmentCompressor
import com.yage.opencode_client.data.model.*
import com.yage.opencode_client.data.repository.HostProfileStore
import com.yage.opencode_client.data.repository.OpenCodeRepository
import com.yage.opencode_client.ssh.SSHKeyManager
import com.yage.opencode_client.ssh.TunnelManager
import com.yage.opencode_client.ssh.TunnelResult
import com.yage.opencode_client.util.SettingsManager
import com.yage.opencode_client.util.LanguageMode
import com.yage.opencode_client.util.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt
import javax.inject.Inject

data class ConnectionFormSettings(
    val serverUrl: String,
    val username: String,
    val password: String,
    val workingDirectory: String,
    val recentWorkingDirectories: List<String>
)

data class AIBuilderSettings(
    val baseURL: String,
    val token: String,
    val customPrompt: String,
    val terminology: String
)

data class PendingImageUi(
    val id: String,
    val filename: String? = null,
    val thumbnail: android.graphics.Bitmap? = null,
    val dataUri: String? = null,
    val byteSize: Int = 0,
    val isProcessing: Boolean = false,
    val error: String? = null
)

data class AppState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val serverVersion: String? = null,
    val hostProfiles: List<HostProfile> = emptyList(),
    val currentHostProfileId: String? = null,
    val connectionPhase: String? = null,
    val sessions: List<Session> = emptyList(),
    val loadedSessionLimit: Int = MainViewModelTimings.sessionPageSize,
    val hasMoreSessions: Boolean = true,
    val isLoadingMoreSessions: Boolean = false,
    // Total unfiltered session count from the server (before the working-directory filter).
    // Lets the empty state hint "N sessions exist elsewhere" + offer a "show all" entry.
    val totalSessionCount: Int = 0,
    // When true, the session list ignores the working-directory filter and shows every session.
    val showAllSessions: Boolean = false,
    val expandedSessionIds: Set<String> = emptySet(),
    val currentSessionId: String? = null,
    val sessionStatuses: Map<String, SessionStatus> = emptyMap(),
    val messages: List<MessageWithParts> = emptyList(),
    val messageLimit: Int = 30,
    val isLoadingMessages: Boolean = false,
    val agents: List<AgentInfo> = emptyList(),
    val selectedAgentName: String = "build",
    val selectedModelIndex: Int = 0,
    val availableModels: List<ModelOption> = ModelPresets.list,
    val providers: ProvidersResponse? = null,
    val pendingPermissions: List<PermissionRequest> = emptyList(),
    val pendingQuestions: List<QuestionRequest> = emptyList(),
    val inputText: String = "",
    val pendingImages: List<PendingImageUi> = emptyList(),
    // Sessions with an in-flight sendMessage request. Prevents duplicate sends when the
    // user double-taps Send before currentSessionStatus flips to busy.
    val sendingSessionIds: Set<String> = emptySet(),
    // Per-session send timestamps (epoch millis). Used as a fallback start time for the
    // elapsed timer during the window between send and the server returning the user message.
    val sessionSendTimestamps: Map<String, Long> = emptyMap(),
    // Per-session todo lists, keyed by sessionId. Populated by REST getSessionTodos after
    // loading messages and kept current by the "todo.updated" SSE event.
    val sessionTodos: Map<String, List<TodoItem>> = emptyMap(),
    val error: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val languageMode: LanguageMode = LanguageMode.SYSTEM,
    val fontSizeScale: Float = 1.0f,
    val uiScale: Float = 1.0f,
    val workingDirectory: String = "",
    val filePathToShowInFiles: String? = null,
    val filePreviewOriginRoute: String? = null,
    val streamingPartTexts: Map<String, String> = emptyMap(),
    val streamingReasoningPart: Part? = null,
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val hideMicIcon: Boolean = false,
    val speechError: String? = null,
    val aiBuilderConnectionOK: Boolean = false,
    val aiBuilderConnectionError: String? = null,
    val isTestingAIBuilderConnection: Boolean = false
) {
    data class ModelOption(val displayName: String, val providerId: String, val modelId: String) {
        val shortName: String
            get() = when {
                "Opus" in displayName -> "Opus"
                "Sonnet" in displayName -> "Sonnet"
                "Haiku" in displayName -> "Haiku"
                "Gemini" in displayName -> "Gemini"
                "GPT" in displayName -> "GPT"
                "Grok" in displayName -> "Grok"
                else -> displayName.split(" ").firstOrNull() ?: displayName
            }
    }

    data class ContextUsage(
        val percentage: Float,
        val totalTokens: Int,
        val contextLimit: Int,
        val providerId: String,
        val providerLabel: String,
        val modelId: String,
        val modelLabel: String,
        val inputTokens: Int,
        val outputTokens: Int,
        val reasoningTokens: Int,
        val cacheReadTokens: Int,
        val cacheWriteTokens: Int,
        val totalCost: Double,
        val totalMessages: Int,
        val userMessages: Int,
        val assistantMessages: Int,
        val sessionTitle: String,
        val sessionCreatedAt: Long? = null,
        val lastActivityAt: Long? = null
    ) {
        val usagePercent: Int
            get() = (percentage * 100f).roundToInt()

        val remainingTokens: Int
            get() = (contextLimit - totalTokens).coerceAtLeast(0)
    }

    data class ConnectionState(
        val isConnected: Boolean = false,
        val isConnecting: Boolean = false,
        val serverVersion: String? = null
    )

    data class SessionState(
        val sessions: List<Session> = emptyList(),
        val currentSessionId: String? = null,
        val sessionStatuses: Map<String, SessionStatus> = emptyMap(),
        val expandedSessionIds: Set<String> = emptySet(),
        val loadedSessionLimit: Int = MainViewModelTimings.sessionPageSize,
        val hasMoreSessions: Boolean = true,
        val isLoadingMoreSessions: Boolean = false,
        val messageLimit: Int = 30,
        val pendingPermissions: List<PermissionRequest> = emptyList(),
        val pendingQuestions: List<QuestionRequest> = emptyList()
    ) {
        val currentSession: Session?
            get() = sessions.find { it.id == currentSessionId }

        val currentSessionStatus: SessionStatus?
            get() = currentSessionId?.let { sessionStatuses[it] }

        val isCurrentSessionBusy: Boolean
            get() = currentSessionStatus?.isBusy == true

        val canLoadMoreSessions: Boolean
            get() = hasMoreSessions && !isLoadingMoreSessions
    }

    data class ChatState(
        val messages: List<MessageWithParts> = emptyList(),
        val streamingPartTexts: Map<String, String> = emptyMap(),
        val streamingReasoningPart: Part? = null,
        val isLoadingMessages: Boolean = false,
        val inputText: String = ""
    )

    data class SpeechState(
        val isRecording: Boolean = false,
        val isTranscribing: Boolean = false,
        val speechError: String? = null,
        val isTestingAIBuilderConnection: Boolean = false,
        val aiBuilderConnectionOK: Boolean = false,
        val aiBuilderConnectionError: String? = null
    )

    data class FileUiState(
        val filePathToShowInFiles: String? = null,
        val filePreviewOriginRoute: String? = null
    )

    data class SettingsState(
        val error: String? = null,
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val languageMode: LanguageMode = LanguageMode.SYSTEM,
        val fontSizeScale: Float = 1.0f,
        val uiScale: Float = 1.0f,
        val workingDirectory: String = "",
        val selectedModelIndex: Int = 0,
        val selectedAgentName: String = "build",
        val availableModels: List<ModelOption> = ModelPresets.list,
        val contextUsage: ContextUsage? = null,
        val agents: List<AgentInfo> = emptyList(),
        val providers: ProvidersResponse? = null,
        val isRecording: Boolean = false,
        val hideMicIcon: Boolean = false
    )

    val connectionState: ConnectionState
        get() = ConnectionState(
            isConnected = isConnected,
            isConnecting = isConnecting,
            serverVersion = serverVersion
        )

    val sessionState: SessionState
        get() = SessionState(
            sessions = sessions,
            currentSessionId = currentSessionId,
            sessionStatuses = sessionStatuses,
            expandedSessionIds = expandedSessionIds,
            loadedSessionLimit = loadedSessionLimit,
            hasMoreSessions = hasMoreSessions,
            isLoadingMoreSessions = isLoadingMoreSessions,
            messageLimit = messageLimit,
            pendingPermissions = pendingPermissions,
            pendingQuestions = pendingQuestions
        )

    val chatState: ChatState
        get() = ChatState(
            messages = messages,
            streamingPartTexts = streamingPartTexts,
            streamingReasoningPart = streamingReasoningPart,
            isLoadingMessages = isLoadingMessages,
            inputText = inputText
        )

    val speechState: SpeechState
        get() = SpeechState(
            isRecording = isRecording,
            isTranscribing = isTranscribing,
            speechError = speechError,
            isTestingAIBuilderConnection = isTestingAIBuilderConnection,
            aiBuilderConnectionOK = aiBuilderConnectionOK,
            aiBuilderConnectionError = aiBuilderConnectionError
        )

    val fileUiState: FileUiState
        get() = FileUiState(
            filePathToShowInFiles = filePathToShowInFiles,
            filePreviewOriginRoute = filePreviewOriginRoute
        )

    val settingsState: SettingsState
        get() = SettingsState(
            error = error,
            themeMode = themeMode,
            languageMode = languageMode,
            fontSizeScale = fontSizeScale,
            uiScale = uiScale,
            workingDirectory = workingDirectory,
            selectedModelIndex = selectedModelIndex,
            selectedAgentName = selectedAgentName,
            availableModels = availableModels,
            contextUsage = contextUsage,
            agents = agents,
            providers = providers,
            isRecording = isRecording,
            hideMicIcon = hideMicIcon
        )

    val currentSession: Session?
        get() = sessions.find { it.id == currentSessionId }

    val currentSessionStatus: SessionStatus?
        get() = currentSessionId?.let { sessionStatuses[it] }

    val isCurrentSessionBusy: Boolean
        get() = currentSessionStatus?.isBusy == true ||
            (currentSessionId != null && currentSessionId in sendingSessionIds)

    val canLoadMoreSessions: Boolean
        get() = hasMoreSessions && !isLoadingMoreSessions

    val visibleAgents: List<AgentInfo>
        get() = agents.filter { it.isVisible }

    private data class ProviderModelLookup(
        val provider: ConfigProvider,
        val modelId: String,
        val model: ProviderModel
    )

    private val providerModelsIndex: Map<String, ProviderModelLookup>
        get() = providers?.providers?.flatMap { provider ->
            provider.models.map { (modelId, model) ->
                "${provider.id}/${modelId}" to ProviderModelLookup(
                    provider = provider,
                    modelId = modelId,
                    model = model
                )
            }
        }?.toMap() ?: emptyMap()

    val contextUsage: ContextUsage?
        get() {
            val lastAssistant = messages.lastOrNull {
                it.info.isAssistant && it.info.tokens?.totalTokenCount() != null
            }
                ?: return null
            val tokens = lastAssistant.info.tokens ?: return null
            val total = tokens.totalTokenCount() ?: return null
            val model = lastAssistant.info.resolvedModel ?: return null
            val key = "${model.providerId}/${model.modelId}"
            val providerModel = providerModelsIndex[key] ?: return null
            val limit = providerModel.model.limit?.context ?: return null
            if (limit <= 0) return null
            return ContextUsage(
                percentage = (total.toFloat() / limit.toFloat()).coerceIn(0f, 1f),
                totalTokens = total,
                contextLimit = limit,
                providerId = model.providerId,
                providerLabel = providerModel.provider.name ?: model.providerId,
                modelId = model.modelId,
                modelLabel = providerModel.model.name ?: providerModel.modelId,
                inputTokens = tokens.input ?: 0,
                outputTokens = tokens.output ?: 0,
                reasoningTokens = tokens.reasoning ?: 0,
                cacheReadTokens = tokens.cache?.read ?: 0,
                cacheWriteTokens = tokens.cache?.write ?: 0,
                totalCost = messages.sumOf { message ->
                    if (message.info.isAssistant) message.info.cost ?: 0.0 else 0.0
                },
                totalMessages = messages.size,
                userMessages = messages.count { it.info.isUser },
                assistantMessages = messages.count { it.info.isAssistant },
                sessionTitle = currentSession?.displayName ?: currentSessionId ?: "",
                sessionCreatedAt = currentSession?.time?.created,
                lastActivityAt = lastAssistant.info.time?.created
            )
        }
}

private fun Message.TokenInfo.totalTokenCount(): Int? {
    total?.let { return it }
    val sum = (input ?: 0) + (output ?: 0) + (reasoning ?: 0) + (cache?.read ?: 0) + (cache?.write ?: 0)
    return sum.takeIf { it > 0 }
}

@HiltViewModel
class MainViewModel @Inject constructor(
    internal val repository: OpenCodeRepository,
    private val settingsManager: SettingsManager,
    private val audioRecorderManager: AudioRecorderManager,
    private val imageCompressor: ImageAttachmentCompressor,
    private val hostProfileStore: HostProfileStore,
    private val tunnelManager: TunnelManager,
    private val sshKeyManager: SSHKeyManager
) : ViewModel() {

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _recreateEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val recreateEvent: SharedFlow<Unit> = _recreateEvent.asSharedFlow()

    private var sseJob: Job? = null
    private var pollJob: Job? = null
    private var lastHealthCheckTime = 0L
    private val imageDataUris = mutableMapOf<String, String>()
    private val compressionJobs = mutableMapOf<String, Job>()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        applySavedSettings(repository, settingsManager, hostProfileStore, _state)
    }

    fun configureServer(
        url: String,
        username: String? = null,
        password: String? = null,
        workingDirectory: String? = null
    ) {
        settingsManager.serverUrl = url
        settingsManager.username = username
        settingsManager.password = password
        settingsManager.workingDirectory = workingDirectory.orEmpty()
        settingsManager.rememberWorkingDirectory(workingDirectory)
        repository.configure(url, username, password, workingDirectory)
        _state.update { it.copy(workingDirectory = settingsManager.workingDirectory) }
        lastHealthCheckTime = 0L
        if (_state.value.isConnected) {
            loadInitialData()
        }
    }

    /**
     * Update the active connection AND persist the changes into the currently selected host
     * profile. This is the save/test handler for the Server Connection section: editing the
     * fields below updates the current profile rather than acting as an independent config.
     */
    fun updateCurrentProfileConnection(
        serverUrl: String,
        username: String?,
        password: String?,
        workingDirectory: String?
    ) {
        // Update settingsManager (the source of truth for the active connection)
        settingsManager.serverUrl = serverUrl
        settingsManager.username = username
        settingsManager.password = password
        settingsManager.workingDirectory = workingDirectory.orEmpty()
        settingsManager.rememberWorkingDirectory(workingDirectory)
        repository.configure(serverUrl, username, password, workingDirectory)
        _state.update { it.copy(workingDirectory = settingsManager.workingDirectory) }
        lastHealthCheckTime = 0L

        // Also persist into the current profile so it's remembered per-profile
        val current = hostProfileStore.currentProfile()
        val updatedProfile = current.copy(
            serverUrl = serverUrl,
            basicAuth = if (!username.isNullOrBlank()) {
                current.basicAuth?.copy(username = username)
                    ?: BasicAuthConfig(username = username, passwordId = current.id)
            } else {
                current.basicAuth
            },
            workingDirectory = workingDirectory.orEmpty()
        )
        if (updatedProfile.basicAuth != null) {
            settingsManager.setBasicAuthPassword(updatedProfile.id, password)
        }
        hostProfileStore.save(updatedProfile)
        refreshHostProfileState()
    }

    fun getSavedConnectionSettings(): ConnectionFormSettings = ConnectionFormSettings(
        serverUrl = settingsManager.serverUrl,
        username = settingsManager.username ?: "",
        password = settingsManager.password ?: "",
        workingDirectory = settingsManager.workingDirectory,
        recentWorkingDirectories = settingsManager.getRecentWorkingDirectories()
    )

    fun getHostProfiles(): List<HostProfile> = hostProfileStore.profiles()

    fun currentHostProfile(): HostProfile = hostProfileStore.currentProfile()

    fun saveHostProfile(profile: HostProfile, basicAuthPassword: String? = null) {
        val normalized = if (profile.basicAuth != null) {
            profile.copy(basicAuth = profile.basicAuth.copy(passwordId = profile.id))
        } else {
            profile
        }
        if (normalized.basicAuth != null) {
            settingsManager.setBasicAuthPassword(normalized.id, basicAuthPassword)
        }
        hostProfileStore.save(normalized)

        // If we just saved the currently-active profile, sync its connection settings into
        // settingsManager so the rest of the app (repository, Server Connection form) stays
        // consistent. Without this, editing a profile's serverUrl in the dialog would persist
        // to the profile JSON but never reach settingsManager, causing stale values on refresh.
        val currentId = hostProfileStore.currentProfile().id
        if (normalized.id == currentId) {
            val password = normalized.basicAuth?.passwordId?.let { settingsManager.basicAuthPassword(it) }
            if (normalized.transport == HostTransport.SSH_TUNNEL) {
                // SSH tunnel: must start the tunnel to get the local port. Don't use profile.serverUrl
                // (it's irrelevant metadata in SSH mode). Sync working directory + credentials only;
                // the tunnel restart will set settingsManager.serverUrl to the local port.
                settingsManager.workingDirectory = normalized.workingDirectory
                if (normalized.basicAuth != null && !password.isNullOrBlank()) {
                    settingsManager.username = normalized.basicAuth.username
                    settingsManager.password = password
                }
                _state.update { it.copy(workingDirectory = normalized.workingDirectory) }
                viewModelScope.launch {
                    configureRepositoryForProfileAsync(normalized)
                }
            } else {
                syncSettingsManagerFromProfile(normalized, password)
                repository.configure(
                    normalized.serverUrl,
                    normalized.basicAuth?.username,
                    password,
                    normalized.workingDirectory
                )
            }
            lastHealthCheckTime = 0L
        }

        refreshHostProfileState()
    }

    fun selectHostProfile(profileId: String) {
        viewModelScope.launch {
            val profile = hostProfileStore.select(profileId)
            configureRepositoryForProfileAsync(profile)
            refreshHostProfileState()
            testConnection()
        }
    }

    fun duplicateHostProfile(profileId: String) {
        hostProfileStore.duplicate(profileId)
        refreshHostProfileState()
    }

    fun deleteHostProfile(profileId: String) {
        hostProfileStore.delete(profileId)
        val current = hostProfileStore.currentProfile()
        configureRepositoryForProfile(current, startTunnel = false)
        refreshHostProfileState()
    }

    fun importHostProfile(payload: String): Result<HostProfile> = runCatching {
        hostProfileStore.importJson(payload).also { refreshHostProfileState() }
    }

    fun exportHostProfile(profile: HostProfile): String = hostProfileStore.exportJson(profile)

    fun ensureSshPublicKey(): String = sshKeyManager.ensureKeyPair()

    fun sshPublicKey(): String? = sshKeyManager.publicKey()

    fun rotateSshKey(): String = sshKeyManager.rotateKey()

    private fun refreshHostProfileState() {
        _state.update {
            it.copy(
                hostProfiles = hostProfileStore.profiles(),
                currentHostProfileId = hostProfileStore.currentProfile().id
            )
        }
    }

    private fun configureRepositoryForProfile(profile: HostProfile, startTunnel: Boolean) {
        val password = profile.basicAuth?.passwordId?.let { settingsManager.basicAuthPassword(it) }
        if (profile.transport == HostTransport.SSH_TUNNEL && startTunnel) {
            viewModelScope.launch { configureRepositoryForProfileAsync(profile) }
            return
        }
        // Sync profile -> settingsManager so the Server Connection section reflects the active profile.
        syncSettingsManagerFromProfile(profile, password)
        repository.configure(profile.serverUrl, profile.basicAuth?.username, password, profile.workingDirectory)
    }

    private suspend fun configureRepositoryForProfileAsync(profile: HostProfile): Boolean {
        val password = profile.basicAuth?.passwordId?.let { settingsManager.basicAuthPassword(it) }
        val baseUrl = when (profile.transport) {
            HostTransport.DIRECT -> profile.serverUrl
            HostTransport.SSH_TUNNEL -> {
                val ssh = profile.ssh ?: run {
                    _state.update { it.copy(error = "SSH profile is missing tunnel settings") }
                    return false
                }
                when (val result = tunnelManager.ensureStarted(ssh)) {
                    is TunnelResult.Success -> result.localUrl
                    is TunnelResult.Failure -> {
                        _state.update {
                            it.copy(
                                isConnected = false,
                                isConnecting = false,
                                connectionPhase = result.phase.name,
                                error = result.message
                            )
                        }
                        return false
                    }
                }
            }
        }
        // Configure repository with the effective baseUrl (direct URL or tunnel local port).
        repository.configure(baseUrl, profile.basicAuth?.username, password, profile.workingDirectory)
        // Sync settingsManager so the app stays consistent. For SSH tunnel mode, the effective
        // connection URL is the local tunnel port (e.g. http://127.0.0.1:4096), NOT profile.serverUrl
        // — profile.serverUrl is irrelevant metadata in SSH mode. Writing it to settingsManager would
        // cause applySavedSettings to connect to the wrong address on app restart (before the tunnel starts).
        syncSettingsManagerFromProfile(profile, password, effectiveBaseUrl = baseUrl)
        return true
    }

    /**
     * Mirror the active [profile]'s connection fields into [settingsManager] so the
     * Server Connection section below the Host Profiles section stays in sync with the
     * currently selected profile. Each profile remembers its own working directory.
     */
    private fun syncSettingsManagerFromProfile(profile: HostProfile, password: String?, effectiveBaseUrl: String? = null) {
        // For SSH tunnel mode, the effective base URL is the local tunnel port (e.g. http://127.0.0.1:4096),
        // not profile.serverUrl. For DIRECT mode, effectiveBaseUrl is null so we use profile.serverUrl.
        settingsManager.serverUrl = effectiveBaseUrl ?: profile.serverUrl
        settingsManager.username = profile.basicAuth?.username
        // Only update the legacy password if the profile actually has one, so switching to an
        // SSH profile (no basic auth) doesn't wipe the legacy password that other DIRECT profiles
        // still reference via LEGACY_BASIC_AUTH_PASSWORD_ID.
        if (profile.basicAuth != null && !password.isNullOrBlank()) {
            settingsManager.password = password
        }
        settingsManager.workingDirectory = profile.workingDirectory
        if (profile.workingDirectory.isNotBlank()) {
            settingsManager.rememberWorkingDirectory(profile.workingDirectory)
        }
        _state.update { it.copy(workingDirectory = profile.workingDirectory) }
    }

    fun getAIBuilderSettings(): AIBuilderSettings = AIBuilderSettings(
        baseURL = settingsManager.aiBuilderBaseURL,
        token = settingsManager.aiBuilderToken,
        customPrompt = settingsManager.aiBuilderCustomPrompt,
        terminology = settingsManager.aiBuilderTerminology
    )

    fun saveAIBuilderSettings(settings: AIBuilderSettings) {
        settingsManager.aiBuilderBaseURL = settings.baseURL
        settingsManager.aiBuilderToken = settings.token
        settingsManager.aiBuilderCustomPrompt = settings.customPrompt
        settingsManager.aiBuilderTerminology = settings.terminology
        _state.update { it.copy(aiBuilderConnectionOK = false, aiBuilderConnectionError = null) }
        settingsManager.aiBuilderLastOKSignature = null
    }

    fun testAIBuilderConnection() {
        launchAIBuilderConnectionTest(viewModelScope, settingsManager, _state)
    }

    fun toggleRecording() {
        val currentState = _state.value
        val speechConfig = currentSpeechInputConfig(settingsManager)
        Log.d(
            TAG,
            "toggleRecording clicked: recording=${currentState.isRecording}, transcribing=${currentState.isTranscribing}, aiBuilderOK=${currentState.aiBuilderConnectionOK}, tokenSet=${speechConfig.token.isNotEmpty()}"
        )
        if (currentState.isTranscribing) {
            Log.w(TAG, "Ignoring toggle while transcription is in progress")
            _state.update {
                it.copy(speechError = "Still transcribing previous audio, please wait.")
            }
            return
        }
        if (currentState.isRecording) {
            val file = audioRecorderManager.stop()
            _state.update { it.copy(isRecording = false, isTranscribing = true) }
            if (file == null) {
                Log.e(TAG, "Recording stop returned null file")
                _state.update { it.copy(isTranscribing = false, speechError = "Recording failed: no file") }
                return
            }
            launchSpeechTranscription(
                scope = viewModelScope,
                state = _state,
                audioRecorderManager = audioRecorderManager,
                config = speechConfig,
                recordingFile = file,
                existingInput = currentState.inputText,
                tag = TAG
            )
        } else {
            if (speechConfig.token.isEmpty()) {
                Log.w(TAG, "Speech start blocked: missing AI Builder token")
                _state.update {
                    it.copy(speechError = "Speech recognition requires an AI Builder token. Configure it in Settings.")
                }
                return
            }
            if (!currentState.aiBuilderConnectionOK) {
                Log.w(TAG, "Speech start blocked: AI Builder connection test has not passed")
                _state.update {
                    it.copy(speechError = "AI Builder connection test has not passed. Please test in Settings first.")
                }
                return
            }
            try {
                audioRecorderManager.start()
                Log.d(TAG, "Recording started")
                _state.update { it.copy(isRecording = true) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recording", e)
                _state.update { it.copy(speechError = "Failed to start recording: ${errorMessageOrFallback(e, "unknown error")}") }
            }
        }
    }

    fun clearSpeechError() {
        _state.update { it.copy(speechError = null) }
    }

    fun addImage(uri: android.net.Uri, contentResolver: android.content.ContentResolver) {
        val sessionId = _state.value.currentSessionId ?: return
        if (_state.value.pendingImages.size >= 5) {
            _state.update { it.copy(error = "Maximum 5 images allowed") }
            return
        }
        val id = java.util.UUID.randomUUID().toString()
        _state.update { it.copy(pendingImages = it.pendingImages + PendingImageUi(id, isProcessing = true)) }
        val capturedSessionId = sessionId
        val job = viewModelScope.launch {
            try {
                val result = imageCompressor.compress(contentResolver, uri)
                if (_state.value.currentSessionId != capturedSessionId) return@launch
                if (!_state.value.pendingImages.any { it.id == id }) return@launch
                if (result.byteSize > 2 * 1024 * 1024) {
                    val sizeMb = result.byteSize / (1024.0 * 1024.0)
                    _state.update { s ->
                        s.copy(pendingImages = s.pendingImages.map {
                            if (it.id == id) it.copy(isProcessing = false, error = "Image too large after compression (%.1f MB)".format(sizeMb))
                            else it
                        })
                    }
                    return@launch
                }
                imageDataUris[id] = result.dataUri
                _state.update { s ->
                    s.copy(pendingImages = s.pendingImages.map {
                        if (it.id == id) it.copy(
                            isProcessing = false,
                            thumbnail = result.thumbnail,
                            dataUri = result.dataUri,
                            byteSize = result.byteSize,
                            filename = result.filename
                        )
                        else it
                    })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_state.value.currentSessionId != capturedSessionId) return@launch
                _state.update { s ->
                    s.copy(pendingImages = s.pendingImages.map {
                        if (it.id == id) it.copy(isProcessing = false, error = "Failed to process image: ${e.message}")
                        else it
                    })
                }
            } finally {
                compressionJobs.remove(id)
            }
        }
        compressionJobs[id] = job
    }

    fun removeImage(id: String) {
        compressionJobs[id]?.cancel()
        compressionJobs.remove(id)
        imageDataUris.remove(id)
        _state.update { it.copy(pendingImages = it.pendingImages.filter { img -> img.id != id }) }
    }

    fun setSpeechError(message: String) {
        _state.update { it.copy(speechError = message) }
    }

    fun testConnection() {
        val now = System.currentTimeMillis()
        if (now - lastHealthCheckTime < 30_000) return
        lastHealthCheckTime = now
        _state.update { it.copy(connectionPhase = "connecting") }
        launchConnectionTest(viewModelScope, repository, _state) {
            loadInitialData()
            startSSE()
            startBusyPolling()
        }
    }

    private fun loadInitialData() {
        loadSessions()
        loadAgents()
        loadProviders()
        loadPendingQuestions()
    }

    fun loadSessions() {
        launchLoadSessions(
            scope = viewModelScope,
            repository = repository,
            state = _state,
            onSelectSession = ::selectSession,
            onLoadSessionStatus = ::loadSessionStatus,
            onLoadMessages = { sessionId -> loadMessages(sessionId) }
        )
    }

    fun loadMoreSessions() {
        launchLoadMoreSessions(
            scope = viewModelScope,
            repository = repository,
            state = _state,
            onSelectSession = ::selectSession
        )
    }

    /**
     * Toggle whether the session list ignores the working-directory filter.
     * Reloads sessions so the list reflects the new mode immediately.
     */
    fun setShowAllSessions(showAll: Boolean) {
        _state.update { it.copy(showAllSessions = showAll) }
        loadSessions()
    }

    private fun loadSessionStatus() {
        launchLoadSessionStatus(viewModelScope, repository, _state)
    }

    fun selectSession(sessionId: String) {
        compressionJobs.values.forEach { it.cancel() }
        compressionJobs.clear()
        imageDataUris.clear()
        _state.update { it.copy(pendingImages = emptyList()) }
        selectSessionState(_state, settingsManager, sessionId)
        loadMessages(sessionId)
        loadSessionStatus()
    }

    fun loadMessages(sessionId: String, resetLimit: Boolean = true) {
        launchLoadMessages(viewModelScope, repository, _state, sessionId, resetLimit, settingsManager)
    }

    /** Load messages with delay when triggered by SSE/send (server may need time to persist). */
    private fun loadMessagesWithRetry(sessionId: String, resetLimit: Boolean = true) {
        launchLoadMessagesWithRetry(viewModelScope, sessionId, _state, resetLimit, ::loadMessages)
    }

    fun loadMoreMessages() {
        val sessionId = _state.value.currentSessionId ?: return
        launchLoadMoreMessages(viewModelScope, repository, _state, sessionId)
    }

    private fun loadAgents() {
        viewModelScope.launch {
            repository.getAgents()
                .onSuccess { agents ->
                    _state.update { it.copy(agents = agents) }
                }
                .onFailure { error ->
                    reportNonFatalIssue(TAG, "Failed to load agents", error)
                }
        }
    }

    private fun loadProviders() {
        launchLoadProviders(viewModelScope, repository, _state, settingsManager) { message, error ->
            reportNonFatalIssue(TAG, message, error)
        }
    }

    fun createSession(title: String? = null) {
        launchCreateSession(viewModelScope, repository, _state, title, ::selectSession)
    }

    fun forkSession(sessionId: String, messageId: String?) {
        launchForkSession(viewModelScope, repository, _state, sessionId, messageId, ::selectSession)
    }

    fun updateSessionTitle(sessionId: String, title: String) {
        launchUpdateSessionTitle(viewModelScope, repository, _state, sessionId, title)
    }

    fun archiveSession(sessionId: String) {
        launchSetSessionArchived(viewModelScope, repository, _state, sessionId, archived = true)
    }

    fun restoreSession(sessionId: String) {
        launchSetSessionArchived(viewModelScope, repository, _state, sessionId, archived = false)
    }

    fun deleteSession(sessionId: String) {
        launchDeleteSession(viewModelScope, repository, _state, sessionId, ::selectSession)
    }

    fun sendMessage() {
        val sessionId = _state.value.currentSessionId ?: return
        if (sessionId in _state.value.sendingSessionIds) return
        val text = _state.value.inputText.trim()
        val images = _state.value.pendingImages

        if (text.isEmpty() && images.isEmpty()) return
        if (images.any { it.isProcessing || it.error != null }) return
        if (images.any { !imageDataUris.containsKey(it.id) }) return

        _state.update {
            it.copy(
                sendingSessionIds = it.sendingSessionIds + sessionId,
                sessionSendTimestamps = it.sessionSendTimestamps + (sessionId to System.currentTimeMillis())
            )
        }

        val currentSession = _state.value.currentSession

        // If the session is archived, auto-restore before sending.
        if (currentSession?.isArchived == true) {
            viewModelScope.launch {
                repository.updateSessionArchived(sessionId, -1L, settingsManager.workingDirectory.ifEmpty { null })
                    .onSuccess { updated ->
                        _state.update { state ->
                            state.copy(sessions = state.sessions.map { session -> if (session.id == sessionId) updated else session })
                        }
                    }
                    .onFailure { error ->
                        _state.update { it.copy(error = "Failed to restore session: ${errorMessageOrFallback(error, "unknown error")}") }
                        return@launch
                    }
            }
            // Continue to send — the server processes the restore and the message sequentially.
        }

        val agent = _state.value.selectedAgentName
        val model = buildSelectedModel(_state.value)
        StreamDebugLogger.logSendRequested(
            sessionId = sessionId,
            textLength = text.length,
            agent = agent,
            model = model
        )

        val imageParts = images.map { ui ->
            PromptRequest.PartInput.File(
                mime = "image/jpeg",
                url = imageDataUris[ui.id]!!,
                filename = ui.filename
            )
        }

        launchSendMessage(
            scope = viewModelScope,
            repository = repository,
            state = _state,
            sessionId = sessionId,
            text = text,
            agent = agent,
            model = model,
            imageParts = imageParts,
            onRefreshMessages = ::loadMessagesWithRetry,
            onSuccess = {
                settingsManager.setDraftText(sessionId, "")
                val sentIds = images.map { it.id }.toSet()
                sentIds.forEach { id ->
                    imageDataUris.remove(id)
                    compressionJobs[id]?.cancel()
                    compressionJobs.remove(id)
                }
                _state.update { it.copy(pendingImages = emptyList()) }
            },
            onComplete = {
                _state.update {
                    it.copy(
                        sendingSessionIds = it.sendingSessionIds - sessionId,
                        sessionSendTimestamps = it.sessionSendTimestamps - sessionId
                    )
                }
            }
        )
    }

    fun abortSession() {
        val sessionId = _state.value.currentSessionId ?: return
        viewModelScope.launch {
            repository.abortSession(sessionId)
                .onFailure { error ->
                    _state.update { it.copy(error = errorMessageOrFallback(error, "Failed to abort session")) }
                }
        }
    }

    fun setInputText(text: String) {
        _state.update { it.copy(inputText = text) }
        _state.value.currentSessionId?.let { settingsManager.setDraftText(it, text) }
    }

    fun selectAgent(agentName: String) {
        settingsManager.selectedAgentName = agentName
        _state.update { it.copy(selectedAgentName = agentName) }
        _state.value.currentSessionId?.let { settingsManager.setAgentForSession(it, agentName) }
    }

    fun toggleSessionExpanded(sessionId: String) {
        _state.update { state ->
            val next = if (state.expandedSessionIds.contains(sessionId)) {
                state.expandedSessionIds - sessionId
            } else {
                state.expandedSessionIds + sessionId
            }
            state.copy(expandedSessionIds = next)
        }
    }

    fun selectModel(index: Int) {
        val models = _state.value.availableModels
        val clamped = index.coerceIn(0, max(models.size - 1, 0))
        val key = models.getOrNull(clamped)?.let { "${it.providerId}/${it.modelId}" } ?: ""
        settingsManager.selectedModelKey = key
        _state.update { it.copy(selectedModelIndex = clamped) }
        _state.value.currentSessionId?.let { settingsManager.setModelForSession(it, key) }
    }

    fun setThemeMode(mode: ThemeMode) {
        settingsManager.themeMode = mode
        _state.update { it.copy(themeMode = mode) }
    }

    fun setLanguageMode(mode: LanguageMode) {
        settingsManager.languageMode = mode
        _state.update { it.copy(languageMode = mode) }
        _recreateEvent.tryEmit(Unit)
    }

    fun setHideMicIcon(hide: Boolean) {
        settingsManager.hideMicIcon = hide
        _state.update { it.copy(hideMicIcon = hide) }
    }

    fun setFontSizeScale(scale: Float, persist: Boolean = true) {
        val normalized = (scale.coerceIn(0.7f, 1.6f) * 100).roundToInt() / 100f
        if (persist) {
            settingsManager.fontSizeScale = normalized
        }
        _state.update { it.copy(fontSizeScale = normalized) }
    }

    fun persistFontSizeScale() {
        settingsManager.fontSizeScale = _state.value.fontSizeScale
    }

    fun setUiScale(scale: Float, persist: Boolean = true) {
        val normalized = (scale.coerceIn(0.65f, 1.35f) * 100).roundToInt() / 100f
        if (persist) {
            settingsManager.uiScale = normalized
        }
        _state.update { it.copy(uiScale = normalized) }
    }

    fun persistUiScale() {
        settingsManager.uiScale = _state.value.uiScale
    }

    fun respondPermission(sessionId: String, permissionId: String, response: PermissionResponse) {
        viewModelScope.launch {
            repository.respondPermission(sessionId, permissionId, response)
                .onSuccess {
                    _state.update { it.copy(
                        pendingPermissions = it.pendingPermissions.filter { p -> p.id != permissionId }
                    )}
                }
                .onFailure { error ->
                    _state.update { it.copy(error = errorMessageOrFallback(error, "Failed to respond to permission")) }
                }
        }
    }

    fun loadPendingPermissions() {
        viewModelScope.launch {
            repository.getPendingPermissions()
                .onSuccess { permissions ->
                    _state.update { it.copy(pendingPermissions = permissions) }
                }
                .onFailure { error ->
                    Log.w(TAG, "Failed to load permissions: ${error.message}")
                }
        }
    }

    fun loadPendingQuestions() {
        viewModelScope.launch {
            repository.getPendingQuestions()
                .onSuccess { questions ->
                    _state.update { it.copy(pendingQuestions = questions) }
                }
                .onFailure { error ->
                    Log.w(TAG, "Failed to load questions: ${error.message}")
                }
        }
    }

    fun replyQuestion(requestId: String, answers: List<List<String>>, onError: () -> Unit = {}) {
        viewModelScope.launch {
            repository.replyQuestion(requestId, answers)
                .onSuccess {
                    _state.update { currentState ->
                        currentState.copy(pendingQuestions = currentState.pendingQuestions.filter { it.id != requestId })
                    }
                }
                .onFailure { error ->
                    Log.w(TAG, "Failed to reply question: ${error.message}")
                    onError()
                }
        }
    }

    fun rejectQuestion(requestId: String) {
        viewModelScope.launch {
            repository.rejectQuestion(requestId)
                .onSuccess {
                    _state.update { currentState ->
                        currentState.copy(pendingQuestions = currentState.pendingQuestions.filter { it.id != requestId })
                    }
                }
                .onFailure { error ->
                    Log.w(TAG, "Failed to reject question: ${error.message}")
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun showFileInFiles(path: String, originRoute: String? = null) {
        _state.update { it.copy(filePathToShowInFiles = path, filePreviewOriginRoute = originRoute) }
    }

    fun clearFileToShow() {
        _state.update { it.copy(filePathToShowInFiles = null, filePreviewOriginRoute = null) }
    }

    /** Poll loadMessages every 2s when session is busy, as SSE fallback. */
    private fun startBusyPolling() {
        pollJob?.cancel()
        pollJob = launchBusyPolling(viewModelScope, _state, ::loadMessages)
    }

    private fun startSSE() {
        sseJob?.cancel()
        sseJob = launchSseCollection(viewModelScope, repository, _state, ::handleSSEEvent)
    }

    private fun handleSSEEvent(event: SSEEvent) {
        handleIncomingSseEvent(
            state = _state,
            event = event,
            onRefreshMessages = ::loadMessagesWithRetry,
            onLoadPendingPermissions = ::loadPendingPermissions,
            onNonFatalIssue = { message -> reportNonFatalIssue(TAG, message) }
        )
    }

    override fun onCleared() {
        super.onCleared()
        sseJob?.cancel()
        pollJob?.cancel()
    }

    private companion object {
        private const val TAG = "MainViewModel"
    }
}
