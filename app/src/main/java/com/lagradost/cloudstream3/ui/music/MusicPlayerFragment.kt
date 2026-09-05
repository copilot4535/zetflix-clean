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
import android.view.View
import android.widget.ImageButton
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

    private val swipeGestureDetector by lazy {
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

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
                            mediaController?.seekToPreviousMediaItem()
                        } else {
                            mediaController?.seekToNextMediaItem()
                        }
                        return true
                    }
                } else {
                    if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY > 0) {
                            activity?.onBackPressed()
                        } else {
                            val args = Bundle().apply {
                                putInt(MusicCombinedBottomSheetFragment.ARG_INITIAL_TAB, MusicCombinedBottomSheetFragment.TAB_LYRICS)
                            }
                            activity?.navigate(R.id.navigation_music_combined_panel, args)
                        }
                        return true
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

    @SuppressLint("ClickableViewAccessibility")
    private fun setupUI() {
        binding?.musicPlayerBack?.setOnClickListener {
            activity?.onBackPressed()
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

        binding?.musicPlayerMore?.setOnClickListener { view ->
            viewModel.currentPlayingSong.value?.let { song ->
                val options = listOf(
                    0 to "Sleep Timer",
                    1 to getString(R.string.music_show_credits),
                    2 to getString(R.string.music_start_radio),
                    3 to getString(R.string.result_share)
                )
                view.popupMenuNoIconsAndNoStringRes(options) {
                    when (itemId) {
                        0 -> showSleepTimerDialog()
                        1 -> activity?.navigate(R.id.navigation_song_credits)
                        2 -> viewModel.startRadio(song.videoId)
                        3 -> shareCurrentSong()
                    }
                }
            }
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
                val args = Bundle().apply { 
                    putInt(MusicCombinedBottomSheetFragment.ARG_INITIAL_TAB, MusicCombinedBottomSheetFragment.TAB_QUEUE) 
                }
                activity?.navigate(R.id.navigation_music_combined_panel, args)
            }
        }
    }

    private fun openLyricsPanel() {
        val lyrics = viewModel.lyrics.value
        val hasLyrics = when (lyrics) {
            is Resource.Success -> !lyrics.value.plainLyrics.isNullOrBlank() || !lyrics.value.syncedLyrics.isNullOrBlank()
            else -> false
        }

        if (hasLyrics) {
            val args = Bundle().apply {
                putInt(MusicCombinedBottomSheetFragment.ARG_INITIAL_TAB, MusicCombinedBottomSheetFragment.TAB_LYRICS)
            }
            activity?.navigate(R.id.navigation_music_combined_panel, args)
        } else {
            Toast.makeText(context, "Lyrics not available for this song", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSleepTimerDialog() {
        activity?.navigate(R.id.navigation_sleep_timer)
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

    private fun updateMetadata(metadata: androidx.media3.common.MediaMetadata) {
        val song = viewModel.currentPlayingSong.value
        val title = if (!metadata.title.isNullOrBlank()) metadata.title else song?.title
        val artist = if (!metadata.artist.isNullOrBlank()) metadata.artist else song?.artist

        binding?.musicPlayerTitle?.apply {
            text = title ?: "Unknown Title"
            isSelected = true
        }
        binding?.musicPlayerArtist?.text = artist ?: "Unknown Artist"
        
        val artworkUri = metadata.artworkUri?.toString()
        val url = if (!artworkUri.isNullOrBlank()) artworkUri else song?.thumbnailUrl
        loadArtworkAndTheme(url, song?.videoId)
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
                
                loadArtworkAndTheme(song.thumbnailUrl, song.videoId)

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

                // Try to get album if controller is available
                mediaController?.currentMediaItem?.mediaMetadata?.let { updateMetadata(it) }
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

        viewModel.lyrics.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Success -> {
                    val plain = resource.value.plainLyrics
                    val synced = resource.value.syncedLyrics
                    val hasLyrics = !plain.isNullOrBlank() || !synced.isNullOrBlank()
                    
                    binding?.musicPlayerLyricsPreview?.isVisible = hasLyrics
                    if (hasLyrics) {
                        val snippet = if (!plain.isNullOrBlank()) {
                            plain.lines().filter { it.isNotBlank() }.take(2).joinToString("\n")
                        } else {
                            // Extract snippet from synced lyrics if plain is not available
                            synced?.lines()?.filter { it.contains("]") }?.take(2)
                                ?.joinToString("\n") { it.substringAfter("]").trim() }
                        }
                        binding?.musicPlayerLyricsSnippet?.text = snippet
                    }
                }
                else -> {
                    binding?.musicPlayerLyricsPreview?.isVisible = false
                }
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
        val darkMuted = if (palette.darkMutedColor != Color.BLACK) palette.darkMutedColor else Color.BLACK

        val targetColors = intArrayOf(vibrant, dominant, darkMuted)
        MusicColorHelper.animateGradientChange(binding?.musicPlayerBackgroundGradient, currentGradientColors, targetColors)
        currentGradientColors = targetColors

        val buttonTint = ColorStateList.valueOf(vibrant)

        binding?.let { b ->
            b.musicPlayerView.findViewById<ImageButton>(R.id.music_player_shuffle)?.imageTintList = buttonTint
            b.musicPlayerView.findViewById<ImageButton>(R.id.music_player_repeat)?.imageTintList = buttonTint
            b.musicPlayerView.findViewById<ImageButton>(R.id.music_player_like)?.imageTintList = buttonTint
            b.musicPlayerView.findViewById<ImageButton>(R.id.music_player_share)?.imageTintList = buttonTint
            b.musicPlayerView.findViewById<ImageButton>(R.id.music_player_queue)?.imageTintList = buttonTint
            b.musicPlayerView.findViewById<ImageButton>(R.id.music_player_download)?.imageTintList = buttonTint
        }
    }

    override fun onDestroyView() {
        mediaController?.removeListener(playerListener)
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        mediaController = null
        super.onDestroyView()
    }
}
