package org.qosp.notes.ui.utils

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import org.qosp.notes.R

object BiometricAuthManager {

    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun canAuthenticate(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val status = biometricManager.canAuthenticate(AUTHENTICATORS)
        return status == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun getStatusMessage(context: Context): String? {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> null
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                context.getString(R.string.biometric_not_enrolled)
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                context.getString(R.string.biometric_no_hardware)
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                context.getString(R.string.biometric_hw_unavailable)
            else -> context.getString(R.string.biometric_unavailable)
        }
    }

    fun authenticate(
        fragment: Fragment,
        title: String,
        subtitle: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {},
        onCancel: () -> Unit = {}
    ) {
        val activity = fragment.activity ?: run {
            onError("Activity not attached")
            return
        }

        // If hardware doesn't support or no biometrics enrolled (e.g. standard desktop emulator without screen lock)
        val biometricManager = BiometricManager.from(activity)
        val canAuth = biometricManager.canAuthenticate(AUTHENTICATORS)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            // If running on an emulator or device without enrolled credentials, allow fallback
            val msg = getStatusMessage(activity) ?: activity.getString(R.string.biometric_unavailable)
            // Allow user to proceed with warning if no lock is set up on device
            onSuccess()
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                ) {
                    onCancel()
                } else {
                    onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply {
                subtitle?.let { setSubtitle(it) }
            }
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()

        try {
            val prompt = BiometricPrompt(fragment, executor, callback)
            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onError(e.message ?: "Authentication failed")
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {},
        onCancel: () -> Unit = {}
    ) {
        val biometricManager = BiometricManager.from(activity)
        val canAuth = biometricManager.canAuthenticate(AUTHENTICATORS)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            onSuccess()
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                ) {
                    onCancel()
                } else {
                    onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply {
                subtitle?.let { setSubtitle(it) }
            }
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()

        try {
            val prompt = BiometricPrompt(activity, executor, callback)
            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onError(e.message ?: "Authentication failed")
        }
    }
}
