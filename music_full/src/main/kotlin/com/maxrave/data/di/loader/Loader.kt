package com.maxrave.data.di.loader

import com.maxrave.data.di.databaseModule
import com.maxrave.data.di.listenTogetherModule
import com.maxrave.data.di.mediaHandlerModule
import com.maxrave.data.di.repositoryModule
import com.maxrave.media3.di.loadMediaService as loadMediaServiceNative
import org.koin.core.context.loadKoinModules

fun loadAllModules() {
    loadKoinModules(
        listOf(
            databaseModule,
            repositoryModule,
        ),
    )
    loadKoinModules(mediaHandlerModule)
    // NOTE: `createdAtStart` is NOT enough for a module loaded this way — nothing constructs it
    // unless something injects it, and the bridge exists purely to listen, so nobody would.
    // ListenTogetherViewModel injects and starts it; start() is idempotent.
    loadKoinModules(listenTogetherModule)
    loadMediaService()
}

fun loadMediaService() {
    loadMediaServiceNative()
}
