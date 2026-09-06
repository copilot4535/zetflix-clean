package com.lagradost.cloudstream3.ui.music

import android.annotation.SuppressLint
import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentLyricsBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.services.music.MusicService
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.drawableToBitmap
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import coil3.asDrawable
import android.content.res.ColorStateList
import coil3.imageLoader
import kotlin.math.abs
import androidx.navigation.fragment.findNavController

class LyricsFragment : BaseFragment<FragmentLyricsBinding>(
    BindingCreator.Inflate(FragmentLyricsBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var lyricsBackgroundAnimator: android.animation.ValueAnimator? = null
    private var isScrubbing = false
    
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
                
                // Only trigger swipe down if it's clearly a downward gesture
                if (abs(diffY) > abs(diffX)) {
                    if (diffY > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        // Check if the lyrics view is scrolled to the top to avoid accidental dismissal while scrolling lyrics
                        val syncedView = binding?.lyricsSyncedView
                        val plainScroll = binding?.lyricsPlainScroll
                        
                        val isAtTop = when {
                            syncedView?.isVisible == true -> {
                                val lm = syncedView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                                lm?.findFirstCompletelyVisibleItemPosition() == 0
                            }
                            plainScroll?.isVisible == true -> {
                                plainScroll.scrollY == 0
                            }
                            else -> true
                        }
                        
                        if (isAtTop) {
                            findNavController().popBackStack()
                            return true
                        }
                    }
                }
                return false
            }
        })
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updatePlaybackState()
            handler.postDelayed(this, 100)
        }
    }

    override fun fixLayout(view: View) {}

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        
        setupUI()
        setupMediaController()
        observeViewModel()

        binding?.root?.setOnTouchListener { _, event ->
            swipeGestureDetector.onTouchEvent(event)
        }
    }

    private fun setupUI() {
        binding?.lyricsClose?.setOnClickListener {
            findNavController().popBackStack()
        }

        binding?.lyricsPlayPause?.setOnClickListener {
            mediaController?.let {
                if (it.isPlaying) it.pause() else it.play()
            }
        }

        binding?.lyricsBackToCurrent?.setOnClickListener {
            forceLyricsScroll()
            binding?.lyricsBackToCurrent?.isVisible = false
        }

        binding?.lyricsExoProgress?.addListener(object : androidx.media3.ui.TimeBar.OnScrubListener {
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
        })

        binding?.let { b ->
            com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding(b.lyricsHeader, padBottom = false)
            com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding(b.lyricsBottomControls, padTop = false)
        }
    }

    private fun setupMediaController() {
        val context = context ?: return
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
            mediaController?.addListener(object : androidx.media3.common.Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updatePlayPauseIcon(isPlaying)
                }
            })
            updatePlayPauseIcon(mediaController?.isPlaying == true)
            handler.post(updateRunnable)
        }, MoreExecutors.directExecutor())
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        binding?.lyricsPlayPause?.setImageResource(
            if (isPlaying) R.drawable.ic_baseline_pause_24 else R.drawable.ic_baseline_play_arrow_24
        )
    }

    private fun updatePlaybackState() {
        val controller = mediaController ?: return
        if (isScrubbing) return

        val position = controller.currentPosition
        val duration = controller.duration
        
        binding?.lyricsExoProgress?.setPosition(position)
        binding?.lyricsExoProgress?.setDuration(duration)
        
        val posText = androidx.media3.common.util.Util.getStringForTime(StringBuilder(), java.util.Formatter(), position)
        val durText = androidx.media3.common.util.Util.getStringForTime(StringBuilder(), java.util.Formatter(), duration)
        
        binding?.lyricsExoPosition?.text = posText
        binding?.lyricsExoDuration?.text = durText

        binding?.lyricsSyncedView?.let { syncedView ->
            syncedView.updateProgress(position)
            binding?.lyricsBackToCurrent?.isVisible = !syncedView.autoScrollEnabled
        }
    }

    private fun updateScrubbingTime(position: Long) {
        val positionText = androidx.media3.common.util.Util.getStringForTime(StringBuilder(), java.util.Formatter(), position)
        binding?.lyricsExoPosition?.text = positionText
    }

    private fun observeViewModel() {
        observe(viewModel.currentPlayingSong) { song ->
            binding?.lyricsTitle?.text = song?.title
            binding?.lyricsArtist?.text = song?.artist ?: "Unknown Artist"
            binding?.lyricsThumbnail?.loadImage(song?.thumbnailUrl)
            
            song?.thumbnailUrl?.let { url ->
                val request = coil3.request.ImageRequest.Builder(requireContext())
                    .data(url)
                    .target(
                        onSuccess = { result ->
                            val bitmap = drawableToBitmap(result.asDrawable(resources))
                            if (bitmap != null) {
                                lifecycleScope.launch {
                                    val palette = MusicColorHelper.getPalette(song.videoId, bitmap)
                                    applyDynamicTheming(song.videoId, palette)
                                }
                            }
                        }
                    )
                    .build()
                requireContext().imageLoader.enqueue(request)
            }
        }

        observe(viewModel.lyricsUiState) { state ->
            val status = state.status
            val lyrics = state.lyrics
            
            binding?.lyricsLoading?.isVisible = status == LyricsStatus.LOADING
            
            // Mutually exclusive: If lyrics are available, "not found" MUST be hidden.
            binding?.lyricsEmpty?.isVisible = status == LyricsStatus.NOT_AVAILABLE || status == LyricsStatus.ERROR
            
            if (status == LyricsStatus.ERROR) {
                binding?.lyricsEmpty?.text = state.errorMessage ?: "Lyrics error"
            } else {
                binding?.lyricsEmpty?.text = "Lyrics not found"
            }
            
            when (status) {
                LyricsStatus.AVAILABLE -> {
                    if (lyrics != null) {
                        if (!lyrics.syncedLyrics.isNullOrBlank()) {
                            val lines = LrcParser.parse(lyrics.syncedLyrics)
                            binding?.lyricsSyncedView?.setLyrics(lines)
                            binding?.lyricsSyncedView?.isVisible = true
                            binding?.lyricsPlainScroll?.isVisible = false
                        } else if (!lyrics.plainLyrics.isNullOrBlank()) {
                            binding?.lyricsPlainText?.text = lyrics.plainLyrics
                            binding?.lyricsSyncedView?.isVisible = false
                            binding?.lyricsPlainScroll?.isVisible = true
                        }
                    }
                }
                else -> {
                    binding?.lyricsSyncedView?.isVisible = false
                    binding?.lyricsPlainScroll?.isVisible = false
                }
            }
        }
    }

    private var currentGradientColors = intArrayOf(0xFF000000.toInt(), 0xFF000000.toInt())

    private fun applyDynamicTheming(mediaId: String?, musicPalette: MusicPalette) {
        val palette = MusicColorHelper.generateLyricsPalette(mediaId, musicPalette)
        val lyricsBg = palette.background
        
        // Spotify-style: uniform dark field derived from artwork
        val targetColors = intArrayOf(lyricsBg, lyricsBg, Color.BLACK)

        lyricsBackgroundAnimator?.cancel()
        lyricsBackgroundAnimator = MusicColorHelper.animateGradientChange(
            binding?.lyricsBackgroundGradient,
            currentGradientColors,
            targetColors
        )
        currentGradientColors = targetColors

        // Contrast-aware foreground hierarchy
        val foregroundColor = palette.foregroundPrimary
        val secondaryColor = palette.foregroundSecondary
        val tint = ColorStateList.valueOf(foregroundColor)

        binding?.let { b ->
            b.lyricsTitle.setTextColor(foregroundColor)
            b.lyricsArtist.setTextColor(secondaryColor)
            b.lyricsClose.imageTintList = tint
            b.musicLyricsShare.imageTintList = tint
            b.musicLyricsMore.imageTintList = tint
            b.lyricsExoPosition.setTextColor(secondaryColor)
            b.lyricsExoDuration.setTextColor(secondaryColor)
            
            // Apply palette to synced lyrics view
            b.lyricsSyncedView.setPalette(palette)

            // Spotify-style "Back to current" button styling
            b.lyricsBackToCurrent.setTextColor(if (palette.isLight) Color.BLACK else Color.WHITE)
            b.lyricsBackToCurrent.backgroundTintList = ColorStateList.valueOf(palette.accent)

            // Theme the seek bar
            b.lyricsExoProgress.setScrubberColor(palette.accent)
            b.lyricsExoProgress.setPlayedColor(palette.accent)
            
            // System bar icons
            activity?.let { act ->
                val window = act.window
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = palette.isLight
                insetsController.isAppearanceLightNavigationBars = palette.isLight
            }
        }
    }

    fun forceLyricsScroll() {
        binding?.lyricsSyncedView?.resumeAutoScroll()
    }

    override fun onDestroyView() {
        handler.removeCallbacks(updateRunnable)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroyView()
    }
}
