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
        binding?.lyricsBackgroundBlur?.isVisible = !isChild
        
        setupUI()
        setupMediaController()
        observeViewModel()
    }

    private fun setupUI() {
        binding?.lyricsClose?.setOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
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
            binding?.lyricsBackgroundBlur?.loadImage(song?.thumbnailUrl)
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
