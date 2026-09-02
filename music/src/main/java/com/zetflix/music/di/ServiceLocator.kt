package com.zetflix.music.di

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.zetflix.music.constants.MaxSongCacheSizeKey
import com.zetflix.music.db.InternalDatabase
import com.zetflix.music.db.MusicDatabase
import com.zetflix.music.lyrics.LyricsHelper
import com.zetflix.music.playback.DownloadUtil
import com.zetflix.music.playback.MediaLibrarySessionCallback
import com.zetflix.music.utils.dataStore
import com.zetflix.music.utils.get

object ServiceLocator {
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    private val context: Context
        get() = appContext ?: error("ServiceLocator not initialized")

    val database: MusicDatabase by lazy {
        InternalDatabase.newInstance(context)
    }

    val databaseProvider: DatabaseProvider by lazy {
        StandaloneDatabaseProvider(context)
    }

    val playerCache: SimpleCache by lazy {
        val cacheSize = context.dataStore[MaxSongCacheSizeKey] ?: 1024
        val evictor = if (cacheSize == -1) NoOpCacheEvictor() else LeastRecentlyUsedCacheEvictor(cacheSize * 1024 * 1024L)
        SimpleCache(context.filesDir.resolve("exoplayer"), evictor, databaseProvider)
    }

    val downloadCache: SimpleCache by lazy {
        SimpleCache(context.filesDir.resolve("download"), NoOpCacheEvictor(), databaseProvider)
    }

    val downloadUtil: DownloadUtil by lazy {
        DownloadUtil(context, database, databaseProvider, downloadCache, playerCache)
    }

    val lyricsHelper: LyricsHelper by lazy {
        LyricsHelper(context)
    }

    val mediaLibrarySessionCallback: MediaLibrarySessionCallback by lazy {
        MediaLibrarySessionCallback(context, database, downloadUtil)
    }
}
