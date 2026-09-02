package com.lagradost.cloudstream3.ui.music

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentMusicBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.services.music.MusicService
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard

class MusicFragment : BaseFragment<FragmentMusicBinding>(
    BindingCreator.Inflate(FragmentMusicBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var musicAdapter: MusicSearchAdapter
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val handler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    private fun updateProgress() {
        mediaController?.let {
            if (it.isPlaying) {
                binding?.miniPlayerProgress?.max = it.duration.toInt()
                binding?.miniPlayerProgress?.progress = it.currentPosition.toInt()
            }
        }
    }

    override fun fixLayout(view: View) {
        // Implement fixLayout if needed
    }

    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        
        setupRecyclerView()
        setupSearch()
        setupController()
        observeViewModel()

        arguments?.getString("search_query")?.let { query ->
            binding?.musicSearchEditText?.setText(query)
            viewModel.search(query)
        }

        arguments?.getString("album_id")?.let { albumId ->
            viewModel.loadAlbumSongs(albumId)
        }

        arguments?.getString("playlist_id")?.let { playlistId ->
            viewModel.loadPlaylistSongs(playlistId)
        }
        
        binding?.musicMiniPlayer?.setOnClickListener {
            findNavController().navigate(R.id.action_music_search_to_music_player)
        }

        binding?.musicMiniPlayPause?.setOnClickListener {
            mediaController?.let {
                if (it.isPlaying) it.pause() else it.play()
            }
        }

        binding?.musicLyricsButton?.setOnClickListener {
            findNavController().navigate(R.id.action_music_search_to_music_lyrics)
        }

        handler.post(progressRunnable)
    }

    private fun setupRecyclerView() {
        musicAdapter = MusicSearchAdapter { song ->
            viewModel.loadStreamAndPlay(song)
        }
        binding?.musicRecyclerView?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = musicAdapter
        }
    }

    private fun setupSearch() {
        binding?.musicSearchEditText?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding?.musicSearchEditText?.text?.toString()
                if (!query.isNullOrBlank()) {
                    viewModel.search(query)
                    hideKeyboard()
                }
                true
            } else {
                false
            }
        }
    }

    private fun observeViewModel() {
        observe(viewModel.searchResult) { resource ->
            binding?.musicLoadingProgress?.isVisible = resource is Resource.Loading
            binding?.musicErrorText?.isVisible = resource is Resource.Failure
            binding?.musicRecyclerView?.isVisible = resource is Resource.Success

            when (resource) {
                is Resource.Success -> {
                    musicAdapter.submitList(resource.value)
                }
                is Resource.Failure -> {
                    binding?.musicErrorText?.text = resource.errorString
                }
                else -> {}
            }
        }

        observe(viewModel.streamUrl) { resource ->
            binding?.musicLoadingProgress?.isVisible = resource is Resource.Loading
            
            when (resource) {
                is Resource.Success -> {
                    val (url, song) = resource.value
                    startMusicService(url, song)
                    
                    binding?.musicMiniPlayer?.isVisible = true
                    binding?.musicNowPlayingText?.text = getString(R.string.playing_format, song.title)
                }
                is Resource.Failure -> {
                    Toast.makeText(context, "Error: ${resource.errorString}", Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }

    private fun startMusicService(url: String, song: MusicSearchResponse) {
        val intent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_PLAY
            putExtra(MusicService.EXTRA_URL, url)
            putExtra(MusicService.EXTRA_TITLE, song.title)
            putExtra(MusicService.EXTRA_ARTIST, song.artist)
            putExtra(MusicService.EXTRA_THUMBNAIL, song.thumbnailUrl)
        }
        context?.let {
            ContextCompat.startForegroundService(it, intent)
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

    private fun updateMiniPlayerControls() {
        mediaController?.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                binding?.musicMiniPlayPause?.setImageResource(
                    if (isPlaying) R.drawable.ic_baseline_pause_24 else R.drawable.ic_baseline_play_arrow_24
                )
            }
        })
    }

    override fun onDestroyView() {
        handler.removeCallbacks(progressRunnable)
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        mediaController = null
        super.onDestroyView()
    }
}
