package com.lagradost.cloudstream3.ui.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object ZetFlixAuthPrefs {
    const val ZETFLIX_AUTH_COMPLETE = "zetflix_auth_complete"
    private const val PREFS_FILE = "zetflix_secure_prefs"

    private var _prefs: SharedPreferences? = null
    private fun getPrefs(context: Context): SharedPreferences {
        return _prefs ?: EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ).also { _prefs = it }
    }

    fun isZetFlixAuthenticated(context: Context): Boolean {
        return getPrefs(context).getBoolean(ZETFLIX_AUTH_COMPLETE, false)
    }

    fun setZetFlixAuthenticated(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(ZETFLIX_AUTH_COMPLETE, value).apply()
    }
}
