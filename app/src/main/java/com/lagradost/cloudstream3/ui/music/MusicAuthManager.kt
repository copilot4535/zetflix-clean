package com.lagradost.cloudstream3.ui.music

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.lagradost.cloudstream3.CloudStreamApp
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object MusicAuthManager {
    private const val PREFS_NAME = "music_auth_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_COOKIE = "yt_cookie"
    private const val KEY_ACCOUNT_METADATA = "account_metadata"

    private val masterKey by lazy {
        MasterKey.Builder(CloudStreamApp.context!!)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val sharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            CloudStreamApp.context!!,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveTokens(accessToken: String?, refreshToken: String?) {
        sharedPreferences.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            apply()
        }
    }

    fun getAccessToken(): String? = sharedPreferences.getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = sharedPreferences.getString(KEY_REFRESH_TOKEN, null)

    fun saveCookie(cookie: String?) {
        sharedPreferences.edit().putString(KEY_COOKIE, cookie).apply()
    }

    fun getCookie(): String? = sharedPreferences.getString(KEY_COOKIE, null)

    fun saveMetadata(metadata: AccountMetadata?) {
        val json = if (metadata != null) Json.encodeToString(metadata) else null
        sharedPreferences.edit().putString(KEY_ACCOUNT_METADATA, json).apply()
    }

    fun getMetadata(): AccountMetadata? {
        val json = sharedPreferences.getString(KEY_ACCOUNT_METADATA, null) ?: return null
        return try {
            Json.decodeFromString<AccountMetadata>(json)
        } catch (e: Exception) {
            null
        }
    }

    fun clear() {
        sharedPreferences.edit().clear().apply()
    }
}
