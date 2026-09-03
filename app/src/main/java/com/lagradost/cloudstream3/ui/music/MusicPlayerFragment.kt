package com.lagradost.cloudstream3.ui.music

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.common.util.UnstableApi
import androidx.palette.graphics.Palette
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
import com.lagradost.cloudstream3.utils.drawableToBitmap
import kotlin.math.abs

import androidx.appcompat.app.AlertDialog
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIconsAndNoStringRes

@UnstableApi
class MusicPlayerFragment : BaseFragment<FragmentMusicPlayerBinding>(
    BindingCreator.Inflate(FragmentMusicPlayerBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var relatedAdapter: MusicSearchAdapter
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var isLiked = false

    override fun fixLayout(view: View) {}

    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        
        setupUI()
        setupGestures()
        setupController()
        observeViewModel()

        binding?.musicPlayerView?.apply {
            showController()
            controllerAutoShow = true
            controllerHideOnTouch = false
            controllerShowTimeoutMs = 0
        }
    }

    private fun setupUI() {
        binding?.musicPlayerBack?.setOnClickListener {
            activity?.onBackPressed()
        }

        relatedAdapter = MusicSearchAdapter({ index ->
            viewModel.playQueue(relatedAdapter.currentList, index)
        }, { _, _ -> })

        binding?.musicPlayerRelatedRecycler?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = relatedAdapter
        }

        binding?.musicPlayerMore?.setOnClickListener { view ->
            val options = listOf(0 to "Sleep Timer", 1 to "Share")
            view.popupMenuNoIconsAndNoStringRes(options) {
                when (itemId) {
                    0 -> showSleepTimerDialog()
                    1 -> shareCurrentSong()
                }
            }
        }
        
        binding?.musicPlayerView?.let { playerView ->
            playerView.findViewById<View>(R.id.music_player_lyrics)?.setOnClickListener {
                activity?.navigate(R.id.global_to_navigation_lyrics)
            }
            
            playerView.findViewById<View>(R.id.music_player_shuffle)?.setOnClickListener {
                mediaController?.let {
                    it.shuffleModeEnabled = !it.shuffleModeEnabled
                    updateShuffleIcon(it.shuffleModeEnabled)
                }
            }
            
            playerView.findViewById<View>(R.id.music_player_repeat)?.setOnClickListener {
                mediaController?.let {
                    val nextMode = when (it.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                    it.repeatMode = nextMode
                    updateRepeatIcon(nextMode)
                }
            }

            playerView.findViewById<View>(R.id.music_player_like)?.setOnClickListener {
                viewModel.currentPlayingSong.value?.let { song ->
                    viewModel.toggleLikeSong(song)
                }
            }

            playerView.findViewById<View>(R.id.music_player_share)?.setOnClickListener {
                shareCurrentSong()
            }

            playerView.findViewById<View>(R.id.music_player_queue)?.setOnClickListener {
                activity?.navigate(R.id.global_to_navigation_music_queue)
            }

            playerView.findViewById<View>(R.id.music_player_download)?.setOnClickListener {
                viewModel.currentPlayingSong.value?.let { song ->
                    if (MusicPersistence.getDownloadedSongs().any { it.videoId == song.videoId }) {
                        viewModel.removeDownload(song.videoId)
                    } else {
                        viewModel.downloadSong(song)
                    }
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                val swipeThreshold = 100
                val swipeVelocityThreshold = 100

                if (abs(diffX) > abs(diffY)) {
                    // Horizontal swipe
                    if (abs(diffX) > swipeThreshold && abs(velocityX) > swipeVelocityThreshold) {
                        if (diffX > 0) {
                            // Right swipe
                            mediaController?.seekToPreviousMediaItem()
                        } else {
                            // Left swipe
                            mediaController?.seekToNextMediaItem()
                        }
                        return true
                    }
                } else {
                    // Vertical swipe
                    if (abs(diffY) > swipeThreshold && abs(velocityY) > swipeVelocityThreshold) {
                        if (diffY < 0) {
                            // Up swipe
                            activity?.navigate(R.id.global_to_navigation_lyrics)
                        } else {
                            // Down swipe
                            activity?.onBackPressed()
                        }
                        return true
                    }
                }
                return false
            }
        })

        binding?.musicPlayerAlbumArtCard?.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun showSleepTimerDialog() {
        val options = arrayOf("Off", "15 minutes", "30 minutes", "60 minutes", "Custom")
        AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
            .setTitle("Sleep Timer")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel.startSleepTimer(0)
                    1 -> viewModel.startSleepTimer(15)
                    2 -> viewModel.startSleepTimer(30)
                    3 -> viewModel.startSleepTimer(60)
                    4 -> showCustomSleepTimerDialog()
                }
            }
            .show()
    }

    private fun showCustomSleepTimerDialog() {
        // Implement custom if needed, for now just presets
    }

    private fun setupController() {
        val context = context ?: return
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
            binding?.musicPlayerView?.player = mediaController
            mediaController?.let {
                updateShuffleIcon(it.shuffleModeEnabled)
                updateRepeatIcon(it.repeatMode)
                it.addListener(playerListener)
            }
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            updateShuffleIcon(shuffleModeEnabled)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            updateRepeatIcon(repeatMode)
        }
    }

    private fun updateShuffleIcon(enabled: Boolean) {
        val button = binding?.musicPlayerView?.findViewById<ImageButton>(R.id.music_player_shuffle)
        button?.let {
            it.alpha = if (enabled) 1.0f else 0.6f
            it.drawable?.setTint(if (enabled) context?.getColor(R.color.zetflix_accent) ?: android.graphics.Color.RED else android.graphics.Color.WHITE)
        }
    }

    private fun updateRepeatIcon(mode: Int) {
        val button = binding?.musicPlayerView?.findViewById<ImageButton>(R.id.music_player_repeat)
        button?.let {
            it.alpha = if (mode != Player.REPEAT_MODE_OFF) 1.0f else 0.6f
            it.drawable?.setTint(if (mode != Player.REPEAT_MODE_OFF) context?.getColor(R.color.zetflix_accent) ?: android.graphics.Color.RED else android.graphics.Color.WHITE)
        }
    }

    private fun updateLikeIcon(liked: Boolean) {
        val button = binding?.musicPlayerView?.findViewById<ImageButton>(R.id.music_player_like)
        button?.let {
            it.setImageResource(if (liked) R.drawable.ic_baseline_favorite_24 else R.drawable.ic_baseline_favorite_border_24)
            it.drawable?.setTint(if (liked) context?.getColor(R.color.zetflix_accent) ?: android.graphics.Color.RED else android.graphics.Color.WHITE)
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
                binding?.musicPlayerAlbumArt?.loadImage(song.thumbnailUrl) {
                    listener(onSuccess = { _, result ->
                        val drawable = result.image.asDrawable(resources)
                        val bitmap = drawableToBitmap(drawable)
                        if (bitmap != null) {
                            Palette.from(bitmap).generate { palette ->
                                val color = palette?.getVibrantColor(android.graphics.Color.BLACK) ?: android.graphics.Color.BLACK
                                binding?.musicPlayerBackgroundGradient?.setBackgroundColor(color)
                            }
                        }
                    })
                }
                updateLikeIcon(MusicPersistence.isSongLiked(song.videoId))
                updateDownloadIcon(MusicPersistence.getDownloadedSongs().any { it.videoId == song.videoId })
                viewModel.loadRelatedSongs(song.videoId)
            }
        }

        viewModel.relatedSongs.observe(viewLifecycleOwner) { resource ->
            if (resource is Resource.Success) {
                relatedAdapter.submitList(resource.value)
            }
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
