package com.lagradost.cloudstream3.ui.auth

import android.content.Context
import android.content.SharedPreferences
import com.lagradost.cloudstream3.utils.ZetFlixCryptoUtils

object ZetFlixAuthPrefs {
    private const val ZETFLIX_AUTH_COMPLETE = "zetflix_auth_complete"
    private const val KEY_EMAIL = "email"
    private const val KEY_PHONE_COUNTRY_CODE = "phoneCountryCode"
    private const val KEY_PHONE_NATIONAL_NUMBER = "phoneNationalNumber"
    private const val KEY_PASSWORD = "password"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_DEVICE_SECRET = "device_secret"

    private fun getPrefs(context: Context): SharedPreferences {
        return ZetFlixCryptoUtils.getEncryptedPrefs(context)
    }

    fun isZetFlixAuthenticated(context: Context): Boolean {
        return getPrefs(context).getBoolean(ZETFLIX_AUTH_COMPLETE, false)
    }

    fun setZetFlixAuthenticated(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(ZETFLIX_AUTH_COMPLETE, value).apply()
    }

    fun saveCredentials(
        context: Context,
        email: String,
        phoneCountryCode: String,
        phoneNationalNumber: String,
        password: String
    ) {
        val deviceId = getPrefs(context).getString(KEY_DEVICE_ID, null) ?: java.util.UUID.randomUUID().toString()
        val deviceSecret = getPrefs(context).getString(KEY_DEVICE_SECRET, null) ?: (java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID().toString())

        getPrefs(context).edit().apply {
            putString(KEY_EMAIL, email.trim().lowercase())
            putString(KEY_PHONE_COUNTRY_CODE, phoneCountryCode.trim())
            putString(KEY_PHONE_NATIONAL_NUMBER, phoneNationalNumber.filter { it.isDigit() })
            putString(KEY_PASSWORD, password.trim())
            putString(KEY_DEVICE_ID, deviceId)
            putString(KEY_DEVICE_SECRET, deviceSecret)
            apply()
        }
    }

    fun getStoredEmail(context: Context): String? = getPrefs(context).getString(KEY_EMAIL, null)
    fun getStoredPhoneNationalNumber(context: Context): String? = getPrefs(context).getString(KEY_PHONE_NATIONAL_NUMBER, null)
    fun getStoredPhoneCountryCode(context: Context): String? = getPrefs(context).getString(KEY_PHONE_COUNTRY_CODE, null)
    fun getStoredPassword(context: Context): String? = getPrefs(context).getString(KEY_PASSWORD, null)

    fun clearCredentials(context: Context) {
        getPrefs(context).edit().apply {
            remove(KEY_EMAIL)
            remove(KEY_PHONE_COUNTRY_CODE)
            remove(KEY_PHONE_NATIONAL_NUMBER)
            remove(KEY_PASSWORD)
            remove(KEY_DEVICE_ID)
            remove(KEY_DEVICE_SECRET)
            apply()
        }
    }
}
