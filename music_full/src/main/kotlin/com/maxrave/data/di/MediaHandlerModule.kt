package com.maxrave.data.di

import com.maxrave.common.Config
import com.maxrave.data.mediaservice.createMediaServiceHandler
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.repository.AnalyticsRepository
import com.maxrave.domain.repository.LocalPlaylistRepository
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.repository.StreamRepository
import kotlinx.coroutines.CoroutineScope
import org.koin.core.qualifier.named
import org.koin.dsl.module

val mediaHandlerModule =
    module {
        single<MediaPlayerHandler>(createdAtStart = true) {
            createMediaServiceHandler(
                dataStoreManager = get<DataStoreManager>(),
                songRepository = get<SongRepository>(),
                streamRepository = get<StreamRepository>(),
                localPlaylistRepository = get<LocalPlaylistRepository>(),
                analyticsRepository = get<AnalyticsRepository>(),
                coroutineScope = get<CoroutineScope>(named(Config.SERVICE_SCOPE)),
            )
        }
    }