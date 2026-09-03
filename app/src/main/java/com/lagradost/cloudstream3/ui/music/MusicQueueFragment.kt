package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentMusicQueueBinding
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@androidx.media3.common.util.UnstableApi
class MusicQueueFragment : BaseFragment<FragmentMusicQueueBinding>(
    BindingCreator.Inflate(FragmentMusicQueueBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var queueAdapter: MusicSearchAdapter

    override fun fixLayout(view: View) {}

    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        
        setupRecyclerView()
        observeViewModel()

        binding?.musicQueueToolbar?.setNavigationOnClickListener {
            activity?.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        queueAdapter = MusicSearchAdapter({ index ->
            // In queue view, we might want to jump to this song
            // For now, let's just use playQueue with the same list
            viewModel.queueReady.value?.let { resource ->
                if (resource is com.lagradost.cloudstream3.mvvm.Resource.Success) {
                    val songs = resource.value.first.map { it.first }
                    viewModel.playQueue(songs, index)
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
            if (song != null) {
                binding?.musicQueueCurrentItem?.musicSongTitle?.text = song.title
                binding?.musicQueueCurrentItem?.musicSongArtist?.text = song.artist
                binding?.musicQueueCurrentItem?.musicSongThumbnail?.loadImage(song.thumbnailUrl)
                binding?.musicQueueCurrentItem?.musicSongMenu?.setOnClickListener {
                    showSongMenu(song)
                }
            }
        }

        viewModel.queueReady.observe(viewLifecycleOwner) { resource ->
            if (resource is com.lagradost.cloudstream3.mvvm.Resource.Success) {
                val songs = resource.value.first.map { it.first }
                val currentIndex = resource.value.second
                // Filter out current song from "Up Next"
                val upNext = if (currentIndex in songs.indices) {
                    songs.subList(currentIndex + 1, songs.size)
                } else {
                    songs
                }
                queueAdapter.submitList(upNext)
                
                // Update Up Next header visibility
                // binding?.upNextHeader?.isVisible = upNext.isNotEmpty()
            }
        }
    }
}
