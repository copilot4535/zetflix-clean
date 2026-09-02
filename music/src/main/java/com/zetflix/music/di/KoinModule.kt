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
import com.zetflix.music.viewmodels.*
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val musicModule = module {
    single<MusicDatabase> { InternalDatabase.newInstance(androidContext()) }
    single<DatabaseProvider> { StandaloneDatabaseProvider(androidContext()) }

    single(named("PlayerCache")) {
        val context = androidContext()
        val databaseProvider: DatabaseProvider = get()
        val constructor = {
            SimpleCache(
                context.filesDir.resolve("exoplayer"),
                when (val cacheSize = context.dataStore[MaxSongCacheSizeKey] ?: 1024) {
                    -1 -> NoOpCacheEvictor()
                    else -> LeastRecentlyUsedCacheEvictor(cacheSize * 1024 * 1024L)
                },
                databaseProvider
            )
        }
        constructor().release()
        constructor()
    }

    single(named("DownloadCache")) {
        val context = androidContext()
        val databaseProvider: DatabaseProvider = get()
        val constructor = {
            SimpleCache(context.filesDir.resolve("download"), NoOpCacheEvictor(), databaseProvider)
        }
        constructor().release()
        constructor()
    }

    single { LyricsHelper(get()) }
    single { DownloadUtil(androidContext(), get(), get(named("DownloadCache"))) }
    single { MediaLibrarySessionCallback(androidContext(), get(), get(), get(), get(named("PlayerCache")), get(named("DownloadCache"))) }

    viewModel { HomeViewModel(get()) }
    viewModel { AlbumViewModel(get(), get()) }
    viewModel { ArtistItemsViewModel(get()) }
    viewModel { ArtistViewModel(get()) }
    viewModel { BackupRestoreViewModel(get()) }
    viewModel { HistoryViewModel(get()) }
    viewModel { LibrarySongsViewModel(get()) }
    viewModel { LibraryArtistsViewModel(get()) }
    viewModel { LibraryAlbumsViewModel(get()) }
    viewModel { LibraryPlaylistsViewModel(get()) }
    viewModel { ArtistSongsViewModel(get()) }
    viewModel { LocalPlaylistViewModel(get()) }
    viewModel { LocalSearchViewModel(get()) }
    viewModel { LyricsMenuViewModel(get()) }
    viewModel { MoodAndGenresViewModel() }
    viewModel { NewReleaseViewModel(get()) }
    viewModel { OnlinePlaylistViewModel(get()) }
    viewModel { OnlineSearchSuggestionViewModel(get()) }
    viewModel { OnlineSearchViewModel(get()) }
    viewModel { StatsViewModel(get()) }
    viewModel { YouTubeBrowseViewModel(get()) }
}
