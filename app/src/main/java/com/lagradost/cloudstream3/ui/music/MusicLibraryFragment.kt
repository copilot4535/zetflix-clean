package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentMusicLibraryBinding
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIconsAndNoStringRes

@androidx.media3.common.util.UnstableApi
class MusicLibraryFragment : BaseFragment<FragmentMusicLibraryBinding>(
    BindingCreator.Inflate(FragmentMusicLibraryBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var likedSongsAdapter: MusicSearchAdapter
    private lateinit var historyAdapter: MusicSearchAdapter
    private lateinit var downloadsAdapter: MusicSearchAdapter

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
        likedSongsAdapter = MusicSearchAdapter({ index ->
            viewModel.playQueue(likedSongsAdapter.currentList, index)
        }, { view, song ->
            showSongMenu(view, song)
        })
        binding?.libraryLikedSongsRecycler?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = likedSongsAdapter
        }

        historyAdapter = MusicSearchAdapter({ index ->
            viewModel.playQueue(historyAdapter.currentList, index)
        }, { view, song ->
            showSongMenu(view, song)
        })
        binding?.libraryHistoryRecycler?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = historyAdapter
        }

        downloadsAdapter = MusicSearchAdapter({ index ->
            viewModel.playQueue(downloadsAdapter.currentList, index)
        }, { view, song ->
            showSongMenu(view, song)
        })
        binding?.libraryDownloadsRecycler?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = downloadsAdapter
        }

        binding?.libraryLikedSongsHeader?.setOnClickListener {
            val isVisible = binding?.libraryLikedSongsRecycler?.visibility == View.VISIBLE
            binding?.libraryLikedSongsRecycler?.visibility = if (isVisible) View.GONE else View.VISIBLE
        }
    }

    private fun showSongMenu(view: View, song: MusicSearchResponse) {
        val isLiked = MusicPersistence.isSongLiked(song.videoId)
        val isDownloaded = MusicPersistence.getDownloadedSongs().any { it.videoId == song.videoId }
        
        val options = mutableListOf<Pair<Int, String>>()
        options.add(0 to if (isLiked) "Remove from Liked" else "Like")
        options.add(1 to if (isDownloaded) "Remove Download" else "Download")
        options.add(2 to "Add to Playlist")

        view.popupMenuNoIconsAndNoStringRes(options) {
            when (itemId) {
                0 -> viewModel.toggleLikeSong(song)
                1 -> {
                    if (isDownloaded) viewModel.removeDownload(song.videoId)
                    else viewModel.downloadSong(song)
                }
                2 -> showAddToPlaylistDialog(song)
            }
        }
    }

    private fun showAddToPlaylistDialog(song: MusicSearchResponse) {
        val playlists = MusicPersistence.getPlaylists()
        if (playlists.isEmpty()) {
            Toast.makeText(context, "No playlists created", Toast.LENGTH_SHORT).show()
            return
        }
        val names = playlists.map { it.name }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
            .setTitle("Add to Playlist")
            .setItems(names) { _, which ->
                viewModel.addSongToPlaylist(names[which], song)
            }
            .show()
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

        viewModel.downloadedSongs.observe(viewLifecycleOwner) { downloads ->
            downloadsAdapter.submitList(downloads)
            binding?.libraryDownloadsEmpty?.visibility = if (downloads.isEmpty()) View.VISIBLE else View.GONE
            binding?.libraryDownloadsRecycler?.visibility = if (downloads.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.playlists.observe(viewLifecycleOwner) { playlists ->
            binding?.libraryPlaylistsEmpty?.visibility = if (playlists.isEmpty()) View.VISIBLE else View.GONE
            // Handle playlists adapter if needed
        }
    }
}
