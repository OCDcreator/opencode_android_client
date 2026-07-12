package com.yage.opencode_client.ui

import android.util.Log
import com.yage.opencode_client.data.audio.AIBuildersAudioClient
import com.yage.opencode_client.data.repository.HostProfileStore
import com.yage.opencode_client.data.repository.OpenCodeRepository
import com.yage.opencode_client.util.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun applySavedSettings(
    repository: OpenCodeRepository,
    settingsManager: SettingsManager,
    hostProfileStore: HostProfileStore,
    state: MutableStateFlow<AppState>
) {
    debugConnectionLog(
        "applySavedSettings serverUrl=${settingsManager.serverUrl} " +
            "workingDirectory=${settingsManager.workingDirectory}"
    )

    // Load host profiles into state. The store auto-migrates legacy settings into a
    // default profile on first access, so profiles() never returns empty in production.
    val profiles = hostProfileStore.profiles()
    val currentProfile = if (profiles.isNotEmpty()) hostProfileStore.currentProfile() else null
    val currentProfileId = currentProfile?.id ?: hostProfileStore.currentProfile().id

    // Prefer the current profile's connection settings so the active profile is honored
    // at startup; fall back to raw settingsManager values when no profiles exist.
    val effectiveUrl = currentProfile?.serverUrl?.ifBlank { null } ?: settingsManager.serverUrl
    val effectiveUsername = currentProfile?.basicAuth?.username ?: settingsManager.username
    val effectivePassword = currentProfile?.basicAuth?.passwordId
        ?.let { settingsManager.basicAuthPassword(it) } ?: settingsManager.password
    val effectiveWorkingDir = currentProfile?.workingDirectory?.ifBlank { null }
        ?: settingsManager.workingDirectory

    repository.configure(
        baseUrl = effectiveUrl,
        username = effectiveUsername,
        password = effectivePassword,
        workingDirectory = effectiveWorkingDir
    )

    val savedModelKey = settingsManager.selectedModelKey
    val modelIndex = if (savedModelKey.isNotEmpty()) {
        ModelPresets.list.indexOfFirst { "${it.providerId}/${it.modelId}" == savedModelKey }
            .takeIf { it >= 0 } ?: 0
    } else 0

    state.update {
        it.copy(
            currentSessionId = settingsManager.currentSessionId,
            selectedModelIndex = modelIndex,
            selectedAgentName = settingsManager.selectedAgentName ?: "build",
            themeMode = settingsManager.themeMode,
            languageMode = settingsManager.languageMode,
            fontSizeScale = settingsManager.fontSizeScale,
            uiScale = settingsManager.uiScale,
            hideMicIcon = settingsManager.hideMicIcon,
            workingDirectory = effectiveWorkingDir,
            hostProfiles = profiles,
            currentHostProfileId = currentProfileId
        )
    }

    val savedSignature = settingsManager.aiBuilderLastOKSignature
    val currentSignature = aiBuilderSignature(
        settingsManager.aiBuilderBaseURL.trim(),
        AIBuildersAudioClient.sanitizeBearerToken(settingsManager.aiBuilderToken)
    )
    if (savedSignature != null && savedSignature == currentSignature) {
        state.update { it.copy(aiBuilderConnectionOK = true) }
    }
}

internal fun launchConnectionTest(
    scope: CoroutineScope,
    repository: OpenCodeRepository,
    state: MutableStateFlow<AppState>,
    onHealthyConnection: () -> Unit
) {
    scope.launch {
        state.update { it.copy(isConnecting = true, error = null) }
        repository.checkHealth()
            .onSuccess { health ->
                debugConnectionLog("testConnection success healthy=${health.healthy} version=${health.version}")
                state.update {
                    it.copy(
                        isConnected = health.healthy,
                        serverVersion = health.version,
                        isConnecting = false,
                        connectionPhase = if (health.healthy) null else "HEALTH"
                    )
                }
                if (health.healthy) {
                    onHealthyConnection()
                }
            }
            .onFailure { error ->
                debugConnectionLog("testConnection failed", error)
                state.update {
                    it.copy(
                        isConnected = false,
                        isConnecting = false,
                        connectionPhase = "HEALTH",
                        error = errorMessageOrFallback(error, "Connection failed")
                    )
                }
            }
    }
}

private const val CONNECTION_ACTIONS_TAG = "MainViewModelConnection"

private fun debugConnectionLog(message: String, throwable: Throwable? = null) {
    try {
        if (throwable == null) Log.d(CONNECTION_ACTIONS_TAG, message) else Log.d(CONNECTION_ACTIONS_TAG, message, throwable)
    } catch (_: RuntimeException) {
        // android.util.Log is not mocked in local JVM unit tests.
    }
}

internal fun launchAIBuilderConnectionTest(
    scope: CoroutineScope,
    settingsManager: SettingsManager,
    state: MutableStateFlow<AppState>
) {
    scope.launch {
        state.update { it.copy(isTestingAIBuilderConnection = true, aiBuilderConnectionError = null) }
        val token = AIBuildersAudioClient.sanitizeBearerToken(settingsManager.aiBuilderToken)
        if (token.isEmpty()) {
            state.update {
                it.copy(
                    isTestingAIBuilderConnection = false,
                    aiBuilderConnectionOK = false,
                    aiBuilderConnectionError = "AI Builder token is empty"
                )
            }
            return@launch
        }

        val baseURL = settingsManager.aiBuilderBaseURL.trim()
        AIBuildersAudioClient.testConnection(baseURL, token)
            .onSuccess {
                val signature = aiBuilderSignature(baseURL, token)
                settingsManager.aiBuilderLastOKSignature = signature
                settingsManager.aiBuilderLastOKTestedAt = System.currentTimeMillis()
                state.update {
                    it.copy(
                        isTestingAIBuilderConnection = false,
                        aiBuilderConnectionOK = true,
                        aiBuilderConnectionError = null
                    )
                }
            }
            .onFailure { error ->
                settingsManager.aiBuilderLastOKSignature = null
                state.update {
                    it.copy(
                        isTestingAIBuilderConnection = false,
                        aiBuilderConnectionOK = false,
                        aiBuilderConnectionError = errorMessageOrFallback(error, "Connection failed")
                    )
                }
            }
    }
}
