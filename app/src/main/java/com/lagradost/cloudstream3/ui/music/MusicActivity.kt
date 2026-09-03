package com.lagradost.cloudstream3.ui.music

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import com.lagradost.cloudstream3.mvvm.Resource
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

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            mediaController?.let {
                if (it.isPlaying && it.duration > 0) {
                    val progress = (it.currentPosition * 100 / it.duration).toInt()
                    binding.globalMiniPlayer.musicMiniProgress.progress = progress
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

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
        handler.post(progressRunnable)
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
                    // Sync with ViewModel for other observers
                    viewModel.currentPlayingSong.value?.let { current ->
                        if (current.title != mediaMetadata.title.toString()) {
                            // This is a bit tricky since we don't have the full MusicSearchResponse here
                            // But we can try to find it in the current queue if available
                        }
                    }
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
        binding.globalMiniPlayer.musicMiniTitle.isSelected = true
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

        viewModel.queueReady.observe(this) { resource ->
            if (resource is Resource.Success) {
                val (queue, index) = resource.value
                startMusicQueueService(queue, index)
            }
        }

        viewModel.streamUrl.observe(this) { resource ->
            if (resource is Resource.Success) {
                val (url, song) = resource.value
                startMusicService(url, song)
            }
        }
    }

    private fun startMusicQueueService(queue: List<Pair<MusicSearchResponse, String>>, index: Int) {
        val intent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_PLAY_QUEUE
            val urls = queue.map { it.second }
            val titles = queue.map { it.first.title }
            val artists = queue.map { it.first.artist ?: "" }
            val thumbnails = queue.map { it.first.thumbnailUrl ?: "" }
            val videoIds = queue.map { it.first.videoId }
            
            putStringArrayListExtra(MusicService.EXTRA_URLS, ArrayList(urls))
            putStringArrayListExtra(MusicService.EXTRA_TITLES, ArrayList(titles))
            putStringArrayListExtra(MusicService.EXTRA_ARTISTS, ArrayList(artists))
            putStringArrayListExtra(MusicService.EXTRA_THUMBNAILS, ArrayList(thumbnails))
            putStringArrayListExtra(MusicService.EXTRA_VIDEO_IDS, ArrayList(videoIds))
            putExtra(MusicService.EXTRA_START_INDEX, index)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun startMusicService(url: String, song: MusicSearchResponse) {
        val intent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_PLAY
            putExtra(MusicService.EXTRA_URL, url)
            putExtra(MusicService.EXTRA_TITLE, song.title)
            putExtra(MusicService.EXTRA_ARTIST, song.artist)
            putExtra(MusicService.EXTRA_THUMBNAIL, song.thumbnailUrl)
            putExtra(MusicService.EXTRA_VIDEO_ID, song.videoId)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressRunnable)
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        mediaController = null
        super.onDestroy()
    }
}
