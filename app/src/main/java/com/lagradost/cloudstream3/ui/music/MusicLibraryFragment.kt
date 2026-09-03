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
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@androidx.media3.common.util.UnstableApi
class MusicLibraryFragment : BaseFragment<FragmentMusicLibraryBinding>(
    BindingCreator.Inflate(FragmentMusicLibraryBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var likedSongsAdapter: MusicSearchAdapter
    private lateinit var historyAdapter: MusicSearchAdapter
    private lateinit var downloadsAdapter: MusicSearchAdapter
    private lateinit var playlistsAdapter: MusicPlaylistAdapter

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
        }, { _, song ->
            showSongMenu(song)
        })
        binding?.libraryLikedSongsRecycler?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = likedSongsAdapter
        }

        historyAdapter = MusicSearchAdapter({ index ->
            viewModel.playQueue(historyAdapter.currentList, index)
        }, { _, song ->
            showSongMenu(song)
        })
        binding?.libraryHistoryRecycler?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = historyAdapter
        }

        downloadsAdapter = MusicSearchAdapter({ index ->
            viewModel.playQueue(downloadsAdapter.currentList, index)
        }, { _, song ->
            showSongMenu(song)
        })
        binding?.libraryDownloadsRecycler?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = downloadsAdapter
        }

        playlistsAdapter = MusicPlaylistAdapter { playlist ->
            val args = Bundle().apply {
                putString("playlist_name", playlist.name)
                // In local persistence we don't have a playlist ID, but we can pass name
                putString("playlist_id", "local_${playlist.name}") 
            }
            activity?.navigate(R.id.music_nav_detail, args)
        }
        binding?.libraryPlaylistsRecycler?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = playlistsAdapter
        }

        binding?.libraryLikedSongsHeader?.setOnClickListener {
            val isVisible = binding?.libraryLikedSongsRecycler?.visibility == View.VISIBLE
            binding?.libraryLikedSongsRecycler?.visibility = if (isVisible) View.GONE else View.VISIBLE
        }

        binding?.libraryDownloadsHeader?.setOnClickListener {
            activity?.navigate(R.id.music_nav_downloads)
        }

        binding?.libraryCreatePlaylist?.setOnClickListener {
            showCreatePlaylistDialog()
        }

        binding?.librarySettings?.setOnClickListener {
            activity?.navigate(R.id.music_nav_settings)
        }
    }

    private fun showSongMenu(song: MusicSearchResponse) {
        val args = Bundle().apply {
            putString("track", Json.encodeToString(song))
        }
        activity?.navigate(R.id.navigation_track_options, args)
    }

    private fun showCreatePlaylistDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
        builder.setTitle("Create Playlist")
        
        val input = android.widget.EditText(requireContext())
        input.hint = "Playlist Name"
        input.setTextColor(android.graphics.Color.WHITE)
        input.setHintTextColor(android.graphics.Color.GRAY)
        
        val padding = 48
        val container = android.widget.FrameLayout(requireContext())
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(padding, 0, padding, 0)
        input.layoutParams = params
        container.addView(input)
        
        builder.setView(container)

        builder.setPositiveButton("Create") { _, _ ->
            val name = input.text.toString()
            if (name.isNotBlank()) {
                viewModel.createPlaylist(name)
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        builder.show()
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
            playlistsAdapter.submitList(playlists)
            binding?.libraryPlaylistsEmpty?.visibility = if (playlists.isEmpty()) View.VISIBLE else View.GONE
            binding?.libraryPlaylistsRecycler?.visibility = if (playlists.isEmpty()) View.GONE else View.VISIBLE
        }
    }
}
