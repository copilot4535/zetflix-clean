package com.lagradost.cloudstream3.utils

import android.animation.ObjectAnimator
import android.app.Activity
import android.app.Activity.RESULT_CANCELED
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Spanned
import android.util.Log
import android.view.View
import android.view.View.LAYOUT_DIRECTION_LTR
import android.view.View.LAYOUT_DIRECTION_RTL
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.core.text.toSpanned
import androidx.core.widget.ContentLoadingProgressBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.wrappers.Wrappers
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.AllLanguagesName
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getActivity
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainActivity.Companion.afterRepositoryLoadedEvent
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.ui.WebviewFragment
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.result.ResultFragment
import com.lagradost.cloudstream3.ui.settings.extensions.PluginsFragment
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.FillerEpisodeCheck.toClassDir
import com.lagradost.cloudstream3.utils.JsUnpacker.Companion.load
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import okhttp3.Cache
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

object AppContextUtils {
    fun RecyclerView.isRecyclerScrollable(): Boolean {
        val layoutManager =
            this.layoutManager as? LinearLayoutManager?
        val adapter = adapter
        return if (layoutManager == null || adapter == null) false else layoutManager.findLastCompletelyVisibleItemPosition() < adapter.itemCount - 7
    }

    fun View.isLtr() = this.layoutDirection == LAYOUT_DIRECTION_LTR
    fun View.isRtl() = this.layoutDirection == LAYOUT_DIRECTION_RTL

    fun BottomSheetDialog?.ownHide() {
        this?.hide()
    }

    fun BottomSheetDialog?.ownShow() {
        this?.window?.setWindowAnimations(-1)
        this?.show()
        Handler(Looper.getMainLooper()).postDelayed({
            this?.window?.setWindowAnimations(com.google.android.material.R.style.Animation_Design_BottomSheetDialog)
        }, 200)
    }

    fun String?.html(): Spanned {
        return getHtmlText(this ?: return "".toSpanned())
    }

    private fun getHtmlText(text: String): Spanned {
        return try {
            HtmlCompat.fromHtml(
                text, HtmlCompat.FROM_HTML_MODE_LEGACY
            )
        } catch (e: Exception) {
            logError(e)
            text.toSpanned()
        }
    }

    fun ViewPager2.reduceDragSensitivity(f: Int = 4) {
        val recyclerViewField = ViewPager2::class.java.getDeclaredField("mRecyclerView")
        recyclerViewField.isAccessible = true
        val recyclerView = recyclerViewField.get(this) as RecyclerView

        val touchSlopField = RecyclerView::class.java.getDeclaredField("mTouchSlop")
        touchSlopField.isAccessible = true
        val touchSlop = touchSlopField.get(recyclerView) as Int
        touchSlopField.set(recyclerView, touchSlop * f)
    }

    fun ContentLoadingProgressBar?.animateProgressTo(to: Int) {
        if (this == null) return
        val animation: ObjectAnimator = ObjectAnimator.ofInt(
            this,
            "progress",
            this.progress,
            to
        )
        animation.duration = 500
        animation.setAutoCancel(true)
        animation.interpolator = DecelerateInterpolator()
        animation.start()
    }

    fun Context.createNotificationChannel(
        channelId: String,
        channelName: String,
        description: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel =
                NotificationChannel(channelId, channelName, importance).apply {
                    this.description = description
                }

            val notificationManager: NotificationManager =
                this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            notificationManager.createNotificationChannel(channel)
        }
    }

    /** Sort subtitles by names */
    fun sortSubs(subs: Set<SubtitleData>): List<SubtitleData> {
        return subs
            .sortedWith(
                compareBy { subtitle: SubtitleData -> subtitle.originalName }
                    .thenBy { subtitle: SubtitleData -> subtitle.nameSuffix })
    }

    fun Context.getApiSettings(): HashSet<String> {
        val hashSet = HashSet<String>()
        val activeLangs = getApiProviderLangSettings()
        val hasUniversal = activeLangs.contains(AllLanguagesName)
        hashSet.addAll(apis.filter { hasUniversal || activeLangs.contains(it.lang) }
            .map { it.name })
        return hashSet
    }

    fun Context.getApiDubstatusSettings(): HashSet<DubStatus> {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val hashSet = HashSet<DubStatus>()
        hashSet.addAll(DubStatus.values())
        val list = settingsManager.getStringSet(
            this.getString(R.string.display_sub_key),
            hashSet.map { it.name }.toMutableSet()
        ) ?: return hashSet

        val names = DubStatus.values().map { it.name }.toHashSet()

        return list.filter { names.contains(it) }.map { DubStatus.valueOf(it) }.toHashSet()
    }

    fun Context.getApiProviderLangSettings(): HashSet<String> {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val hashSet = hashSetOf(AllLanguagesName)
        val list = settingsManager.getStringSet(
            this.getString(R.string.provider_lang_key),
            hashSet
        )

        if (list.isNullOrEmpty()) return hashSet
        return list.toHashSet()
    }

    fun Context.getApiTypeSettings(): HashSet<TvType> {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val hashSet = HashSet<TvType>()
        hashSet.addAll(TvType.values())
        val list = settingsManager.getStringSet(
            this.getString(R.string.search_types_list_key),
            hashSet.map { it.name }.toMutableSet()
        )

        if (list.isNullOrEmpty()) return hashSet

        val names = TvType.values().map { it.name }.toHashSet()
        val realSet = list.filter { names.contains(it) }.map { TvType.valueOf(it) }.toHashSet()
        if (realSet.isEmpty()) return hashSet

        return realSet
    }

    fun Context.updateHasTrailers() {
        LoadResponse.isTrailersEnabled = getHasTrailers()
    }

    private fun Context.getHasTrailers(): Boolean {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        return settingsManager.getBoolean(this.getString(R.string.show_trailers_key), true)
    }

    fun Context.shouldShowPlayerMetadata(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getBoolean(
            getString(R.string.show_player_metadata_key),
            true
        )
    }

    fun Context.filterProviderByPreferredMedia(hasHomePageIsRequired: Boolean = true): List<MainAPI> {
        val oldLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = TvType::class.java.classLoader

        val default = TvType.values()
            .sorted()
            .filter { it != TvType.NSFW }
            .map { it.ordinal }

        Thread.currentThread().contextClassLoader = oldLoader

        val defaultSet = default.map { it.toString() }.toSet()
        val currentPrefMedia = try {
            PreferenceManager.getDefaultSharedPreferences(this)
                .getStringSet(this.getString(R.string.prefer_media_type_key), defaultSet)
                ?.mapNotNull { it.toIntOrNull() ?: return@mapNotNull null }
        } catch (e: Throwable) {
            null
        } ?: default
        val langs = this.getApiProviderLangSettings()
        val hasUniversal = langs.contains(AllLanguagesName)
        val allApis =
            apis.filter { api -> (hasUniversal || langs.contains(api.lang)) && (api.hasMainPage || !hasHomePageIsRequired) }
        return if (currentPrefMedia.isEmpty()) {
            allApis
        } else {
            allApis.filter { api -> api.supportedTypes.any { currentPrefMedia.contains(it.ordinal) } }
        }
    }

    fun Context.filterSearchResultByFilmQuality(data: List<SearchResponse>): List<SearchResponse> {
        if (data.isNotEmpty()) {
            val filteredSearchQuality = PreferenceManager.getDefaultSharedPreferences(this)
                ?.getStringSet(getString(R.string.pref_filter_search_quality_key), setOf())
                ?.mapNotNull { entry ->
                    entry.toIntOrNull() ?: return@mapNotNull null
                } ?: listOf()
            if (filteredSearchQuality.isNotEmpty()) {
                return data.filter { item ->
                    val searchQualVal = item.quality?.ordinal ?: -1
                    !filteredSearchQuality.contains(searchQualVal)
                }
            }
        }
        return data
    }

    fun Context.filterHomePageListByFilmQuality(data: HomePageList): HomePageList {
        if (data.list.isNotEmpty()) {
            val filteredSearchQuality = PreferenceManager.getDefaultSharedPreferences(this)
                ?.getStringSet(getString(R.string.pref_filter_search_quality_key), setOf())
                ?.mapNotNull { entry ->
                    entry.toIntOrNull() ?: return@mapNotNull null
                } ?: listOf()
            if (filteredSearchQuality.isNotEmpty()) {
                return HomePageList(
                    name = data.name,
                    isHorizontalImages = data.isHorizontalImages,
                    list = data.list.filter { item ->
                        val searchQualVal = item.quality?.ordinal ?: -1
                        !filteredSearchQuality.contains(searchQualVal)
                    }
                )
            }
        }
        return data
    }

    fun Activity.loadRepository(url: String) {
        ioSafe {
            val repo = RepositoryManager.parseRepository(url) ?: return@ioSafe
            val data = RepositoryData(
                repo.iconUrl ?: "",
                repo.name,
                url
            )
            RepositoryManager.addRepository(data)
            main {
                showToast(
                    getString(R.string.player_loaded_subtitles, repo.name),
                    Toast.LENGTH_LONG
                )
            }
            afterRepositoryLoadedEvent.invoke(true)
            addRepositoryDialog(data)
        }
    }

    fun Activity.addRepositoryDialog(
        repositoryData: RepositoryData
    ) {
        val repos = RepositoryManager.getRepositories()

        fun openAddedRepo() {
            if (repos.isNotEmpty()) {
                navigate(
                    R.id.global_to_navigation_settings_plugins,
                    PluginsFragment.newInstance(
                        repositoryData,
                    )
                )
            }
        }

        runOnUiThread {
            AlertDialog.Builder(this).apply {
                setTitle(repositoryData.name)
                setMessage(R.string.download_all_plugins_from_repo)
                setPositiveButton(R.string.open_downloaded_repo) { _, _ ->
                    openAddedRepo()
                }
                setNegativeButton(R.string.dismiss, null)
                show().setDefaultFocus()
            }
        }
    }

    private fun Context.hasWebView(): Boolean {
        return this.packageManager.hasSystemFeature("android.software.webview")
    }

    fun openWebView(fragment: Fragment?, url: String) {
        if (fragment?.context?.hasWebView() == true)
            safe {
                fragment
                    .findNavController()
                    .navigate(R.id.navigation_webview, WebviewFragment.newInstance(url))
            }
    }

    fun Context.openBrowser(
        url: String,
        fallbackWebview: Boolean = false,
        fragment: Fragment? = null,
    ) = (this.getActivity() ?: activity)?.runOnUiThread {
        try {
            if (AdBlocker.isAd(url)) {
                Log.d("AdBlock", "Blocked ad redirect: $url")
                return@runOnUiThread
            }

            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = url.toUri()
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

            val activityResultRegistry = fragment?.activity?.activityResultRegistry
            if (activityResultRegistry != null) {
                activityResultRegistry.register(
                    url,
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == RESULT_CANCELED && fallbackWebview) {
                        openWebView(fragment, url)
                    }
                }.launch(intent)
            } else this.startActivity(intent)
        } catch (e: Exception) {
            logError(e)
            if (fallbackWebview) {
                openWebView(fragment, url)
            }
        }
    }

    fun Context.isNetworkAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities =
                connectivityManager.getNetworkCapabilities(network) ?: return false
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected == true
        }
    }

    fun splitQuery(url: java.net.URL): Map<String, String> {
        return com.lagradost.cloudstream3.splitUrlParameters(url.toString())
    }

    fun Context.getNameFull(name: String?, episode: Int?, season: Int?): String {
        val rEpisode = if (episode == 0) null else episode
        val rSeason = if (season == 0) null else season

        val seasonName = getString(R.string.season)
        val episodeName = getString(R.string.episode)
        val seasonNameShort = getString(R.string.season_short)
        val episodeNameShort = getString(R.string.episode_short)

        if (name != null) {
            return if (rEpisode != null && rSeason != null) {
                "$seasonNameShort${rSeason}:$episodeNameShort${rEpisode} $name"
            } else if (rEpisode != null) {
                "$episodeName $rEpisode. $name"
            } else {
                name
            }
        } else {
            if (rEpisode != null && rSeason != null) {
                return "$seasonName $rSeason - $episodeName $rEpisode"
            } else if (rSeason == null) {
                return "$episodeName $rEpisode"
            }
        }
        return ""
    }

    fun Context.getShortSeasonText(episode: Int?, season: Int?): String? {
        val rEpisode = if (episode == 0) null else episode
        val rSeason = if (season == 0) null else season
        val seasonNameShort = getString(R.string.season_short)
        val episodeNameShort = getString(R.string.episode_short)
        return if (rEpisode != null && rSeason != null) {
            "$seasonNameShort${rSeason}:$episodeNameShort${rEpisode}"
        } else if (rEpisode != null) {
            "$episodeNameShort$rEpisode"
        } else null
    }

    fun Activity?.loadCache() {
        try {
            cacheClass("android.net.NetworkCapabilities".load())
        } catch (_: Exception) {
        }
    }

    private fun getResultsId(): Int {
        return R.id.global_to_navigation_results_phone
    }

    fun loadResult(
        url: String,
        apiName: String,
        name: String,
        startAction: Int = 0,
        startValue: Int = 0
    ) {
        (activity as FragmentActivity?)?.loadResult(url, apiName, name, startAction, startValue)
    }

    fun FragmentActivity.loadResult(
        url: String,
        apiName: String,
        name: String,
        startAction: Int = 0,
        startValue: Int = 0
    ) {

        this.runOnUiThread {
            this.navigate(
                getResultsId(),
                ResultFragment.newInstance(url, apiName, name, startAction, startValue)
            )
        }
    }

    fun loadSearchResult(
        card: SearchResponse,
        startAction: Int = 0,
        startValue: Int? = null,
    ) {
        activity?.loadSearchResult(card, startAction, startValue)
    }

    fun Activity?.loadSearchResult(
        card: SearchResponse,
        startAction: Int = 0,
        startValue: Int? = null,
    ) {
        this?.runOnUiThread {
            this.navigate(
                getResultsId(),
                ResultFragment.newInstance(card, startAction, startValue)
            )
        }
    }

    fun Activity.requestLocalAudioFocus(focusRequest: AudioFocusRequest?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest == null) {
                Log.e("TAG", "focusRequest was null")
                return
            }

            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.requestAudioFocus(focusRequest)
        } else {
            val audioManager: AudioManager =
                getSystemService(Context.AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private var currentAudioFocusRequest: AudioFocusRequest? = null
    private var currentAudioFocusChangeListener: AudioManager.OnAudioFocusChangeListener? = null
    var onAudioFocusEvent = Event<Boolean>()

    private fun getAudioListener(): AudioManager.OnAudioFocusChangeListener? {
        if (currentAudioFocusChangeListener != null) return currentAudioFocusChangeListener
        currentAudioFocusChangeListener = AudioManager.OnAudioFocusChangeListener {
            onAudioFocusEvent.invoke(
                when (it) {
                    AudioManager.AUDIOFOCUS_GAIN -> false
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> false
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> false
                    else -> true
                }
            )
        }
        return currentAudioFocusChangeListener
    }

    fun Context.isCastApiAvailable(): Boolean {
        val isCastApiAvailable =
            GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(applicationContext) == ConnectionResult.SUCCESS

        try {
            applicationContext?.let {
                val task = CastContext.getSharedInstance(it) { it.run() }
                task.result
            }
        } catch (e: Exception) {
            println(e)
            return false
        }

        return isCastApiAvailable
    }

    fun Context.isConnectedToChromecast(): Boolean {
        if (isCastApiAvailable()) {
            val executor: Executor = Executors.newSingleThreadExecutor()
            val castContext = CastContext.getSharedInstance(this, executor)
            if (castContext.result.castState == CastState.CONNECTED) {
                return true
            }
        }
        return false
    }

    fun AlertDialog.setDefaultFocus(buttonFocus: Int = DialogInterface.BUTTON_NEGATIVE) {
        this.getButton(buttonFocus).run {
            isFocusableInTouchMode = true
            requestFocus()
        }
    }

    fun Context.isUsingMobileData(): Boolean {
        val connectionManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork: Network? = connectionManager.activeNetwork
            val networkCapabilities = connectionManager.getNetworkCapabilities(activeNetwork)
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true &&
                    !networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            connectionManager.activeNetworkInfo?.type == ConnectivityManager.TYPE_MOBILE
        }
    }


    private fun Activity?.cacheClass(clazz: String?) {
        clazz?.let { c ->
            this?.cacheDir?.let {
                Cache(
                    directory = File(it, c.toClassDir()),
                    maxSize = 20L * 1024L * 1024L
                )
            }
        }
    }

    fun Context.isAppInstalled(uri: String): Boolean {
        val pm = Wrappers.packageManager(this)

        return try {
            pm.getPackageInfo(uri, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getFocusRequest(): AudioFocusRequest? {
        if (currentAudioFocusRequest != null) return currentAudioFocusRequest
        currentAudioFocusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
                setAudioAttributes(AudioAttributes.Builder().run {
                    setUsage(AudioAttributes.USAGE_MEDIA)
                    setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    build()
                })
                setAcceptsDelayedFocusGain(true)
                getAudioListener()?.let {
                    setOnAudioFocusChangeListener(it)
                }
                build()
            }
        } else null
        return currentAudioFocusRequest
    }
}
