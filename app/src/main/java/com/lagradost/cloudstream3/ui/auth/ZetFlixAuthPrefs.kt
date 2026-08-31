package com.lagradost.cloudstream3.ui.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

object ZetFlixAuthPrefs {
    const val PREFS_FILE = "zetflix_auth_secure_prefs"
    private const val TAG = "ZetFlixAuthDebug"

    private var encryptedPrefs: SharedPreferences? = null

    private fun getPrefs(context: Context): SharedPreferences {
        synchronized(this) {
            if (encryptedPrefs == null) {
                try {
                    encryptedPrefs = createEncryptedPrefs(context)
                    migrateOldPrefs(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing EncryptedSharedPreferences, trying to clear corrupted prefs", e)
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                            context.deleteSharedPreferences(PREFS_FILE)
                        } else {
                            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).edit().clear().apply()
                        }
                        encryptedPrefs = createEncryptedPrefs(context)
                        migrateOldPrefs(context)
                    } catch (e2: Exception) {
                        Log.e(TAG, "Critical failure initializing EncryptedSharedPreferences. Falling back to unencrypted storage.", e2)
                        // Use a different filename for fallback to avoid format conflicts with encrypted storage
                        encryptedPrefs = context.getSharedPreferences(PREFS_FILE + "_fallback", Context.MODE_PRIVATE)
                    }
                }
            }
            return encryptedPrefs!!
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun migrateOldPrefs(context: Context) {
        val oldPrefs = context.getSharedPreferences("zetflix_auth_prefs", Context.MODE_PRIVATE)
        if (oldPrefs.all.isNotEmpty()) {
            Log.d(TAG, "Migrating old unencrypted preferences to secure storage")
            val editor = encryptedPrefs?.edit() ?: return
            oldPrefs.all.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                }
            }
            editor.apply()
            oldPrefs.edit().clear().apply()
            Log.d(TAG, "Migration complete. Old preferences cleared.")
        }
    }

    fun getString(context: Context, key: String, defaultValue: String? = null): String? {
        return try {
            val value = getPrefs(context).getString(key, defaultValue)
            Log.d(TAG, "Prefs Read: key=$key, value=" + (if (key == "password") "****" else value.toString()))
            value
        } catch (e: Exception) {
            Log.e(TAG, "Error reading key: $key", e)
            null
        }
    }

    fun putString(context: Context, key: String, value: String?) {
        try {
            Log.d(TAG, "Prefs Write: key=$key, value=" + (if (key == "password") "****" else value.toString()))
            getPrefs(context).edit().putString(key, value).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error writing key: $key", e)
        }
    }

    fun getBoolean(context: Context, key: String, defaultValue: Boolean = false): Boolean {
        return try {
            val value = getPrefs(context).getBoolean(key, defaultValue)
            Log.d(TAG, "Prefs Read: key=$key, value=$value")
            value
        } catch (e: Exception) {
            Log.e(TAG, "Error reading key: $key", e)
            defaultValue
        }
    }

    fun putBoolean(context: Context, key: String, value: Boolean) {
        try {
            Log.d(TAG, "Prefs Write: key=$key, value=$value")
            getPrefs(context).edit().putBoolean(key, value).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error writing key: $key", e)
        }
    }

    fun isZetFlixAuthenticated(context: Context): Boolean {
        return getBoolean(context, "zetflix_auth_complete", false)
    }

    fun setZetFlixAuthenticated(context: Context, value: Boolean) {
        putBoolean(context, "zetflix_auth_complete", value)
    }

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
        Log.d(TAG, "Prefs Clear Credentials")
        try {
            getPrefs(context).edit().clear().apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing credentials", e)
        }
    }
}
