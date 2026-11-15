package com.example.mylock.authentication

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

sealed interface BiometricFallbackOutcome {
    data object Authenticated : BiometricFallbackOutcome
    data object Cancelled : BiometricFallbackOutcome
    data class Unavailable(val reason: String) : BiometricFallbackOutcome
    data class Failed(val errorCode: Int, val message: String) : BiometricFallbackOutcome
}

class BiometricFallbackManager {

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String,
        onResult: (BiometricFallbackOutcome) -> Unit
    ) {
        val biometricManager = BiometricManager.from(activity)
        val allowedAuthenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        }

        val canAuth = biometricManager.canAuthenticate(allowedAuthenticators)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            val reason = when (canAuth) {
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                    "No compatible biometric hardware available on this device."
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                    "Biometric hardware is currently unavailable."
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                    "No system fingerprint, face, or screen lock credential is enrolled in Android Settings."
                else -> "System biometric authentication is unavailable (code $canAuth)."
            }
            onResult(BiometricFallbackOutcome.Unavailable(reason))
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onResult(BiometricFallbackOutcome.Authenticated)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                ) {
                    onResult(BiometricFallbackOutcome.Cancelled)
                } else {
                    onResult(BiometricFallbackOutcome.Failed(errorCode, errString.toString()))
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // BiometricPrompt handles retries internally until max attempts
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val promptBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Note: DEVICE_CREDENTIAL cannot be combined with setNegativeButtonText
            promptBuilder.setAllowedAuthenticators(allowedAuthenticators)
        } else {
            promptBuilder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            promptBuilder.setNegativeButtonText(negativeButtonText)
        }

        prompt.authenticate(promptBuilder.build())
    }
}
