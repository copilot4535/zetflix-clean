package com.lagradost.cloudstream3

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.Session
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.APIHolder.initAll
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.removeKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity.loadThemes
import com.lagradost.cloudstream3.CommonActivity.onColorSelectedEvent
import com.lagradost.cloudstream3.CommonActivity.onDialogDismissedEvent
import com.lagradost.cloudstream3.CommonActivity.onUserLeaveHint
import com.lagradost.cloudstream3.CommonActivity.screenHeight
import com.lagradost.cloudstream3.CommonActivity.setActivityInstance
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.CommonActivity.updateLocale
import com.lagradost.cloudstream3.CommonActivity.updateTheme
import com.lagradost.cloudstream3.databinding.ActivityMainBinding
import com.lagradost.cloudstream3.databinding.BottomResultviewPreviewBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.mvvm.observeNullable
import com.lagradost.cloudstream3.network.initClient
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_loadAllOnlinePlugins
import com.lagradost.cloudstream3.plugins.PluginManager.loadSinglePlugin
import com.lagradost.cloudstream3.receivers.VideoDownloadRestartReceiver
import com.lagradost.cloudstream3.services.SubscriptionWorkManager
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.SyncWatchType
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.result.ResultViewModel2
import com.lagradost.cloudstream3.ui.result.SyncViewModel
import com.lagradost.cloudstream3.ui.search.SearchFragment
import com.lagradost.cloudstream3.ui.search.SearchResultBuilder
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.SettingsGeneral
import com.google.android.material.navigationrail.NavigationRailView
import com.google.android.material.snackbar.Snackbar
import com.lagradost.cloudstream3.utils.BackupUtils.backup
import com.lagradost.cloudstream3.utils.BackupUtils.setUpBackup
import com.lagradost.cloudstream3.utils.TvChannelUtils
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.lagradost.cloudstream3.ui.settings.Globals.isLandscape
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.ui.setup.HAS_DONE_SETUP_KEY
import com.lagradost.cloudstream3.ui.setup.SetupFragmentExtensions
import com.lagradost.cloudstream3.utils.ApkInstaller
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiDubstatusSettings
import com.lagradost.cloudstream3.utils.AppContextUtils.html
import com.lagradost.cloudstream3.utils.AppContextUtils.isCastApiAvailable
import com.lagradost.cloudstream3.utils.AppContextUtils.isNetworkAvailable
import com.lagradost.cloudstream3.utils.AppContextUtils.loadCache
import com.lagradost.cloudstream3.utils.AppContextUtils.loadSearchResult
import com.lagradost.cloudstream3.utils.AppContextUtils.updateHasTrailers
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.BiometricCallback
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.biometricPrompt
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.deviceHasPasswordPinLock
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.isAuthEnabled
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.promptInfo
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.startBiometricAuthentication
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.accounts
import com.lagradost.cloudstream3.utils.DataStoreHelper.migrateResumeWatching
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.InAppUpdater.runAutoUpdate
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SnackbarHelper.showSnackbar
import com.lagradost.cloudstream3.utils.UIHelper.changeStatusBarState
import com.lagradost.cloudstream3.utils.UIHelper.checkWrite
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.enableEdgeToEdgeCompat
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.requestRW
import com.lagradost.cloudstream3.utils.UIHelper.setNavigationBarColorCompat
import com.lagradost.cloudstream3.utils.UIHelper.showProgress
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import com.lagradost.cloudstream3.utils.USER_PROVIDER_API
import com.lagradost.cloudstream3.utils.USER_SELECTED_HOMEPAGE_API
import com.lagradost.cloudstream3.utils.downloader.DownloadQueueManager
import com.lagradost.cloudstream3.utils.setText
import com.lagradost.cloudstream3.utils.setTextHtml
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.safefile.SafeFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.charset.Charset
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.attachBackPressedCallback
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.detachBackPressedCallback
import com.lagradost.cloudstream3.utils.AvatarDrawableGenerator
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import kotlin.reflect.full.createInstance

class MainActivity : AppCompatActivity(), ColorPickerDialogListener, BiometricCallback {
    companion object {
        var activityResultLauncher: ActivityResultLauncher<Intent>? = null

        const val TAG = "MAINACT"
        const val ANIMATED_OUTLINE: Boolean = false
        var lastError: String? = null

        /** Update lastError variable based on error file, to check if app crashed.
         * Can be called multiple times without changing the lastError variable changing.
         **/
        fun setLastError(context: Context) {
            if (lastError != null) return

            val errorFile = context.filesDir.resolve("last_error")
            if (errorFile.exists() && errorFile.isFile) {
                lastError = errorFile.readText(Charset.defaultCharset())
                errorFile.delete()
            } else {
                lastError = null
            }
        }

        private const val FILE_DELETE_KEY = "FILES_TO_DELETE_KEY"
        const val API_NAME_EXTRA_KEY = "API_NAME_EXTRA_KEY"

        /**
         * Transient files to delete on application exit.
         * Deletes files on onDestroy().
         */
        private var filesToDelete: Set<String>
            // This needs to be persistent because the application may exit without calling onDestroy.
            get() = getKey<Set<String>>(FILE_DELETE_KEY) ?: setOf()
            private set(value) = setKey(FILE_DELETE_KEY, value)

        /**
         * Add file to delete on Exit.
         */
        fun deleteFileOnExit(file: File) {
            filesToDelete = filesToDelete + file.path
        }

        /**
         * Setting this will automatically enter the query in the search
         * next time the search fragment is opened.
         * This variable will clear itself after one use. Null does nothing.
         *
         * This is a very bad solution but I was unable to find a better one.
         **/
        var nextSearchQuery: String? = null

        /**
         * Fires every time a new batch of plugins have been loaded, no guarantee about how often this is run and on which thread
         * Boolean signifies if stuff should be force reloaded (true if force reload, false if reload when necessary).
         *
         * The force reloading are used for plugin development to instantly reload the page on deployWithAdb
         * */
        val afterPluginsLoadedEvent = Event<Boolean>()
        val mainPluginsLoadedEvent =
            Event<Boolean>() // homepage api, used to speed up time to load for homepage
        val afterRepositoryLoadedEvent = Event<Boolean>()

        // kinda shitty solution, but cant com main->home otherwise for popups
        val bookmarksUpdatedEvent = Event<Boolean>()

        /**
         * Used by DataStoreHelper to fully reload home when switching accounts
         */
        val reloadHomeEvent = Event<Boolean>()


        /**
         * Used by DataStoreHelper to fully reload Navigation Rail header picture
         */
        val reloadAccountEvent = Event<Boolean>()

        /**
         * @return true if the str has launched an app task (be it successful or not)
         * @param isWebview does not handle providers and opening download page if true. Can still add repos and login.
         * */
        fun handleAppIntentUrl(
            activity: FragmentActivity?,
            str: String?,
            isWebview: Boolean,
            extraArgs: Bundle? = null
        ): Boolean = false


        fun centerView(view: View?) {
            if (view == null) return
            try {
                Log.v(TAG, "centerView: $view")
                val r = Rect(0, 0, 0, 0)
                view.getDrawingRect(r)
                val x = r.centerX()
                val y = r.centerY()
                val dx = r.width() / 2 //screenWidth / 2
                val dy = screenHeight / 2
                val r2 = Rect(x - dx, y - dy, x + dx, y + dy)
                view.requestRectangleOnScreen(r2, false)
            } catch (_: Throwable) {
            }
        }
    }


    var lastPopup: SearchResponse? = null
    var lastPopupJob: Job? = null
    fun loadPopup(result: SearchResponse, load: Boolean = true) {
        lastPopup = result
        val syncName = syncViewModel.syncName(result.apiName)

        // based on apiName we decide on if it is a local list or not, this is because
        // we want to show a bit of extra UI to sync apis
        if (result is SyncAPI.LibraryItem && syncName != null) {
            isLocalList = false
            syncViewModel.setSync(syncName, result.syncId)
            syncViewModel.updateMetaAndUser()
        } else {
            isLocalList = true
            syncViewModel.clear()
        }

        lastPopupJob?.cancel()
        lastPopupJob = if (load) {
            viewModel.load(
                this, result.url, result.apiName, false, if (getApiDubstatusSettings()
                        .contains(DubStatus.Dubbed)
                ) DubStatus.Dubbed else DubStatus.Subbed, null
            )
        } else {
            viewModel.loadSmall(result)
        }
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        onColorSelectedEvent.invoke(Pair(dialogId, color))
    }

    override fun onDialogDismissed(dialogId: Int) {
        onDialogDismissedEvent.invoke(dialogId)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateLocale() // android fucks me by chaining lang when rotating the phone
        updateTheme(this) // Update if system theme

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navHostFragment.navController.currentDestination?.let { updateNavBar(it) }
    }

    private fun updateNavBar(destination: NavDestination) {
        this.hideKeyboard()

        // Fucks up anime info layout since that has its own layout
        binding?.castMiniControllerHolder?.isVisible =
            !listOf(
                R.id.navigation_results_phone,
                R.id.navigation_player
            ).contains(destination.id)

        val isNavVisible = listOf(
            R.id.navigation_home,
            R.id.navigation_search,
            R.id.navigation_library,
            R.id.navigation_downloads,
            R.id.navigation_settings,
            R.id.navigation_download_child,
            R.id.navigation_download_queue,
            R.id.navigation_subtitles,
            R.id.navigation_chrome_subtitles,
            R.id.navigation_settings_player,
            R.id.navigation_settings_updates,
            R.id.navigation_settings_ui,
            R.id.navigation_settings_account,
            R.id.navigation_settings_providers,
            R.id.navigation_settings_general,
            R.id.navigation_settings_extensions,
            R.id.navigation_settings_plugins,
            R.id.navigation_test_providers,
        ).contains(destination.id)

        binding?.apply {
            navRailView.isVisible = isNavVisible && isLandscape()
            navView.isVisible = isNavVisible && !isLandscape()

            /**
             * We need to make sure if we return to a sub-fragment,
             * the correct navigation item is selected so that it does not
             * highlight the wrong one in UI.
             */
            when (destination.id) {
                in listOf(
                    R.id.navigation_downloads,
                    R.id.navigation_download_child,
                    R.id.navigation_download_queue
                ) -> {
                    navRailView.menu.findItem(R.id.navigation_downloads).isChecked = true
                    navView.menu.findItem(R.id.navigation_downloads).isChecked = true
                }

                in listOf(
                    R.id.navigation_settings,
                    R.id.navigation_subtitles,
                    R.id.navigation_chrome_subtitles,
                    R.id.navigation_settings_player,
                    R.id.navigation_settings_updates,
                    R.id.navigation_settings_ui,
                    R.id.navigation_settings_account,
                    R.id.navigation_settings_providers,
                    R.id.navigation_settings_general,
                    R.id.navigation_settings_extensions,
                    R.id.navigation_settings_plugins,
                    R.id.navigation_test_providers
                ) -> {
                    navRailView.menu.findItem(R.id.navigation_settings).isChecked = true
                    navView.menu.findItem(R.id.navigation_settings).isChecked = true
                }
            }
        }
    }

    //private var mCastSession: CastSession? = null
    var mSessionManager: SessionManager? = null
    private val mSessionManagerListener: SessionManagerListener<Session> by lazy { SessionManagerListenerImpl() }

    private inner class SessionManagerListenerImpl : SessionManagerListener<Session> {
        override fun onSessionStarting(session: Session) {
        }

        override fun onSessionStarted(session: Session, sessionId: String) {
            invalidateOptionsMenu()
        }

        override fun onSessionStartFailed(session: Session, i: Int) {
        }

        override fun onSessionEnding(session: Session) {
        }

        override fun onSessionResumed(session: Session, wasSuspended: Boolean) {
            invalidateOptionsMenu()
        }

        override fun onSessionResumeFailed(session: Session, i: Int) {
        }

        override fun onSessionSuspended(session: Session, i: Int) {
        }

        override fun onSessionEnded(session: Session, error: Int) {
        }

        override fun onSessionResuming(session: Session, s: String) {
        }
    }

    override fun onResume() {
        super.onResume()
        afterPluginsLoadedEvent += ::onAllPluginsLoaded
        setActivityInstance(this)
        updateAvatarInNavigation()
        try {
            if (isCastApiAvailable()) {
                mSessionManager?.addSessionManagerListener(mSessionManagerListener)
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    override fun onPause() {
        super.onPause()

        // Start any delayed updates
        if (ApkInstaller.delayedInstaller?.startInstallation() == true) {
            Toast.makeText(this, R.string.update_started, Toast.LENGTH_LONG).show()
        }
        try {
            if (isCastApiAvailable()) {
                mSessionManager?.removeSessionManagerListener(mSessionManagerListener)
                //mCastSession = null
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        CommonActivity.dispatchKeyEvent(this, event) ?: super.dispatchKeyEvent(event)

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        CommonActivity.onKeyDown(this, keyCode, event) ?: super.onKeyDown(keyCode, event)


    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        onUserLeaveHint(this)
    }

    @SuppressLint("ApplySharedPref") // commit since the op needs to be synchronous
    private fun showConfirmExitDialog(settingsManager: SharedPreferences) {
        val confirmBeforeExit = settingsManager.getInt(getString(R.string.confirm_exit_key), -1)

        if (confirmBeforeExit == 1 || (confirmBeforeExit == -1)) {
            finish()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.confirm_exit_dialog, null)
        val dontShowAgainCheck: CheckBox = dialogView.findViewById(R.id.checkboxDontShowAgain)
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
            .setTitle(R.string.confirm_exit_dialog)
            .setNegativeButton(R.string.no) { _, _ -> /*NO-OP*/ }
            .setPositiveButton(R.string.yes) { _, _ ->
                if (dontShowAgainCheck.isChecked) {
                    settingsManager.edit(commit = true) {
                        putInt(getString(R.string.confirm_exit_key), 1)
                    }
                }
                finish()
            }

        builder.show()
    }

    override fun onDestroy() {
        filesToDelete.forEach { path ->
            val result = File(path).deleteRecursively()
            if (result) {
                Log.d(TAG, "Deleted temporary file: $path")
            } else {
                Log.d(TAG, "Failed to delete temporary file: $path")
            }
        }
        filesToDelete = setOf()
        val broadcastIntent = Intent()
        broadcastIntent.action = "restart_service"
        broadcastIntent.setClass(this, VideoDownloadRestartReceiver::class.java)
        this.sendBroadcast(broadcastIntent)
        afterPluginsLoadedEvent -= ::onAllPluginsLoaded
        detachBackPressedCallback("MainActivityDefault")
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        handleAppIntent(intent)
        super.onNewIntent(intent)
    }

    private fun handleAppIntent(intent: Intent?) {
        if (intent == null) return
        val str = intent.dataString
        loadCache()

        handleAppIntentUrl(this, str, false, intent.extras)
    }

    private fun NavDestination.matchDestination(@IdRes destId: Int): Boolean =
        hierarchy.any { it.id == destId }

    private var lastNavTime = 0L
    private fun onNavDestinationSelected(item: MenuItem, navController: NavController): Boolean {
        val currentTime = System.currentTimeMillis()
        // safeDebounce: Check if a previous tap happened within the last 400ms
        if (currentTime - lastNavTime < 400) return false
        lastNavTime = currentTime

        val destinationId = item.itemId

        // Check if we are already at the selected destination
        if (navController.currentDestination?.id == destinationId) return false

        val builder = NavOptions.Builder().setLaunchSingleTop(true).setRestoreState(true)
            .setEnterAnim(R.anim.enter_anim)
            .setExitAnim(R.anim.exit_anim)
            .setPopEnterAnim(R.anim.pop_enter)
            .setPopExitAnim(R.anim.pop_exit)
        if (item.order and Menu.CATEGORY_SECONDARY == 0) {
            builder.setPopUpTo(
                navController.graph.findStartDestination().id,
                inclusive = false,
                saveState = true
            )
        }
        return try {
            navController.navigate(destinationId, null, builder.build())
            navController.currentDestination?.matchDestination(destinationId) == true
        } catch (e: IllegalArgumentException) {
            Log.e("NavigationError", "Failed to navigate: ${e.message}")
            false
        }
    }

    private val pluginsLock = Mutex()
    private fun onAllPluginsLoaded(success: Boolean = false) {
        ioSafe {
            pluginsLock.withLock {
                allProviders.withLock {
                    // Load cloned sites after plugins have been loaded since clones depend on plugins.
                    try {
                        getKey<Array<SettingsGeneral.CustomSite>>(USER_PROVIDER_API)?.let { list ->
                            list.forEach { custom ->
                                allProviders.firstOrNull {
                                    it::class.simpleName == custom.parentClassName
                                }?.let {
                                    allProviders.add(
                                        it::class.createInstance().apply {
                                            name = custom.name
                                            lang = custom.lang
                                            mainUrl = custom.url.trimEnd('/')
                                            canBeOverridden = false
                                        }
                                    )
                                }
                            }
                        }
                        // it.hashCode() is not enough to make sure they are distinct
                        apis = allProviders.distinctBy {
                            it.lang + it.name + it.mainUrl + it::class.qualifiedName
                        }
                        APIHolder.apiMap = null
                    } catch (e: Exception) {
                        logError(e)
                    }
                }
            }
        }
    }

    lateinit var viewModel: ResultViewModel2
    lateinit var syncViewModel: SyncViewModel

    /** kinda dirty, however it signals that we should use the watch status as sync or not*/
    var isLocalList: Boolean = false
    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {

        viewModel = ViewModelProvider(this)[ResultViewModel2::class.java]
        syncViewModel = ViewModelProvider(this)[SyncViewModel::class.java]

        return super.onCreateView(name, context, attrs)
    }

    private fun hidePreviewPopupDialog() {
        bottomPreviewPopup.dismissSafe(this)
        lastPopupJob?.cancel()
        lastPopupJob = null
        bottomPreviewPopup = null
        bottomPreviewBinding = null
    }

    private var bottomPreviewPopup: Dialog? = null
    private var bottomPreviewBinding: BottomResultviewPreviewBinding? = null
    private fun showPreviewPopupDialog(): BottomResultviewPreviewBinding {
        val ret = (bottomPreviewBinding ?: run {

            val builder: Dialog
            val layout: Int

            builder = BottomSheetDialog(this)
            layout = R.layout.bottom_resultview_preview

            val root = layoutInflater.inflate(layout, null, false)
            val binding = BottomResultviewPreviewBinding.bind(root)

            bottomPreviewBinding = binding
            builder.setContentView(root)
            builder.setOnDismissListener {
                bottomPreviewPopup = null
                bottomPreviewBinding = null
                viewModel.clear()
            }
            builder.setCanceledOnTouchOutside(true)
            builder.show()
            bottomPreviewPopup = builder
            binding
        })

        return ret
    }

    var binding: ActivityMainBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        app.initClient(this, ignoreSSL = false)
        @OptIn(UnsafeSSL::class)
        insecureApp.initClient(this, ignoreSSL = true)

        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

        setLastError(this)

        val settingsForProvider = SettingsJson()
        settingsForProvider.enableAdult =
            settingsManager.getBoolean(getString(R.string.enable_nsfw_on_providers_key), false)

        MainAPI.settingsForProvider = settingsForProvider

        loadThemes(this)
        enableEdgeToEdgeCompat()
        setNavigationBarColorCompat(R.attr.primaryGrayBackground)
        updateLocale()
        super.onCreate(savedInstanceState)
        try {
            if (isCastApiAvailable()) {
                CastContext.getSharedInstance(this) { it.run() }
                    .addOnSuccessListener { mSessionManager = it.sessionManager }
            }
        } catch (t: Throwable) {
            logError(t)
        }

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)

        // backup when we update the app, I don't trust myself to not boot lock users, might want to make this a setting?
        safe {
            val appVer = BuildConfig.VERSION_NAME
            val lastAppAutoBackup: String = getKey<String>("VERSION_NAME") ?: ""
            if (appVer != lastAppAutoBackup) {
                setKey("VERSION_NAME", BuildConfig.VERSION_NAME)
                if (lastAppAutoBackup.isEmpty()) return@safe

                safe {
                    backup(this)
                }
                safe {
                    // Recompile oat on new version
                    PluginManager.deleteAllOatFiles(this)
                }
            }
        }

        // just in case, MAIN SHOULD *NEVER* BOOT LOOP CRASH
        binding = try {
            val newLocalBinding = ActivityMainBinding.inflate(layoutInflater, null, false)
            setContentView(newLocalBinding.root)
            newLocalBinding
        } catch (t: Throwable) {
            showToast(txt(R.string.unable_to_inflate, t.message ?: ""), Toast.LENGTH_LONG)
            null
        }

        binding?.apply {
            fixSystemBarsPadding(
                navView,
                heightResId = R.dimen.nav_view_height,
                padTop = false,
                overlayCutout = false
            )

            fixSystemBarsPadding(
                navRailView,
                widthResId = R.dimen.nav_rail_view_width,
                padRight = false,
                padTop = false
            )
        }

        // overscan
        val padding = settingsManager.getInt(getString(R.string.overscan_key), 0).toPx
        binding?.homeRoot?.setPadding(padding, padding, padding, padding)

        changeStatusBarState(false)

        /** Biometric stuff for users without accounts **/
        val noAccounts = settingsManager.getBoolean(
            getString(R.string.skip_startup_account_select_key),
            false
        ) || accounts.count() <= 1

        if (isAuthEnabled(this) && noAccounts) {
            if (deviceHasPasswordPinLock(this)) {
                startBiometricAuthentication(this, R.string.biometric_authentication_title, false)

                promptInfo?.let { prompt ->
                    biometricPrompt?.authenticate(prompt)
                }

                // hide background while authenticating, Sorry moms & dads 🙏
                binding?.navHostFragment?.isInvisible = true
            }
        }

        // Automatically enable jsdelivr if cant connect to raw.githubusercontent.com
        if (this.getKey<Boolean>(getString(R.string.jsdelivr_proxy_key)) == null && isNetworkAvailable()) {
            main {
                if (checkGithubConnectivity()) {
                    this.setKey(getString(R.string.jsdelivr_proxy_key), false)
                } else {
                    this.setKey(getString(R.string.jsdelivr_proxy_key), true)
                    showSnackbar(
                        this@MainActivity,
                        R.string.jsdelivr_enabled,
                        Snackbar.LENGTH_LONG,
                        R.string.revert
                    ) { setKey(getString(R.string.jsdelivr_proxy_key), false) }
                }
            }
        }

        ioSafe { SafeFile.check(this@MainActivity) }

        if (PluginManager.checkSafeModeFile()) {
            safe {
                showToast(R.string.safe_mode_file, Toast.LENGTH_LONG)
            }
        } else if (lastError == null) {
            ioSafe {
                DataStoreHelper.currentHomePage?.let { homeApi ->
                    mainPluginsLoadedEvent.invoke(loadSinglePlugin(this@MainActivity, homeApi))
                } ?: run {
                    mainPluginsLoadedEvent.invoke(false)
                }

                ioSafe {
                    if (settingsManager.getBoolean(
                            getString(R.string.auto_update_plugins_key),
                            true
                        )
                    ) {
                        PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_updateAllOnlinePluginsAndLoadThem(
                            this@MainActivity
                        )
                    } else {
                        ___DO_NOT_CALL_FROM_A_PLUGIN_loadAllOnlinePlugins(this@MainActivity)
                    }

                    //Automatically download not existing plugins, using mode specified.
                    val autoDownloadPlugin = AutoDownloadMode.getEnum(
                        settingsManager.getInt(
                            getString(R.string.auto_download_plugins_key),
                            2
                        )
                    ) ?: AutoDownloadMode.Disable
                    if (autoDownloadPlugin != AutoDownloadMode.Disable) {
                        PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_downloadNotExistingPluginsAndLoad(
                            this@MainActivity,
                            autoDownloadPlugin
                        )
                    }
                }

                ioSafe {
                    PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_loadAllLocalPlugins(
                        this@MainActivity,
                        false
                    )
                }
            }
        } else {
            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            builder.setTitle(R.string.safe_mode_title)
            builder.setMessage(R.string.safe_mode_description)
            builder.apply {
                setPositiveButton(R.string.safe_mode_crash_info) { _, _ ->
                    val tbBuilder: AlertDialog.Builder = AlertDialog.Builder(context)
                    tbBuilder.setTitle(R.string.safe_mode_title)
                    tbBuilder.setMessage(lastError)
                    tbBuilder.show()
                }

                setNegativeButton("Ok") { _, _ -> }
            }
            builder.show()
        }


        fun setUserData(status: Resource<SyncAPI.AbstractSyncStatus>?) {
            if (isLocalList) return
            bottomPreviewBinding?.apply {
                when (status) {
                    is Resource.Success -> {
                        resultviewPreviewBookmark.isEnabled = true
                        resultviewPreviewBookmark.setText(status.value.status.stringRes)
                        resultviewPreviewBookmark.setIconResource(status.value.status.iconRes)
                    }

                    is Resource.Failure -> {
                        resultviewPreviewBookmark.isEnabled = false
                        resultviewPreviewBookmark.setIconResource(R.drawable.ic_baseline_bookmark_border_24)
                        resultviewPreviewBookmark.text = status.errorString
                    }

                    else -> {
                        resultviewPreviewBookmark.isEnabled = false
                        resultviewPreviewBookmark.showProgress()
                    }
                }
            }
        }

        fun setWatchStatus(state: WatchType?) {
            if (!isLocalList || state == null) return

            bottomPreviewBinding?.resultviewPreviewBookmark?.apply {
                setIconResource(state.iconRes)
                setText(state.stringRes)
            }
        }

        fun setSubscribeStatus(state: Boolean?) {
            bottomPreviewBinding?.resultviewPreviewSubscribe?.apply {
                if (state != null) {
                    val drawable = if (state) {
                        R.drawable.ic_baseline_notifications_active_24
                    } else {
                        R.drawable.baseline_notifications_none_24
                    }
                    setImageResource(drawable)
                }
                isVisible = state != null

                setOnClickListener {
                    viewModel.toggleSubscriptionStatus(context) { newStatus: Boolean? ->
                        if (newStatus == null) return@toggleSubscriptionStatus

                        val message = if (newStatus) {
                            // Kinda icky to have this here, but it works.
                            SubscriptionWorkManager.enqueuePeriodicWork(context)
                            R.string.subscription_new
                        } else {
                            R.string.subscription_deleted
                        }

                        val name = (viewModel.page.value as? Resource.Success)?.value?.title
                            ?: txt(R.string.no_data).asStringNull(context) ?: ""
                        showToast(txt(message, name), Toast.LENGTH_SHORT)
                    }
                }
            }
        }

        observe(viewModel.watchStatus, ::setWatchStatus)
        observe(syncViewModel.userData, ::setUserData)
        observeNullable(viewModel.subscribeStatus, ::setSubscribeStatus)

        observeNullable(viewModel.page) { resource ->
            if (resource == null) {
                hidePreviewPopupDialog()
                return@observeNullable
            }
            when (resource) {
                is Resource.Failure -> {
                    showToast(R.string.error)
                    viewModel.clear()
                    hidePreviewPopupDialog()
                }

                is Resource.Loading -> {
                    showPreviewPopupDialog().apply {
                        resultviewPreviewLoading.isVisible = true
                        resultviewPreviewResult.isVisible = false
                        resultviewPreviewLoadingShimmer.startShimmer()
                    }
                }

                is Resource.Success -> {
                    val d = resource.value
                    showPreviewPopupDialog().apply {
                        resultviewPreviewLoading.isVisible = false
                        resultviewPreviewResult.isVisible = true
                        resultviewPreviewLoadingShimmer.stopShimmer()

                        resultviewPreviewTitle.text = d.title

                        resultviewPreviewMetaType.setText(d.typeText)
                        resultviewPreviewMetaYear.setText(d.yearText)
                        resultviewPreviewMetaDuration.setText(d.durationText)
                        resultviewPreviewMetaRating.setText(d.ratingText)

                        resultviewPreviewDescription.setTextHtml(d.plotText)
                        resultviewPreviewPoster.loadImage(
                            d.posterImage ?: d.posterBackgroundImage,
                            headers = d.posterHeaders
                        )

                        setUserData(syncViewModel.userData.value)
                        setWatchStatus(viewModel.watchStatus.value)
                        setSubscribeStatus(viewModel.subscribeStatus.value)

                        resultviewPreviewBookmark.setOnClickListener {
                            if (isLocalList) {
                                val value = viewModel.watchStatus.value ?: WatchType.NONE

                                this@MainActivity.showBottomDialog(
                                    WatchType.entries.map { getString(it.stringRes) }.toList(),
                                    value.ordinal,
                                    this@MainActivity.getString(R.string.action_add_to_bookmarks),
                                    showApply = false,
                                    {}) {
                                    viewModel.updateWatchStatus(
                                        WatchType.entries[it],
                                        this@MainActivity
                                    )
                                }
                            } else {
                                val value =
                                    (syncViewModel.userData.value as? Resource.Success)?.value?.status
                                        ?: SyncWatchType.NONE

                                this@MainActivity.showBottomDialog(
                                    SyncWatchType.entries.map { getString(it.stringRes) }.toList(),
                                    value.ordinal,
                                    this@MainActivity.getString(R.string.action_add_to_bookmarks),
                                    showApply = false,
                                    {}) {
                                    syncViewModel.setStatus(SyncWatchType.entries[it].internalId)
                                    syncViewModel.publishUserData()
                                }
                            }
                        }

                        observeNullable(viewModel.favoriteStatus) observeFavoriteStatus@{ isFavorite ->
                            resultviewPreviewFavorite.isVisible = isFavorite != null
                            if (isFavorite == null) return@observeFavoriteStatus

                            val drawable = if (isFavorite) {
                                R.drawable.ic_baseline_favorite_24
                            } else {
                                R.drawable.ic_baseline_favorite_border_24
                            }

                            resultviewPreviewFavorite.setImageResource(drawable)
                        }

                        resultviewPreviewFavorite.setOnClickListener {
                            viewModel.toggleFavoriteStatus(this@MainActivity) { newStatus: Boolean? ->
                                if (newStatus == null) return@toggleFavoriteStatus

                                val message = if (newStatus) {
                                    R.string.favorite_added
                                } else {
                                    R.string.favorite_removed
                                }

                                val name = (viewModel.page.value as? Resource.Success)?.value?.title
                                    ?: txt(R.string.no_data).asStringNull(this@MainActivity) ?: ""
                                showToast(txt(message, name), Toast.LENGTH_SHORT)
                            }
                        }

                        resultviewPreviewDescription.setOnClickListener { view ->
                            view.context?.let { ctx ->
                                val builder: AlertDialog.Builder =
                                    AlertDialog.Builder(ctx, R.style.AlertDialogCustom)
                                builder.setMessage(d.plotText.asString(ctx).html())
                                    .setTitle(d.plotHeaderText.asString(ctx))
                                    .show()
                            }
                        }

                        resultviewPreviewMoreInfo.setOnClickListener {
                            viewModel.clear()
                            hidePreviewPopupDialog()
                            lastPopup?.let {
                                loadSearchResult(it)
                            }
                        }
                    }
                }
            }
        }

        SearchResultBuilder.updateCache(this)

        ioSafe {
            initAll()
            apis = allProviders.distinctBy { it }
        }

        setUpBackup()

        CommonActivity.init(this)
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        navController.addOnDestinationChangedListener { _: NavController, navDestination: NavDestination, bundle: Bundle? ->
            updateNavBar(navDestination)
            if (navDestination.matchDestination(R.id.navigation_search) && !nextSearchQuery.isNullOrBlank()) {
                bundle?.apply {
                    this.putString(SearchFragment.SEARCH_QUERY, nextSearchQuery)
                }
            }

            if (navDestination.matchDestination(R.id.navigation_home)) {
                attachBackPressedCallback("MainActivity") {
                    showConfirmExitDialog(settingsManager)
                }
            } else detachBackPressedCallback("MainActivity")
        }

        binding?.navView?.apply {
            itemRippleColor = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            itemActiveIndicatorColor = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            setupWithNavController(navController)
            setOnItemSelectedListener { item ->
                onNavDestinationSelected(
                    item,
                    navController
                )
            }

        }

        binding?.navRailView?.apply {
            itemRippleColor = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            itemActiveIndicatorColor = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)

            itemSpacing = 12.toPx
            setupWithNavController(navController)

            setOnItemSelectedListener { item ->
                onNavDestinationSelected(
                    item,
                    navController
                )
            }
        }

        val rail = binding?.navRailView
        if (rail != null) {
            binding?.navRailView?.labelVisibilityMode =
                NavigationRailView.LABEL_VISIBILITY_UNLABELED
        }

        for (view in listOf(binding?.navView, binding?.navRailView)) {
            view?.findViewById<View?>(R.id.navigation_home)?.setOnLongClickListener {
                val recycler = binding?.root?.findViewById<RecyclerView?>(R.id.home_master_recycler)
                recycler?.smoothScrollToPosition(0)
                return@setOnLongClickListener recycler != null
            }


            view?.findViewById<View?>(R.id.navigation_search)?.setOnLongClickListener {
                for (recyclerId in arrayOf(
                    R.id.search_master_recycler,
                    R.id.search_autofit_results,
                    R.id.search_history_recycler
                )) {
                    val recycler = binding?.root?.findViewById<RecyclerView?>(recyclerId)
                        ?: return@setOnLongClickListener false
                    recycler.smoothScrollToPosition(0)
                }
                return@setOnLongClickListener true
            }

            view?.findViewById<View?>(R.id.navigation_downloads)?.setOnLongClickListener {
                val recycler: RecyclerView? = binding?.root?.findViewById(R.id.download_list)
                    ?: binding?.root?.findViewById(R.id.download_child_list)
                recycler?.smoothScrollToPosition(0)
                return@setOnLongClickListener recycler != null
            }
        }

        loadCache()
        updateHasTrailers()

        if (!checkWrite()) {
            requestRW()
            if (checkWrite()) return
        }

        if (BuildConfig.DEBUG) {
            var providersAndroidManifestString = "Current androidmanifest should be:\n"
            allProviders.withLock {
                for (api in allProviders) {
                    providersAndroidManifestString += "<data android:scheme=\"https\" android:host=\"${
                        api.mainUrl.removePrefix(
                            "https://"
                        )
                    }\" android:pathPrefix=\"/\"/>\n"
                }
            }
            println(providersAndroidManifestString)
        }

        handleAppIntent(intent)

        ioSafe {
            runAutoUpdate()
        }

        APIRepository.dubStatusActive = getApiDubstatusSettings()

        try {
            loadCache()
            File(filesDir, "exoplayer").deleteRecursively() // old cache
            deleteFileOnExit(File(cacheDir, "exoplayer"))   // current cache
        } catch (e: Exception) {
            logError(e)
        }
        println("Loaded everything")

        ioSafe {
            migrateResumeWatching()
        }

        getKey<String>(USER_SELECTED_HOMEPAGE_API)?.let { homepage ->
            DataStoreHelper.currentHomePage = homepage
            removeKey(USER_SELECTED_HOMEPAGE_API)
        }

        try {
            if (getKey<Boolean>(HAS_DONE_SETUP_KEY, false) != true) {
                if (PluginManager.getPluginsOnline().isEmpty()
                    && PluginManager.getPluginsLocal().isEmpty()
                ) {
                    navController.navigate(
                        R.id.navigation_setup_extensions,
                        SetupFragmentExtensions.newInstance(false)
                    )
                } else {
                    navController.navigate(R.id.navigation_setup_language)
                }
            }
        } catch (e: Exception) {
            logError(e)
        }

        attachBackPressedCallback("MainActivityDefault") {
            setNavigationBarColorCompat(R.attr.primaryGrayBackground)
            updateLocale()
            runDefault()
        }

        DownloadQueueManager.init(this)
        updateAvatarInNavigation()
    }

    private fun loadEmailFromEncryptedPrefs(): String? {
        return try {
            val mainKey = MasterKey.Builder(this)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val sharedPreferences = EncryptedSharedPreferences.create(
                this,
                "zetflix_secure_prefs",
                mainKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            sharedPreferences.getString("email", null)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun updateAvatarInNavigation() {
        val email = loadEmailFromEncryptedPrefs() ?: return
        val drawable = AvatarDrawableGenerator.generateMonogramDrawable(this, email)

        binding?.navView?.menu?.findItem(R.id.navigation_settings)?.icon = drawable
        binding?.navRailView?.menu?.findItem(R.id.navigation_settings)?.icon = drawable
    }

    /** Biometric stuff **/
    override fun onAuthenticationSuccess() {
        binding?.navHostFragment?.isInvisible = false
    }

    override fun onAuthenticationError() {
        finish()
    }

    suspend fun checkGithubConnectivity(): Boolean {
        return try {
            app.get(
                "https://raw.githubusercontent.com/recloudstream/.github/master/connectivitycheck",
                timeout = 5
            ).text.trim() == "ok"
        } catch (t: Throwable) {
            false
        }
    }
}
