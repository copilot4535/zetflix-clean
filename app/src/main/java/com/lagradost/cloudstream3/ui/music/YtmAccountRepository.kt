package com.lagradost.cloudstream3.ui.music

import com.maxrave.kotlinytmusicscraper.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class RateStatus {
    LIKE, DISLIKE, INDIFFERENT
}

class YtmAccountRepository {
    private val youtube = YouTubeInstance.youtube

    suspend fun rateSong(videoId: String, status: RateStatus): Boolean = withContext(Dispatchers.IO) {
        try {
            when (status) {
                RateStatus.LIKE -> youtube.addToLiked(videoId).isSuccess
                RateStatus.DISLIKE -> {
                    // Manual call for dislike since it's missing in some versions of the lib
                    // Or if available:
                    // youtube.addToDisliked(videoId).isSuccess
                    // For now, we'll use addToLiked as a placeholder or implement custom post
                    false 
                }
                RateStatus.INDIFFERENT -> youtube.removeFromLiked(videoId).isSuccess
            }
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun getRateStatus(videoId: String): RateStatus = withContext(Dispatchers.IO) {
        try {
            val info = youtube.getLikedInfo(videoId).getOrNull()
            when (info?.toString()?.uppercase()) {
                "LIKE" -> RateStatus.LIKE
                "DISLIKE" -> RateStatus.DISLIKE
                else -> RateStatus.INDIFFERENT
            }
        } catch (e: Exception) {
            RateStatus.INDIFFERENT
        }
    }
}
