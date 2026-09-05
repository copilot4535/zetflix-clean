package com.lagradost.cloudstream3.services.music

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

@UnstableApi
data class MusicDownloadState(
    val videoId: String,
    val state: Int,
    val progress: Float
)

@UnstableApi
object MusicDownloadManager {
    private const val DOWNLOAD_CONTENT_DIRECTORY = "music_downloads"
    
    private var downloadManager: DownloadManager? = null
    private var downloadCache: Cache? = null
    private var databaseProvider: DatabaseProvider? = null

    private val _downloadStates = MutableStateFlow<Map<String, MusicDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, MusicDownloadState>> = _downloadStates.asStateFlow()

    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager? {
        if (downloadManager == null) {
            try {
                val dbProvider = getDatabaseProvider(context)
                val cache = getDownloadCache(context)
                downloadManager = DownloadManager(
                    context,
                    dbProvider,
                    cache,
                    getHttpDataSourceFactory(context),
                    { it.run() }
                ).apply {
                    maxParallelDownloads = 3
                    addListener(object : DownloadManager.Listener {
                        override fun onDownloadChanged(
                            downloadManager: DownloadManager,
                            download: Download,
                            finalException: Exception?
                        ) {
                            updateDownloadState(download)
                        }

                        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                            val current = _downloadStates.value.toMutableMap()
                            current.remove(download.request.id)
                            _downloadStates.value = current
                        }
                    })
                }
                downloadManager?.let { loadInitialStates(it) }
            } catch (e: Exception) {
                android.util.Log.e("MusicDownloadManager", "Failed to initialize DownloadManager", e)
                return null
            }
        }
        return downloadManager
    }

    private fun loadInitialStates(manager: DownloadManager) {
        val cursor = manager.downloadIndex.getDownloads()
        val states = mutableMapOf<String, MusicDownloadState>()
        while (cursor.moveToNext()) {
            val download = cursor.download
            states[download.request.id] = MusicDownloadState(
                download.request.id,
                download.state,
                download.percentDownloaded
            )
        }
        _downloadStates.value = states
    }

    private fun updateDownloadState(download: Download) {
        val current = _downloadStates.value.toMutableMap()
        current[download.request.id] = MusicDownloadState(
            download.request.id,
            download.state,
            download.percentDownloaded
        )
        _downloadStates.value = current
    }

    @Synchronized
    fun getDownloadCache(context: Context): Cache {
        val cache = downloadCache
        if (cache != null) return cache
        
        try {
            val downloadContentDirectory = File(context.getExternalFilesDir(null), DOWNLOAD_CONTENT_DIRECTORY)
            downloadCache = SimpleCache(downloadContentDirectory, NoOpCacheEvictor(), getDatabaseProvider(context))
        } catch (e: Exception) {
            android.util.Log.e("MusicDownloadManager", "Failed to create download cache", e)
            throw e
        }
        
        return downloadCache ?: throw IllegalStateException("Download cache is null after initialization")
    }

    @Synchronized
    private fun getDatabaseProvider(context: Context): DatabaseProvider {
        val provider = databaseProvider
        if (provider != null) return provider
        
        val newProvider = StandaloneDatabaseProvider(context)
        databaseProvider = newProvider
        return newProvider
    }

    fun getHttpDataSourceFactory(context: Context): DataSource.Factory {
        return DefaultHttpDataSource.Factory()
    }

    fun getReadOnlyDataSourceFactory(context: Context): DataSource.Factory {
        return try {
            val cache = getDownloadCache(context)
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(getHttpDataSourceFactory(context))
                .setCacheWriteDataSinkFactory(null) // Read-only
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } catch (e: Exception) {
            android.util.Log.e("MusicDownloadManager", "Error creating read-only source factory", e)
            getHttpDataSourceFactory(context)
        }
    }
}
