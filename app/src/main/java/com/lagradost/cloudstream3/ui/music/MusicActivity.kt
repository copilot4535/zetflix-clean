package com.lagradost.cloudstream3.ui.music

import android.content.res.ColorStateList
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.ui.setupWithNavController
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.view.updateLayoutParams
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.insecureApp
import com.lagradost.cloudstream3.UnsafeSSL
import com.lagradost.cloudstream3.network.initClient
import com.lagradost.cloudstream3.databinding.ActivityMusicBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.services.music.MusicService
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper
import com.lagradost.cloudstream3.utils.UIHelper.enableEdgeToEdgeCompat
import com.lagradost.cloudstream3.utils.UIHelper.navigate

import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.media3.common.util.UnstableApi

import coil3.asDrawable
import coil3.imageLoader
import coil3.request.crossfade
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.lagradost.cloudstream3.utils.drawableToBitmap

@UnstableApi
class MusicActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_OPEN_TAB = "extra_open_tab"
    }

    private lateinit var binding: ActivityMusicBinding
    private lateinit var viewModel: MusicViewModel
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var miniPlayerAnimator: android.animation.ValueAnimator? = null

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
        CommonActivity.init(this)

        super.onCreate(savedInstanceState)
        enableEdgeToEdgeCompat()
        binding = ActivityMusicBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MusicViewModel::class.java]

        setupPreloader()
        setupNavigation()
        setupGlobalMiniPlayer()
        setupController()
        observeViewModel()
        handler.post(progressRunnable)
        
        setupBackHandler()
        setupInsets()

        binding.btnReturnToMovies.setOnClickListener {
            returnToMain()
        }

        // Trigger background initialization
        viewModel.initMusic()
    }

    private fun setupPreloader() {
        viewModel.isInitialized.observe(this) { isReady ->
            if (isReady) {
                showMainContent()
            }
        }

        // 3-second safety timeout
        handler.postDelayed({
            if (viewModel.isInitialized.value != true) {
                Log.w("MusicActivity", "Initialization timed out, showing UI anyway.")
                showMainContent()
            }
        }, 3000)
    }

    private var isTransitioning = false
    private fun showMainContent() {
        if (isTransitioning || (binding.musicContentLayout.isVisible && binding.musicContentLayout.alpha == 1f)) return
        isTransitioning = true

        binding.musicContentLayout.isVisible = true
        binding.musicContentLayout.animate()
            .alpha(1f)
            .setDuration(300)
            .withEndAction {
                binding.musicPreloaderLayout.isVisible = false
                isTransitioning = false
                
                // Ensure bottom nav is shown correctly based on current destination
                val navHostFragment = supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                val destinationId = navHostFragment?.navController?.currentDestination?.id
                
                // Show nav for all destinations except immersive ones (player/lyrics)
                val isImmersive = destinationId == R.id.navigation_music_player || 
                                 destinationId == R.id.navigation_lyrics
                
                toggleBottomNav(!isImmersive)
            }
            .start()
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val navHostFragment = supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                val navController = navHostFragment?.navController
                
                // If we are at the start destination, move task to back (don't return to MainActivity)
                if (navController?.currentDestination?.id == navController?.graph?.startDestinationId) {
                    moveTaskToBack(true)
                } else {
                    // Otherwise let the NavController handle it
                    if (navController?.popBackStack() != true) {
                        moveTaskToBack(true)
                    }
                }
            }
        })
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.musicContentLayout) { v, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            val destinationId = navHostFragment?.navController?.currentDestination?.id
            
            // Check if we are in an immersive screen (like the player)
            val isImmersive = destinationId == R.id.navigation_music_player || 
                             destinationId == R.id.navigation_lyrics
            
            val topInset = maxOf(systemBars.top, displayCutout.top)
            val bottomInset = systemBars.bottom
            
            // Pad only top for non-immersive screens
            v.updatePadding(
                top = if (isImmersive) 0 else topInset,
                bottom = 0
            )

            // Apply bottom inset to the bottom navigation container as margin
            // Base margin is 8dp (compact floating look)
            val baseBottomMargin = (8 * resources.displayMetrics.density).toInt()
            val targetBottomMargin = if (isImmersive) 0 else (bottomInset + baseBottomMargin)
            
            val lp = binding.musicBottomNavContainer.layoutParams as? android.view.ViewGroup.MarginLayoutParams
            if (lp?.bottomMargin != targetBottomMargin) {
                binding.musicBottomNavContainer.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
                    bottomMargin = targetBottomMargin
                }
            }
            
            // Ensure the BottomNavigationView itself DOES NOT add internal padding for insets
            // This prevents the "Double Inset" gap inside the pill
            binding.musicBottomNav.updatePadding(bottom = 0)
            
            windowInsets
        }

        // Also explicitly disable internal inset handling on the nav view itself
        ViewCompat.setOnApplyWindowInsetsListener(binding.musicBottomNav) { _, insets ->
            insets // Return insets without consuming or applying them
        }
    }

    private fun returnToMain() {
        // 1. Resource Teardown
        try {
            mediaController?.let {
                it.release()
            }
            controllerFuture?.let {
                MediaController.releaseFuture(it)
            }
            mediaController = null
            controllerFuture = null
        } catch (e: Exception) {
            Log.e("MusicActivity", "Error releasing media controller", e)
        }

        // 2. Clear Coil memory cache
        this.imageLoader.memoryCache?.clear()

        // 3. Cancel active music coroutines
        lifecycleScope.coroutineContext.cancelChildren()

        // 4. Navigation
        val intent = Intent(this, com.lagradost.cloudstream3.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        binding.musicBottomNav.apply {
            setupWithNavController(navController)
            // Use the color selector created in res/color/music_bottom_nav_icon_color.xml
            itemIconTintList = ContextCompat.getColorStateList(context, R.color.music_bottom_nav_icon_color)
            itemTextColor = ContextCompat.getColorStateList(context, R.color.music_bottom_nav_icon_color)
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isHome = destination.id == R.id.music_nav_home
            binding.btnReturnToMovies.isVisible = isHome

            // Show bottom navigation for all primary music screens, hide only for full-screen experiences
            val isImmersive = destination.id == R.id.navigation_music_player || 
                             destination.id == R.id.navigation_lyrics
            
            toggleBottomNav(!isImmersive)
            updateMiniPlayerVisibility()
            
            // Re-apply insets when destination changes to handle immersive/non-immersive transitions
            ViewCompat.requestApplyInsets(binding.musicContentLayout)
        }

        val openTab = intent.getStringExtra(EXTRA_OPEN_TAB)
        if (openTab == "library") {
            binding.musicBottomNav.selectedItemId = R.id.music_nav_library
        } else if (openTab == "search") {
            binding.musicBottomNav.selectedItemId = R.id.music_nav_search
        }
    }

    private fun toggleBottomNav(show: Boolean) {
        val navContainer = binding.musicBottomNavContainer
        val navHeight = if (navContainer.height > 0) 
            navContainer.height.toFloat() 
        else 
            80 * resources.displayMetrics.density // Updated fallback for 52dp + margin
            
        val targetAlpha = if (show) 1f else 0f
        // Ensure it moves completely off screen
        val targetTranslationY = if (show) 0f else (navHeight + 300f)
        
        // Use a small epsilon for float comparison to avoid redundant animations
        if (navContainer.isVisible == show && 
            Math.abs(navContainer.alpha - targetAlpha) < 0.01f &&
            Math.abs(navContainer.translationY - targetTranslationY) < 1f) return
        
        if (show) {
            navContainer.isVisible = true
            navContainer.animate().cancel()
            navContainer.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        } else {
            navContainer.animate().cancel()
            navContainer.animate()
                .translationY(navHeight + 300f)
                .alpha(0f)
                .setDuration(300)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    navContainer.isVisible = false
                }
                .start()
        }
    }

    fun getMediaControllerMedia3(): MediaController? = mediaController

    private fun setupGlobalMiniPlayer() {
        val gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                openFullPlayer()
                return true
            }

            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                if (Math.abs(diffX) > 100 && Math.abs(velocityX) > 100) {
                    if (diffX > 0) {
                        mediaController?.seekToPrevious()
                    } else {
                        mediaController?.seekToNext()
                    }
                    return true
                }
                return false
            }
        })

        // Root click listener for the mini player
        binding.globalMiniPlayer.musicMiniPlayer.setOnClickListener {
            openFullPlayer()
        }

        binding.globalMiniPlayer.musicMiniPlayer.setOnTouchListener { v, event ->
            if (gestureDetector.onTouchEvent(event)) return@setOnTouchListener true
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                v.performClick()
            }
            true
        }

        binding.globalMiniPlayer.musicMiniPlayPause.setOnClickListener {
            mediaController?.let {
                if (it.isPlaying) it.pause() else it.play()
            }
        }
        
        binding.globalMiniPlayer.musicMiniLike.setOnClickListener {
            viewModel.currentPlayingSong.value?.let { song ->
                viewModel.toggleLikeSong(song)
            }
        }
    }

    private fun openFullPlayer() {
        val extras = FragmentNavigatorExtras(
            binding.globalMiniPlayer.musicMiniThumbnail to "album_art"
        )
        this@MusicActivity.navigate(R.id.global_to_navigation_music_player, extras = extras)
    }

    private fun setupController() {
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                mediaController?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updatePlayPauseIcon(isPlaying)
                        viewModel.updatePlaybackState(isPlaying)
                    }

                    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                        updateMiniPlayerMetadata(mediaMetadata)
                    }
                    
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        updateMiniPlayerVisibility()
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        mediaItem?.mediaMetadata?.let { updateMiniPlayerMetadata(it) }
                        updateMiniPlayerVisibility()
                        viewModel.updateCurrentSong(mediaItem)
                        mediaController?.let { viewModel.updatePlaybackState(it.isPlaying) }
                    }
                })
                // Initial state sync
                mediaController?.let {
                    updatePlayPauseIcon(it.isPlaying)
                    it.currentMediaItem?.mediaMetadata?.let { metadata -> updateMiniPlayerMetadata(metadata) }
                    updateMiniPlayerVisibility()
                    viewModel.updateCurrentSong(it.currentMediaItem)
                    viewModel.updatePlaybackState(it.isPlaying)
                    viewModel.reconcileWithPlayer(it)
                }
            } catch (e: Exception) {
                Log.e("MusicActivity", "Error getting media controller", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun updateMiniPlayerVisibility() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val destinationId = navHostFragment?.navController?.currentDestination?.id
        val isPlayer = destinationId == R.id.navigation_music_player
        val isLyrics = destinationId == R.id.navigation_lyrics
        val isFullScreen = isPlayer || isLyrics
        
        val hasMedia = mediaController?.currentMediaItem != null
        val isIdle = mediaController?.playbackState == Player.STATE_IDLE
        val isBuffering = mediaController?.playbackState == Player.STATE_BUFFERING
        
        val shouldShow = !isFullScreen && hasMedia && !isIdle
        
        // Update buffering state
        binding.globalMiniPlayer.musicMiniLoading.isVisible = isBuffering
        binding.globalMiniPlayer.musicMiniPlayPause.isVisible = !isBuffering
        
        if (shouldShow) {
            if (!binding.globalMiniPlayer.musicMiniPlayer.isVisible || binding.globalMiniPlayer.musicMiniPlayer.alpha < 1f) {
                binding.globalMiniPlayer.musicMiniPlayer.isVisible = true
                binding.globalMiniPlayer.musicMiniPlayer.animate()
                    .alpha(1f)
                    .setDuration(400)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }
        } else if (isFullScreen) {
            // Smoothly hide mini player if entering full player
            if (isPlayer && binding.globalMiniPlayer.musicMiniPlayer.isVisible) {
                binding.globalMiniPlayer.musicMiniPlayer.animate()
                    .alpha(0f)
                    .setDuration(400)
                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                    .withEndAction {
                        binding.globalMiniPlayer.musicMiniPlayer.isVisible = false
                    }
                    .start()
            } else {
                binding.globalMiniPlayer.musicMiniPlayer.isVisible = false
                binding.globalMiniPlayer.musicMiniPlayer.alpha = 0f
            }
        } else {
            binding.globalMiniPlayer.musicMiniPlayer.isVisible = false
            binding.globalMiniPlayer.musicMiniPlayer.alpha = 0f
        }
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        val icon = if (isPlaying) R.drawable.ic_baseline_pause_24 else R.drawable.ic_baseline_play_arrow_24
        binding.globalMiniPlayer.musicMiniPlayPause.setImageResource(icon)
    }

    private var currentMiniPlayerColor: Int = 0xFF121212.toInt()

    private fun updateMiniPlayerMetadata(metadata: MediaMetadata) {
        val mediaId = mediaController?.currentMediaItem?.mediaId
        
        // Coordinated update: Load image first, then update everything together
        binding.globalMiniPlayer.musicMiniThumbnail.loadImage(metadata.artworkUri?.toString()) {
            // Enable crossfade for smooth transition
            crossfade(400)
            
            listener(onSuccess = { _, result ->
                val drawable = result.image.asDrawable(resources)
                val bitmap = drawableToBitmap(drawable)
                
                lifecycleScope.launch {
                    val palette = if (bitmap != null) MusicColorHelper.getPalette(mediaId, bitmap) else null
                    
                    // Update text and theme together
                    binding.globalMiniPlayer.musicMiniTitle.text = metadata.title ?: "Unknown Title"
                    binding.globalMiniPlayer.musicMiniTitle.isSelected = true
                    binding.globalMiniPlayer.musicMiniArtist.text = metadata.artist ?: "Unknown Artist"
                    
                    palette?.let { applyMiniPlayerTheming(it) }
                }
            }, onError = { _, _ ->
                // Fallback if image fails
                binding.globalMiniPlayer.musicMiniTitle.text = metadata.title ?: "Unknown Title"
                binding.globalMiniPlayer.musicMiniArtist.text = metadata.artist ?: "Unknown Artist"
            })
        }
    }

    private fun applyMiniPlayerTheming(palette: MusicPalette) {
        val targetColor = MusicColorHelper.generateSpotifyMiniPlayerBackground(palette)
        val accentColor = MusicColorHelper.getVibrantAccent(palette)
        
        miniPlayerAnimator?.cancel()
        miniPlayerAnimator = MusicColorHelper.animateColorChange(currentMiniPlayerColor, targetColor) { color ->
            binding.globalMiniPlayer.musicMiniPlayer.setCardBackgroundColor(color)
            currentMiniPlayerColor = color
        }
        
        // Update progress bar and icons - use pure white/gray for icons as per spec
        binding.globalMiniPlayer.musicMiniProgress.progressDrawable.setTint(accentColor)
        binding.globalMiniPlayer.musicMiniLoading.setIndicatorColor(accentColor)
        
        // Controls remain neutral white
        binding.globalMiniPlayer.musicMiniPlayPause.imageTintList = ColorStateList.valueOf(android.graphics.Color.WHITE)
        
        updateLikeIcon(accentColor)
    }

    private fun updateLikeIcon(accentColor: Int? = null) {
        val currentSong = viewModel.currentPlayingSong.value
        val isLiked = viewModel.likedSongs.value?.any { it.videoId == currentSong?.videoId } == true
        
        binding.globalMiniPlayer.musicMiniLike.setImageResource(
            if (isLiked) R.drawable.ic_baseline_favorite_24 else R.drawable.ic_baseline_favorite_border_24
        )
        
        val tint = if (isLiked) {
            accentColor ?: ContextCompat.getColor(this, R.color.zetflix_accent)
        } else {
            android.graphics.Color.WHITE
        }
        binding.globalMiniPlayer.musicMiniLike.setColorFilter(tint)
    }

    private fun observeViewModel() {
        viewModel.likedSongs.observe(this) {
            updateLikeIcon()
        }
        
        viewModel.queueReady.observe(this) { event ->
            val content = event.peekContent()
            val (resource, requestId) = content
            if (requestId == viewModel.currentQueueRequestId) {
                event.getContentIfNotHandled()?.let {
                    if (resource is Resource.Success) {
                        val (queue, index) = resource.value
                        startMusicQueueService(queue, index)
                    }
                }
            }
        }

        viewModel.queueUpdate.observe(this) { event ->
            val content = event.peekContent()
            val (resource, requestId) = content
            if (requestId == viewModel.currentQueueRequestId) {
                event.getContentIfNotHandled()?.let {
                    if (resource is Resource.Success) {
                        val (queue, index) = resource.value
                        startMusicQueueService(queue, index, updateOnly = true)
                    }
                }
            }
        }

        viewModel.streamUrl.observe(this) { resource ->
            if (resource is Resource.Success) {
                val (url, song) = resource.value
                startMusicService(url, song)
            }
        }
    }

    private fun startMusicQueueService(queue: List<Pair<MusicSearchResponse, String>>, index: Int, updateOnly: Boolean = false) {
        val intent = Intent(this, MusicService::class.java).apply {
            action = if (updateOnly) MusicService.ACTION_UPDATE_QUEUE else MusicService.ACTION_PLAY_QUEUE
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
