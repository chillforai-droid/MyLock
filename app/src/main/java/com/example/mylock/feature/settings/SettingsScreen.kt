package com.example.mylock.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.mylock.core.localization.LanguageRegistry
import com.example.mylock.core.localization.LocalStrings
import com.example.mylock.core.model.AppLanguage
import com.example.mylock.core.model.SecurityLevel
import com.example.mylock.core.model.ThemePreference
import com.example.mylock.domain.MyLockUiState

@Composable
fun SettingsScreen(
    state: MyLockUiState,
    onSetVoiceAuthEnabled: (Boolean) -> Unit,
    onSetCustomPhrase: (String) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetTheme: (ThemePreference) -> Unit,
    onSetMicSensitivity: (Float) -> Unit,
    onSetSecurityLevel: (SecurityLevel) -> Unit,
    onSetOfflineModeOnly: (Boolean) -> Unit,
    onSetStoreRawAudio: (Boolean) -> Unit,
    onSetAuditLogging: (Boolean) -> Unit,
    onSetAppLockEnabled: (Boolean) -> Unit,
    onSaveBackupPin: (String) -> Unit,
    onDeleteVoiceProfile: () -> Unit,
    onNavigateEnroll: (Boolean) -> Unit,
    onNavigateVerify: () -> Unit,
    onNavigateLogs: () -> Unit,
    onNavigateCompatibility: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val settings = state.settings
    var showPhraseDialog by remember { mutableStateOf(false) }
    var phraseInput by remember(settings.customVoicePhrase) { mutableStateOf(settings.customVoicePhrase) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showPhraseDialog) {
        AlertDialog(
            onDismissRequest = { showPhraseDialog = false },
            title = { Text("Change Custom Voice Phrase") },
            text = {
                OutlinedTextField(
                    value = phraseInput,
                    onValueChange = { phraseInput = it },
                    label = { Text("Voice Phrase") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (phraseInput.trim().length >= 6) {
                            onSetCustomPhrase(phraseInput)
                            showPhraseDialog = false
                        }
                    }
                ) { Text("Save Phrase") }
            },
            dismissButton = {
                TextButton(onClick = { showPhraseDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Configure App Backup PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Stored as PBKDF2WithHmacSHA256 salted hash for Safe Compatibility Mode fallback.")
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it.filter(Char::isDigit).take(8) },
                        label = { Text("4–8 Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length >= 4) {
                            onSaveBackupPin(pinInput)
                            pinInput = ""
                            showPinDialog = false
                        }
                    }
                ) { Text("Save PIN") }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Voice Profile?") },
            text = { Text("Permanently erase encrypted voice embedding and remove Keystore key?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteVoiceProfile()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
                .testTag("settings_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = strings.settingsTitle,
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            // 1. Enable/disable, 2. Register, 3. Change phrase, 4. Re-enroll, 5. Delete profile, 8. Voice test
            item {
                SettingsCard(title = "Voice Authentication & Profile") {
                    SettingSwitchRow(
                        title = "1. Enable Voice Authentication",
                        subtitle = "Allow speaker verification inside My Lock Safe Compatibility Mode",
                        checked = settings.voiceAuthEnabled,
                        onCheckedChange = onSetVoiceAuthEnabled,
                        testTag = "setting_voice_auth_switch"
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onNavigateEnroll(false) },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("2. Register Voice") }
                        OutlinedButton(
                            onClick = { onNavigateEnroll(true) },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("4. Re-enroll") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showPhraseDialog = true },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("3. Change Phrase") }
                        OutlinedButton(
                            onClick = onNavigateVerify,
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("8. Voice Test") }
                    }
                    if (state.profileSummary.isEnrolled) {
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) { Text("5. Delete Voice Profile") }
                    }
                }
            }

            // 6. Language selection + Theme
            item {
                SettingsCard(title = "6. Language & Interface Appearance") {
                    Text("Interface Language (English & हिन्दी)", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LanguageRegistry.supportedLanguages.forEach { lang ->
                            FilterChip(
                                selected = settings.language == lang,
                                onClick = { onSetLanguage(lang) },
                                label = { Text(lang.nativeDisplayName) },
                                modifier = Modifier.testTag("setting_lang_${lang.code}")
                            )
                        }
                    }
                    Text(
                        text = "Extensible Indian Language Registry: ${LanguageRegistry.plannedIndianLanguages.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("Theme Mode", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemePreference.entries.forEach { pref ->
                            FilterChip(
                                selected = settings.themePreference == pref,
                                onClick = { onSetTheme(pref) },
                                label = { Text(pref.name) },
                                modifier = Modifier.testTag("setting_theme_${pref.name}")
                            )
                        }
                    }
                }
            }

            // 7. Microphone Sensitivity & 9. Security Level
            item {
                SettingsCard(title = "7. Microphone & 9. Security Threshold") {
                    Text(
                        text = "Microphone Sensitivity Gain: ${"%.2f".format(settings.micSensitivity)}x",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = settings.micSensitivity,
                        onValueChange = onSetMicSensitivity,
                        valueRange = 0.5f..2.0f
                    )
                    Text(
                        text = "Speaker Verification Security Level",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecurityLevel.entries.forEach { level ->
                            FilterChip(
                                selected = settings.securityLevel == level,
                                onClick = { onSetSecurityLevel(level) },
                                label = { Text("${level.name} (${(level.threshold * 100).toInt()}%)") },
                                modifier = Modifier.testTag("setting_security_${level.name}")
                            )
                        }
                    }
                }
            }

            // 10. Offline mode, 11. Privacy controls, 13. App PIN backup
            item {
                SettingsCard(title = "10. Offline Mode, 11. Privacy & 13. App Backup PIN") {
                    SettingSwitchRow(
                        title = "10. Strict Offline Processing",
                        subtitle = "Zero external network calls; all DSP & Keystore crypto runs locally",
                        checked = settings.offlineModeOnly,
                        onCheckedChange = onSetOfflineModeOnly
                    )
                    SettingSwitchRow(
                        title = "11a. Retain Raw Audio Samples (Opt-In)",
                        subtitle = "Default OFF: raw PCM audio is wiped immediately after embedding extraction",
                        checked = settings.storeRawAudioSamples,
                        onCheckedChange = onSetStoreRawAudio
                    )
                    SettingSwitchRow(
                        title = "11b. Local Security Audit Logging",
                        subtitle = "Record verification, anti-spoof, and Keystore events in local Room DB",
                        checked = settings.auditLoggingEnabled,
                        onCheckedChange = onSetAuditLogging
                    )
                    SettingSwitchRow(
                        title = "App-Level Safe Compatibility Gate on Launch",
                        subtitle = "Require Voice / BiometricPrompt / Backup PIN to open My Lock vault",
                        checked = settings.appLockEnabled,
                        onCheckedChange = onSetAppLockEnabled
                    )
                    OutlinedButton(
                        onClick = { showPinDialog = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("setting_backup_pin_button")
                    ) {
                        Text(if (settings.hasBackupPin) "13. Update App Backup PIN" else "13. Set App Backup PIN")
                    }
                }
            }

            // 12. Authentication logs & 14. Device compatibility report
            item {
                SettingsCard(title = "12. Audit Logs & 14. Compatibility Diagnostics") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onNavigateLogs,
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("12. Auth Logs") }
                        OutlinedButton(
                            onClick = onNavigateCompatibility,
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("14. Compatibility") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
        )
    }
}
