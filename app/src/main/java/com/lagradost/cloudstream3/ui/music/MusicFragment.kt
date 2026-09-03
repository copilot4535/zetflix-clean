package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentMusicBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard

class MusicFragment : BaseFragment<FragmentMusicBinding>(
    BindingCreator.Inflate(FragmentMusicBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var musicAdapter: MusicSearchAdapter

    override fun fixLayout(view: View) {
        // Implement fixLayout if needed
    }

    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        
        setupRecyclerView()
        setupSearch()
        observeViewModel()

        val searchQuery = arguments?.getString("search_query")
        val albumId = arguments?.getString("album_id")
        val playlistId = arguments?.getString("playlist_id")

        if (searchQuery != null && searchQuery != "null") {
            binding?.musicSearchEditText?.setText(searchQuery)
            viewModel.search(searchQuery)
        } else if (albumId != null && albumId != "null") {
            viewModel.loadAlbumSongs(albumId)
        } else if (playlistId != null && playlistId != "null") {
            viewModel.loadPlaylistSongs(playlistId)
        } else {
            // Load trending songs if no arguments provided
            viewModel.loadTrendingSongs()
        }
    }

    private fun setupRecyclerView() {
        musicAdapter = MusicSearchAdapter { index ->
            val songs = musicAdapter.currentList
            viewModel.playQueue(songs, index)
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
                    binding?.musicSearchHeader?.isVisible = false
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

            if (resource is Resource.Success) {
                musicAdapter.submitList(resource.value)
                
                // Show "Trending" header only if it was a trending load (query empty)
                val query = binding?.musicSearchEditText?.text?.toString()
                binding?.musicSearchHeader?.isVisible = query.isNullOrBlank()
                if (query.isNullOrBlank()) {
                    binding?.musicSearchHeader?.text = "Trending Now"
                }
            } else if (resource is Resource.Failure) {
                binding?.musicErrorText?.text = resource.errorString
            }
        }

        observe(viewModel.queueReady) { resource ->
            when (resource) {
                is Resource.Failure -> {
                    Toast.makeText(context, "Queue Error: ${resource.errorString}", Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }
}
