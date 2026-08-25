@file:Suppress("DEPRECATION")

package com.lagradost.cloudstream3.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import android.widget.FrameLayout
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.media3.common.C.TIME_UNSET
import androidx.media3.common.C.TRACK_TYPE_AUDIO
import androidx.media3.common.C.TRACK_TYPE_TEXT
import androidx.media3.common.C.TRACK_TYPE_VIDEO
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cronet.CronetDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer.STATE_ENABLED
import androidx.media3.exoplayer.Renderer.STATE_STARTED
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistTracker
import androidx.media3.exoplayer.source.ClippingMediaSource
import androidx.media3.exoplayer.source.ConcatenatingMediaSource
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.TrackSelector
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.ui.SubtitleView
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.AudioFile
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.MainActivity.Companion.deleteFileOnExit
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.debugAssert
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.ui.player.CustomDecoder.Companion.fixSubtitleAlignment
import com.lagradost.cloudstream3.ui.player.live.LiveHelper
import com.lagradost.cloudstream3.ui.player.live.PREFERRED_LIVE_OFFSET
import com.lagradost.cloudstream3.ui.subtitles.SaveCaptionStyle
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.applyStyle
import com.lagradost.cloudstream3.utils.AppContextUtils.isUsingMobileData
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.CLEARKEY_DRM_UUID
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread
import com.lagradost.cloudstream3.utils.DataStoreHelper.currentAccount
import com.lagradost.cloudstream3.utils.DrmExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkPlayList
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.PLAYREADY_DRM_UUID
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromTagToLanguageName
import com.lagradost.cloudstream3.utils.WIDEVINE_DRM_UUID
import com.lagradost.cloudstream3.utils.videoskip.VideoSkipStamp
import kotlinx.coroutines.delay
import okhttp3.Interceptor
import org.chromium.net.CronetEngine
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import kotlin.uuid.toJavaUuid

const val TAG = "CS3ExoPlayer"
const val PREFERRED_AUDIO_LANGUAGE_KEY = "preferred_audio_language"

/** toleranceBeforeUs – The maximum time that the actual position seeked to may precede the
 * requested seek position, in microseconds. Must be non-negative. */
const val toleranceBeforeUs = 300_000L

/**
 * toleranceAfterUs – The maximum time that the actual position seeked to may exceed the requested
 * seek position, in microseconds. Must be non-negative.
 */
const val toleranceAfterUs = 300_000L

@OptIn(UnstableApi::class)
class CS3IPlayer : IPlayer {
    private var playerListener: Player.Listener? = null
    private var isPlaying = false
    private var exoPlayer: ExoPlayer? = null
        set(value) {
            debugAssert(
                { field != null && value != null },
                { "Previous player instance should be released!" })
            field = value
        }

    var cacheSize = 0L
    var simpleCacheSize = 0L
    var videoBufferMs = 0L

    val imageGenerator = IPreviewGenerator.new()

    private val seekActionTime = 30000L
    private val isMediaSeekable
        get() = exoPlayer?.let {
            it.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM) && it.isCurrentMediaItemSeekable
        } ?: false

    private var ignoreSSL: Boolean = true
    private var playBackSpeed: Float = 1.0f

    private var lastMuteVolume: Float = 1.0f

    private var currentLink: ExtractorLink? = null
    private var currentDownloadedFile: ExtractorUri? = null
    private var hasUsedFirstRender = false

    private var currentWindow: Int = 0
    private var playbackPosition: Long = 0

    private val subtitleHelper = PlayerSubtitleHelper()

    private var isAudioOnlyBackground = false

    data class MediaItemSlice(
        val mediaItem: MediaItem,
        val durationUs: Long,
        val drm: DrmMetadata? = null
    )

    data class DrmMetadata(
        val kid: String? = null,
        val key: String? = null,
        val uuid: UUID,
        val kty: String? = null,
        val licenseUrl: String? = null,
        val keyRequestParameters: HashMap<String, String>,
    )

    override fun getDuration(): Long? = exoPlayer?.duration
    override fun getPosition(): Long? = exoPlayer?.currentPosition
    override fun getIsPlaying(): Boolean = isPlaying
    override fun getPlaybackSpeed(): Float = playBackSpeed

    private var playerSelectedSubtitleTracks = listOf<Pair<String, Boolean>>()
    private var requestedListeningPercentages: List<Int>? = null

    private var eventHandler: ((PlayerEvent) -> Unit)? = null

    @AnyThread
    fun event(event: PlayerEvent) {
        if (Looper.getMainLooper().isCurrentThread) {
            eventHandler?.invoke(event)
        } else runOnMainThread {
            eventHandler?.invoke(event)
        }
    }

    @Volatile
    var isPlayerActive: Boolean = false

    override fun releaseCallbacks() {
        eventHandler = null
        if (isPlayerActive) {
            isPlayerActive = false
            activePlayers -= 1
            releaseCronetEngine()
        }
    }

    @AnyThread
    override fun initCallbacks(
        @MainThread eventHandler: ((PlayerEvent) -> Unit),
        requestedListeningPercentages: List<Int>?,
    ) {
        this.requestedListeningPercentages = requestedListeningPercentages
        this.eventHandler = eventHandler
        if (!isPlayerActive) {
            isPlayerActive = true
            activePlayers += 1
        }
    }

    fun String.stripTrackId(): String {
        return this.replace(Regex("""^\d+:"""), "")
    }

    fun initSubtitles(subView: SubtitleView?, subHolder: FrameLayout?, style: SaveCaptionStyle?) {
        subtitleHelper.initSubtitles(subView, subHolder, style)
    }

    override fun getPreview(fraction: Float): Bitmap? {
        return imageGenerator.getPreviewImage(fraction)
    }

    override fun hasPreview(): Boolean {
        if (exoPlayer?.isCurrentMediaItemDynamic == true) {
            return false
        }
        return imageGenerator.hasPreview()
    }

    override fun loadPlayer(
        context: Context,
        sameEpisode: Boolean,
        link: ExtractorLink?,
        data: ExtractorUri?,
        startPosition: Long?,
        subtitles: Set<SubtitleData>,
        subtitle: SubtitleData?,
        autoPlay: Boolean?,
        preview: Boolean,
    ) {
        Log.i(TAG, "loadPlayer")
        if (sameEpisode) {
            saveData()
        } else {
            currentSubtitles = subtitle
            playbackPosition = 0
        }

        startPosition?.let {
            playbackPosition = it
        }

        isPlaying = autoPlay ?: isPlaying

        releasePlayer()

        if (link != null) {
            (imageGenerator as? PreviewGenerator)?.let { gen ->
                if (preview) {
                    gen.load(link, sameEpisode)
                } else {
                    gen.clear(sameEpisode)
                }
            }

            loadOnlinePlayer(context, link)
        } else if (data != null) {
            (imageGenerator as? PreviewGenerator)?.let { gen ->
                if (preview) {
                    gen.load(context, data, sameEpisode)
                } else {
                    gen.clear(sameEpisode)
                }
            }
            loadOfflinePlayer(context, data)
        } else {
            throw IllegalArgumentException("Requires link or uri")
        }

    }

    override fun setActiveSubtitles(subtitles: Set<SubtitleData>) {
        Log.i(TAG, "setActiveSubtitles ${subtitles.size}")
        subtitleHelper.setAllSubtitles(subtitles)
    }

    private var currentSubtitles: SubtitleData? = null

    private fun List<Tracks.Group>.getTrack(id: String?): Pair<TrackGroup, Int>? {
        if (id == null) return null
        return this.firstNotNullOfOrNull { group ->
            (0 until group.mediaTrackGroup.length).map {
                group.getTrackFormat(it) to it
            }.firstOrNull {
                it.first.id?.stripTrackId() == id
            }
                ?.let { group.mediaTrackGroup to it.second }
        }
    }

    override fun setMaxVideoSize(width: Int, height: Int, id: String?) {
        if (id != null) {
            val videoTrack =
                exoPlayer?.currentTracks?.groups?.filter { it.type == TRACK_TYPE_VIDEO }
                    ?.getTrack(id)

            if (videoTrack != null) {
                exoPlayer?.trackSelectionParameters = exoPlayer?.trackSelectionParameters
                    ?.buildUpon()
                    ?.setOverrideForType(
                        TrackSelectionOverride(
                            videoTrack.first,
                            videoTrack.second
                        )
                    )
                    ?.build()
                    ?: return
                return
            }
        }

        exoPlayer?.trackSelectionParameters = exoPlayer?.trackSelectionParameters
            ?.buildUpon()
            ?.setMaxVideoSize(width, height)
            ?.build()
            ?: return
    }

    override fun setPreferredAudioTrack(trackLanguage: String?, id: String?, formatIndex: Int?) {
        preferredAudioTrackLanguage = trackLanguage
        id?.let { trackId ->
            val trackFormatIndex = formatIndex ?: 0
            exoPlayer?.currentTracks?.groups
                ?.filter { it.type == TRACK_TYPE_AUDIO }
                ?.find { group ->
                    group.getFormats().any { (format, _) ->
                        format.id == trackId
                    }
                }
                ?.let { group ->
                    exoPlayer?.trackSelectionParameters
                        ?.buildUpon()
                        ?.setOverrideForType(
                            TrackSelectionOverride(
                                group.mediaTrackGroup,
                                trackFormatIndex
                            )
                        )
                        ?.build()
                }
                ?.let { newParams ->
                    exoPlayer?.trackSelectionParameters = newParams
                    return
                }
        }
        exoPlayer?.trackSelectionParameters = exoPlayer?.trackSelectionParameters
            ?.buildUpon()
            ?.setPreferredAudioLanguage(trackLanguage)
            ?.build() ?: return
    }

    private fun List<Tracks.Group>.getFormats(): List<Pair<Format, Int>> {
        return this.flatMap {
            it.getFormats()
        }
    }

    private fun Tracks.Group.getFormats(): List<Pair<Format, Int>> {
        return (0 until this.mediaTrackGroup.length).mapNotNull { i ->
            if (this.isSupported)
                this.mediaTrackGroup.getFormat(i) to i
            else null
        }
    }

    private fun Format.toAudioTrack(formatIndex: Int?): AudioTrack {
        return AudioTrack(
            this.id,
            this.label,
            this.language,
            this.sampleMimeType,
            this.channelCount,
            formatIndex ?: 0,
        )
    }

    private fun Format.toSubtitleTrack(): TextTrack {
        return TextTrack(
            this.id?.stripTrackId(),
            this.label,
            this.language,
            this.sampleMimeType,
        )
    }

    private fun Format.toVideoTrack(): VideoTrack {
        return VideoTrack(
            this.id?.stripTrackId(),
            this.label,
            this.language,
            this.width,
            this.height,
            this.sampleMimeType
        )
    }

    override fun getVideoTracks(): CurrentTracks {
        val allTrackGroups = exoPlayer?.currentTracks?.groups ?: emptyList()
        val videoTracks = allTrackGroups.filter { it.type == TRACK_TYPE_VIDEO }
            .getFormats()
            .map { it.first.toVideoTrack() }
        var currentAudioTrack: AudioTrack? = null
        val audioTracks = allTrackGroups.filter { it.type == TRACK_TYPE_AUDIO }
            .flatMap { group ->
                group.getFormats().map { (format, formatIndex) ->
                    val audioTrack = format.toAudioTrack(formatIndex)
                    if (group.isTrackSelected(formatIndex)) {
                        currentAudioTrack = audioTrack
                    }
                    audioTrack
                }
            }
        val textTracks = allTrackGroups.filter { it.type == TRACK_TYPE_TEXT }
            .getFormats()
            .map { it.first.toSubtitleTrack() }
        val currentTextTracks = textTracks.filter { track ->
            playerSelectedSubtitleTracks.any { it.second && it.first == track.id }
        }
        return CurrentTracks(
            exoPlayer?.videoFormat?.toVideoTrack(),
            currentAudioTrack,
            currentTextTracks,
            videoTracks,
            audioTracks,
            textTracks
        )
    }

    override fun setPreferredSubtitles(subtitle: SubtitleData?): Boolean {
        Log.i(TAG, "setPreferredSubtitles init $subtitle")
        currentSubtitles = subtitle
        val trackSelector = exoPlayer?.trackSelector as? DefaultTrackSelector ?: return false
        if (subtitle == null) {
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .setTrackTypeDisabled(TRACK_TYPE_TEXT, true)
                    .clearOverridesOfType(TRACK_TYPE_TEXT)
            )
            return false
        }
        when (subtitleHelper.subtitleStatus(subtitle)) {
            SubtitleStatus.REQUIRES_RELOAD -> {
                Log.i(TAG, "setPreferredSubtitles REQUIRES_RELOAD")
                return true
            }

            SubtitleStatus.NOT_FOUND -> {
                Log.i(TAG, "setPreferredSubtitles NOT_FOUND")
                return true
            }

            SubtitleStatus.IS_ACTIVE -> {
                Log.i(TAG, "setPreferredSubtitles IS_ACTIVE")
                exoPlayer?.currentTracks?.groups
                    ?.filter { it.type == TRACK_TYPE_TEXT }
                    ?.getTrack(subtitle.getId())
                    ?.let { (trackGroup, trackIndex) ->
                        trackSelector.setParameters(
                            trackSelector.buildUponParameters()
                                .setTrackTypeDisabled(TRACK_TYPE_TEXT, false)
                                .setOverrideForType(TrackSelectionOverride(trackGroup, trackIndex))
                        )
                    }
                return false
            }
        }
    }

    private var currentSubtitleOffset: Long = 0

    override fun setSubtitleOffset(offset: Long) {
        currentSubtitleOffset = offset
        CustomDecoder.subtitleOffset = offset
        if (currentTextRenderer?.state == STATE_ENABLED || currentTextRenderer?.state == STATE_STARTED) {
            exoPlayer?.currentPosition?.also { pos ->
                currentTextRenderer?.resetPosition(pos, false)
            }
        }
    }

    override fun getSubtitleOffset(): Long {
        return currentSubtitleOffset
    }

    override fun getSubtitleCues(): List<SubtitleCue> {
        return currentSubtitleDecoder?.getSubtitleCues() ?: emptyList()
    }

    override fun getCurrentPreferredSubtitle(): SubtitleData? {
        return subtitleHelper.getAllSubtitles().firstOrNull { sub ->
            playerSelectedSubtitleTracks.any { (id, isSelected) ->
                isSelected && sub.getId() == id
            }
        }
    }

    override fun getAspectRatio(): Rational? {
        return exoPlayer?.videoFormat?.let { format ->
            Rational(format.width, format.height)
        }
    }

    override fun updateSubtitleStyle(style: SaveCaptionStyle) {
        subtitleHelper.setSubStyle(style)
    }

    override fun saveData() {
        Log.i(TAG, "saveData")
        updatedTime()

        exoPlayer?.let { exo ->
            playbackPosition = exo.currentPosition
            currentWindow = exo.currentMediaItemIndex
            isPlaying = exo.isPlaying
        }
    }

    private fun releasePlayer(saveTime: Boolean = true) {
        Log.i(TAG, "releasePlayer")
        eventLooperIndex += 1
        if (saveTime)
            updatedTime()

        currentTextRenderer = null
        currentSubtitleDecoder = null

        exoPlayer?.apply {
            playWhenReady = false

            try {
                pause()
            } catch (t: Throwable) {
                logError(t)
            }
            playerListener?.let {
                removeListener(it)
                playerListener = null
            }
            stop()
            release()
        }

        exoPlayer = null
        event(PlayerAttachedEvent(null))
    }

    override fun onStop() {
        Log.i(TAG, "onStop")

        saveData()
        if (!isAudioOnlyBackground) {
            handleEvent(CSPlayerEvent.Pause, PlayerEventSource.Player)
        }
    }

    override fun onPause() {
        Log.i(TAG, "onPause")
        saveData()
        if (!isAudioOnlyBackground) {
            handleEvent(CSPlayerEvent.Pause, PlayerEventSource.Player)
        }
    }

    override fun onResume(context: Context) {
        isAudioOnlyBackground = false
        if (exoPlayer == null)
            reloadPlayer(context)
    }

    override fun release() {
        imageGenerator.release()
        releasePlayer()
    }

    override fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        playBackSpeed = speed
    }

    companion object {
        private const val CRONET_TIMEOUT_MS = 15_000

        private var cronetEngine: CronetEngine? = null

        @Volatile
        private var activePlayers = 0

        @Volatile
        private var cronetReleasedId = 0

        fun releaseCronetEngine() {
            if (cronetEngine == null) return

            val id = ++cronetReleasedId
            val posted = Handler(Looper.getMainLooper()).postDelayed({
                releaseCronetEngineInstantly(id)
            }, 60_000)

            if (!posted) {
                releaseCronetEngineInstantly(id)
            }
        }

        private fun releaseCronetEngineInstantly(id: Int) {
            if (activePlayers == 0 && id == cronetReleasedId) {
                try {
                    cronetEngine?.shutdown()
                } catch (t: Throwable) {
                    logError(t)
                } finally {
                    Log.d(TAG, "CronetEngine shutdown")
                    cronetEngine = null
                }
            }
        }

        var preferredAudioTrackLanguage: String? = null
            get() {
                return field ?: getKey<String>(
                    "$currentAccount/$PREFERRED_AUDIO_LANGUAGE_KEY",
                    field
                )?.also {
                    field = it
                }
            }
            set(value) {
                setKey("$currentAccount/$PREFERRED_AUDIO_LANGUAGE_KEY", value)
                field = value
            }

        private var simpleCache: SimpleCache? = null

        private fun createOnlineSource(
            headers: Map<String, String>?,
            interceptor: Interceptor?
        ): HttpDataSource.Factory {
            val client = if (interceptor == null) {
                app.baseClient
            } else {
                app.baseClient.newBuilder()
                    .addInterceptor(interceptor)
                    .build()
            }
            val source = OkHttpDataSource.Factory(client).setUserAgent(USER_AGENT)

            if (!headers.isNullOrEmpty()) {
                source.setDefaultRequestProperties(headers)
            }
            return source
        }

        fun tryCreateEngine(context: Context, diskCacheSize: Long): CronetEngine? {
            cronetEngine?.let {
                return it
            }

            return try {
                val cacheDirectory = File(context.cacheDir, "CronetEngine")
                cacheDirectory.deleteRecursively()
                if (!cacheDirectory.exists()) {
                    cacheDirectory.mkdirs()
                }
                CronetEngine.Builder(context)
                    .enableBrotli(true)
                    .enableHttp2(true)
                    .enableQuic(true)
                    .setStoragePath(cacheDirectory.absolutePath)
                    .setLibraryLoader(null)
                    .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK, diskCacheSize)
                    .build().also { buildEngine ->
                        Log.d(
                            TAG,
                            "Created CronetEngine with cache at ${cacheDirectory.absolutePath}"
                        )
                        cronetEngine = buildEngine
                    }
            } catch (t: Throwable) {
                logError(t)
                null
            }
        }

        private fun createVideoSource(
            link: ExtractorLink,
            engine: CronetEngine?,
            interceptor: Interceptor?,
        ): HttpDataSource.Factory {
            val userAgent = link.headers.entries.find {
                it.key.equals("User-Agent", ignoreCase = true)
            }?.value ?: USER_AGENT

            val source = if (interceptor == null) {
                if (engine == null) {
                    Log.d(TAG, "Using DefaultHttpDataSource for $link")
                    OkHttpDataSource.Factory(app.baseClient).setUserAgent(userAgent)
                } else {
                    Log.d(TAG, "Using CronetDataSource for $link")
                    CronetDataSource.Factory(engine, Executors.newSingleThreadExecutor())
                        .setUserAgent(userAgent)
                        .setConnectionTimeoutMs(CRONET_TIMEOUT_MS)
                        .setReadTimeoutMs(CRONET_TIMEOUT_MS)
                        .setResetTimeoutOnRedirects(true)
                        .setHandleSetCookieRequests(true)
                }
            } else {
                Log.d(TAG, "Using OkHttpDataSource for $link")
                val client = app.baseClient.newBuilder()
                    .addInterceptor(interceptor)
                    .build()
                OkHttpDataSource.Factory(client).setUserAgent(userAgent)
            }

            val refererMap =
                if (link.referer.isBlank()) emptyMap() else mapOf("referer" to link.referer)

            val headers = refererMap + link.headers

            return source.apply {
                setDefaultRequestProperties(headers)
            }
        }

        private fun Context.createOfflineSource(): DataSource.Factory {
            return DefaultDataSource.Factory(
                this,
                DefaultHttpDataSource.Factory().setUserAgent(USER_AGENT)
            )
        }

        private fun getCache(context: Context, cacheSize: Long): SimpleCache? {
            return try {
                val databaseProvider = StandaloneDatabaseProvider(context)
                SimpleCache(
                    File(
                        context.cacheDir, "exoplayer"
                    ).also { deleteFileOnExit(it) },
                    LeastRecentlyUsedCacheEvictor(cacheSize),
                    databaseProvider
                )
            } catch (e: Exception) {
                logError(e)
                null
            }
        }

        private fun getMediaItemBuilder(mimeType: String):
                MediaItem.Builder {
            return MediaItem.Builder()
                .setMimeType(mimeType)
        }

        private fun getMediaItem(mimeType: String, uri: Uri): MediaItem {
            return getMediaItemBuilder(mimeType).setUri(uri).build()
        }

        private fun getMediaItem(mimeType: String, url: String): MediaItem {
            return getMediaItemBuilder(mimeType).setUri(url).build()
        }

        private fun getTrackSelector(context: Context, maxVideoHeight: Int?): TrackSelector {
            val trackSelector = DefaultTrackSelector(context)
            trackSelector.parameters = trackSelector.buildUponParameters()
                .setMaxVideoSize(Int.MAX_VALUE, maxVideoHeight ?: Int.MAX_VALUE)
                .setPreferredAudioLanguage(null)
                .build()
            return trackSelector
        }

        private var currentSubtitleDecoder: CustomSubtitleDecoderFactory? = null
        private var currentTextRenderer: TextRenderer? = null
    }

    private fun getCurrentTimestamp(writePosition: Long? = null): VideoSkipStamp? {
        val position = writePosition ?: this@CS3IPlayer.getPosition() ?: return null
        for (lastTimeStamp in lastTimeStamps) {
            if (lastTimeStamp.timestamp.startMs <= position && (position + (toleranceBeforeUs / 1000L) + 1) < lastTimeStamp.timestamp.endMs) {
                return lastTimeStamp
            }
        }
        return null
    }

    fun updatedTime(
        writePosition: Long? = null,
        source: PlayerEventSource = PlayerEventSource.Player
    ) {
        val position = writePosition ?: exoPlayer?.currentPosition

        getCurrentTimestamp(position)?.let { timestamp ->
            event(TimestampInvokedEvent(timestamp, source))
        }

        val duration = exoPlayer?.contentDuration
        if (duration != null && position != null) {
            event(
                PositionEvent(
                    source,
                    fromMs = exoPlayer?.currentPosition ?: 0,
                    position,
                    duration
                )
            )
        }
    }

    override fun seekTime(time: Long, source: PlayerEventSource) {
        exoPlayer?.seekTime(time, source)
    }

    override fun seekTo(time: Long, source: PlayerEventSource) {
        if (isMediaSeekable) {
            updatedTime(time, source)
            exoPlayer?.seekTo(time)
        } else {
            Log.i(TAG, "Media is not seekable, we can not seek to $time")
        }
    }

    private fun ExoPlayer.seekTime(time: Long, source: PlayerEventSource) {
        if (isMediaSeekable) {
            updatedTime(currentPosition + time, source)
            seekTo(currentPosition + time)
        } else {
            Log.i(TAG, "Media is not seekable, we can not seek to $time")
        }
    }

    override fun handleEvent(event: CSPlayerEvent, source: PlayerEventSource) {
        Log.i(TAG, "handleEvent ${event.name}")
        try {
            exoPlayer?.apply {
                when (event) {
                    CSPlayerEvent.Play -> {
                        event(PlayEvent(source))
                        if (playbackState == Player.STATE_IDLE) {
                            val seekPosition = currentPosition
                            exoPlayer?.addListener(object : Player.Listener {
                                private var seekApplied = false
                                override fun onPlaybackStateChanged(playbackState: Int) {
                                    if (seekApplied || playbackState != Player.STATE_READY) return
                                    seekApplied = true
                                    exoPlayer?.seekTo(currentWindow, seekPosition)
                                    exoPlayer?.removeListener(this)
                                }
                            })
                            prepare()
                        }
                        play()
                    }

                    CSPlayerEvent.Pause -> {
                        event(PauseEvent(source))
                        pause()
                    }

                    CSPlayerEvent.ToggleMute -> {
                        if (volume <= 0) {
                            volume = lastMuteVolume
                        } else {
                            lastMuteVolume = volume
                            volume = 0f
                        }
                    }

                    CSPlayerEvent.PlayPauseToggle -> {
                        if (isPlaying) {
                            handleEvent(CSPlayerEvent.Pause, source)
                        } else {
                            handleEvent(CSPlayerEvent.Play, source)
                        }
                    }

                    CSPlayerEvent.SeekForward -> seekTime(seekActionTime, source)

                    CSPlayerEvent.SeekBack -> seekTime(-seekActionTime, source)

                    CSPlayerEvent.Restart -> seekTo(0, source)

                    CSPlayerEvent.NextEpisode -> event(
                        EpisodeSeekEvent(
                            offset = 1,
                            source = source
                        )
                    )

                    CSPlayerEvent.PrevEpisode -> event(
                        EpisodeSeekEvent(
                            offset = -1,
                            source = source
                        )
                    )

                    CSPlayerEvent.SkipCurrentChapter -> {
                        getCurrentTimestamp()?.let { lastTimeStamp ->
                            if (lastTimeStamp.skipToNextEpisode) {
                                handleEvent(CSPlayerEvent.NextEpisode, source)
                            } else {
                                seekTo(lastTimeStamp.timestamp.endMs + 1L)
                            }
                            event(TimestampSkippedEvent(timestamp = lastTimeStamp, source = source))
                        }
                    }

                    CSPlayerEvent.PlayAsAudio -> {
                        isAudioOnlyBackground = true
                        activity?.moveTaskToBack(false)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "handleEvent error", t)
            event(ErrorEvent(t))
        }
    }

    private var eventLooperIndex = 0
    private fun torrentEventLooper(hash: String) = ioSafe {
        eventLooperIndex += 2
        val currentIndex = eventLooperIndex + 1
        while (eventLooperIndex <= currentIndex && eventHandler != null) {
            try {
                val status = Torrent.get(hash)
                event(
                    DownloadEvent(
                        connections = status.activePeers,
                        downloadSpeed = status.downloadSpeed?.toLong()!!,
                        totalBytes = status.torrentSize!!,
                        downloadedBytes = status.bytesRead!!,
                    )
                )
            } catch (_: NullPointerException) {
            } catch (t: Throwable) {
                logError(t)
            }
            delay(1000)
        }
    }

    private fun buildExoPlayer(
        context: Context,
        mediaItemSlices: List<MediaItemSlice>,
        subSources: List<SingleSampleMediaSource>,
        currentWindow: Int,
        playbackPosition: Long,
        playBackSpeed: Float,
        subtitleOffset: Long,
        cacheSize: Long,
        videoBufferMs: Long,
        onlineSource: HttpDataSource.Factory? = null,
        playWhenReady: Boolean = true,
        trackSelector: TrackSelector? = null,
        maxVideoHeight: Int? = null,
        audioSources: List<MediaSource> = emptyList()
    ): ExoPlayer {
        val exoPlayerBuilder =
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(context).setLiveTargetOffsetMs(
                        PREFERRED_LIVE_OFFSET
                    )
                )
                .setLivePlaybackSpeedControl(
                    DefaultLivePlaybackSpeedControl.Builder()
                        .setFallbackMaxPlaybackSpeed(1.03f)
                        .setFallbackMinPlaybackSpeed(0.97f)
                        .build()
                )
                .setRenderersFactory { eventHandler, videoRendererEventListener, audioRendererEventListener, _, metadataRendererOutput ->
                    val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
                    val current = settingsManager.getInt(
                        context.getString(R.string.software_decoding_key),
                        -1
                    )
                    val (isSoftwareDecodingEnabled, isSoftwareDecodingPreferred) = when (current) {
                        0 -> true to false
                        2 -> true to true
                        1 -> false to false
                        else -> true to false
                    }

                    val factory = if (isSoftwareDecodingEnabled) {
                        FixedNextRenderersFactory(context).apply {
                            setEnableDecoderFallback(true)
                            setExtensionRendererMode(
                                if (isSoftwareDecodingPreferred)
                                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                                else
                                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                            )
                        }
                    } else {
                        DefaultRenderersFactory(context)
                    }

                    val style = CustomDecoder.style
                    val customTextOutput = TextOutput { cue ->
                        val (bitmapCues, textCues) = cue.cues.toList()
                            .partition { it.bitmap != null }

                        val styledBitmapCues = bitmapCues.map { bitmapCue ->
                            bitmapCue
                                .buildUpon()
                                .fixSubtitleAlignment()
                                .applyStyle(style)
                                .build()
                        }

                        val set = HashSet<CharSequence>()
                        val buffer = StringBuilder()

                        val styledTextCues = textCues.groupBy {
                            it.lineAnchor to it.position.times(1000.0f).toInt()
                        }.mapNotNull { (_, entries) ->
                            set.clear()
                            buffer.clear()
                            var count = 0
                            for (x in entries) {
                                val text = x.text ?: continue

                                if (!set.add(text)) {
                                    continue
                                }
                                if (++count > 1) buffer.append('\n')

                                buffer.append(text.trim())
                            }

                            val combinedCueText = buffer.toString()

                            entries
                                .firstOrNull()
                                ?.buildUpon()
                                ?.setText(combinedCueText)
                                ?.fixSubtitleAlignment()
                                ?.applyStyle(style)
                                ?.build()
                        }

                        val combinedCues = styledBitmapCues + styledTextCues

                        subtitleHelper.subtitleView?.setCues(combinedCues)
                    }

                    factory.createRenderers(
                        eventHandler,
                        videoRendererEventListener,
                        audioRendererEventListener,
                        customTextOutput,
                        metadataRendererOutput
                    ).map {
                        if (it is TextRenderer) {
                            CustomDecoder.subtitleOffset = subtitleOffset
                            val decoder = CustomSubtitleDecoderFactory()

                            val currentTextRenderer = TextRenderer(
                                customTextOutput,
                                eventHandler.looper,
                                decoder
                            ).apply {
                                experimentalSetLegacyDecodingEnabled(true)
                            }.also { renderer ->
                                currentTextRenderer = renderer
                                currentSubtitleDecoder = decoder
                            }
                            currentTextRenderer
                        } else
                            it
                    }.toTypedArray()
                }
                .setTrackSelector(
                    trackSelector ?: getTrackSelector(
                        context,
                        maxVideoHeight
                    )
                )
                .setSeekParameters(SeekParameters(toleranceBeforeUs, toleranceAfterUs))
                .setLoadControl(
                    DefaultLoadControl.Builder()
                        .setTargetBufferBytes(
                            if (cacheSize <= 0) {
                                DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES
                            } else {
                                if (cacheSize > Int.MAX_VALUE) Int.MAX_VALUE else cacheSize.toInt()
                            }
                        )
                        .setBackBuffer(
                            30000,
                            true
                        )
                        .setBufferDurationsMs(
                            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                            if (videoBufferMs <= 0) {
                                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS
                            } else {
                                videoBufferMs.toInt()
                            },
                            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                        ).build()
                )

        val extractorFactor = UpdatedDefaultExtractorsFactory()
            .setFragmentedMp4ExtractorFlags(FragmentedMp4Extractor.FLAG_MERGE_FRAGMENTED_SIDX)

        val dataSourceFactory = if (onlineSource == null) {
            null
        } else {
            if (simpleCache == null)
                simpleCache = getCache(context, simpleCacheSize)

            val cacheFactory = CacheDataSource.Factory().apply {
                simpleCache?.let { setCache(it) }
                setUpstreamDataSourceFactory(onlineSource)
            }
            cacheFactory
        }

        val defaultMediaSourceFactory = if (dataSourceFactory != null) {
            DefaultMediaSourceFactory(dataSourceFactory, extractorFactor)
        } else {
            DefaultMediaSourceFactory(context, extractorFactor)
        }

        val videoMediaSource = if (mediaItemSlices.size == 1) {
            val item = mediaItemSlices.first()

            item.drm?.let { drm ->
                when (drm.uuid) {
                    CLEARKEY_DRM_UUID.toJavaUuid() -> {
                        val client = dataSourceFactory
                            ?: throw IllegalArgumentException("Must supply onlineSource")
                        val drmCallback =
                            LocalMediaDrmCallback("{\"keys\":[{\"kty\":\"${drm.kty}\",\"k\":\"${drm.key}\",\"kid\":\"${drm.kid}\"}],\"type\":\"temporary\"}".toByteArray())
                        val manager = DefaultDrmSessionManager.Builder()
                            .setPlayClearSamplesWithoutKeys(true)
                            .setMultiSession(false)
                            .setKeyRequestParameters(drm.keyRequestParameters)
                            .setUuidAndExoMediaDrmProvider(
                                drm.uuid,
                                FrameworkMediaDrm.DEFAULT_PROVIDER
                            )
                            .build(drmCallback)

                        DashMediaSource.Factory(client)
                            .setDrmSessionManagerProvider { manager }
                            .createMediaSource(item.mediaItem)
                    }

                    WIDEVINE_DRM_UUID.toJavaUuid(),
                    PLAYREADY_DRM_UUID.toJavaUuid() -> {
                        val client = dataSourceFactory
                            ?: throw IllegalArgumentException("Must supply onlineSource")
                        val drmCallback = HttpMediaDrmCallback(drm.licenseUrl, client)
                        val manager = DefaultDrmSessionManager.Builder()
                            .setPlayClearSamplesWithoutKeys(true)
                            .setMultiSession(true)
                            .setKeyRequestParameters(drm.keyRequestParameters)
                            .setUuidAndExoMediaDrmProvider(
                                drm.uuid,
                                FrameworkMediaDrm.DEFAULT_PROVIDER
                            )
                            .build(drmCallback)

                        DashMediaSource.Factory(client)
                            .setDrmSessionManagerProvider { manager }
                            .createMediaSource(item.mediaItem)
                    }

                    else -> {
                        Log.e(
                            TAG,
                            "DRM Metadata class is not supported: ${drm::class.simpleName}"
                        )
                        null
                    }
                }
            } ?: run {
                defaultMediaSourceFactory.createMediaSource(item.mediaItem)
            }
        } else {
            try {
                val source = ConcatenatingMediaSource2.Builder()
                mediaItemSlices.forEach { item ->
                    source.add(
                        ClippingMediaSource(
                            defaultMediaSourceFactory.createMediaSource(item.mediaItem),
                            item.durationUs
                        )
                    )
                }
                source.build()
            } catch (_: IllegalArgumentException) {
                val source =
                    ConcatenatingMediaSource()
                mediaItemSlices.forEach { item ->
                    source.addMediaSource(
                        ClippingMediaSource(
                            defaultMediaSourceFactory.createMediaSource(item.mediaItem),
                            item.durationUs
                        )
                    )
                }
                source
            }
        }
        return exoPlayerBuilder.build().apply {
            setPlayWhenReady(playWhenReady)
            seekTo(currentWindow, playbackPosition)
            val allSources = listOf(videoMediaSource) + subSources + audioSources
            setMediaSource(
                MergingMediaSource(*allSources.toTypedArray()),
                playbackPosition
            )
            setHandleAudioBecomingNoisy(true)
            setPlaybackSpeed(playBackSpeed)
            this.addAnalyticsListener(tracksAnalyticsListener)
        }
    }

    private fun loadExo(
        context: Context,
        mediaSlices: List<MediaItemSlice>,
        subSources: List<SingleSampleMediaSource>,
        audioSources: List<MediaSource> = emptyList(),
        onlineSource: HttpDataSource.Factory? = null,
    ) {
        Log.i(TAG, "loadExo")
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
        val maxVideoHeight = settingsManager.getInt(
            context.getString(if (context.isUsingMobileData()) R.string.quality_pref_mobile_data_key else R.string.quality_pref_key),
            Int.MAX_VALUE
        )

        try {
            hasUsedFirstRender = false

            exoPlayer = buildExoPlayer(
                context,
                mediaSlices,
                subSources,
                currentWindow,
                playbackPosition,
                playBackSpeed,
                cacheSize = cacheSize,
                videoBufferMs = videoBufferMs,
                playWhenReady = isPlaying,
                subtitleOffset = currentSubtitleOffset,
                maxVideoHeight = maxVideoHeight,
                audioSources = audioSources,
                onlineSource = onlineSource,
            )

            event(PlayerAttachedEvent(exoPlayer))
            exoPlayer?.prepare()

            if (onlineSource == null && playbackPosition > (exoPlayer?.duration ?: 0L)) {
                exoPlayer?.addListener(object : Player.Listener {
                    private var seekApplied = false
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (seekApplied || playbackState != Player.STATE_READY) return
                        seekApplied = true
                        exoPlayer?.seekTo(currentWindow, playbackPosition)
                        exoPlayer?.removeListener(this)
                    }
                })
            }

            exoPlayer?.let { exo ->
                event(StatusEvent(CSPlayerLoading.IsBuffering, CSPlayerLoading.IsBuffering))
                isPlaying = exo.isPlaying
            }

            if (mediaSlices.isEmpty() && subSources.isEmpty()) {
                return
            }

            LiveHelper.registerPlayer(exoPlayer)

            exoPlayer?.addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    safe {
                        val textTracks = tracks.groups.filter { it.type == TRACK_TYPE_TEXT }

                        playerSelectedSubtitleTracks =
                            textTracks.map { group ->
                                group.getFormats().mapNotNull { (format, _) ->
                                    (format.id?.stripTrackId()
                                        ?: return@mapNotNull null) to group.isSelected
                                }
                            }.flatten()

                        val exoPlayerReportedTracks =
                            tracks.groups.filter { it.type == TRACK_TYPE_TEXT }.getFormats()
                                .mapNotNull { (format, _) ->
                                    if (format.id == null ||
                                        format.language == null ||
                                        format.language?.startsWith("-") == true
                                    ) return@mapNotNull null

                                    return@mapNotNull SubtitleData(
                                        fromTagToLanguageName(format.language)
                                            ?: format.language!!,
                                        format.label ?: "",
                                        format.id!!.stripTrackId(),
                                        SubtitleOrigin.EMBEDDED_IN_VIDEO,
                                        format.sampleMimeType ?: MimeTypes.APPLICATION_SUBRIP,
                                        emptyMap(),
                                        format.language,
                                    )
                                }

                        event(EmbeddedSubtitlesFetchedEvent(tracks = exoPlayerReportedTracks))
                        event(TracksChangedEvent())
                        event(SubtitlesUpdatedEvent())
                    }
                }

                @Suppress("OVERRIDE_DEPRECATION")
                override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                    exoPlayer?.let { exo ->
                        event(
                            StatusEvent(
                                wasPlaying = if (isPlaying) CSPlayerLoading.IsPlaying else CSPlayerLoading.IsPaused,
                                isPlaying =
                                    when (playbackState) {
                                        Player.STATE_ENDED -> CSPlayerLoading.IsEnded
                                        Player.STATE_BUFFERING -> CSPlayerLoading.IsBuffering
                                        else -> if (exo.isPlaying) CSPlayerLoading.IsPlaying else CSPlayerLoading.IsPaused
                                    }
                            )
                        )
                        isPlaying = exo.isPlaying
                    }

                    when (playbackState) {
                        Player.STATE_READY -> {
                            onRenderFirst()
                        }

                        else -> {}
                    }


                    if (playWhenReady) {
                        when (playbackState) {
                            Player.STATE_READY -> {

                            }

                            Player.STATE_ENDED -> {
                                event(VideoEndedEvent())
                            }

                            Player.STATE_BUFFERING -> {
                                updatedTime(source = PlayerEventSource.Player)
                            }

                            Player.STATE_IDLE -> {

                            }

                            else -> Unit
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    when {
                        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                                && exoPlayer?.duration != TIME_UNSET -> {
                            exoPlayer?.prepare()
                        }

                        error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                            exoPlayer?.seekToDefaultPosition()
                            exoPlayer?.prepare()
                        }

                        error.cause is HlsPlaylistTracker.PlaylistStuckException -> {
                            val position = exoPlayer?.currentPosition ?: exoPlayer?.duration ?: 0

                            val aheadOfLive = LiveHelper.getLiveManager(exoPlayer)?.getTimeAheadOfLive(position) ?: 0

                            if (aheadOfLive > 100) {
                                exoPlayer?.seekTo(position - aheadOfLive)
                            } else {
                                exoPlayer?.seekToDefaultPosition()
                            }
                            exoPlayer?.prepare()
                        }


                        else -> {
                            event(ErrorEvent(error))
                        }
                    }

                    super.onPlayerError(error)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    super.onIsPlayingChanged(isPlaying)
                    if (isPlaying) {
                        event(RequestAudioFocusEvent())
                        onRenderFirst()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    super.onPlaybackStateChanged(playbackState)
                    when (playbackState) {
                        Player.STATE_READY -> {

                        }

                        Player.STATE_ENDED -> {
                            if (PreferenceManager.getDefaultSharedPreferences(context)
                                    ?.getBoolean(
                                        context.getString(R.string.autoplay_next_key),
                                        true
                                    ) == true
                            ) {
                                handleEvent(
                                    CSPlayerEvent.NextEpisode,
                                    source = PlayerEventSource.Player
                                )
                            }
                        }

                        Player.STATE_BUFFERING -> {
                            updatedTime(source = PlayerEventSource.Player)
                        }

                        Player.STATE_IDLE -> {
                        }

                        else -> Unit
                    }
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    super.onVideoSizeChanged(videoSize)
                    event(ResizedEvent(height = videoSize.height, width = videoSize.width))
                }

                override fun onRenderedFirstFrame() {
                    super.onRenderedFirstFrame()
                    onRenderFirst()
                    updatedTime(source = PlayerEventSource.Player)
                }
            }.also { playerListener = it })
        } catch (t: Throwable) {
            Log.e(TAG, "loadExo error", t)
            event(ErrorEvent(t))
        }
    }

    private var lastTimeStamps: List<VideoSkipStamp> = emptyList()

    override fun addTimeStamps(timeStamps: List<VideoSkipStamp>) {
        lastTimeStamps = timeStamps
        timeStamps.forEach { timestamp ->
            exoPlayer?.createMessage { _, _ ->
                updatedTime(source = PlayerEventSource.Player)
            }
                ?.setLooper(Looper.getMainLooper())
                ?.setPosition(timestamp.timestamp.startMs)
                ?.setDeleteAfterDelivery(false)
                ?.send()
        }
        updatedTime(source = PlayerEventSource.Player)
    }

    fun onRenderFirst() {
        if (hasUsedFirstRender) {
            return
        }
        Log.i(TAG, "Rendered first frame")
        hasUsedFirstRender = true

        setPreferredSubtitles(currentSubtitles)
        val format = exoPlayer?.videoFormat
        val width = format?.width
        val height = format?.height
        if (height != null && width != null) {
            event(ResizedEvent(width = width, height = height))
            updatedTime()
            exoPlayer?.apply {
                requestedListeningPercentages?.forEach { percentage ->
                    createMessage { _, _ ->
                        updatedTime()
                    }
                        .setLooper(Looper.getMainLooper())
                        .setPosition(contentDuration * percentage / 100)
                        .setDeleteAfterDelivery(false)
                        .send()
                }
            }
        }
    }

    private fun loadOfflinePlayer(context: Context, data: ExtractorUri) {
        Log.i(TAG, "loadOfflinePlayer")
        try {
            currentDownloadedFile = data

            val mediaItem = getMediaItem(MimeTypes.VIDEO_MP4, data.uri)
            val offlineSourceFactory = context.createOfflineSource()

            val (subSources, activeSubtitles) = getSubSources(
                offlineSourceFactory = offlineSourceFactory,
                subHelper = subtitleHelper,
                interceptor = null,
            )

            subtitleHelper.setActiveSubtitles(activeSubtitles.toSet())
            loadExo(context, listOf(MediaItemSlice(mediaItem, Long.MIN_VALUE)), subSources)
        } catch (t: Throwable) {
            Log.e(TAG, "loadOfflinePlayer error", t)
            event(ErrorEvent(t))
        }
    }

    private fun getSubSources(
        offlineSourceFactory: DataSource.Factory?,
        subHelper: PlayerSubtitleHelper,
        interceptor: Interceptor?,
    ): Pair<List<SingleSampleMediaSource>, List<SubtitleData>> {
        val activeSubtitles = ArrayList<SubtitleData>()
        val subSources = subHelper.getAllSubtitles().mapNotNull { sub ->
            val subConfig = MediaItem.SubtitleConfiguration.Builder(sub.getFixedUrl().toUri())
                .setMimeType(sub.mimeType)
                .setLanguage("_${sub.name}")
                .setId(sub.getId())
                .setSelectionFlags(0)
                .build()
            when (sub.origin) {
                SubtitleOrigin.DOWNLOADED_FILE, SubtitleOrigin.EMBEDDED_IN_VIDEO -> {
                    if (offlineSourceFactory != null) {
                        activeSubtitles.add(sub)
                        SingleSampleMediaSource.Factory(offlineSourceFactory)
                            .createMediaSource(subConfig, TIME_UNSET)
                    } else {
                        null
                    }
                }

                SubtitleOrigin.URL -> {
                    val dataSourceFactory = createOnlineSource(sub.headers, interceptor)
                    activeSubtitles.add(sub)
                    SingleSampleMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(subConfig, TIME_UNSET)
                }
            }
        }
        return Pair(subSources, activeSubtitles)
    }

    private fun getAudioSources(
        audioTracks: List<AudioFile>,
        interceptor: Interceptor?,
    ): List<MediaSource> {
        return audioTracks.mapNotNull { audio ->
            try {
                val mediaItem = getMediaItem(MimeTypes.AUDIO_UNKNOWN, audio.url)
                val dataSourceFactory = createOnlineSource(audio.headers, interceptor)
                DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create audio source for ${audio.url}: ${e.message}")
                null
            }
        }
    }

    override fun isActive(): Boolean {
        return exoPlayer != null
    }

    @MainThread
    private fun loadTorrent(context: Context, link: ExtractorLink) {
        ioSafe {
            try {
                if (exoPlayer == null) return@ioSafe
                val (newLink, status) = Torrent.transformLink(link)
                val hash = status.hash
                if (exoPlayer == null) return@ioSafe
                runOnMainThread {
                    if (exoPlayer == null) return@runOnMainThread
                    releasePlayer()
                    if (hash != null) {
                        torrentEventLooper(hash)
                    }
                    loadOnlinePlayer(context, newLink)
                }
            } catch (t: Throwable) {
                event(ErrorEvent(t))
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    @MainThread
    private fun loadOnlinePlayer(context: Context, link: ExtractorLink, retry: Boolean = false) {
        Log.i(TAG, "loadOnlinePlayer $link")
        try {
            val mime = when (link.type) {
                ExtractorLinkType.M3U8 -> MimeTypes.APPLICATION_M3U8
                ExtractorLinkType.DASH -> MimeTypes.APPLICATION_MPD
                ExtractorLinkType.VIDEO -> MimeTypes.VIDEO_MP4
                ExtractorLinkType.TORRENT, ExtractorLinkType.MAGNET -> {
                    val default = TvType.entries.toTypedArray()
                        .sorted()
                        .filter { it != TvType.NSFW }
                        .map { it.ordinal }

                    val defaultSet = default.map { it.toString() }.toSet()
                    val currentPrefMedia = try {
                        PreferenceManager.getDefaultSharedPreferences(context)
                            .getStringSet(
                                context.getString(R.string.prefer_media_type_key),
                                defaultSet
                            )
                            ?.mapNotNull { it.toIntOrNull() ?: return@mapNotNull null }
                    } catch (_: Throwable) {
                        null
                    } ?: default

                    if (!currentPrefMedia.contains(TvType.Torrent.ordinal)) {
                        val errorMessage = context.getString(R.string.torrent_preferred_media)
                        event(ErrorEvent(ErrorLoadingException(errorMessage)))
                        return
                    }

                    if (Torrent.hasAcceptedTorrentForThisSession == false) {
                        val errorMessage = context.getString(R.string.torrent_not_accepted)
                        event(ErrorEvent(ErrorLoadingException(errorMessage)))
                        return
                    }
                    if (!retry) {
                        releasePlayer()
                        loadExo(context, listOf(), listOf())
                    }
                    event(
                        StatusEvent(
                            wasPlaying = CSPlayerLoading.IsPlaying,
                            isPlaying = CSPlayerLoading.IsBuffering
                        )
                    )

                    if (Torrent.hasAcceptedTorrentForThisSession == true) {
                        loadTorrent(context, link)
                        return
                    }

                    val builder: AlertDialog.Builder = AlertDialog.Builder(context)

                    val dialogClickListener =
                        DialogInterface.OnClickListener { _, which ->
                            when (which) {
                                DialogInterface.BUTTON_POSITIVE -> {
                                    Torrent.hasAcceptedTorrentForThisSession = true
                                    loadTorrent(context, link)
                                }

                                DialogInterface.BUTTON_NEGATIVE -> {
                                    Torrent.hasAcceptedTorrentForThisSession = false
                                    val errorMessage =
                                        context.getString(R.string.torrent_not_accepted)
                                    event(ErrorEvent(ErrorLoadingException(errorMessage)))
                                }
                            }
                        }

                    builder.setTitle(R.string.play_torrent_button)
                        .setMessage(R.string.torrent_info)
                        .setCancelable(false).setOnCancelListener {
                            val errorMessage = context.getString(R.string.torrent_not_accepted)
                            event(ErrorEvent(ErrorLoadingException(errorMessage)))
                        }
                        .setPositiveButton(R.string.ok, dialogClickListener)
                        .setNegativeButton(R.string.go_back, dialogClickListener)
                        .show().setDefaultFocus()

                    return
                }
            }

            currentLink = link

            if (ignoreSSL) {
                val sslContext: SSLContext = SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf(SSLTrustManager()), SecureRandom())
                sslContext.createSSLEngine()
                HttpsURLConnection.setDefaultHostnameVerifier { _: String, _: SSLSession ->
                    true
                }
                HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            }


            val mediaItems = when (link) {
                is ExtractorLinkPlayList -> link.playlist.map {
                    MediaItemSlice(getMediaItem(mime, it.url), it.durationUs)
                }

                is DrmExtractorLink -> {
                    listOf(
                        MediaItemSlice(
                            getMediaItem(mime, link.url), Long.MIN_VALUE,
                            drm = DrmMetadata(
                                kid = link.kid,
                                key = link.key,
                                uuid = link.uuid.toJavaUuid(),
                                kty = link.kty,
                                licenseUrl = link.licenseUrl,
                                keyRequestParameters = link.keyRequestParameters,
                            )
                        )
                    )
                }

                else -> listOf(
                    MediaItemSlice(getMediaItem(mime, link.url), Long.MIN_VALUE)
                )
            }

            if (playbackPosition == 0L && (link.type == ExtractorLinkType.M3U8 || link.type == ExtractorLinkType.DASH)) {
                playbackPosition = TIME_UNSET
            }

            val provider = getApiFromNameNull(link.source)
            val interceptor: Interceptor? = provider?.getVideoInterceptor(link)

            val onlineSourceFactory =
                createVideoSource(
                    link = link,
                    engine = tryCreateEngine(context, simpleCacheSize),
                    interceptor = interceptor
                )

            val offlineSourceFactory = context.createOfflineSource()

            val (subSources, activeSubtitles) = getSubSources(
                offlineSourceFactory = offlineSourceFactory,
                subHelper = subtitleHelper,
                interceptor = interceptor,
            )

            val audioSources = getAudioSources(
                audioTracks = link.audioTracks,
                interceptor = interceptor,
            )

            subtitleHelper.setActiveSubtitles(activeSubtitles.toSet())

            loadExo(
                context = context,
                mediaSlices = mediaItems,
                subSources = subSources,
                audioSources = audioSources,
                onlineSource = onlineSourceFactory
            )
        } catch (t: Throwable) {
            Log.e(TAG, "loadOnlinePlayer error", t)
            event(ErrorEvent(t))
        }
    }

    override fun reloadPlayer(context: Context) {
        Log.i(TAG, "reloadPlayer")

        releasePlayer(false)
        currentLink?.let {
            loadOnlinePlayer(context, it)
        } ?: currentDownloadedFile?.let {
            loadOfflinePlayer(context, it)
        }
    }

    private val tracksAnalyticsListener = object : AnalyticsListener {

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            event(TracksChangedEvent())
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            event(TracksChangedEvent())
        }

        override fun onVideoDisabled(
            eventTime: AnalyticsListener.EventTime,
            decoderCounters: DecoderCounters
        ) {
            event(TracksChangedEvent())
        }

        override fun onAudioDisabled(
            eventTime: AnalyticsListener.EventTime,
            decoderCounters: DecoderCounters
        ) {
            event(TracksChangedEvent())
        }
    }

}
