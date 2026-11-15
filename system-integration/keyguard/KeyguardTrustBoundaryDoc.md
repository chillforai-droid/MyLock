# Keyguard & AOSP Trust Boundary Documentation

## Why Standard Apps Cannot Unlock Android Keyguard
1. **`KeyguardManager` Isolation:** `KeyguardManager.requestDismissKeyguard()` on a non-privileged app only prompts the user to enter their existing PIN/Pattern/Biometric. It cannot accept a third-party boolean "match" result to unlock the device.
2. **`BiometricPrompt` Modality Whitelist:** `BiometricPrompt` delegates exclusively to `BiometricService` (`frameworks/base/services/core/java/com/android/server/biometrics/`), which binds only to registered `IBiometricAuthenticator` HAL services configured in `config_biometric_sensors`.
3. **Accessibility Service Abuse Prohibition:** Using `AccessibilityService` to simulate PIN taps or dismiss lock screens is insecure and violates Google Play policy.

## Authorized AOSP Modification Points (OEM / Custom ROM Only)
1. **`frameworks/base/core/res/res/values/config.xml`**:
   - Register voice biometric sensor ID, modality (`TYPE_VOICE`), and CDD strength tier in `config_biometric_sensors` (Class 1 Convenience or Class 2 Weak unless SAR/IAR/FAR/spoof thresholds meet Class 3 Strong under Android CDD 7.3.10).
2. **`frameworks/base/services/core/java/com/android/server/biometrics/`**:
   - Add `VoiceSensor` / `BiometricVoiceService` binding to `OemSpeakerAuthHalContract` over stable AIDL (`android.hardware.biometrics.voice`).
3. **`frameworks/base/services/core/java/com/android/server/trust/TrustManagerService.java`**:
   - Alternative bounded path: Platform-signed `TrustAgentService` extending `android.service.trust.TrustAgentService` granting temporary `grantTrust()` ONLY after primary device unlock, never on cold boot (FBE Direct Boot requires user PIN/password).
4. **SystemUI (`KeyguardUpdateMonitor`)**:
   - Display explicit microphone active privacy indicator and fallback prompt to primary PIN/Strong Biometric after 5 failed attempts or 4 hours idle.
