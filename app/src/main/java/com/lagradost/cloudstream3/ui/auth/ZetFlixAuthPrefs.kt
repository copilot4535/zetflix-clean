package com.lagradost.cloudstream3.ui.auth

import android.content.Context
import android.content.SharedPreferences
import com.lagradost.cloudstream3.utils.ZetFlixCryptoUtils

object ZetFlixAuthPrefs {
    const val ZETFLIX_AUTH_COMPLETE = "zetflix_auth_complete"

    private fun getPrefs(context: Context): SharedPreferences {
        return ZetFlixCryptoUtils.getEncryptedPrefs(context)
    }

    fun isZetFlixAuthenticated(context: Context): Boolean {
        return getPrefs(context).getBoolean(ZETFLIX_AUTH_COMPLETE, false)
    }

    fun setZetFlixAuthenticated(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(ZETFLIX_AUTH_COMPLETE, value).apply()
    }
}
