package com.lagradost.cloudstream3.ui.music

import android.content.ComponentName
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.ActivityMusicBinding
import com.lagradost.cloudstream3.services.music.MusicService
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.enableEdgeToEdgeCompat
import com.lagradost.cloudstream3.utils.UIHelper.navigate

class MusicActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_OPEN_TAB = "extra_open_tab"
    }

    private lateinit var binding: ActivityMusicBinding
    private lateinit var viewModel: MusicViewModel
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeCompat()
        binding = ActivityMusicBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MusicViewModel::class.java]

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.musicBottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isFullScreen = destination.id == R.id.navigation_music_player || 
                               destination.id == R.id.navigation_lyrics
            binding.musicBottomNav.isVisible = !isFullScreen
            updateMiniPlayerVisibility()
        }

        val openTab = intent.getStringExtra(EXTRA_OPEN_TAB)
        if (openTab == "library") {
            binding.musicBottomNav.selectedItemId = R.id.music_nav_library
        } else if (openTab == "search") {
            binding.musicBottomNav.selectedItemId = R.id.music_nav_search
        }

        setupGlobalMiniPlayer()
        setupController()
        observeViewModel()
    }

    private fun setupGlobalMiniPlayer() {
        binding.globalMiniPlayer.musicMiniPlayer.setOnClickListener {
            this.navigate(R.id.global_to_navigation_music_player)
        }

        binding.globalMiniPlayer.musicMiniPlayPause.setOnClickListener {
            mediaController?.let {
                if (it.isPlaying) it.pause() else it.play()
            }
        }
    }

    private fun setupController() {
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
            mediaController?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updatePlayPauseIcon(isPlaying)
                }

                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    updateMiniPlayerMetadata(mediaMetadata)
                }
                
                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateMiniPlayerVisibility()
                }
            })
            // Initial state
            mediaController?.let {
                updatePlayPauseIcon(it.isPlaying)
                it.currentMediaItem?.mediaMetadata?.let { metadata -> updateMiniPlayerMetadata(metadata) }
                updateMiniPlayerVisibility()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun updateMiniPlayerVisibility() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val destinationId = navHostFragment?.navController?.currentDestination?.id
        val isFullScreen = destinationId == R.id.navigation_music_player || 
                           destinationId == R.id.navigation_lyrics
        
        binding.globalMiniPlayer.musicMiniPlayer.isVisible = !isFullScreen && 
            mediaController?.playbackState != Player.STATE_IDLE && 
            mediaController?.currentMediaItem != null
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        binding.globalMiniPlayer.musicMiniPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_baseline_pause_24 else R.drawable.ic_baseline_play_arrow_24
        )
    }

    private fun updateMiniPlayerMetadata(metadata: MediaMetadata) {
        binding.globalMiniPlayer.musicMiniTitle.text = metadata.title
        binding.globalMiniPlayer.musicMiniArtist.text = metadata.artist
        binding.globalMiniPlayer.musicMiniThumbnail.loadImage(metadata.artworkUri?.toString())
    }

    private fun observeViewModel() {
        viewModel.currentPlayingSong.observe(this) { song ->
            if (song != null) {
                binding.globalMiniPlayer.musicMiniPlayer.isVisible = true
                binding.globalMiniPlayer.musicMiniTitle.text = song.title
                binding.globalMiniPlayer.musicMiniArtist.text = song.artist
                binding.globalMiniPlayer.musicMiniThumbnail.loadImage(song.thumbnailUrl)
            }
        }
    }

    override fun onDestroy() {
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        mediaController = null
        super.onDestroy()
    }
}
