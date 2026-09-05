package com.lagradost.cloudstream3.ui.music

import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.common.util.concurrent.ListenableFuture
import com.lagradost.cloudstream3.databinding.FragmentLyricsBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.services.music.MusicService
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import coil3.asDrawable

class LyricsFragment : BaseFragment<FragmentLyricsBinding>(
    BindingCreator.Inflate(FragmentLyricsBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateLyricsHighlight()
            handler.postDelayed(this, 500)
        }
    }

    override fun fixLayout(view: View) {}

    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        
        val isChild = arguments?.getBoolean(MusicCombinedBottomSheetFragment.ARG_IS_CHILD) ?: false
        binding?.lyricsHeader?.isVisible = !isChild
        
        if (isChild) {
            binding?.lyricsBackgroundBlur?.isVisible = false
            binding?.lyricsBackgroundOverlay?.setBackgroundColor(0xCC000000.toInt()) // More opaque in sheet
        }
        
        setupUI()
        setupMediaController()
        observeViewModel()
    }

    private fun setupUI() {
        binding?.lyricsClose?.setOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }

        binding?.let { b ->
            com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding(b.lyricsHeader, padBottom = false)
            com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding(b.lyricsSyncedView, padTop = false)
            com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding(b.lyricsPlainScroll, padTop = false)
        }
    }

    private fun setupMediaController() {
        val context = context ?: return
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
            handler.post(updateRunnable)
        }, { it.run() })
    }

    private fun observeViewModel() {
        observe(viewModel.currentPlayingSong) { song ->
            binding?.lyricsTitle?.text = song?.title
            binding?.lyricsBackgroundBlur?.loadImage(song?.thumbnailUrl) {
                listener(onSuccess = { _, result ->
                    val drawable = result.image.asDrawable(resources)
                    val bitmap = com.lagradost.cloudstream3.utils.drawableToBitmap(drawable)
                    if (bitmap != null) {
                        lifecycleScope.launch {
                            val palette = MusicColorHelper.getPalette(song?.videoId, bitmap)
                            applyDynamicBackground(palette)
                        }
                    }
                })
            }
        }

        observe(viewModel.lyrics) { resource ->
            binding?.lyricsLoading?.isVisible = resource is Resource.Loading
            binding?.lyricsEmpty?.isVisible = resource is Resource.Failure
            
            when (resource) {
                is Resource.Success -> {
                    val data = resource.value
                    if (!data.syncedLyrics.isNullOrBlank()) {
                        val lines = LrcParser.parse(data.syncedLyrics)
                        binding?.lyricsSyncedView?.setLyrics(lines)
                        binding?.lyricsSyncedView?.isVisible = true
                        binding?.lyricsPlainScroll?.isVisible = false
                    } else if (!data.plainLyrics.isNullOrBlank()) {
                        binding?.lyricsPlainText?.text = data.plainLyrics
                        binding?.lyricsSyncedView?.isVisible = false
                        binding?.lyricsPlainScroll?.isVisible = true
                    } else {
                        binding?.lyricsEmpty?.isVisible = true
                    }
                }
                else -> {}
            }
        }
    }

    private var currentGradientColors = intArrayOf(0xFF000000.toInt(), 0xFF000000.toInt())

    private fun applyDynamicBackground(palette: MusicPalette) {
        val baseColor = if (palette.darkMutedColor != 0xFF1A1A1A.toInt()) {
            palette.darkMutedColor
        } else if (palette.darkVibrantColor != 0xFF1A1A1A.toInt()) {
            palette.darkVibrantColor
        } else {
            palette.dominantColor
        }

        val color1 = MusicColorHelper.darkenColor(baseColor, 0.8f)
        val color2 = MusicColorHelper.darkenColor(palette.vibrantColor, 0.4f)
        val targetColors = intArrayOf(color1, color2, 0xFF000000.toInt())

        MusicColorHelper.animateGradientChange(
            binding?.lyricsBackgroundGradient,
            currentGradientColors,
            targetColors
        )
        currentGradientColors = targetColors
    }

    private fun updateLyricsHighlight() {
        val controller = mediaController ?: return
        if (!controller.isPlaying) return
        
        binding?.lyricsSyncedView?.updateProgress(controller.currentPosition)
    }

    fun forceLyricsScroll() {
        binding?.lyricsSyncedView?.let { syncedView ->
            val adapter = syncedView.adapter as? LyricsLineAdapter
            val index = adapter?.currentLineIndex ?: -1
            if (index != -1) {
                syncedView.scrollToPositionCentered(index)
            }
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacks(updateRunnable)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroyView()
    }
}
