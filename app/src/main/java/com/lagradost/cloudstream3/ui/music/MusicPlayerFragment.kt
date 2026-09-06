package com.lagradost.cloudstream3.ui.music

import android.widget.Toast
import android.view.GestureDetector
import android.view.MotionEvent
import kotlin.math.abs
import android.util.Log
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collect
import com.lagradost.cloudstream3.services.music.MusicDownloadState
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.common.util.UnstableApi
import coil3.asDrawable
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentMusicPlayerBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.services.music.MusicService
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIconsAndNoStringRes
import com.lagradost.cloudstream3.utils.drawableToBitmap
import com.lagradost.cloudstream3.ui.music.MusicColorHelper
import com.lagradost.cloudstream3.ui.music.MusicPalette
import com.lagradost.cloudstream3.ui.music.MusicSearchResponse
import com.lagradost.cloudstream3.ui.music.MusicPersistence
import com.lagradost.cloudstream3.ui.music.RateStatus
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import androidx.appcompat.app.AlertDialog
import androidx.transition.Fade
import com.lagradost.cloudstream3.utils.UIHelper.getSharedElementTransition

import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

@UnstableApi
class MusicPlayerFragment : BaseFragment<FragmentMusicPlayerBinding>(
    BindingCreator.Inflate(FragmentMusicPlayerBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var isLiked = false
    private var lastThemedMediaId: String? = null
    private var currentGradientColors = intArrayOf(Color.BLACK, Color.BLACK, Color.BLACK)
    private var backgroundAnimator: android.animation.ValueAnimator? = null
    private var isScrubbing = false

    private var currentLyrics: List<LyricLine> = emptyList()
    private var currentLyricsPalette: LyricsPalette? = null
    private val lyricsHandler = Handler(Looper.getMainLooper())
    private val updateLyricsRunnable = object : Runnable {
        override fun run() {
            updateLyricsPreview()
            lyricsHandler.postDelayed(this, 500L)
        }
    }

    private fun updateLyricsPreview() {
        val controller = mediaController ?: return
        if (currentLyrics.isEmpty()) return

        val position = controller.currentPosition
        val currentIndex = currentLyrics.indexOfLast { it.timestampMs <= position }
        
        if (currentIndex != -1) {
            val builder = SpannableStringBuilder()
            val palette = currentLyricsPalette
            
            // Show active line and next 3 lines for a more immersive preview
            val maxLines = 4
            val endIdx = minOf(currentIndex + maxLines, currentLyrics.size)
            
            for (i in currentIndex until endIdx) {
                val line = currentLyrics[i]
                val start = builder.length
                builder.append(line.text)
                val end = builder.length
                
                if (palette != null) {
                    if (i == currentIndex) {
                        builder.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        builder.setSpan(ForegroundColorSpan(palette.foregroundPrimary), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    } else {
                        builder.setSpan(ForegroundColorSpan(palette.foregroundSecondary), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                
                if (i < endIdx - 1) builder.append("\n")
            }
            
            binding?.musicPlayerLyricsSnippet?.text = builder
        }
    }

    private val swipeGestureDetector by lazy {
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                mediaController?.let {
                    if (it.isPlaying) it.pause() else it.play()
                }
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x
                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            mediaController?.let { MusicPlayerHelper.handlePrevious(it) }
                        } else {
                            mediaController?.seekToNextMediaItem()
                        }
                        return true
                    }
                } else {
                    if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY > SWIPE_THRESHOLD && binding?.musicPlayerScrollView?.scrollY == 0) {
                            // Only minimize to Mini Player if we are NOT at the Lyrics layer.
                            // The activity handles the back stack correctly if popBackStack() is called.
                            activity?.onBackPressedDispatcher?.onBackPressed()
                            return true
                        } else if (diffY < -SWIPE_THRESHOLD) {
                            // Smooth scroll to lyrics if they are available
                            if (binding?.musicPlayerLyricsPreview?.isVisible == true) {
                                binding?.musicPlayerScrollView?.smoothScrollTo(0, binding?.musicPlayerLyricsPreview?.top ?: 0)
                            }
                        }
                    }
                }
                return false
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedElementEnterTransition = getSharedElementTransition()
        sharedElementReturnTransition = getSharedElementTransition()
        
        enterTransition = Fade().apply {
            duration = 300
        }
        returnTransition = Fade().apply {
            duration = 300
        }
    }

    override fun fixLayout(view: View) {}

    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        postponeEnterTransition()
        
        setupUI()
        setupController()
        setupScrubbing()
        observeViewModel()

        binding?.musicPlayerView?.apply {
            showController()
            controllerAutoShow = true
            controllerHideOnTouch = false
            controllerShowTimeoutMs = 0
        }

        // Handle insets for the player
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val insetTypes = WindowInsetsCompat.Type.systemBars() or 
                            WindowInsetsCompat.Type.displayCutout()
            val bars = insets.getInsets(insetTypes)
            
            binding?.musicPlayerTopBar?.updatePadding(top = bars.top)
            binding?.musicPlayerView?.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        if (mediaController?.isPlaying == true && currentLyrics.isNotEmpty()) {
            lyricsHandler.removeCallbacks(updateLyricsRunnable)
            lyricsHandler.post(updateLyricsRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        lyricsHandler.removeCallbacks(updateLyricsRunnable)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupUI() {
        binding?.musicPlayerBack?.setOnClickListener {
            activity?.onBackPressed()
        }

        binding?.musicPlayerView?.let { playerView ->
            val playPauseButton = playerView.findViewById<ImageButton>(R.id.exo_play_pause)
            playPauseButton?.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    }
                }
                false
            }
        }

        binding?.musicPlayerAlbumArtCard?.setOnTouchListener { _, event ->
            swipeGestureDetector.onTouchEvent(event)
            true
        }

        binding?.musicPlayerArtist?.setOnClickListener {
            viewModel.currentPlayingSong.value?.let { song ->
                val args = Bundle().apply {
                    putString("artist_name", song.artist)
                    putString("artist_id", song.artist)
                }
                activity?.navigate(R.id.music_nav_artist, args)
            }
        }

        binding?.musicPlayerMore?.setOnClickListener {
            activity?.navigate(R.id.navigation_track_options)
        }

        binding?.musicPlayerLike?.setOnClickListener {
            viewModel.currentPlayingSong.value?.let { song ->
                viewModel.toggleLikeSong(song)
            }
        }

        binding?.musicPlayerLyricsPreview?.setOnClickListener {
            openLyricsPanel()
        }
        
        binding?.musicPlayerView?.let { playerView ->
            playerView.findViewById<View>(R.id.music_player_lyrics)?.setOnClickListener {
                openLyricsPanel()
            }

            playerView.findViewById<View>(R.id.music_player_download)?.setOnClickListener {
                viewModel.currentPlayingSong.value?.let { song ->
                    viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val isDownloaded = MusicPersistence.getDownloadedSongs().any { it.videoId == song.videoId }
                        viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            if (isDownloaded) {
                                viewModel.removeDownload(song.videoId)
                            } else {
                                viewModel.downloadSong(song)
                            }
                        }
                    }
                }
            }

            playerView.findViewById<View>(R.id.music_player_queue)?.setOnClickListener {
                activity?.navigate(R.id.navigation_music_queue_sheet)
            }

            playerView.findViewById<View>(R.id.music_player_devices)?.setOnClickListener {
                Toast.makeText(context, "Device picker coming soon", Toast.LENGTH_SHORT).show()
            }

            playerView.findViewById<View>(R.id.music_player_share)?.setOnClickListener {
                shareCurrentSong()
            }
        }
    }

    private fun setupScrubbing() {
        binding?.musicPlayerView?.findViewById<androidx.media3.ui.DefaultTimeBar>(R.id.exo_progress)?.addListener(
            object : androidx.media3.ui.TimeBar.OnScrubListener {
                override fun onScrubStart(timeBar: androidx.media3.ui.TimeBar, position: Long) {
                    isScrubbing = true
                }

                override fun onScrubMove(timeBar: androidx.media3.ui.TimeBar, position: Long) {
                    updateScrubbingTime(position)
                }

                override fun onScrubStop(timeBar: androidx.media3.ui.TimeBar, position: Long, canceled: Boolean) {
                    isScrubbing = false
                    if (!canceled) {
                        mediaController?.seekTo(position)
                    }
                }
            }
        )
    }

    private fun updateScrubbingTime(position: Long) {
        val positionText = androidx.media3.common.util.Util.getStringForTime(StringBuilder(), java.util.Formatter(), position)
        binding?.musicPlayerView?.findViewById<TextView>(R.id.exo_position)?.text = positionText
    }

    private fun openLyricsPanel() {
        val state = viewModel.lyricsUiState.value
        val hasLyrics = state?.status == LyricsStatus.AVAILABLE

        if (hasLyrics) {
            // Spotify-style: Navigate to full immersive lyrics fragment
            activity?.navigate(R.id.navigation_lyrics)
        } else {
            Toast.makeText(context, "Lyrics not available for this song", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupController() {
        val context = context ?: return
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                binding?.musicPlayerView?.player = mediaController
                mediaController?.let {
                    updateShuffleIcon(it.shuffleModeEnabled)
                    updateRepeatIcon(it.repeatMode)
                    it.addListener(playerListener)
                    it.currentMediaItem?.mediaMetadata?.let { metadata ->
                        updateMetadata(metadata)
                    }
                    setupMediaListeners(it)
                }
            } catch (e: Exception) {
                Log.e("MusicPlayerFragment", "Failed to bind MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupMediaListeners(controller: MediaController) {
        binding?.musicPlayerView?.let { playerView ->
            playerView.findViewById<ImageButton>(R.id.music_player_shuffle)?.setOnClickListener {
                controller.shuffleModeEnabled = !controller.shuffleModeEnabled
            }

            playerView.findViewById<ImageButton>(R.id.music_player_repeat)?.setOnClickListener {
                val nextMode = when (controller.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                    else -> Player.REPEAT_MODE_OFF
                }
                controller.repeatMode = nextMode
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseAnimation(isPlaying)
            if (isPlaying) {
                lyricsHandler.removeCallbacks(updateLyricsRunnable)
                lyricsHandler.post(updateLyricsRunnable)
            } else {
                lyricsHandler.removeCallbacks(updateLyricsRunnable)
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            updateShuffleIcon(shuffleModeEnabled)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            updateRepeatIcon(repeatMode)
        }

        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
            updateMetadata(mediaMetadata)
        }
    }

    private fun updatePlayPauseAnimation(isPlaying: Boolean) {
        val playPauseButton = binding?.musicPlayerView?.findViewById<ImageButton>(R.id.exo_play_pause) ?: return
        
        val targetIcon = if (isPlaying) R.drawable.ic_baseline_pause_24 else R.drawable.ic_baseline_play_arrow_24
        
        // Premium crossfade morph transition
        playPauseButton.animate()
            .alpha(0.5f)
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(100)
            .withEndAction {
                playPauseButton.setImageResource(targetIcon)
                playPauseButton.animate()
                    .alpha(1f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun updateMetadata(mediaMetadata: androidx.media3.common.MediaMetadata) {
        val song = viewModel.currentPlayingSong.value
        val title = if (!mediaMetadata.title.isNullOrBlank()) mediaMetadata.title else song?.title
        val artist = if (!mediaMetadata.artist.isNullOrBlank()) mediaMetadata.artist else song?.artist

        binding?.musicPlayerTitle?.apply {
            text = title ?: "Unknown Title"
            isSelected = true
        }
        binding?.musicPlayerArtist?.text = artist ?: "Unknown Artist"
        
        val artworkUri = mediaMetadata.artworkUri?.toString()
        val rawUrl = if (!artworkUri.isNullOrBlank()) artworkUri else song?.thumbnailUrl
        val highResUrl = getHighResArtwork(rawUrl, song?.videoId) ?: rawUrl
        loadArtworkAndTheme(highResUrl, song?.videoId)
    }

    private fun getHighResArtwork(url: String?, videoId: String?): String? {
        if (url.isNullOrBlank()) return null
        // Strategy 1: Use maxresdefault for YouTube videos
        if (!videoId.isNullOrBlank() && (url.contains("ytimg.com") || url.contains("googleusercontent.com"))) {
            return "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
        }
        // Strategy 2: Upgrade InnerTube size parameters to 1080
        return url.replace(Regex("(=w|\\bw)[0-9]+(-h[0-9]+)?"), "$11080-h1080")
    }

    private fun loadArtworkAndTheme(url: String?, videoId: String?) {
        if (videoId != null && videoId == lastThemedMediaId) return
        lastThemedMediaId = videoId

        if (url.isNullOrBlank()) {
            startPostponedEnterTransition()
            return
        }
        
        // Use a flag to avoid multiple calls to startPostponedEnterTransition
        var transitionStarted = false
        fun safeStartTransition() {
            if (!transitionStarted) {
                transitionStarted = true
                startPostponedEnterTransition()
            }
        }

        binding?.musicPlayerAlbumArt?.loadImage(url) {
            listener(
                onSuccess = { _, result ->
                    val bitmap = drawableToBitmap(result.image.asDrawable(resources))
                    if (bitmap != null) {
                        viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                            val palette = MusicColorHelper.getPalette(videoId ?: "", bitmap)
                            viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                applyDynamicTheming(palette)
                            }
                        }
                    }
                    safeStartTransition()
                },
                onError = { _, _ ->
                    safeStartTransition()
                }
            )
        }
        
        // Safety timeout for transition postponement
        view?.postDelayed({
            safeStartTransition()
        }, 1000)
    }

    private fun updateShuffleIcon(enabled: Boolean) {
        val button = binding?.musicPlayerView?.findViewById<ImageButton>(R.id.music_player_shuffle)
        button?.let {
            it.alpha = if (enabled) 1.0f else 0.6f
            it.drawable?.setTint(if (enabled) context?.getColor(R.color.music_spotify_green) ?: Color.GREEN else Color.WHITE)
        }
    }

    private fun updateRepeatIcon(mode: Int) {
        val button = binding?.musicPlayerView?.findViewById<ImageButton>(R.id.music_player_repeat)
        button?.let {
            it.alpha = if (mode != Player.REPEAT_MODE_OFF) 1.0f else 0.6f
            it.drawable?.setTint(if (mode != Player.REPEAT_MODE_OFF) context?.getColor(R.color.music_spotify_green) ?: Color.GREEN else Color.WHITE)
        }
    }

    private fun updateLikeIcon(liked: Boolean) {
        binding?.musicPlayerLike?.let {
            it.setImageResource(if (liked) R.drawable.ic_baseline_favorite_24 else R.drawable.ic_baseline_favorite_border_24)
            it.drawable?.setTint(if (liked) context?.getColor(R.color.zetflix_accent) ?: Color.RED else Color.WHITE)
        }
    }

    private fun updateRateStatusIcons(status: RateStatus) {
        val accentColor = context?.getColor(R.color.zetflix_accent) ?: Color.RED
        val whiteColor = Color.WHITE

        binding?.musicPlayerLike?.let {
            it.setImageResource(if (status == RateStatus.LIKE) R.drawable.ic_baseline_favorite_24 else R.drawable.ic_baseline_favorite_border_24)
            it.imageTintList = ColorStateList.valueOf(if (status == RateStatus.LIKE) accentColor else whiteColor)
        }
    }

    private fun updateDownloadProgress(state: MusicDownloadState) {
        val button = binding?.musicPlayerView?.findViewById<ImageButton>(R.id.music_player_download)
        button?.let {
            when (state.state) {
                androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING -> {
                    it.setImageResource(R.drawable.download_icon_load)
                    it.alpha = 0.5f + (state.progress / 200f) // Simple visual progress
                }
                androidx.media3.exoplayer.offline.Download.STATE_COMPLETED -> {
                    updateDownloadIcon(true)
                }
                else -> {
                    it.setImageResource(R.drawable.netflix_download)
                    it.alpha = 1.0f
                }
            }
        }
    }

    private fun updateDownloadIcon(downloaded: Boolean) {
        val button = binding?.musicPlayerView?.findViewById<ImageButton>(R.id.music_player_download)
        button?.let {
            it.setImageResource(if (downloaded) R.drawable.download_icon_done else R.drawable.netflix_download)
            it.drawable?.setTint(if (downloaded) context?.getColor(R.color.zetflix_accent) ?: android.graphics.Color.RED else android.graphics.Color.WHITE)
        }
    }

    private fun shareCurrentSong() {
        val song = viewModel.currentPlayingSong.value ?: return
        val shareText = "Listening to ${song.title} by ${song.artist} on ZetFlix Music!\nhttps://www.youtube.com/watch?v=${song.videoId}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Share Song")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, "Share via"))
    }

    private fun observeViewModel() {
        viewModel.currentPlayingSong.observe(viewLifecycleOwner) { song ->
            if (song != null) {
                binding?.musicPlayerTitle?.text = song.title
                binding?.musicPlayerArtist?.text = song.artist ?: "Unknown Artist"
                
                val highResUrl = getHighResArtwork(song.thumbnailUrl, song.videoId) ?: song.thumbnailUrl
                loadArtworkAndTheme(highResUrl, song.videoId)

                viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val liked = MusicPersistence.isSongLiked(song.videoId)
                    val downloaded = MusicPersistence.getDownloadedSongs().any { it.videoId == song.videoId }
                    viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        updateLikeIcon(liked)
                        updateDownloadIcon(downloaded)
                    }
                }
                
                viewModel.updateRateStatus(song.videoId)
                viewModel.loadRelatedSongs(song.videoId)

                // Mock About the Song content
                binding?.musicPlayerAboutSongCard?.isVisible = true
                binding?.musicPlayerAboutSongContent?.text = "Discover the inspiration behind \"${song.title}\". This track marks a significant evolution in ${song.artist}'s sound, blending soulful melodies with modern production."

                // Try to get album if controller is available
                mediaController?.currentMediaItem?.mediaMetadata?.let { updateMetadata(it) }
            }
        }

        viewModel.relatedSongs.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Success -> {
                    val songs = resource.value
                    binding?.musicPlayerSongDnaCard?.isVisible = songs.isNotEmpty()
                    if (songs.isNotEmpty()) {
                        val itemAdapter = MusicHomeItemAdapter(MusicHomeAdapter.ItemViewType.NORMAL, { index ->
                            viewModel.loadStreamAndPlay(songs[index])
                        })
                        binding?.musicPlayerSongDnaList?.apply {
                            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
                            adapter = itemAdapter
                            isNestedScrollingEnabled = false
                        }
                        itemAdapter.submitList(songs.map { MusicHomeItem(it.title, it.artist, it.videoId, it.thumbnailUrl, MusicItemType.SONG) })
                    }
                }
                else -> {
                    binding?.musicPlayerSongDnaCard?.isVisible = false
                }
            }
        }

        viewModel.rateStatus.observe(viewLifecycleOwner) { status ->
            updateRateStatusIcons(status)
        }

        viewModel.likedSongs.observe(viewLifecycleOwner) { songs ->
            viewModel.currentPlayingSong.value?.let { song ->
                updateLikeIcon(songs.any { it.videoId == song.videoId })
            }
        }

        viewModel.downloadedSongs.observe(viewLifecycleOwner) { downloads ->
            viewModel.currentPlayingSong.value?.let { song ->
                updateDownloadIcon(downloads.any { it.videoId == song.videoId })
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.downloadStates.collect { states ->
                    viewModel.currentPlayingSong.value?.let { song ->
                        states[song.videoId]?.let { state ->
                            updateDownloadProgress(state)
                        } ?: run {
                            viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val isDownloaded = MusicPersistence.getDownloadedSongs().any { it.videoId == song.videoId }
                                viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                    updateDownloadIcon(isDownloaded)
                                }
                            }
                        }
                    }
                }
            }
        }

        viewModel.lyricsUiState.observe(viewLifecycleOwner) { state ->
            val status = state.status
            val lyrics = state.lyrics
            val hasLyrics = status == LyricsStatus.AVAILABLE && lyrics != null
            
            binding?.musicPlayerLyricsPreview?.isVisible = hasLyrics
            if (hasLyrics && lyrics != null) {
                if (!lyrics.syncedLyrics.isNullOrBlank()) {
                    currentLyrics = LrcParser.parse(lyrics.syncedLyrics)
                    if (currentLyrics.isNotEmpty()) {
                        lyricsHandler.removeCallbacks(updateLyricsRunnable)
                        lyricsHandler.post(updateLyricsRunnable)
                    }
                } else if (!lyrics.plainLyrics.isNullOrBlank()) {
                    currentLyrics = emptyList()
                    lyricsHandler.removeCallbacks(updateLyricsRunnable)
                    val snippet = lyrics.plainLyrics.lines().filter { it.isNotBlank() }.take(2).joinToString("\n")
                    binding?.musicPlayerLyricsSnippet?.text = snippet
                }
            } else {
                currentLyrics = emptyList()
                lyricsHandler.removeCallbacks(updateLyricsRunnable)
            }
        }

        viewModel.sleepTimerTimeLeft.observe(viewLifecycleOwner) { millis ->
            if (millis != null && millis > 0) {
                binding?.musicPlayerSleepTimer?.isVisible = true
                val minutes = millis / 1000 / 60
                val seconds = (millis / 1000) % 60
                binding?.musicPlayerSleepTimer?.text = String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
            } else {
                binding?.musicPlayerSleepTimer?.isVisible = false
            }
        }
    }

    private fun applyDynamicTheming(palette: MusicPalette) {
        val defaultSurface = 0xFF121212.toInt()
        val defaultAccent = context?.getColor(R.color.zetflix_accent) ?: Color.RED

        val dominant = if (palette.dominantColor != Color.BLACK && palette.dominantColor != 0xFF1A1A1A.toInt()) palette.dominantColor else defaultSurface
        val vibrant = if (palette.vibrantColor != Color.BLACK && palette.vibrantColor != 0xFFE50914.toInt()) palette.vibrantColor else defaultAccent

        // Content cards background
        val lyricsPalette = MusicColorHelper.generateLyricsPalette(palette)
        currentLyricsPalette = lyricsPalette
        val cardBg = lyricsPalette.background
        
        binding?.musicPlayerLyricsPreview?.setCardBackgroundColor(cardBg)
        binding?.musicPlayerAboutSongCard?.setCardBackgroundColor(cardBg)
        binding?.musicPlayerSongDnaCard?.setCardBackgroundColor(cardBg)
        
        updateLyricsPreview() // Refresh preview with new colors

        // Ensure player background gradient follows the specification (Vibrant -> Dominant -> DarkMuted/Black)
        val colorTop = MusicColorHelper.darkenColor(vibrant, 0.4f)
        val colorMid = MusicColorHelper.darkenColor(dominant, 0.2f)
        val colorBot = Color.BLACK

        val targetColors = intArrayOf(colorTop, colorMid, colorBot)
        MusicColorHelper.animateGradientChange(binding?.musicPlayerBackgroundGradient, currentGradientColors, targetColors)
        currentGradientColors = targetColors

        // Foreground/UI Colors based on luminance
        val isLight = MusicColorHelper.calculateLuminance(colorTop) > 0.6f
        val foregroundColor = if (isLight) Color.BLACK else Color.WHITE
        val secondaryForegroundColor = if (isLight) 0x99000000.toInt() else 0xB3FFFFFF.toInt()
        val foregroundTint = ColorStateList.valueOf(foregroundColor)

        binding?.let { b ->
            b.musicPlayerTitle.setTextColor(foregroundColor)
            b.musicPlayerArtist.setTextColor(secondaryForegroundColor)
            b.musicPlayerBack.imageTintList = foregroundTint
            b.musicPlayerMore.imageTintList = foregroundTint
            
            // Section labels
            b.musicPlayerLyricsLabel.setTextColor(secondaryForegroundColor)
            b.musicPlayerAboutSongLabel.setTextColor(secondaryForegroundColor)
            b.musicPlayerSongDnaLabel.setTextColor(secondaryForegroundColor)
            b.musicPlayerAboutSongContent.setTextColor(foregroundColor)
            
            // Player View controls
            val playerView = b.musicPlayerView
            playerView.findViewById<ImageButton>(R.id.exo_prev)?.imageTintList = foregroundTint
            playerView.findViewById<ImageButton>(R.id.exo_next)?.imageTintList = foregroundTint
            
            playerView.findViewById<ImageButton>(R.id.music_player_devices)?.imageTintList = foregroundTint
            playerView.findViewById<ImageButton>(R.id.music_player_lyrics)?.imageTintList = foregroundTint
            playerView.findViewById<ImageButton>(R.id.music_player_share)?.imageTintList = foregroundTint
            playerView.findViewById<ImageButton>(R.id.music_player_download)?.imageTintList = foregroundTint
            playerView.findViewById<ImageButton>(R.id.music_player_queue)?.imageTintList = foregroundTint
            
            // Handle Media3 TextViews safely
            context?.let { ctx ->
                val posId = ctx.resources.getIdentifier("exo_position", "id", ctx.packageName)
                val durId = ctx.resources.getIdentifier("exo_duration", "id", ctx.packageName)
                if (posId != 0) playerView.findViewById<TextView>(posId)?.setTextColor(secondaryForegroundColor)
                if (durId != 0) playerView.findViewById<TextView>(durId)?.setTextColor(secondaryForegroundColor)
            }

            // System bar icons
            activity?.let { act ->
                val window = act.window
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = isLight
                insetsController.isAppearanceLightNavigationBars = isLight
            }
            
            // Re-sync state-dependent buttons
            mediaController?.let {
                updateShuffleIcon(it.shuffleModeEnabled)
                updateRepeatIcon(it.repeatMode)
            }
            viewModel.currentPlayingSong.value?.let { song ->
                viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val liked = MusicPersistence.isSongLiked(song.videoId)
                    val downloaded = MusicPersistence.getDownloadedSongs().any { it.videoId == song.videoId }
                    viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        updateLikeIcon(liked)
                        updateDownloadIcon(downloaded)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        lyricsHandler.removeCallbacks(updateLyricsRunnable)
        mediaController?.removeListener(playerListener)
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        mediaController = null
        super.onDestroyView()
    }
}
