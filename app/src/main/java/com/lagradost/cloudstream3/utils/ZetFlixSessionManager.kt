package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.auth.ZetFlixAuthPrefs
import com.lagradost.cloudstream3.ui.auth.ZetFlixLoginActivity

object ZetFlixSessionManager {
    private const val LOGIN_TIMESTAMP_KEY = "zetflix_login_timestamp"
    private const val SESSION_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000L

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        return ZetFlixCryptoUtils.getEncryptedPrefs(context)
    }

    fun setLoginTimestamp(context: Context) {
        getEncryptedPrefs(context).edit().putLong(LOGIN_TIMESTAMP_KEY, System.currentTimeMillis()).apply()
    }

    fun isSessionValid(context: Context): Boolean {
        if (!ZetFlixAuthPrefs.isZetFlixAuthenticated(context)) return false
        
        val timestamp = getEncryptedPrefs(context).getLong(LOGIN_TIMESTAMP_KEY, -1L)
        if (timestamp == -1L) return false
        
        val elapsed = System.currentTimeMillis() - timestamp
        return elapsed <= SESSION_EXPIRY_MS
    }

    fun logout(context: Context) {
        ZetFlixAuthPrefs.setZetFlixAuthenticated(context, false)
        
        getEncryptedPrefs(context).edit().apply {
            remove("phoneCountryCode")
            remove("phoneNationalNumber")
            remove("email")
            remove("password")
            remove("device_id")
            remove("device_secret")
            remove(LOGIN_TIMESTAMP_KEY)
            apply()
        }

        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(context.getString(R.string.biometric_key), false)
            .apply()

        val intent = Intent(context, ZetFlixLoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }
}
