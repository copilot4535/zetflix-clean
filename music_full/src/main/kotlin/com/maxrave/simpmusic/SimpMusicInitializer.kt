package com.maxrave.simpmusic

import android.annotation.SuppressLint
import android.content.Context
import android.database.CursorWindow
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.Configuration
import androidx.work.WorkManager
import cat.ereza.customactivityoncrash.config.CaocConfig
import com.maxrave.data.di.loader.loadAllModules
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.simpmusic.di.viewModelModule
import com.maxrave.simpmusic.service.backup.AutoBackupScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import multiplatform.network.cmptoast.AppContext
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.simpmusic.crashlytics.configCrashlytics
import org.simpmusic.lastfm.configLastfm
import java.lang.reflect.Field

object SimpMusicInitializer {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init(context: Context) {
        val appContext = context.applicationContext
        
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        
        // Koin initialization
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidLogger(level = Level.DEBUG)
                androidContext(appContext)
                loadAllModules()
                loadKoinModules(viewModelModule)
            }
        }

        // Config Crashlytics and Lastfm
        configCrashlytics(appContext, BuildKonfig.sentryDsn)
        configLastfm(BuildKonfig.lastfmApiKey, BuildKonfig.lastfmSecret)

        // initialize WorkManager
        try {
            val workConfig = Configuration.Builder()
                .setMinimumLoggingLevel(Log.INFO)
                .build()
            WorkManager.initialize(appContext, workConfig)
        } catch (e: Exception) {
            // Already initialized or failed
        }

        // Initialize and start AutoBackupScheduler
        try {
            val dataStoreManager: DataStoreManager = GlobalContext.get().get()
            val autoBackupScheduler = AutoBackupScheduler(appContext, dataStoreManager)
            applicationScope.launch {
                autoBackupScheduler.observeAndSchedule()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        CaocConfig.Builder
            .create()
            .backgroundMode(CaocConfig.BACKGROUND_MODE_SILENT)
            .enabled(true)
            .showErrorDetails(true)
            .showRestartButton(true)
            .errorDrawable(R.mipmap.ic_launcher_round)
            .logErrorOnRestart(false)
            .trackActivities(true)
            .minTimeBetweenCrashesMs(2000)
            .restartActivity(SimpMusicActivity::class.java)
            .apply()

        @SuppressLint("DiscouragedPrivateApi")
        try {
            val field: Field = CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
            field.isAccessible = true
            val expectSize = 100 * 1024 * 1024
            field.set(null, expectSize)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        AppContext.apply {
            set(appContext)
        }
    }
}
