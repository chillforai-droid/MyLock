package com.example.mylock.feature.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.mylock.core.localization.LocalStrings
import com.example.mylock.design.CompatibilityScopeBanner
import com.example.mylock.design.MyLockLogoBadge
import com.example.mylock.design.SecurityMetricCard
import com.example.mylock.design.SemanticTone
import com.example.mylock.design.StatusPill
import com.example.mylock.domain.MyLockUiState

@Composable
fun DashboardScreen(
    state: MyLockUiState,
    onNavigateEnroll: () -> Unit,
    onNavigateVerify: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateLogs: () -> Unit,
    onNavigateCompatibility: () -> Unit,
    onTestAppLockGate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val compat = state.compatibilityReport
    val enrolled = state.profileSummary.isEnrolled

    val voiceTone = when {
        !state.settings.voiceAuthEnabled -> SemanticTone.NEUTRAL
        state.verification.lockoutRemainingSeconds > 0 -> SemanticTone.ERROR
        enrolled -> SemanticTone.SUCCESS
        else -> SemanticTone.WARNING
    }
    val voiceStatusValue = when {
        !state.settings.voiceAuthEnabled -> strings.statusDisabled
        state.verification.lockoutRemainingSeconds > 0 ->
            "${strings.statusLockedOut} (${state.verification.lockoutRemainingSeconds}s)"
        enrolled -> strings.statusEnrolled
        else -> strings.statusNotEnrolled
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
                .testTag("dashboard_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. My Lock Logo + Hero Security Banner
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_security_banner),
                            contentDescription = "My Lock acoustic security waveform banner",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(148.dp)
                                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(148.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0x6605080F),
                                            Color(0xEE0A111E)
                                        )
                                    )
                                )
                                .padding(16.dp),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                MyLockLogoBadge(
                                    title = strings.appName,
                                    subtitle = strings.appSubtitle
                                )
                                StatusPill(
                                    label = if (state.settings.offlineModeOnly) "OFFLINE VAULT" else "LOCAL",
                                    tone = SemanticTone.PRIMARY,
                                    icon = Icons.Filled.Security
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 6. Enrollment button
                        Button(
                            onClick = onNavigateEnroll,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("dashboard_enroll_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.actionEnrollVoice, maxLines = 1)
                        }

                        // 7. Test voice button
                        FilledTonalButton(
                            onClick = onNavigateVerify,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("dashboard_test_voice_button")
                        ) {
                            Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.actionTestVoice, maxLines = 1)
                        }
                    }
                }
            }

            // Explicit Honest Android Security Boundary Banner
            item {
                CompatibilityScopeBanner(
                    badgeText = strings.compatibilityModeBadge,
                    title = strings.systemUnlockUnavailableTitle,
                    description = strings.systemUnlockUnavailableDesc,
                    isSystemSupported = compat?.systemIntegrationStatus?.systemKeyguardUnlockSupported == true,
                    modifier = Modifier.testTag("dashboard_compatibility_banner")
                )
            }

            // 2. Voice Authentication Status & 3. Device Compatibility Status
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecurityMetricCard(
                        title = strings.voiceAuthStatusTitle,
                        primaryValue = voiceStatusValue,
                        supportingText = if (enrolled) {
                            "Engine: ${state.modelInfoLabel} • Threshold: ${(state.settings.securityLevel.threshold * 100).toInt()}%"
                        } else {
                            "Enroll 3 voice samples to create a Keystore-encrypted speaker profile."
                        },
                        icon = Icons.Filled.RecordVoiceOver,
                        tone = voiceTone,
                        badgeText = if (enrolled) "READY" else "SETUP",
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_voice_auth_status"),
                        onClick = onNavigateVerify
                    )

                    SecurityMetricCard(
                        title = strings.deviceCompatStatusTitle,
                        primaryValue = compat?.let { "Android ${it.androidVersionRelease} (API ${it.apiLevel})" } ?: "Inspecting...",
                        supportingText = compat?.let {
                            "Mic HW: ${if (it.hasMicrophoneHardware) "Yes" else "No"} • Biometric Strong: ${if (it.biometricStrongAvailable) "Ready" else "Unavailable"} • Keyguard Secure: ${if (it.isKeyguardSecure) "Yes" else "No"}"
                        } ?: "",
                        icon = Icons.Filled.Devices,
                        tone = if (compat?.hasMicrophoneHardware == true) SemanticTone.PRIMARY else SemanticTone.ERROR,
                        badgeText = compat?.let { "API ${it.apiLevel}" } ?: "...",
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_device_compatibility"),
                        onClick = onNavigateCompatibility
                    )
                }
            }

            // 4. Voice Profile Status & 5. Authentication Security Status
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecurityMetricCard(
                        title = strings.voiceProfileStatusTitle,
                        primaryValue = if (enrolled) {
                            "${state.profileSummary.sampleCount} Samples • ${state.profileSummary.maskedPhraseHint}"
                        } else {
                            "No Encrypted Voiceprint"
                        },
                        supportingText = if (enrolled) {
                            val hw = if (state.profileSummary.hardwareKeystoreBacked) "Hardware Keystore AES-256-GCM" else "AndroidKeyStore AES-256-GCM"
                            "$hw • Raw audio discarded by default"
                        } else {
                            "AES-256-GCM Keystore vault empty"
                        },
                        icon = Icons.Filled.Lock,
                        tone = if (enrolled) SemanticTone.SUCCESS else SemanticTone.NEUTRAL,
                        badgeText = if (enrolled) "ENCRYPTED" else "EMPTY",
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_voice_profile_status"),
                        onClick = onNavigateEnroll
                    )

                    SecurityMetricCard(
                        title = strings.authSecurityStatusTitle,
                        primaryValue = "Level: ${state.settings.securityLevel.name}",
                        supportingText = "Challenge TTL 30s • Replay SHA-256 cache • Spectral liveness heuristics • Attempts left: ${state.verification.remainingAttempts}/5",
                        icon = Icons.Filled.AdminPanelSettings,
                        tone = SemanticTone.PRIMARY,
                        badgeText = "ANTI-SPOOF",
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_auth_security_status"),
                        onClick = onNavigateSettings
                    )
                }
            }

            // 10. Device Integration Status
            item {
                val integration = compat?.systemIntegrationStatus
                SecurityMetricCard(
                    title = strings.deviceIntegrationStatusTitle,
                    primaryValue = if (integration?.systemKeyguardUnlockSupported == true) {
                        "OEM Privileged Bridge Active"
                    } else {
                        "App-Level Safe Compatibility Mode"
                    },
                    supportingText = integration?.exactRestrictionReason
                        ?: "Inspecting system integration trust boundary...",
                    icon = Icons.Filled.PhonelinkLock,
                    tone = if (integration?.systemKeyguardUnlockSupported == true) SemanticTone.SUCCESS else SemanticTone.WARNING,
                    badgeText = "TIER: 3RD-PARTY",
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_device_integration_status"),
                    onClick = onNavigateCompatibility
                )
            }

            // 8. Settings, 9. Security Logs, Compatibility Matrix, and Safe App Lock Gate actions
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onNavigateSettings,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("dashboard_settings_button")
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.actionSettings, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onNavigateLogs,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("dashboard_logs_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${strings.actionSecurityLogs} (${state.auditLogs.size})", maxLines = 1)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onNavigateCompatibility,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("dashboard_compatibility_button")
                        ) {
                            Text(strings.actionCompatibilityReport, maxLines = 1)
                        }
                        FilledTonalButton(
                            onClick = onTestAppLockGate,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("dashboard_applock_gate_button")
                        ) {
                            Text(strings.actionAppLockGate, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
