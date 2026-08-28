package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

object ZetFlixCryptoUtils {
    private const val PREFS_FILE = "zetflix_secure_prefs"
    private const val TAG = "ZetFlixCryptoUtils"

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    fun getEncryptedPrefs(context: Context): SharedPreferences {
        return cachedPrefs ?: synchronized(this) {
            cachedPrefs ?: try {
                createPrefs(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error creating EncryptedSharedPreferences, resetting...", e)
                resetPrefs(context)
                createPrefs(context)
            }.also { cachedPrefs = it }
        }
    }

    private fun createPrefs(context: Context): SharedPreferences {
        val mainKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            mainKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun resetPrefs(context: Context) {
        try {
            val prefsPath = context.filesDir.parent?.let { "$it/shared_prefs/$PREFS_FILE.xml" }
            if (prefsPath != null) {
                val sharedPrefsFile = File(prefsPath)
                if (sharedPrefsFile.exists()) {
                    sharedPrefsFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset preferences file", e)
        }
    }
}
