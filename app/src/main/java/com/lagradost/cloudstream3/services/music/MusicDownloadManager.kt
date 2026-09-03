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
import androidx.media3.exoplayer.offline.DownloadManager
import java.io.File

@UnstableApi
object MusicDownloadManager {
    private const val DOWNLOAD_CONTENT_DIRECTORY = "music_downloads"
    
    private var downloadManager: DownloadManager? = null
    private var downloadCache: Cache? = null
    private var databaseProvider: DatabaseProvider? = null

    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        if (downloadManager == null) {
            downloadManager = DownloadManager(
                context,
                getDatabaseProvider(context),
                getDownloadCache(context),
                getHttpDataSourceFactory(context),
                { it.run() }
            ).apply {
                maxParallelDownloads = 3
            }
        }
        return downloadManager!!
    }

    @Synchronized
    fun getDownloadCache(context: Context): Cache {
        if (downloadCache == null) {
            val downloadContentDirectory = File(context.getExternalFilesDir(null), DOWNLOAD_CONTENT_DIRECTORY)
            downloadCache = SimpleCache(downloadContentDirectory, NoOpCacheEvictor(), getDatabaseProvider(context))
        }
        return downloadCache!!
    }

    @Synchronized
    private fun getDatabaseProvider(context: Context): DatabaseProvider {
        if (databaseProvider == null) {
            databaseProvider = StandaloneDatabaseProvider(context)
        }
        return databaseProvider!!
    }

    fun getHttpDataSourceFactory(context: Context): DataSource.Factory {
        return DefaultHttpDataSource.Factory()
    }

    fun getReadOnlyDataSourceFactory(context: Context): DataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(getDownloadCache(context))
            .setUpstreamDataSourceFactory(getHttpDataSourceFactory(context))
            .setCacheWriteDataSinkFactory(null) // Read-only
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
