# MY LOCK — Architecture & Security Design

## 1. Layered Architecture (Clean Architecture + MVVM)
- **Presentation (`feature/*`, `design/*`):** Jetpack Compose + Material 3 with adaptive `NavigationBar` (compact) and `NavigationRail` (medium/expanded), bilingual `LocalStrings` (`en` / `hi`), and reactive `StateFlow` observation via `collectAsStateWithLifecycle()`.
- **Domain / Orchestration (`domain/*`, `authentication/*`):** `MyLockViewModel` and `VoiceAuthenticationManager` coordinate enrollment, verification, anti-spoofing, and official `BiometricFallbackManager`.
- **Audio & Biometric Signal Pipeline (`audio/*`, `ml/*`, `speaker/*`, `antispoofing/*`):**
  1. `AudioCaptureSource` captures 16 kHz 16-bit mono PCM.
  2. `AudioPreprocessor` removes DC offset, applies pre-emphasis (`0.97`), normalizes, and produces 25 ms Hamming frames.
  3. `VoiceActivityDetector` measures SNR, active speech duration, and clipping ratio.
  4. `AntiSpoofingEngine` validates dynamic challenge TTL/nonce, checks rate-limiter lockout, verifies SHA-256 non-replay uniqueness, and evaluates HNR / spectral rolloff / flatness variance.
  5. `SpeakerEmbeddingExtractor` computes L2-normalized speaker vectors; `SpeakerSimilarityEngine` fuses enrollment centroids and scores cosine similarity against `SecurityLevel` thresholds (`LOW=0.72`, `STANDARD=0.80`, `HIGH=0.87`).
- **Security & Persistence (`security/*`, `database/*`, `data/*`):**
  - Voice embeddings are serialized and encrypted via `AndroidKeystoreCryptoProvider` (AES-256-GCM, StrongBox when supported, TEE otherwise) into `VoiceProfileVault`.
  - Raw audio is discarded immediately after feature extraction unless the user explicitly opts in via Privacy Controls.
  - Security events are persisted locally in Room (`MyLockDatabase`), and preferences in Jetpack DataStore (`SettingsRepository`).
  - Zero `INTERNET` permission is declared in `AndroidManifest.xml`, guaranteeing offline-only operation.

## 2. Pretrained Neural Model Integration Path
To plug in a licensed ECAPA-TDNN or Wespeaker ONNX/TFLite model, implement `SpeakerEmbeddingExtractor`, bundle the verified `.onnx`/`.tflite` asset, and wire it into `MyLockContainer`. Changing `modelVersion` ensures old embeddings are safely identified for controlled re-enrollment.
