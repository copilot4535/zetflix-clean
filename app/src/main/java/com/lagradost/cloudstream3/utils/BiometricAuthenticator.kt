package com.lagradost.cloudstream3.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getString
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R

object BiometricAuthenticator {

    const val TAG = "cs3Auth"
    private const val MAX_FAILED_ATTEMPTS = 3
    private var failedAttempts = 0
    private var biometricManager: BiometricManager? = null
    var biometricPrompt: BiometricPrompt? = null
    var promptInfo: BiometricPrompt.PromptInfo? = null
    var authCallback: BiometricCallback? = null // listen to authentication success

    private fun initializeBiometrics(activity: FragmentActivity) {
        val executor = ContextCompat.getMainExecutor(activity)

        biometricManager = BiometricManager.from(activity)

        biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    showToast("$errString")
                    Log.e(TAG, "$errorCode")
                    authCallback?.onAuthenticationError()
                        //activity.finish()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    failedAttempts = 0
                    authCallback?.onAuthenticationSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    failedAttempts++
                    if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                        failedAttempts = 0
                        activity.finish()
                    }
                }
            })
    }

    @Suppress("DEPRECATION")
    // authentication dialog prompt builder
    private fun authenticationDialog(
        activity: Activity,
        title: Int,
        setDeviceCred: Boolean,
    ) {
        val description = activity.getString(R.string.biometric_prompt_description)

        if (setDeviceCred) {
            // For API level > 30, Newer API setAllowedAuthenticators is used
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

                val authFlag = DEVICE_CREDENTIAL or BIOMETRIC_WEAK or BIOMETRIC_STRONG
                promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(activity.getString(title))
                    .setDescription(description)
                    .setAllowedAuthenticators(authFlag)
                    .build()
            } else {
                // for apis < 30
                promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(activity.getString(title))
                    .setDescription(description)
                    .setDeviceCredentialAllowed(true)
                    .build()
            }
        } else {
            promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(title))
                .setDescription(description)
                .setNegativeButtonText(activity.getString(R.string.zetflix_login_button)) // "Login" or "Use Password"
                .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
                .build()
        }
    }

    // checks if device is secured i.e has at least some type of lock
    fun deviceHasPasswordPinLock(context: Context?): Boolean {
        val keyMgr =
            context?.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyMgr?.isKeyguardSecure ?: false
    }

    fun canSetupBiometrics(context: Context): Boolean {
        return deviceHasPasswordPinLock(context) && isBiometricHardwareAvailable(context)
    }

    fun isBiometricAvailable(context: Context): Boolean {
        return isBiometricHardwareAvailable(context)
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
        
        // Permissive check: show if hardware isn't explicitly missing
        return result != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE && 
               result != BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED &&
               result != BiometricManager.BIOMETRIC_STATUS_UNKNOWN
    }

    // function to start authentication in any fragment or activity
    fun startBiometricAuthentication(
        activity: FragmentActivity,
        title: Int,
        setDeviceCred: Boolean,
        callback: BiometricCallback? = null
    ) {
        initializeBiometrics(activity)
        authCallback = callback ?: (activity as? BiometricCallback)
        
        if (isBiometricHardwareAvailable(activity)) {
            authenticationDialog(activity, title, setDeviceCred)
            promptInfo?.let { biometricPrompt?.authenticate(it) }
        } else {
            if (setDeviceCred && deviceHasPasswordPinLock(activity)) {
                authenticationDialog(activity, R.string.password_pin_authentication_title, true)
                promptInfo?.let { biometricPrompt?.authenticate(it) }
            } else {
                showToast(R.string.biometric_unsupported)
                authCallback?.onAuthenticationError()
            }
        }
    }

    fun isFingerprintEnabled(context: Context): Boolean {
        return isAuthEnabled(context)
    }

    fun isAuthEnabled(ctx: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(ctx)
            .getBoolean(getString(ctx, R.string.biometric_key), false)
    }

    fun setFingerprintEnabled(context: Context, enabled: Boolean) {
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
