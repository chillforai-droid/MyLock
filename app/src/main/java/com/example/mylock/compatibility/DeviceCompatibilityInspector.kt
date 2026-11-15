package com.example.mylock.compatibility

import android.Manifest
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import com.example.mylock.core.model.DeviceCompatibilityReport
import com.example.mylock.oem.SystemVoiceUnlockBridge

class DeviceCompatibilityInspector(
    private val context: Context,
    private val systemBridge: SystemVoiceUnlockBridge
) {

    fun inspect(): DeviceCompatibilityReport {
        val pm = context.packageManager
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val biometricManager = BiometricManager.from(context)

        val hasMicFeature = pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        val hasInputDevice = audioManager?.getDevices(AudioManager.GET_DEVICES_INPUTS)
            ?.any {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            } ?: hasMicFeature

        val micPermissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notificationRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val notificationGranted = if (notificationRequired) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val strongResult = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        val weakResult = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        val deviceCredentialResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            if (keyguardManager?.isDeviceSecure == true) {
                BiometricManager.BIOMETRIC_SUCCESS
            } else {
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
            }
        }

        val strongBoxSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        } else {
            false
        }

        val isDeviceOwner = dpm?.isDeviceOwnerApp(context.packageName) ?: false
        val isProfileOwner = dpm?.isProfileOwnerApp(context.packageName) ?: false

        val ignoringBatteryOpt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else {
            true
        }

        return DeviceCompatibilityReport(
            apiLevel = Build.VERSION.SDK_INT,
            androidVersionRelease = Build.VERSION.RELEASE ?: "Unknown",
            deviceManufacturer = Build.MANUFACTURER ?: "Unknown",
            deviceModel = Build.MODEL ?: "Unknown",
            hasMicrophoneHardware = hasMicFeature || hasInputDevice,
            microphonePermissionGranted = micPermissionGranted,
            notificationPermissionRequired = notificationRequired,
            notificationPermissionGranted = notificationGranted,
            isKeyguardSecure = keyguardManager?.isDeviceSecure ?: false,
            isDeviceLocked = keyguardManager?.isKeyguardLocked ?: false,
            biometricStrongAvailable = strongResult == BiometricManager.BIOMETRIC_SUCCESS,
            biometricWeakAvailable = weakResult == BiometricManager.BIOMETRIC_SUCCESS,
            deviceCredentialAvailable = deviceCredentialResult == BiometricManager.BIOMETRIC_SUCCESS,
            strongBoxSupported = strongBoxSupported,
            keystoreHardwareBackedPossible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M,
            isDeviceOwner = isDeviceOwner,
            isProfileOwner = isProfileOwner,
            ignoringBatteryOptimizations = ignoringBatteryOpt,
            foregroundServiceMicPermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            systemIntegrationStatus = systemBridge.inspectIntegrationStatus()
        )
    }
}
