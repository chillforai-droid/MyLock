package com.example.mylock.feature.authentication

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.mylock.core.model.VerificationStatus
import com.example.mylock.design.AcousticWaveformVisualizer
import com.example.mylock.design.SemanticTone
import com.example.mylock.design.StatusPill
import com.example.mylock.design.semanticContentColor
import com.example.mylock.domain.MyLockUiState

@Composable
fun AuthenticationScreen(
    state: MyLockUiState,
    onRefreshChallenge: () -> Unit,
    onStartCapture: () -> Unit,
    onStopAndVerify: () -> Unit,
    onLaunchSystemBiometric: (FragmentActivity) -> Unit,
    onVerifyBackupPin: (String, (Boolean) -> Unit) -> Unit,
    onNavigateEnroll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val verify = state.verification
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinStatusText by remember { mutableStateOf<String?>(null) }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) onStartCapture()
        }
    )

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("App Backup PIN Verification") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Verify your local PBKDF2-hashed My Lock backup PIN (App-Level Safe Compatibility Mode).")
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 8) pinInput = it.filter(Char::isDigit) },
                        label = { Text("Enter Backup PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    pinStatusText?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onVerifyBackupPin(pinInput) { ok ->
                            pinStatusText = if (ok) {
                                "Backup PIN verified successfully (App-Level)."
                            } else {
                                "Incorrect backup PIN."
                            }
                        }
                    }
                ) {
                    Text("Verify PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPinDialog = false
                    pinInput = ""
                    pinStatusText = null
                }) {
                    Text("Close")
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.verifyTitle,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    StatusPill(
                        label = "${verify.remainingAttempts}/5 TRIES",
                        tone = if (verify.lockoutRemainingSeconds > 0) SemanticTone.ERROR else SemanticTone.PRIMARY
                    )
                }
            }

            if (!state.profileSummary.isEnrolled) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Voice Profile Not Enrolled",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Enroll your voice profile first to test speaker verification and anti-spoofing checks.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onNavigateEnroll,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text(strings.actionEnrollVoice)
                            }
                        }
                    }
                }
            }

            // Dynamic Anti-Replay Challenge Card
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.verifyChallengeLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusPill(
                                    label = if (verify.challengeRemainingSeconds > 0) {
                                        "TTL ${verify.challengeRemainingSeconds}s"
                                    } else {
                                        "EXPIRED"
                                    },
                                    tone = if (verify.challengeRemainingSeconds > 5) SemanticTone.PRIMARY else SemanticTone.ERROR
                                )
                                IconButton(
                                    onClick = onRefreshChallenge,
                                    modifier = Modifier.testTag("verify_refresh_challenge_button")
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = strings.verifyRefreshChallenge)
                                }
                            }
                        }

                        Text(
                            text = "\"${verify.activeChallenge?.phraseText ?: "..."}\"",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = strings.verifyChallengeWarning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        AcousticWaveformVisualizer(
                            amplitude = state.liveMicAmplitude,
                            isRecording = verify.isRecording
                        )

                        if (verify.lockoutRemainingSeconds > 0) {
                            Text(
                                text = "Rate-limit lockout active: ${verify.lockoutRemainingSeconds}s remaining.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        if (verify.isAnalyzing) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Evaluating acoustic liveness & speaker embedding...")
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (verify.isRecording) {
                                        onStopAndVerify()
                                    } else {
                                        val hasMic = state.compatibilityReport?.microphonePermissionGranted == true
                                        if (hasMic) onStartCapture() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                enabled = state.profileSummary.isEnrolled && state.settings.voiceAuthEnabled && verify.lockoutRemainingSeconds == 0L,
                                colors = if (verify.isRecording) {
                                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                } else {
                                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("verify_record_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (verify.isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (verify.isRecording) strings.verifyStopAndAnalyze else strings.verifyStartListening
                                )
                            }
                        }
                    }
                }
            }

            // Verification Telemetry & Multi-Layer Anti-Spoofing Result
            verify.lastResult?.let { res ->
                item {
                    val isVerified = res.status == VerificationStatus.VERIFIED_APP_LEVEL
                    val tone = if (isVerified) SemanticTone.SUCCESS else SemanticTone.ERROR
                    Card(
                        modifier = Modifier.border(
                            1.dp,
                            semanticContentColor(tone).copy(alpha = 0.4f),
                            MaterialTheme.shapes.large
                        ),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isVerified) Icons.Filled.GppGood else Icons.Filled.GppMaybe,
                                        contentDescription = null,
                                        tint = semanticContentColor(tone)
                                    )
                                    Text(
                                        text = res.confidenceLabel,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                StatusPill(
                                    label = res.status.name,
                                    tone = tone
                                )
                            }

                            Text(
                                text = res.diagnosticMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${strings.verifySimilarityScore}: ${(res.similarityScore * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = "${strings.verifyThresholdLabel}: ${(res.thresholdUsed * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            LinearProgressIndicator(
                                progress = { res.similarityScore.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                            )

                            res.antiSpoofingReport?.let { anti ->
                                Text(
                                    text = "${strings.verifyAntiSpoofSummary}: ${if (anti.passed) "PASSED" else "FLAGGED"} • Challenge=${anti.challengeValid} • ReplayUnique=${anti.replayHashUnique} • PlaybackRisk=${"%.2f".format(anti.acousticReplayRiskScore)} • HNR=${"%.1f".format(anti.harmonicToNoiseRatioDb)}dB • HighFreqCutoff=${anti.highFreqCutoffDetected}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Official Android Biometric Fallback & Backup PIN (Safe Compatibility Mode)
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Official Android Authentication Fallback",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Use official Android BiometricPrompt (Class 3 fingerprint/face or system device PIN/pattern) or your local PBKDF2 app backup PIN for app-level verification.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        verify.fallbackMessage?.let { msg ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val activity = context as? FragmentActivity
                                if (activity != null) {
                                    onLaunchSystemBiometric(activity)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("verify_biometric_fallback_button")
                        ) {
                            Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.verifyFallbackBiometric)
                        }

                        if (state.settings.hasBackupPin) {
                            OutlinedButton(
                                onClick = { showPinDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("verify_backup_pin_button")
                            ) {
                                Icon(Icons.Filled.Pin, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(strings.verifyFallbackPin)
                            }
                        }
                    }
                }
            }
        }
    }
}
