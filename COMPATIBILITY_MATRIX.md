# MY LOCK — Android API 26–36 Compatibility Matrix & Test Plan

| API | Android | Supported in Standard App | Restricted (Requires OEM / Platform) | Required Permissions |
|---|---|---|---|---|
| 26 | 8.0 Oreo | `AudioRecord`, Keystore AES-GCM, `KeyguardManager.isKeyguardSecure` | No `BiometricPrompt`, no StrongBox, no Keyguard unlock | `RECORD_AUDIO`, `VIBRATE` |
| 27 | 8.1 Oreo | Same as API 26 + NNAPI 1.0 | No native `BiometricPrompt`, background mic blocked | `RECORD_AUDIO`, `VIBRATE` |
| 28 | 9 Pie | `BiometricPrompt`, StrongBox check (`FEATURE_STRONGBOX_KEYSTORE`) | Idle background mic silenced; Class 3 only | `RECORD_AUDIO`, `USE_BIOMETRIC` |
| 29 | 10 Q | `BiometricManager.canAuthenticate()`, Scoped Storage | Background activity launches blocked | `RECORD_AUDIO`, `USE_BIOMETRIC` |
| 30 | 11 R | `Authenticators` flags (`BIOMETRIC_STRONG`/`DEVICE_CREDENTIAL`), Keystore auth params | One-time mic permission; FGS mic type required | `RECORD_AUDIO`, `USE_BIOMETRIC` |
| 31 | 12 S | Material You, Privacy Dashboard mic indicator awareness | Global mic privacy toggle silences capture; FGS launch restrictions | `RECORD_AUDIO`, `USE_BIOMETRIC` |
| 32 | 12L | Foldable / large-screen adaptive `NavigationRail` | Same as API 31 | `RECORD_AUDIO`, `USE_BIOMETRIC` |
| 33 | 13 Tiramisu | Per-app localization, `POST_NOTIFICATIONS` runtime check | Notification requires explicit grant | `RECORD_AUDIO`, `USE_BIOMETRIC`, `POST_NOTIFICATIONS` |
| 34 | 14 U | Strict FGS type checks, Credential Manager synergy | `FOREGROUND_SERVICE_MICROPHONE` required for any FGS mic | `RECORD_AUDIO`, `USE_BIOMETRIC`, `POST_NOTIFICATIONS` |
| 35 | 15 V | Mandatory edge-to-edge, Private Space isolation | `BOOT_COMPLETED` cannot launch mic FGS | `RECORD_AUDIO`, `USE_BIOMETRIC`, `POST_NOTIFICATIONS` |
| 36 | 16 Baklava | Hardened Identity Check + targetSdk 36 compliance | Full OEM/AOSP HAL required for lock screen | `RECORD_AUDIO`, `USE_BIOMETRIC`, `POST_NOTIFICATIONS` |

## Compatibility Test Plan (API 26–36)
1. **Automated Multi-SDK Robolectric Matrix (`CompatibilityAndApiMatrixRobolectricTest`):** Executes across SDKs 26, 28, 30, 33, 34, 35, and 36 verifying `DeviceCompatibilityInspector`, `StandardSafeCompatibilityBridge`, permission flags, and API matrix completeness for all 11 API levels (26..36).
2. **Audio & Security Unit Suite (`VoiceEngineAndSecurityTest`):** Tests mic denied, no mic hardware, short/silent/noisy/clipping audio, same-speaker match vs different-speaker rejection, exact SHA-256 replay rejection, high-freq playback cutoff detection, challenge expiration timeout, 5-attempt rate-limit lockout, Keystore/AES encryption + secure wipe, and PBKDF2 backup PIN verification.
3. **Compose CUJ Robolectric Suite (`MyLockComposeCujRobolectricTest`):** Verifies Dashboard rendering, Safe Compatibility Mode banner, navigation across Enrollment/Verify/Logs/Settings/Compatibility, and bilingual switching.
