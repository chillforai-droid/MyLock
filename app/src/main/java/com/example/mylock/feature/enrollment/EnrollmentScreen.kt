package com.example.mylock.feature.enrollment

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.example.mylock.core.localization.LocalStrings
import com.example.mylock.design.AcousticWaveformVisualizer
import com.example.mylock.design.SemanticTone
import com.example.mylock.design.StatusPill
import com.example.mylock.domain.EnrollmentStep
import com.example.mylock.domain.MyLockUiState

@Composable
fun EnrollmentScreen(
    state: MyLockUiState,
    onAcceptPrivacy: () -> Unit,
    onMicPermissionResult: (Boolean) -> Unit,
    onPhraseChange: (String) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onReEnroll: () -> Unit,
    onDeleteProfile: () -> Unit,
    onNavigateVerify: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val enroll = state.enrollment
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> onMicPermissionResult(granted) }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Encrypted Voice Profile?") },
            text = {
                Text("This permanently wipes your encrypted speaker embedding and removes the Android Keystore alias. You will need to re-enroll to use voice verification.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteProfile()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_profile_button")
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
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
                        text = strings.enrollmentTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    StatusPill(
                        label = if (state.profileSummary.isEnrolled) "PROFILE ACTIVE" else "NEW PROFILE",
                        tone = if (state.profileSummary.isEnrolled) SemanticTone.SUCCESS else SemanticTone.PRIMARY
                    )
                }
            }

            if (state.profileSummary.isEnrolled && enroll.step != EnrollmentStep.PHRASE_AND_RECORDING) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Current Encrypted Profile",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(
                                text = "Samples: ${state.profileSummary.sampleCount} • Phrase Hint: ${state.profileSummary.maskedPhraseHint} • Model: ${state.profileSummary.modelId} v${state.profileSummary.modelVersion}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = onReEnroll,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("enrollment_reenroll_button")
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.enrollmentReEnroll)
                                }
                                OutlinedButton(
                                    onClick = { showDeleteConfirm = true },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("enrollment_delete_button")
                                ) {
                                    Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.enrollmentDeleteProfile)
                                }
                            }
                        }
                    }
                }
            }

            item {
                when (enroll.step) {
                    EnrollmentStep.PRIVACY_DISCLOSURE -> {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.PrivacyTip,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        text = strings.enrollmentPrivacyTitle,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                                Text(
                                    text = strings.enrollmentPrivacyBody,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Important: Speaker verification evaluates voice acoustic characteristics (who spoke), not speech-to-text words alone.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Button(
                                    onClick = onAcceptPrivacy,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("enrollment_accept_privacy_button")
                                ) {
                                    Text(strings.enrollmentAcceptPrivacy)
                                }
                            }
                        }
                    }

                    EnrollmentStep.MICROPHONE_PERMISSION -> {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Mic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        text = strings.enrollmentMicPermissionTitle,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                                Text(
                                    text = strings.enrollmentMicPermissionBody,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                enroll.errorMessage?.let { err ->
                                    Text(
                                        text = err,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Button(
                                    onClick = {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("enrollment_grant_mic_button")
                                ) {
                                    Text(strings.enrollmentGrantMic)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Text("Open Android App Settings")
                                }
                            }
                        }
                    }

                    EnrollmentStep.PHRASE_AND_RECORDING -> {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = strings.enrollmentPhraseTitle,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                OutlinedTextField(
                                    value = enroll.customPhrase,
                                    onValueChange = onPhraseChange,
                                    label = { Text(strings.enrollmentPhraseHint) },
                                    singleLine = true,
                                    enabled = !enroll.isRecording && !enroll.isProcessing,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("enrollment_phrase_input")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${strings.enrollmentSamplesProgress}: ${enroll.acceptedEmbeddingsCount} / ${enroll.targetSamples}",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    StatusPill(
                                        label = "${enroll.acceptedEmbeddingsCount}/${enroll.targetSamples}",
                                        tone = SemanticTone.PRIMARY
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = {
                                        enroll.acceptedEmbeddingsCount.toFloat() / enroll.targetSamples.coerceAtLeast(1)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                )

                                AcousticWaveformVisualizer(
                                    amplitude = state.liveMicAmplitude,
                                    isRecording = enroll.isRecording
                                )

                                if (enroll.isRecording) {
                                    Text(
                                        text = "Speak clearly for 2–4 seconds: \"${enroll.customPhrase}\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                enroll.statusMessage?.let { msg ->
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                enroll.errorMessage?.let { err ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = err,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }

                                enroll.lastQualityReport?.let { q ->
                                    Text(
                                        text = "Last Sample Telemetry • Duration: ${q.totalDurationMs} ms • Active Speech: ${q.activeSpeechMs} ms • SNR: ${"%.1f".format(q.snrDb)} dB • Clipping: ${"%.1f".format(q.clippingRatio * 100)}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (enroll.isProcessing) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text("Extracting speaker acoustic embedding...")
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            if (enroll.isRecording) onStopRecording() else onStartRecording()
                                        },
                                        colors = if (enroll.isRecording) {
                                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                        } else {
                                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .testTag("enrollment_record_toggle_button")
                                    ) {
                                        Icon(
                                            imageVector = if (enroll.isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                                            contentDescription = null
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (enroll.isRecording) {
                                                strings.enrollmentStopSample
                                            } else {
                                                "${strings.enrollmentRecordSample} #${enroll.acceptedEmbeddingsCount + 1}"
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    EnrollmentStep.COMPLETED -> {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(30.dp)
                                    )
                                    Text(
                                        text = strings.enrollmentSuccessTitle,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                                Text(
                                    text = strings.enrollmentSuccessBody,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = onNavigateVerify,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("enrollment_go_verify_button")
                                ) {
                                    Text(strings.actionTestVoice)
                                }
                                OutlinedButton(
                                    onClick = onReEnroll,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Text(strings.enrollmentReEnroll)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
