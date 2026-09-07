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
    private var isInitializing = false

    private val _downloadStates = MutableStateFlow<Map<String, MusicDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, MusicDownloadState>> = _downloadStates.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (downloadManager != null || isInitializing) return
        isInitializing = true
        
        val appContext = context.applicationContext
        kotlin.concurrent.thread(start = true, name = "MusicDownloadInit") {
            try {
                val dbProvider = getDatabaseProvider(appContext)
                val cache = getDownloadCacheInternal(appContext)
                
                synchronized(this) {
                    downloadManager = DownloadManager(
                        appContext,
                        dbProvider,
                        cache,
                        getHttpDataSourceFactory(appContext),
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
                    isInitializing = false
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicDownloadManager", "Failed to initialize DownloadManager", e)
                synchronized(this) {
                    isInitializing = false
                }
            }
        }
    }

    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager? {
        if (downloadManager == null && !isInitializing) {
            init(context)
        }
        return downloadManager
    }

    private fun loadInitialStates(manager: DownloadManager) {
        try {
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
        } catch (e: Exception) {
            android.util.Log.e("MusicDownloadManager", "Error loading initial states", e)
        }
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
    fun getDownloadCache(context: Context): Cache? {
        if (downloadCache == null && !isInitializing) {
            init(context)
        }
        return downloadCache
    }

    private fun getDownloadCacheInternal(context: Context): Cache {
        val cache = downloadCache
        if (cache != null) return cache
        
        try {
            val downloadContentDirectory = File(context.getExternalFilesDir(null), DOWNLOAD_CONTENT_DIRECTORY)
            val newCache = SimpleCache(downloadContentDirectory, NoOpCacheEvictor(), getDatabaseProvider(context))
            downloadCache = newCache
            return newCache
        } catch (e: Exception) {
            android.util.Log.e("MusicDownloadManager", "Failed to create download cache", e)
            throw e
        }
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
        val appContext = context.applicationContext
        return DataSource.Factory {
            val cache = getDownloadCache(appContext)
            if (cache != null) {
                CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(getHttpDataSourceFactory(appContext))
                    .setCacheWriteDataSinkFactory(null) // Read-only
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                    .createDataSource()
            } else {
                getHttpDataSourceFactory(appContext).createDataSource()
            }
        }
    }
}
