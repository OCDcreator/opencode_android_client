package com.yage.opencode_client.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "opencode_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var serverUrl: String
        get() = encryptedPrefs.getString(KEY_SERVER_URL, DEFAULT_SERVER) ?: DEFAULT_SERVER
        set(value) = encryptedPrefs.edit().putString(KEY_SERVER_URL, value).apply()

    var username: String?
        get() = encryptedPrefs.getString(KEY_USERNAME, null)
        set(value) = encryptedPrefs.edit().putString(KEY_USERNAME, value).apply()

    var password: String?
        get() = encryptedPrefs.getString(KEY_PASSWORD, null)
        set(value) = encryptedPrefs.edit().putString(KEY_PASSWORD, value).apply()

    var currentSessionId: String?
        get() = encryptedPrefs.getString(KEY_SESSION_ID, null)
        set(value) = encryptedPrefs.edit().putString(KEY_SESSION_ID, value).apply()

    var selectedModelIndex: Int
        get() = encryptedPrefs.getInt(KEY_MODEL_INDEX, 0)
        set(value) = encryptedPrefs.edit().putInt(KEY_MODEL_INDEX, value).apply()

    var selectedAgentName: String?
        get() = encryptedPrefs.getString(KEY_AGENT_NAME, null)
        set(value) = encryptedPrefs.edit().putString(KEY_AGENT_NAME, value).apply()

    var themeMode: ThemeMode
        get() = ThemeMode.valueOf(encryptedPrefs.getString(KEY_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        set(value) = encryptedPrefs.edit().putString(KEY_THEME, value.name).apply()

    companion object {
        const val DEFAULT_SERVER = "http://localhost:4096"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_MODEL_INDEX = "model_index"
        private const val KEY_AGENT_NAME = "agent_name"
        private const val KEY_THEME = "theme"
    }
}

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}
