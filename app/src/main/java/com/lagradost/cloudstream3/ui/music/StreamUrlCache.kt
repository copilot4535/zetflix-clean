package com.lagradost.cloudstream3.ui.music

import android.util.Log
import android.util.LruCache

object StreamUrlCache {
    private const val TAG = "StreamUrlCache"
    private const val CACHE_SIZE = 50
    private const val DEFAULT_EXPIRATION_MS = 30 * 60 * 1000L // 30 minutes

    private val cache = LruCache<String, CachedStream>(CACHE_SIZE)

    data class CachedStream(
        val url: String,
        val videoId: String,
        val createdAt: Long,
        val expiresAt: Long,
        val bitrate: Int? = null,
        val itag: Int? = null,
        val mimeType: String? = null
    ) {
        val isExpired: Boolean
            get() = System.currentTimeMillis() > expiresAt
    }

    fun get(videoId: String): String? {
        val entry = cache.get(videoId)
        if (entry == null) {
            Log.d(TAG, "Cache miss for $videoId")
            return null
        }
        
        if (entry.isExpired) {
            Log.d(TAG, "Cache expired for $videoId")
            cache.remove(videoId)
            return null
        }
        
        Log.d(TAG, "Cache hit for $videoId")
        return entry.url
    }

    fun getFull(videoId: String): CachedStream? {
        val entry = cache.get(videoId) ?: return null
        if (entry.isExpired) {
            cache.remove(videoId)
            return null
        }
        return entry
    }

    fun put(
        videoId: String,
        url: String,
        expiresInSeconds: Int? = null,
        bitrate: Int? = null,
        itag: Int? = null,
        mimeType: String? = null
    ) {
        val expirationMs = if (expiresInSeconds != null && expiresInSeconds > 0) {
            expiresInSeconds * 1000L
        } else {
            DEFAULT_EXPIRATION_MS
        }
        
        val createdAt = System.currentTimeMillis()
        val entry = CachedStream(
            url = url,
            videoId = videoId,
            createdAt = createdAt,
            expiresAt = createdAt + expirationMs,
            bitrate = bitrate,
            itag = itag,
            mimeType = mimeType
        )
        
        cache.put(videoId, entry)
        Log.d(TAG, "Cached $videoId (expires in ${expirationMs / 1000}s, bitrate=$bitrate)")
    }

    fun remove(videoId: String) {
        cache.remove(videoId)
    }

    fun clear() {
        cache.evictAll()
    }
    
    fun getStats(): String {
        return "Cache: size=${cache.size()}, hits=${cache.hitCount()}, misses=${cache.missCount()}, evictions=${cache.evictionCount()}"
    }
}
