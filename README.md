# MY LOCK — Offline Voice Authentication & System-Level Security Architecture

**MY LOCK** is a production-oriented Android security platform built with Kotlin, Jetpack Compose (Material 3), Clean Architecture + MVVM, Room, Jetpack DataStore, Android Keystore (AES-256-GCM), and AndroidX `BiometricPrompt`.

## Security & Architectural Transparency
- **Standard Third-Party App Scope (Safe Compatibility Mode):** On ordinary Android devices, third-party apps cannot register custom voice biometrics as a native `BiometricPrompt` modality, dismiss `KeyguardManager`, or replace the OS lock screen. MY LOCK operates honestly in **App-Level Safe Compatibility Mode**, combining offline speaker verification, multi-layer anti-spoofing, official `BiometricPrompt` / Device Credential fallback, and a PBKDF2-hashed App Backup PIN.
- **Optional OEM / AOSP Privileged Module (`system-integration/`):** Kept strictly separate from the standard APK. Documents and specifies the `signature|privileged` service, TEE/HAL contract, SELinux policy, and `HardwareAuthToken` lifecycle required for manufacturer-authorized system unlock.
- **Speaker Verification vs Speech Recognition:** MY LOCK extracts 64-dimensional L2-normalized acoustic speaker embeddings (MFCC mean/std, Mel spectral profile, prosodic/spectral stats) and evaluates cosine similarity—it never uses speech-to-text word matching as proof of speaker identity.

## Key Modules
- `app/src/main/java/com/example/mylock/audio/` — `VoiceCaptureManager` (`AudioRecord` 16kHz PCM), `AudioPreprocessor` (DC removal, pre-emphasis, Hamming window, Radix-2 FFT), `VoiceActivityDetector` (SNR/duration/clipping gate).
- `app/src/main/java/com/example/mylock/ml/` & `speaker/` — Modular `SpeakerEmbeddingExtractor`, `AcousticSpectralEmbeddingEngine`, `ExternalNeuralModelBridge` (for licensed ECAPA-TDNN/Wespeaker models), `SpeakerSimilarityEngine`.
- `app/src/main/java/com/example/mylock/antispoofing/` — `ChallengePhraseManager` (30s TTL + nonce), `AcousticLivenessDetector`, `AntiSpoofingEngine` (exact-replay SHA-256 cache, high-freq cutoff playback check, spectral flatness uniformity check), `RateLimiter`.
- `app/src/main/java/com/example/mylock/security/` — `AndroidKeystoreCryptoProvider` (AES-256-GCM, StrongBox/TEE check), `PinCryptoHasher` (PBKDF2WithHmacSHA256), `VoiceProfileVault` (zero-wipe deletion).
- `app/src/main/java/com/example/mylock/compatibility/` — Live `DeviceCompatibilityInspector` + `AndroidVersionCompatibilityMatrix` (API 26–36).
- `app/src/main/java/com/example/mylock/database/` & `data/` — Room `MyLockDatabase` (Audit logs + encrypted profile metadata) & DataStore `SettingsRepository`.
- `app/src/main/java/com/example/mylock/feature/` — Dashboard, Multi-step Enrollment, Speaker Verification, Compatibility Report, Audit Logs, Settings (English & हिन्दी), App Lock Gate.

## Build & Run Commands
- **Compile & Build Debug APK:** `gradle assembleDebug` (APK output: `app/build/outputs/apk/debug/app-debug.apk`)
- **Run Unit & Robolectric Tests:** `gradle :app:testDebugUnitTest`
- **Release APK Build:** Configure upload keystore via `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` environment variables, then run `gradle assembleRelease`.
