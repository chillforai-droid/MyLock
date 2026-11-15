package com.example.mylock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mylock.core.model.AppLanguage
import com.example.mylock.core.model.SecurityLevel
import com.example.mylock.core.model.ThemePreference
import com.example.mylock.security.PinCryptoHasher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.myLockDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mylock_settings_v1"
)

data class MyLockSettings(
    val voiceAuthEnabled: Boolean = true,
    val customVoicePhrase: String = "My Lock Verify Voice",
    val language: AppLanguage = AppLanguage.ENGLISH,
    val themePreference: ThemePreference = ThemePreference.DARK,
    val micSensitivity: Float = 1.0f,
    val securityLevel: SecurityLevel = SecurityLevel.STANDARD,
    val offlineModeOnly: Boolean = true,
    val storeRawAudioSamples: Boolean = false,
    val auditLoggingEnabled: Boolean = true,
    val appLockEnabled: Boolean = false,
    val hasBackupPin: Boolean = false
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val VOICE_AUTH_ENABLED = booleanPreferencesKey("voice_auth_enabled")
        val CUSTOM_PHRASE = stringPreferencesKey("custom_voice_phrase")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val THEME_PREF = stringPreferencesKey("theme_preference")
        val MIC_SENSITIVITY = floatPreferencesKey("mic_sensitivity")
        val SECURITY_LEVEL = stringPreferencesKey("security_level")
        val OFFLINE_MODE_ONLY = booleanPreferencesKey("offline_mode_only")
        val STORE_RAW_AUDIO = booleanPreferencesKey("store_raw_audio_samples")
        val AUDIT_LOGGING_ENABLED = booleanPreferencesKey("audit_logging_enabled")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val BACKUP_PIN_HASH = stringPreferencesKey("backup_pin_hash")
    }

    val settingsFlow: Flow<MyLockSettings> = context.myLockDataStore.data.map { prefs ->
        MyLockSettings(
            voiceAuthEnabled = prefs[Keys.VOICE_AUTH_ENABLED] ?: true,
            customVoicePhrase = prefs[Keys.CUSTOM_PHRASE] ?: "My Lock Verify Voice",
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE_CODE] ?: AppLanguage.ENGLISH.code),
            themePreference = runCatching {
                ThemePreference.valueOf(prefs[Keys.THEME_PREF] ?: ThemePreference.DARK.name)
            }.getOrDefault(ThemePreference.DARK),
            micSensitivity = (prefs[Keys.MIC_SENSITIVITY] ?: 1.0f).coerceIn(0.5f, 2.0f),
            securityLevel = runCatching {
                SecurityLevel.valueOf(prefs[Keys.SECURITY_LEVEL] ?: SecurityLevel.STANDARD.name)
            }.getOrDefault(SecurityLevel.STANDARD),
            offlineModeOnly = prefs[Keys.OFFLINE_MODE_ONLY] ?: true,
            storeRawAudioSamples = prefs[Keys.STORE_RAW_AUDIO] ?: false,
            auditLoggingEnabled = prefs[Keys.AUDIT_LOGGING_ENABLED] ?: true,
            appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
            hasBackupPin = !prefs[Keys.BACKUP_PIN_HASH].isNullOrBlank()
        )
    }

    suspend fun setVoiceAuthEnabled(enabled: Boolean) {
        context.myLockDataStore.edit { it[Keys.VOICE_AUTH_ENABLED] = enabled }
    }

    suspend fun setCustomVoicePhrase(phrase: String) {
        context.myLockDataStore.edit { it[Keys.CUSTOM_PHRASE] = phrase.trim() }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.myLockDataStore.edit { it[Keys.LANGUAGE_CODE] = language.code }
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        context.myLockDataStore.edit { it[Keys.THEME_PREF] = theme.name }
    }

    suspend fun setMicSensitivity(sensitivity: Float) {
        context.myLockDataStore.edit {
            it[Keys.MIC_SENSITIVITY] = sensitivity.coerceIn(0.5f, 2.0f)
        }
    }

    suspend fun setSecurityLevel(level: SecurityLevel) {
        context.myLockDataStore.edit { it[Keys.SECURITY_LEVEL] = level.name }
    }

    suspend fun setOfflineModeOnly(offlineOnly: Boolean) {
        context.myLockDataStore.edit { it[Keys.OFFLINE_MODE_ONLY] = offlineOnly }
    }

    suspend fun setStoreRawAudioSamples(store: Boolean) {
        context.myLockDataStore.edit { it[Keys.STORE_RAW_AUDIO] = store }
    }

    suspend fun setAuditLoggingEnabled(enabled: Boolean) {
        context.myLockDataStore.edit { it[Keys.AUDIT_LOGGING_ENABLED] = enabled }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.myLockDataStore.edit { it[Keys.APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setBackupPin(pin: String) {
        val hashed = PinCryptoHasher.hashPin(pin)
        context.myLockDataStore.edit { it[Keys.BACKUP_PIN_HASH] = hashed }
    }

    suspend fun clearBackupPin() {
        context.myLockDataStore.edit { it.remove(Keys.BACKUP_PIN_HASH) }
    }

    suspend fun verifyBackupPin(pin: String): Boolean {
        val stored = context.myLockDataStore.data.first()[Keys.BACKUP_PIN_HASH]
        return PinCryptoHasher.verifyPin(pin, stored)
    }
}
