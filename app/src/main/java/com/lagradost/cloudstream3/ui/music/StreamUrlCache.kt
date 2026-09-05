package com.lagradost.cloudstream3.ui.music

import android.util.LruCache

object StreamUrlCache {
    private const val CACHE_SIZE = 50
    private const val EXPIRATION_TIME_MS = 30 * 60 * 1000L // 30 minutes

    private val cache = LruCache<String, CacheEntry>(CACHE_SIZE)

    private data class CacheEntry(
        val url: String,
        val timestamp: Long
    )

    fun get(videoId: String): String? {
        val entry = cache.get(videoId) ?: return null
        if (System.currentTimeMillis() - entry.timestamp > EXPIRATION_TIME_MS) {
            cache.remove(videoId)
            return null
        }
        return entry.url
    }

    fun put(videoId: String, url: String) {
        cache.put(videoId, CacheEntry(url, System.currentTimeMillis()))
    }

    fun remove(videoId: String) {
        cache.remove(videoId)
    }

    fun clear() {
        cache.evictAll()
    }
}
