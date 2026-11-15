# System Integration Contract Test Plan (OEM / AOSP)

1. **Binder Caller Privilege Verification**: Confirm `untrusted_app` UIDs receive `SecurityException` when attempting to bind `MyLockPrivilegedVoiceService`.
2. **Direct Boot Enforcement**: Verify voice unlock cannot dismiss Keyguard prior to first primary credential unlock (`UserManager.isUserUnlocked == false`).
3. **HardwareAuthToken Binding**: Verify Keystore rejects HAT if `challenge` or `userId` is tampered with.
4. **Rate-Limit Persistence**: Confirm 5 failed voice attempts trigger TEE/Keymaster lockout across service restarts.
5. **Fallback Continuity**: Kill voice HAL process during lock screen presentation; verify Keyguard immediately presents primary PIN/Pattern/Strong Biometric with zero lockouts.
