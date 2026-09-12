package com.lagradost.cloudstream3.ui.music

import com.maxrave.kotlinytmusicscraper.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import android.content.Context
import com.lagradost.cloudstream3.CloudStreamApp
import kotlinx.coroutines.flow.update

class YtmAccountRepository {
    private val youtube = YouTubeInstance.youtube
    
    private val _accountState = MutableStateFlow(YouTubeAccountState(AccountConnectionState.DISCONNECTED))
    val accountState: StateFlow<YouTubeAccountState> = _accountState.asStateFlow()

    init {
        // Load initial state from secure storage
        val metadata = MusicAuthManager.getMetadata()
        val cookie = MusicAuthManager.getCookie()
        
        if (cookie != null && metadata != null) {
            youtube.cookie = cookie
            _accountState.value = YouTubeAccountState(AccountConnectionState.CONNECTED, metadata)
        }
    }

    suspend fun connect(cookie: String, metadata: AccountMetadata) = withContext(Dispatchers.IO) {
        _accountState.update { it.copy(connectionState = AccountConnectionState.CONNECTING) }
        try {
            // Validate account with scraper
            val result = youtube.accountInfo(cookie)
            if (result.isSuccess) {
                MusicAuthManager.saveCookie(cookie)
                MusicAuthManager.saveMetadata(metadata)
                youtube.cookie = cookie
                _accountState.update { 
                    it.copy(
                        connectionState = AccountConnectionState.CONNECTED,
                        metadata = metadata
                    )
                }
            } else {
                _accountState.update { 
                    it.copy(
                        connectionState = AccountConnectionState.ERROR,
                        errorMessage = "Failed to validate account"
                    )
                }
            }
        } catch (e: Exception) {
            _accountState.update { 
                it.copy(
                    connectionState = AccountConnectionState.ERROR,
                    errorMessage = e.message
                )
            }
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        MusicAuthManager.clear()
        youtube.cookie = null
        youtube.visitorData = YouTube.DEFAULT_VISITOR_DATA
        _accountState.value = YouTubeAccountState(AccountConnectionState.DISCONNECTED)
    }

    suspend fun rateSong(videoId: String, status: RateStatus): Boolean = withContext(Dispatchers.IO) {
        try {
            when (status) {
                RateStatus.LIKE -> youtube.addToLiked(videoId).isSuccess
                RateStatus.DIFFERENT -> youtube.removeFromLiked(videoId).isSuccess
                RateStatus.DISLIKE -> false // Placeholder
            }
        } catch (e: Exception) {
            if (e.message?.contains("401") == true) {
                _accountState.update { it.copy(connectionState = AccountConnectionState.REAUTH_REQUIRED) }
            }
            false
        }
    }
    
    suspend fun getRateStatus(videoId: String): RateStatus = withContext(Dispatchers.IO) {
        try {
            val info = youtube.getLikedInfo(videoId).getOrNull()
            when (info?.toString()?.uppercase()) {
                "LIKE" -> RateStatus.LIKE
                "DISLIKE" -> RateStatus.DISLIKE
                else -> RateStatus.DIFFERENT
            }
        } catch (e: Exception) {
            RateStatus.DIFFERENT
        }
    }
}

enum class RateStatus {
    LIKE, DISLIKE, DIFFERENT
}
