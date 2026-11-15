package com.example.mylock.feature.authentication

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.mylock.core.localization.LocalStrings
import com.example.mylock.design.AcousticWaveformVisualizer
import com.example.mylock.design.SemanticTone
import com.example.mylock.design.StatusPill
import com.example.mylock.domain.MyLockUiState

@Composable
fun AppLockGateScreen(
    state: MyLockUiState,
    onStartVoiceVerify: () -> Unit,
    onStopVoiceVerify: () -> Unit,
    onLaunchSystemBiometric: (FragmentActivity) -> Unit,
    onVerifyBackupPin: (String, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> if (granted) onStartVoiceVerify() }
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 540.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )

                StatusPill(
                    label = strings.compatibilityModeBadge,
                    tone = SemanticTone.WARNING
                )

                Text(
                    text = strings.appLockTitle,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = strings.appLockSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (state.profileSummary.isEnrolled && state.settings.voiceAuthEnabled) {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Challenge: \"${state.verification.activeChallenge?.phraseText ?: ""}\"",
                                style = MaterialTheme.typography.titleMedium
                            )
                            AcousticWaveformVisualizer(
                                amplitude = state.liveMicAmplitude,
                                isRecording = state.verification.isRecording
                            )
                            state.verification.lastResult?.let { res ->
                                Text(
                                    text = res.diagnosticMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Button(
                                onClick = {
                                    if (state.verification.isRecording) {
                                        onStopVoiceVerify()
                                    } else {
                                        val hasMic = state.compatibilityReport?.microphonePermissionGranted == true
                                        if (hasMic) onStartVoiceVerify() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                colors = if (state.verification.isRecording) {
                                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                } else {
                                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("applock_voice_button")
                            ) {
                                Icon(
                                    imageVector = if (state.verification.isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(if (state.verification.isRecording) strings.verifyStopAndAnalyze else strings.unlockWithVoice)
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        (context as? FragmentActivity)?.let(onLaunchSystemBiometric)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("applock_biometric_button")
                ) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.unlockWithBiometric)
                }

                state.biometricFallbackStatus?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (state.settings.hasBackupPin) {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = {
                                    pinInput = it.filter(Char::isDigit).take(8)
                                    pinError = null
                                },
                                label = { Text("App Backup PIN") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("applock_pin_input")
                            )
                            pinError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = {
                                    onVerifyBackupPin(pinInput) { ok ->
                                        if (!ok) pinError = "Invalid backup PIN."
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("applock_pin_submit_button")
                            ) {
                                Text(strings.unlockWithBackupPin)
                            }
                        }
                    }
                }
            }
        }
    }
}
