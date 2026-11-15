package com.example.mylock.oem

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.example.mylock.core.model.IntegrationTier
import com.example.mylock.core.model.SystemIntegrationStatus

/**
 * Clean architectural boundary separating standard third-party APK capabilities
 * from optional OEM / custom AOSP system-privileged integration.
 *
 * CRITICAL ANDROID SECURITY CONSTRAINT:
 * A standard third-party application CANNOT dismiss the system Keyguard,
 * register a custom voice model as a native BiometricPrompt modality, or replace
 * the system lock screen. This bridge explicitly remains disabled unless a
 * manufacturer-signed privileged system service is present on the device firmware.
 */
interface SystemVoiceUnlockBridge {
    fun inspectIntegrationStatus(): SystemIntegrationStatus

    fun requestAuthorizedSystemRegistration(
        profileId: String,
        attestationChallenge: ByteArray
    ): OemRegistrationResult
}

sealed interface OemRegistrationResult {
    data class Unsupported(val reason: String) : OemRegistrationResult
    data class PendingOemProvisioning(val serviceComponent: String) : OemRegistrationResult
}

class StandardSafeCompatibilityBridge(
    private val context: Context
) : SystemVoiceUnlockBridge {

    companion object {
        const val OEM_SERVICE_ACTION = "com.mylock.system.VOICE_UNLOCK_SERVICE"
        const val OEM_PRIVILEGED_PERMISSION = "com.mylock.system.permission.BIND_VOICE_UNLOCK"
    }

    override fun inspectIntegrationStatus(): SystemIntegrationStatus {
        val pm = context.packageManager
        val serviceIntent = Intent(OEM_SERVICE_ACTION)
        val resolvedServices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentServices(serviceIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentServices(serviceIntent, 0)
        }

        val serviceInstalled = resolvedServices.isNotEmpty()
        val platformSignatureTrusted = if (serviceInstalled) {
            val servicePkg = resolvedServices.first().serviceInfo.packageName
            val sigMatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.checkSignatures(servicePkg, "android") == PackageManager.SIGNATURE_MATCH
            } else {
                @Suppress("DEPRECATION")
                pm.checkSignatures(servicePkg, "android") == PackageManager.SIGNATURE_MATCH
            }
            sigMatch && context.checkSelfPermission(OEM_PRIVILEGED_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            false
        }

        val reason = when {
            !serviceInstalled ->
                "Standard third-party APK: BiometricPrompt does not support custom voice modalities and KeyguardManager cannot be dismissed by third-party apps. No OEM/AOSP privileged voice service (com.mylock.system.VOICE_UNLOCK_SERVICE) is present on this firmware."
            !platformSignatureTrusted ->
                "OEM voice service detected, but platform signature or BIND_VOICE_UNLOCK privileged permission is not granted. Falling back to Safe Compatibility Mode."
            else ->
                "Authorized OEM privileged service verified; hardware attestation still required before Keyguard integration."
        }

        return SystemIntegrationStatus(
            currentTier = if (platformSignatureTrusted) {
                IntegrationTier.OEM_AUTHORIZED_PARTNER
            } else {
                IntegrationTier.THIRD_PARTY_STANDARD_APP
            },
            systemKeyguardUnlockSupported = platformSignatureTrusted,
            oemVoiceServiceInstalled = serviceInstalled,
            oemVoiceServiceSignatureTrusted = platformSignatureTrusted,
            compatibilityModeActive = !platformSignatureTrusted,
            exactRestrictionReason = reason,
            supportedScopeSummary = if (platformSignatureTrusted) {
                "OEM privileged HAL handshake ready"
            } else {
                "App-level Voice Vault + Official BiometricPrompt / PIN Fallback"
            }
        )
    }

    override fun requestAuthorizedSystemRegistration(
        profileId: String,
        attestationChallenge: ByteArray
    ): OemRegistrationResult {
        val status = inspectIntegrationStatus()
        return if (!status.systemKeyguardUnlockSupported) {
            OemRegistrationResult.Unsupported(status.exactRestrictionReason)
        } else {
            OemRegistrationResult.PendingOemProvisioning(OEM_SERVICE_ACTION)
        }
    }
}
