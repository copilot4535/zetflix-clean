package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import com.lagradost.cloudstream3.ui.auth.ZetFlixAuthPrefs
import com.lagradost.cloudstream3.ui.auth.ZetFlixLoginActivity

object ZetFlixSessionManager {
    private const val TAG = "ZetFlixAuthDebug"

    fun logout(context: Context) {
        Log.d(TAG, "Logout initiated")
        ZetFlixAuthPrefs.setZetFlixAuthenticated(context, false)
        // We do NOT clear credentials here so the user can log back in locally.
        // ZetFlixAuthPrefs.clearCredentials(context)
        
        BiometricAuthenticator.setBiometricLoginEnabled(context, false)

        val intent = Intent(context, ZetFlixLoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }
}
