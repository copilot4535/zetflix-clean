package com.lagradost.cloudstream3.ui.music

import android.util.Log
import com.lagradost.cloudstream3.DownloaderTestImpl
import com.lagradost.cloudstream3.services.music.MusicDownloadManager
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

object StreamResolver {
    private const val TAG = "StreamResolver"
    private val mutex = Mutex()
    private val inFlightRequests = mutableMapOf<String, Deferred<String?>>()

    private fun initNewPipe() {
        try {
            NewPipe.getDownloader()
        } catch (e: Exception) {
            DownloaderTestImpl.getInstance()?.let {
                NewPipe.init(it)
            }
        }
    }

    @androidx.media3.common.util.UnstableApi
    suspend fun resolveStreamUrl(videoId: String, params: String? = null, forceRefresh: Boolean = false): String? {
        val startTime = System.currentTimeMillis()
        if (!forceRefresh) {
            // 0. Check cache
            StreamUrlCache.get(videoId)?.let { 
                Log.d(TAG, "Cache hit for $videoId in ${System.currentTimeMillis() - startTime}ms")
                return it 
            }

            // 0.1 Check if already downloaded
            com.lagradost.cloudstream3.CloudStreamApp.context?.let { ctx ->
                val downloadManager = MusicDownloadManager.getDownloadManager(ctx)
                val download = downloadManager?.downloadIndex?.getDownload(videoId)
                if (download != null && download.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED) {
                    val uri = download.request.uri.toString()
                    Log.d(TAG, "Using downloaded file for $videoId in ${System.currentTimeMillis() - startTime}ms")
                    return uri
                }
            }
        }

        // 1. Deduplicate in-flight requests
        val deferred = mutex.withLock {
            inFlightRequests[videoId]?.let { return@withLock it }
            
            val newDeferred = coroutineScope {
                async {
                    try {
                        performExtraction(videoId, params)
                    } finally {
                        mutex.withLock {
                            inFlightRequests.remove(videoId)
                        }
                    }
                }
            }
            inFlightRequests[videoId] = newDeferred
            newDeferred
        }

        return deferred.await()
    }

    private suspend fun performExtraction(videoId: String, params: String? = null): String? {
        Log.d(TAG, "Starting extraction for $videoId")
        val startTime = System.currentTimeMillis()

        // 1. Try InnerTube
        try {
            val playerResult = YouTubeInstance.youtube.player(videoId, params, false).getOrNull()
            val formats = playerResult?.second?.streamingData?.adaptiveFormats
            val audioFormats = formats?.filter { it.isAudio } ?: emptyList()
            
            // Log available formats for Phase 8 diagnostics
            audioFormats.forEach { format ->
                Log.d(TAG, "InnerTube Format: videoId=$videoId, itag=${format.itag}, bitrate=${format.bitrate}, mime=${format.mimeType}")
            }

            val url = audioFormats.maxByOrNull { it.bitrate }?.url
            if (!url.isNullOrBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
                Log.d(TAG, "InnerTube success for $videoId in ${System.currentTimeMillis() - startTime}ms")
                StreamUrlCache.put(videoId, url)
                return url
            }
        } catch (e: Exception) {
            Log.e(TAG, "InnerTube extraction failed for $videoId", e)
        }

        // 2. Fallback to NewPipe
        try {
            initNewPipe()
            val service = ServiceList.YouTube
            val info = StreamInfo.getInfo(service, videoId)
            val url = info.audioStreams.firstOrNull()?.content
            if (!url.isNullOrBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
                Log.d(TAG, "NewPipe fallback success for $videoId in ${System.currentTimeMillis() - startTime}ms")
                StreamUrlCache.put(videoId, url)
                return url
            }
        } catch (e: Exception) {
            Log.e(TAG, "NewPipe fallback failed for $videoId", e)
        }

        Log.e(TAG, "Total extraction failure for $videoId")
        return null
    }
}
