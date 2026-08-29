package com.lagradost.cloudstream3.ui.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.UUID

object ZetFlixAuthPrefs {
    const val PREFS_FILE = "zetflix_auth_prefs"
    private const val TAG = "ZetFlixAuthDebug"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    }

    fun getString(context: Context, key: String, defaultValue: String? = null): String? {
        val value = getPrefs(context).getString(key, defaultValue)
        Log.d(TAG, "Prefs Read: file=$PREFS_FILE, key=$key, value=" + (if (key == "password") "****" else value.toString()))
        return value
    }

    fun putString(context: Context, key: String, value: String?) {
        Log.d(TAG, "Prefs Write: file=$PREFS_FILE, key=$key, value=" + (if (key == "password") "****" else value.toString()))
        getPrefs(context).edit().putString(key, value).apply()
    }

    fun getBoolean(context: Context, key: String, defaultValue: Boolean = false): Boolean {
        val value = getPrefs(context).getBoolean(key, defaultValue)
        Log.d(TAG, "Prefs Read: file=$PREFS_FILE, key=$key, value=" + value.toString())
        return value
    }

    fun putBoolean(context: Context, key: String, value: Boolean) {
        Log.d(TAG, "Prefs Write: file=$PREFS_FILE, key=$key, value=" + value.toString())
        getPrefs(context).edit().putBoolean(key, value).apply()
    }

    fun isZetFlixAuthenticated(context: Context): Boolean {
        return getBoolean(context, "zetflix_auth_complete", false)
    }

    fun setZetFlixAuthenticated(context: Context, value: Boolean) {
        putBoolean(context, "zetflix_auth_complete", value)
    }

    // Specific helpers for consistent keys
    fun getStoredEmail(context: Context): String? = getString(context, "email")
    fun getStoredPhoneNationalNumber(context: Context): String? = getString(context, "phoneNationalNumber")
    fun getStoredPhoneCountryCode(context: Context): String? = getString(context, "phoneCountryCode")
    fun getStoredPassword(context: Context): String? = getString(context, "password")

    fun saveCredentials(
        context: Context,
        email: String,
        phoneCountryCode: String,
        phoneNationalNumber: String,
        password: String,
    ) {
        val deviceId = getString(context, "device_id") ?: UUID.randomUUID().toString()
        val deviceSecret = getString(context, "device_secret") ?: UUID.randomUUID().toString()

        putString(context, "email", email.trim().lowercase())
        putString(context, "phoneCountryCode", phoneCountryCode.trim())
        putString(context, "phoneNationalNumber", phoneNationalNumber.filter { it.isDigit() })
        putString(context, "password", password.trim())
        putString(context, "device_id", deviceId)
        putString(context, "device_secret", deviceSecret)
    }

    fun clearCredentials(context: Context) {
        Log.d(TAG, "Prefs Clear Credentials: file=$PREFS_FILE")
        getPrefs(context).edit().apply {
            remove("email")
            remove("phoneCountryCode")
            remove("phoneNationalNumber")
            remove("password")
            remove("device_id")
            remove("device_secret")
            apply()
        }
    }
}
