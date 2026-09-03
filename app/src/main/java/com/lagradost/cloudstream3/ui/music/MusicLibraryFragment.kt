package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.cloudstream3.databinding.FragmentMusicLibraryBinding
import com.lagradost.cloudstream3.ui.BaseFragment

class MusicLibraryFragment : BaseFragment<FragmentMusicLibraryBinding>(
    BindingCreator.Inflate(FragmentMusicLibraryBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var likedSongsAdapter: MusicSearchAdapter
    private lateinit var historyAdapter: MusicSearchAdapter

    override fun fixLayout(view: View) {
        // Implement fixLayout if needed
    }

    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        
        setupRecyclerViews()
        observeViewModel()
        
        viewModel.loadPersistenceData()
    }

    private fun setupRecyclerViews() {
        likedSongsAdapter = MusicSearchAdapter { index ->
            viewModel.playQueue(likedSongsAdapter.currentList, index)
        }
        binding?.libraryLikedSongsRecycler?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = likedSongsAdapter
        }

        historyAdapter = MusicSearchAdapter { index ->
            viewModel.playQueue(historyAdapter.currentList, index)
        }
        binding?.libraryHistoryRecycler?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = historyAdapter
        }

        binding?.libraryLikedSongsHeader?.setOnClickListener {
            val isVisible = binding?.libraryLikedSongsRecycler?.visibility == View.VISIBLE
            binding?.libraryLikedSongsRecycler?.visibility = if (isVisible) View.GONE else View.VISIBLE
        }
    }

    private fun observeViewModel() {
        viewModel.likedSongs.observe(viewLifecycleOwner) { songs ->
            likedSongsAdapter.submitList(songs)
            // Header is always visible, but we could update song count
        }

        viewModel.history.observe(viewLifecycleOwner) { history ->
            historyAdapter.submitList(history)
            binding?.libraryHistoryEmpty?.visibility = if (history.isEmpty()) View.VISIBLE else View.GONE
            binding?.libraryHistoryRecycler?.visibility = if (history.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.playlists.observe(viewLifecycleOwner) { playlists ->
            binding?.libraryPlaylistsEmpty?.visibility = if (playlists.isEmpty()) View.VISIBLE else View.GONE
            // Handle playlists adapter if needed
        }
    }
}
