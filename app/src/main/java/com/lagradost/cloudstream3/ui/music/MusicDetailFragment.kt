package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentMusicDetailBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@androidx.media3.common.util.UnstableApi
class MusicDetailFragment : BaseFragment<FragmentMusicDetailBinding>(
    BindingCreator.Inflate(FragmentMusicDetailBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var musicAdapter: MusicSearchAdapter

    override fun fixLayout(view: View) {
        // Implement fixLayout if needed
    }

    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        
        setupRecyclerView()
        observeViewModel()

        val albumId = arguments?.getString("album_id")
        val playlistId = arguments?.getString("playlist_id")
        val params = arguments?.getString("params")

        if (playlistId?.startsWith("local_") == true) {
            val playlistName = playlistId.removePrefix("local_")
            viewModel.playlists.value?.find { it.name == playlistName }?.let {
                musicAdapter.submitList(it.songs)
                binding?.musicDetailTitle?.text = it.name
                binding?.musicDetailSubtitle?.text = "${it.songs.size} songs"
                binding?.musicDetailHeaderImage?.loadImage(it.songs.firstOrNull()?.thumbnailUrl)
            }
        } else if (params != null && params != "null") {
            viewModel.loadBrowseResult(params)
        } else if (albumId != null && albumId != "null") {
            viewModel.loadAlbumSongs(albumId)
            binding?.musicDetailTitle?.text = arguments?.getString("album_name")
        } else if (playlistId != null && playlistId != "null") {
            viewModel.loadPlaylistSongs(playlistId)
            binding?.musicDetailTitle?.text = arguments?.getString("playlist_name")
        }

        binding?.musicDetailToolbar?.setNavigationOnClickListener {
            activity?.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        musicAdapter = MusicSearchAdapter({ index ->
            val songs = musicAdapter.currentList
            viewModel.playQueue(songs, index)
        }, { _, song ->
            showSongMenu(song)
        }, { videoId, params ->
            viewModel.prefetchUrl(videoId, params)
        })
        binding?.musicDetailRecycler?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = musicAdapter
        }

        binding?.musicDetailPlayBtn?.setOnClickListener {
            if (musicAdapter.currentList.isNotEmpty()) {
                viewModel.playQueue(musicAdapter.currentList, 0)
            }
        }

        binding?.musicDetailShuffleBtn?.setOnClickListener {
            if (musicAdapter.currentList.isNotEmpty()) {
                viewModel.playQueue(musicAdapter.currentList.shuffled(), 0)
            }
        }
    }

    private fun showSongMenu(song: MusicSearchResponse) {
        val args = Bundle().apply {
            putString("track", Json.encodeToString(song))
        }
        activity?.navigate(R.id.navigation_track_options, args)
    }

    private fun observeViewModel() {
        observe(viewModel.searchResult) { resource ->
            binding?.musicDetailLoading?.isVisible = resource is Resource.Loading
            
            if (resource is Resource.Success) {
                val songs = resource.value
                binding?.musicDetailEmpty?.isVisible = songs.isEmpty()
                binding?.musicDetailRecycler?.isVisible = songs.isNotEmpty()
                binding?.musicDetailControls?.isVisible = songs.isNotEmpty()
                
                musicAdapter.submitList(songs)
                
                val firstSong = songs.firstOrNull()
                if (firstSong != null) {
                    binding?.musicDetailHeaderImage?.loadImage(firstSong.thumbnailUrl)
                    binding?.musicDetailTitle?.text = arguments?.getString("album_name") 
                                                      ?: arguments?.getString("playlist_name")
                                                      ?: firstSong.title
                    binding?.musicDetailSubtitle?.text = firstSong.artist
                }
            } else if (resource is Resource.Failure) {
                binding?.musicDetailEmpty?.isVisible = true
                binding?.musicDetailEmpty?.text = resource.errorString
                binding?.musicDetailRecycler?.isVisible = false
                binding?.musicDetailControls?.isVisible = false
            }
        }
    }
}
