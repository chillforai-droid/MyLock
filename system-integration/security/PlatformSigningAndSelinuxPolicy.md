# Platform Signing, SELinux Policy & Recovery Strategy

## 1. Platform Signing & IPC Security
- Both the AOSP framework (`android` package) and `MyLockSystemAgent` must share the OEM's `platform` signing certificate.
- Caller verification on Binder IPC: Check `Binder.getCallingUid()` and `Context.enforceCallingOrSelfPermission("com.mylock.system.permission.BIND_VOICE_UNLOCK", ...)` before handling any challenge or template request.
- Standard third-party apps (including the standard My Lock companion app) must NEVER be trusted with raw `HardwareAuthToken` generation.

## 2. SELinux Policy Sketch (`mylock_voice_auth.te`)
```text
type mylock_voice_auth, domain;
type mylock_voice_auth_exec, exec_type, system_file_type, file_type;

# Allow binder communication only with biometric_service & audioserver
binder_use(mylock_voice_auth)
binder_call(mylock_voice_auth, system_server)
binder_call(mylock_voice_auth, audioserver)

# Strictly deny direct third-party app socket or file access
neverallow untrusted_app mylock_voice_auth:binder call;
```

## 3. Device Certification Requirements (CDD & CTS)
- **Android CDD Section 7.3.10 (Biometric Sensors):**
  - Measure Spoof Acceptance Rate (SAR) against recorded replay, TTS, and voice cloning attacks.
  - Measure Imposter Accept Rate (IAR) and False Accept Rate (FAR <= 1/50,000 for Class 3 Strong; otherwise classify strictly as Class 1 Convenience / Class 2 Weak).
  - Never allow Class 1 Convenience voice unlock to unlock Android Keystore `AUTH_BIOMETRIC_STRONG` keys or authorize payments.
- **CTS:** Run `CtsBiometricsTestCases` and `CtsDevicePolicyManagerTestCases` to verify zero regression to Keyguard enforcement.

## 4. Rollback & Recovery Strategy
- **Cryptographic Wipe:** If voice HAL fails attestation or integrity checks, revoke voice templates immediately without affecting the user's primary PIN/password or StrongBox keys.
- **Graceful Degradation:** On HAL timeout or crash, `Keyguard` falls back immediately to primary PIN/Password/Strong Biometric.
- **OTA Rollback Protection:** Gate model and HAL updates via Android Verified Boot (AVB 2.0) rollback indices.
