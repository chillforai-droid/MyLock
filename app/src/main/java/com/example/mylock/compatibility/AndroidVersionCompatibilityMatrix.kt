package com.example.mylock.compatibility

import com.example.mylock.core.model.ApiLevelCapabilityRow

object AndroidVersionCompatibilityMatrix {

    fun getFullMatrix(): List<ApiLevelCapabilityRow> = listOf(
        ApiLevelCapabilityRow(
            apiLevel = 26,
            androidVersion = "8.0",
            codename = "Oreo",
            supportedCapabilities = "AudioRecord 16kHz PCM, Keystore AES-GCM, KeyguardManager.isKeyguardSecure, FingerprintManager (deprecated later), background execution limits respected via foreground UI.",
            restrictedCapabilities = "No BiometricPrompt API; no StrongBox Keystore; no third-party Keyguard dismissal or Trust Agent registration.",
            requiredPermissions = "RECORD_AUDIO (runtime), USE_FINGERPRINT (legacy), VIBRATE.",
            requiredSigningLevel = "Standard App Signing (App-level only); Platform key required for Keyguard/TrustAgent.",
            oemDependencies = "OEM audio HAL must provide 16kHz mic capture without aggressive AGC distortion.",
            deviceLimitations = "Older devices may use KeyguardManager.createConfirmDeviceCredentialIntent fallback."
        ),
        ApiLevelCapabilityRow(
            apiLevel = 27,
            androidVersion = "8.1",
            codename = "Oreo MR1",
            supportedCapabilities = "Same AudioRecord + Keystore AES-GCM foundation; improved shared memory & neural networks NNAPI v1.0 availability.",
            restrictedCapabilities = "No native BiometricPrompt; custom voice cannot unlock system lock screen.",
            requiredPermissions = "RECORD_AUDIO (runtime), VIBRATE.",
            requiredSigningLevel = "Standard App Signing (App-level); System/Platform for Keyguard.",
            oemDependencies = "NNAPI driver quality varies widely across chipsets.",
            deviceLimitations = "Background mic capture strictly blocked for idle background UIDs."
        ),
        ApiLevelCapabilityRow(
            apiLevel = 28,
            androidVersion = "9",
            codename = "Pie",
            supportedCapabilities = "BiometricPrompt introduced, StrongBox Keymaster 4.0 hardware keystore check, AndroidX BiometricPrompt wrapper.",
            restrictedCapabilities = "Background apps blocked from accessing microphone (silenced audio); BiometricPrompt only accepts OS-registered Class 3 biometrics.",
            requiredPermissions = "RECORD_AUDIO, USE_BIOMETRIC, FOREGROUND_SERVICE (if foreground service used).",
            requiredSigningLevel = "Standard App Signing (App-level); Platform/Priv-app for TrustAgent.",
            oemDependencies = "StrongBox requires dedicated secure element chip from OEM.",
            deviceLimitations = "IDLE apps receive zeroed mic samples; foreground UI required during voice capture."
        ),
        ApiLevelCapabilityRow(
            apiLevel = 29,
            androidVersion = "10",
            codename = "Q",
            supportedCapabilities = "BiometricManager.canAuthenticate(), Privacy microphone indicator foundation, Scoped storage, background activity launch restrictions.",
            restrictedCapabilities = "Background activity starts blocked; raw voice recordings cannot be captured silently in background.",
            requiredPermissions = "RECORD_AUDIO, USE_BIOMETRIC.",
            requiredSigningLevel = "Standard App Signing; Privileged system permission for VOICE_INTERACTION / TrustAgent.",
            oemDependencies = "OEM BiometricService classifies Face/Fingerprint as Strong vs Weak.",
            deviceLimitations = "Many OEMs shipped 2D Face Unlock classified as WEAK (Class 2), ineligible for crypto-bound keys."
        ),
        ApiLevelCapabilityRow(
            apiLevel = 30,
            androidVersion = "11",
            codename = "R",
            supportedCapabilities = "BiometricManager.Authenticators (BIOMETRIC_STRONG, BIOMETRIC_WEAK, DEVICE_CREDENTIAL), one-time mic permission, KeyGenParameterSpec.setUserAuthenticationParameters.",
            restrictedCapabilities = "One-time permissions auto-revoke; foregroundServiceType='microphone' mandatory for any FGS audio capture; no custom voice Keyguard unlock.",
            requiredPermissions = "RECORD_AUDIO (one-time / while-in-use), USE_BIOMETRIC.",
            requiredSigningLevel = "Standard App Signing; Platform signature for System Keyguard HAL.",
            oemDependencies = "TEE/StrongBox Keymaster 4.1 enforcement.",
            deviceLimitations = "While-in-use mic permission prevents non-foreground voice triggers."
        ),
        ApiLevelCapabilityRow(
            apiLevel = 31,
            androidVersion = "12",
            codename = "S",
            supportedCapabilities = "Dynamic Color (Material You), Privacy Dashboard & hardware mic status bar indicator, mic toggle awareness, stricter FGS launch rules.",
            restrictedCapabilities = "Cannot start foreground services from background; users can globally mute microphone via Quick Settings privacy toggle.",
            requiredPermissions = "RECORD_AUDIO, USE_BIOMETRIC.",
            requiredSigningLevel = "Standard App Signing; System/OEM signature for privileged hotword/DSP.",
            oemDependencies = "Qualcomm/MediaTek SoundTrigger DSP requires OEM firmware & privileged SoundTrigger permission.",
            deviceLimitations = "Global mic privacy toggle returns silence even with RECORD_AUDIO granted."
        ),
        ApiLevelCapabilityRow(
            apiLevel = 32,
            androidVersion = "12L",
            codename = "S_V2",
            supportedCapabilities = "Large-screen & foldable adaptive layouts (WindowSizeClass), same biometric & microphone privacy guarantees as API 31.",
            restrictedCapabilities = "Same background FGS and Keyguard isolation rules as API 31.",
            requiredPermissions = "RECORD_AUDIO, USE_BIOMETRIC.",
            requiredSigningLevel = "Standard App Signing; Platform signature for Keyguard.",
            oemDependencies = "Multi-mic array routing on tablets/foldables.",
            deviceLimitations = "Foldable hinge posture changes mic distance & SNR."
        ),
        ApiLevelCapabilityRow(
            apiLevel = 33,
            androidVersion = "13",
            codename = "Tiramisu",
            supportedCapabilities = "Granular runtime POST_NOTIFICATIONS permission, per-app language preferences, improved AudioManager input device routing.",
            restrictedCapabilities = "Notifications require explicit user permission; no third-party voice Keyguard unlock.",
            requiredPermissions = "RECORD_AUDIO, USE_BIOMETRIC, POST_NOTIFICATIONS (runtime).",
            requiredSigningLevel = "Standard App Signing; Platform signature for SystemUI Keyguard.",
            oemDependencies = "OEM HotwordDetectionService isolated process requires system role.",
            deviceLimitations = "Denied POST_NOTIFICATIONS suppresses security alert notifications."
        ),
        ApiLevelCapabilityRow(
            apiLevel = 34,
            androidVersion = "14",
            codename = "Upside Down Cake",
            supportedCapabilities = "Strict FGS type enforcement, Credential Manager + BiometricPrompt synergy, improved Keystore attestation.",
            restrictedCapabilities = "FOREGROUND_SERVICE_MICROPHONE permission & active while-in-use state required for any mic FGS; background launches blocked.",
            requiredPermissions = "RECORD_AUDIO, USE_BIOMETRIC, POST_NOTIFICATIONS.",
            requiredSigningLevel = "Standard App Signing; OEM/AOSP system partition for Keyguard.",
            oemDependencies = "Android 14 VirtualDeviceManager audio routing considerations.",
            deviceLimitations = "Strict battery/FGS quotas terminate non-compliant background processes."
        ),
        ApiLevelCapabilityRow(
            apiLevel = 35,
            androidVersion = "15",
            codename = "Vanilla Ice Cream",
            supportedCapabilities = "Mandatory edge-to-edge on targetSdk 35+, FGS timeout/type enforcement, Private Space isolation.",
            restrictedCapabilities = "Cannot bypass Keyguard or Private Space lock; BOOT_COMPLETED cannot launch mic FGS.",
            requiredPermissions = "RECORD_AUDIO, USE_BIOMETRIC, POST_NOTIFICATIONS.",
            requiredSigningLevel = "Standard App Signing; Platform/AOSP build for BiometricFramework extension.",
            oemDependencies = "AOSP BiometricService Class 1/2/3 certification.",
            deviceLimitations = "Apps in Private Space have isolated Keystore & Audio permissions."
        ),
        ApiLevelCapabilityRow(
            apiLevel = 36,
            androidVersion = "16",
            codename = "Baklava",
            supportedCapabilities = "Latest targetSdk 36 adaptive UI & security hardening, Identity Check / enhanced biometric protection.",
            restrictedCapabilities = "Hardened Keyguard & identity-check protections strictly isolate third-party apps from system unlock.",
            requiredPermissions = "RECORD_AUDIO, USE_BIOMETRIC, POST_NOTIFICATIONS.",
            requiredSigningLevel = "Standard App Signing; Full OEM/AOSP firmware integration required for lock screen.",
            oemDependencies = "Android 16 Biometric HAL & StrongBox TEE integration.",
            deviceLimitations = "Identity Check requires Class 3 biometrics outside trusted locations."
        )
    )
}
