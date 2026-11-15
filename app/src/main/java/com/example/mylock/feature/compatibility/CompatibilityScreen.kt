package com.example.mylock.feature.compatibility

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.mylock.core.localization.LocalStrings
import com.example.mylock.design.CompatibilityScopeBanner
import com.example.mylock.design.SemanticTone
import com.example.mylock.design.StatusPill
import com.example.mylock.domain.MyLockUiState

@Composable
fun CompatibilityScreen(
    state: MyLockUiState,
    onRefreshCompatibility: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val report = state.compatibilityReport

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.compatTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onRefreshCompatibility,
                        modifier = Modifier.testTag("compat_refresh_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Re-run device audit")
                    }
                }
            }

            if (report != null) {
                item {
                    CompatibilityScopeBanner(
                        badgeText = "INTEGRATION TIER: ${report.systemIntegrationStatus.currentTier.name}",
                        title = if (report.systemIntegrationStatus.systemKeyguardUnlockSupported) {
                            "Authorized OEM Privileged Bridge Active"
                        } else {
                            "Standard Third-Party App — System Unlock Disabled"
                        },
                        description = report.systemIntegrationStatus.exactRestrictionReason,
                        isSystemSupported = report.systemIntegrationStatus.systemKeyguardUnlockSupported
                    )
                }

                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Live Device Security & Hardware Audit",
                                style = MaterialTheme.typography.titleMedium
                            )
                            AuditRow("Device", "${report.deviceManufacturer} ${report.deviceModel}")
                            AuditRow("Android OS", "Android ${report.androidVersionRelease} (API ${report.apiLevel})")
                            AuditRow("Microphone Hardware", yesNo(report.hasMicrophoneHardware))
                            AuditRow("RECORD_AUDIO Permission", yesNo(report.microphonePermissionGranted))
                            AuditRow("Keyguard Secure (PIN/Pattern/Bio)", yesNo(report.isKeyguardSecure))
                            AuditRow("BiometricPrompt Strong (Class 3)", yesNo(report.biometricStrongAvailable))
                            AuditRow("BiometricPrompt Weak (Class 2)", yesNo(report.biometricWeakAvailable))
                            AuditRow("Device Credential Fallback", yesNo(report.deviceCredentialAvailable))
                            AuditRow("StrongBox Hardware Keystore", yesNo(report.strongBoxSupported))
                            AuditRow("Device Owner / Profile Owner", "${report.isDeviceOwner} / ${report.isProfileOwner}")
                            AuditRow("Battery Optimization Ignored", yesNo(report.ignoringBatteryOptimizations))
                            AuditRow("API 34+ Mic FGS Permission Req", yesNo(report.foregroundServiceMicPermissionRequired))
                            AuditRow("API 33+ Notification Perm Req", yesNo(report.notificationPermissionRequired))
                        }
                    }
                }
            }

            // Privilege Tier Trust Boundary Summary
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Android Security Privilege Tiers",
                            style = MaterialTheme.typography.titleMedium
                        )
                        TierLine("1. Standard 3rd-Party APK", "App-level voice auth + BiometricPrompt consumer only. Cannot unlock Keyguard.")
                        TierLine("2. Device Owner (DPC)", "Enterprise policy & password rules via DevicePolicyManager. Cannot inject custom voice biometric.")
                        TierLine("3. Privileged System App (/priv-app)", "Access to signature|privileged permissions (e.g., SoundTrigger / Hotword). Still isolated from Keyguard.")
                        TierLine("4. Platform-Signed / Custom AOSP", "Signed with platform key; can integrate TrustAgent or SystemUI Keyguard BiometricService HAL.")
                        TierLine("5. OEM-Authorized Integration", "Certified Biometric HAL + TEE/StrongBox attested token + CTS/CDD compliance.")
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = strings.compatMatrixTitle,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            items(state.apiMatrix, key = { it.apiLevel }) { row ->
                val isCurrent = report?.apiLevel == row.apiLevel
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrent) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "API ${row.apiLevel} — Android ${row.androidVersion} (${row.codename})",
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (isCurrent) {
                                StatusPill(label = "THIS DEVICE", tone = SemanticTone.PRIMARY)
                            }
                        }
                        Text("Supported: ${row.supportedCapabilities}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Restricted: ${row.restrictedCapabilities}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "Permissions: ${row.requiredPermissions}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Signing: ${row.requiredSigningLevel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "OEM / Limitations: ${row.oemDependencies} | ${row.deviceLimitations}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditRow(label: String, value: String) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    }
}

@Composable
private fun TierLine(tier: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = tier, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(text = detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun yesNo(flag: Boolean): String = if (flag) "Supported / Granted" else "Unavailable / Denied"
