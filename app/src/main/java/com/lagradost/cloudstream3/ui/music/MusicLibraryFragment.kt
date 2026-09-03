package com.lagradost.cloudstream3.ui.music

import android.content.ComponentName
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentMusicLibraryBinding
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.services.music.MusicService
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.navigate

class MusicLibraryFragment : BaseFragment<FragmentMusicLibraryBinding>(
    BindingCreator.Inflate(FragmentMusicLibraryBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    override fun fixLayout(view: View) {
        // Implement fixLayout if needed
    }

    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        
        setupController()
        observeViewModel()

        binding?.musicMiniPlayerInclude?.musicMiniPlayer?.setOnClickListener {
            activity?.navigate(R.id.action_navigation_music_to_navigation_music_player)
        }

        binding?.musicMiniPlayerInclude?.musicMiniPlayPause?.setOnClickListener {
            mediaController?.let {
                if (it.isPlaying) it.pause() else it.play()
            }
        }
    }

    private fun setupController() {
        val context = context ?: return
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
            updateMiniPlayerControls()
        }, MoreExecutors.directExecutor())
    }

    private fun observeViewModel() {
        observe(viewModel.currentPlayingSong) { song ->
            if (song != null) {
                binding?.musicMiniPlayerInclude?.musicMiniPlayer?.isVisible = true
                binding?.musicMiniPlayerInclude?.musicMiniTitle?.text = song.title
                binding?.musicMiniPlayerInclude?.musicMiniArtist?.text = song.artist
                binding?.musicMiniPlayerInclude?.musicMiniThumbnail?.loadImage(song.thumbnailUrl)
            }
        }
    }

    private fun updateMiniPlayerControls() {
        mediaController?.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                binding?.musicMiniPlayerInclude?.musicMiniPlayPause?.setImageResource(
                    if (isPlaying) R.drawable.ic_baseline_pause_24 else R.drawable.ic_baseline_play_arrow_24
                )
            }
        })
    }

    override fun onDestroyView() {
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        mediaController = null
        super.onDestroyView()
    }
}
