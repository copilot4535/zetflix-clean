package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentMusicQueueBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.services.music.MusicService
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.common.Player
import android.content.ComponentName
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@androidx.media3.common.util.UnstableApi
class MusicQueueFragment : BaseFragment<FragmentMusicQueueBinding>(
    BindingCreator.Inflate(FragmentMusicQueueBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var queueAdapter: MusicSearchAdapter
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            updateQueueList()
        }
        
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            updateQueueList()
        }
    }

    override fun fixLayout(view: View) {}

    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        
        val isChild = arguments?.getBoolean(MusicCombinedBottomSheetFragment.ARG_IS_CHILD) ?: false
        binding?.musicQueueToolbar?.isVisible = !isChild
        
        setupRecyclerView()
        setupController()
        observeViewModel()

        binding?.musicQueueToolbar?.setNavigationOnClickListener {
            activity?.onBackPressed()
        }
    }

    private fun setupController() {
        val context = context ?: return
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                mediaController?.addListener(playerListener)
                updateQueueList()
            } catch (e: Exception) {
                android.util.Log.e("MusicQueueFragment", "Error getting media controller", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun updateQueueList() {
        val controller = mediaController ?: return
        val songs = viewModel.currentQueueLiveData.value ?: return
        val currentIndex = controller.currentMediaItemIndex
        
        // Filter out current song from "Up Next"
        val upNext = if (currentIndex in songs.indices) {
            songs.subList(currentIndex + 1, songs.size)
        } else {
            songs
        }
        queueAdapter.submitList(upNext)
    }

    private fun setupRecyclerView() {
        queueAdapter = MusicSearchAdapter({ index ->
            // In queue view, we might want to jump to this song
            // For now, let's just use playQueue with the same list
            viewModel.currentQueueLiveData.value?.let { songs ->
                // The index in upNext corresponds to (original index - currentIndex - 1)
                // But simplified for now: just map the song to its index in currentQueue
                val selectedSong = queueAdapter.currentList[index]
                val originalIndex = songs.indexOf(selectedSong)
                if (originalIndex != -1) {
                    viewModel.playQueue(songs, originalIndex)
                }
            }
        }, { _, song ->
            showSongMenu(song)
        })
        binding?.musicQueueRecycler?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = queueAdapter
        }
    }

    private fun showSongMenu(song: MusicSearchResponse) {
        val args = Bundle().apply {
            putString("track", Json.encodeToString(song))
        }
        activity?.navigate(R.id.navigation_track_options, args)
    }

    private fun observeViewModel() {
        viewModel.currentPlayingSong.observe(viewLifecycleOwner) { song ->
            // Current song is shown in the player itself, not in the queue list
        }

        viewModel.currentQueueLiveData.observe(viewLifecycleOwner) {
            updateQueueList()
        }

        viewModel.queueReady.observe(viewLifecycleOwner) {
            updateQueueList()
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
