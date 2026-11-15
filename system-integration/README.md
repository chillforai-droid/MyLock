# MY LOCK — Optional OEM / AOSP System-Privileged Integration Module

> **SECURITY BOUNDARY NOTICE:**
> This directory defines the architectural specification, AIDL/Binder-aligned service contracts, SELinux policy requirements, and AOSP integration points required for a **device manufacturer (OEM) or custom AOSP ROM build** to support hardware-backed system-level voice authentication.
>
> **It is NOT compiled into the standard Google Play / third-party APK (`app/`).**
> A standard third-party APK cannot obtain `signature|privileged` permissions, cannot bind to `KeyguardService`, and cannot register a custom voice modality with `BiometricPrompt`.

## Required Privilege Level
- **Partition:** `/system_ext/priv-app/MyLockSystemAgent/` or `/vendor/`
- **Signing Key:** AOSP `platform` release certificate matching `frameworks/base`
- **SELinux Domain:** Dedicated `mylock_voice_auth` domain with explicit binder IPC rules to `biometric_service` / `trust_service` and `audioserver`
- **Hardware Root of Trust:** TEE (TrustZone) or StrongBox Keymaster + dedicated DSP SoundTrigger HAL for low-power wake and hardware attestation

## Components in This Module
1. `AndroidManifest.xml` — Privileged system service manifest (`signature|privileged` permissions, `directBootAware="true"`)
2. `service/SystemVoiceUnlockServiceSpec.kt` — Privileged system service boundary & token lifecycle
3. `authentication/OemSpeakerAuthHalContract.kt` — TEE/HAL attestation & speaker verification contract
4. `keyguard/KeyguardTrustBoundaryDoc.md` — AOSP `SystemUI` / `BiometricService` / `TrustManagerService` modification points
5. `security/PlatformSigningAndSelinuxPolicy.md` — Platform signing, SELinux `.te` rules, CTS/CDD compliance, and rollback/recovery
6. `tests/SystemIntegrationContractTestPlan.md` — VTS/CTS and OEM integration verification plan
