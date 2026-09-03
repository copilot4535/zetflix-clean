package com.lagradost.cloudstream3.ui.music

import android.content.ComponentName
import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentMusicPlayerBinding
import com.lagradost.cloudstream3.services.music.MusicService
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe

import com.lagradost.cloudstream3.utils.UIHelper.navigate

class MusicPlayerFragment : BaseFragment<FragmentMusicPlayerBinding>(
    BindingCreator.Inflate(FragmentMusicPlayerBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    override fun fixLayout(view: View) {}

    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        
        setupUI()
        setupController()
        observeViewModel()
    }

    private fun setupUI() {
        binding?.musicPlayerBack?.setOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }
        
        binding?.musicPlayerView?.let { playerView ->
            playerView.findViewById<View>(R.id.music_player_lyrics)?.setOnClickListener {
                activity?.navigate(R.id.action_navigation_music_to_navigation_lyrics)
            }
            
            playerView.findViewById<View>(R.id.music_player_shuffle)?.setOnClickListener {
                mediaController?.shuffleModeEnabled = !(mediaController?.shuffleModeEnabled ?: false)
            }
            
            playerView.findViewById<View>(R.id.music_player_repeat)?.setOnClickListener {
                val currentMode = mediaController?.repeatMode ?: androidx.media3.common.Player.REPEAT_MODE_OFF
                mediaController?.repeatMode = if (currentMode == androidx.media3.common.Player.REPEAT_MODE_OFF) 
                    androidx.media3.common.Player.REPEAT_MODE_ONE else androidx.media3.common.Player.REPEAT_MODE_OFF
            }
        }
    }

    private fun setupController() {
        val context = context ?: return
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
            binding?.musicPlayerView?.player = mediaController
        }, MoreExecutors.directExecutor())
    }

    private fun observeViewModel() {
        viewModel.currentPlayingSong.observe(viewLifecycleOwner) { song ->
            if (song != null) {
                binding?.musicPlayerTitle?.text = song.title
                binding?.musicPlayerArtist?.text = song.artist ?: "Unknown Artist"
                binding?.musicPlayerAlbumArt?.loadImage(song.thumbnailUrl)
            }
        }
    }

    override fun onDestroyView() {
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        mediaController = null
        super.onDestroyView()
    }
}
