package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getString
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R

object BiometricAuthenticator {

    const val TAG = "cs3Auth"
    private var biometricManager: BiometricManager? = null
    var biometricPrompt: BiometricPrompt? = null
    var promptInfo: BiometricPrompt.PromptInfo? = null
    var authCallback: BiometricCallback? = null

    private fun initializeBiometrics(activity: FragmentActivity) {
        val executor = ContextCompat.getMainExecutor(activity)
        biometricManager = BiometricManager.from(activity)

        biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        showToast("$errString")
                    }
                    Log.e(TAG, "Biometric Error: $errorCode - $errString")
                    authCallback?.onAuthenticationError()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d(TAG, "Biometric Succeeded")
                    authCallback?.onAuthenticationSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.d(TAG, "Biometric Failed")
                }
            })
    }

    private fun createPromptInfo(
        context: Context,
        titleRes: Int,
        negativeButtonTextRes: Int = R.string.cancel
    ) {
        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(titleRes))
            .setDescription(context.getString(R.string.biometric_prompt_description))
            .setNegativeButtonText(context.getString(negativeButtonTextRes))
            .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
            .build()
    }

    fun isBiometricHardwareAvailable(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        val result = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                manager.canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
            } else {
                @Suppress("DEPRECATION")
                manager.canAuthenticate()
            }
        } catch (e: Exception) {
            Log.e(TAG, "isBiometricHardwareAvailable error", e)
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
        }

        return result == BiometricManager.BIOMETRIC_SUCCESS ||
                result == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
    }

    fun startBiometricAuthentication(
        activity: FragmentActivity,
        titleRes: Int,
        callback: BiometricCallback? = null,
        negativeButtonTextRes: Int = R.string.cancel
    ) {
        initializeBiometrics(activity)
        authCallback = callback ?: (activity as? BiometricCallback)

        if (isBiometricHardwareAvailable(activity)) {
            createPromptInfo(activity, titleRes, negativeButtonTextRes)
            promptInfo?.let { biometricPrompt?.authenticate(it) }
        } else {
            showToast(R.string.biometric_unsupported)
            authCallback?.onAuthenticationError()
        }
    }

    fun isBiometricLoginEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(getString(context, R.string.biometric_key), false)
    }

    fun setBiometricLoginEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(getString(context, R.string.biometric_key), enabled)
            .apply()
    }

    interface BiometricCallback {
        fun onAuthenticationSuccess()
        fun onAuthenticationError()
    }
}
