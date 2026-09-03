package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.maxrave.kotlinytmusicscraper.YouTube
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard

import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIconsAndNoStringRes

@androidx.media3.common.util.UnstableApi
class MusicFragment : BaseFragment<FragmentMusicBinding>(
    BindingCreator.Inflate(FragmentMusicBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var musicAdapter: MusicSearchAdapter
    private lateinit var suggestionAdapter: MusicSuggestionAdapter

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
        musicAdapter = MusicSearchAdapter({ index ->
            val songs = musicAdapter.currentList
            viewModel.playQueue(songs, index)
        }, { view, song ->
            showSongMenu(view, song)
        })
        binding?.musicRecyclerView?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = musicAdapter
        }

        suggestionAdapter = MusicSuggestionAdapter { suggestion ->
            binding?.musicSearchEditText?.setText(suggestion)
            binding?.musicSearchHeader?.isVisible = false
            binding?.musicSearchSuggestionsRecycler?.isVisible = false
            viewModel.search(suggestion)
            hideKeyboard()
        }
        binding?.musicSearchSuggestionsRecycler?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = suggestionAdapter
        }
    }

    private fun setupSearch() {
        binding?.musicSearchFilterChips?.setOnCheckedChangeListener { group, checkedId ->
            val query = binding?.musicSearchEditText?.text?.toString() ?: ""
            if (query.isNotBlank()) {
                val filter = when (checkedId) {
                    R.id.chip_songs -> YouTube.SearchFilter.FILTER_SONG
                    R.id.chip_albums -> YouTube.SearchFilter.FILTER_ALBUM
                    R.id.chip_artists -> YouTube.SearchFilter.FILTER_ARTIST
                    R.id.chip_playlists -> YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST
                    else -> YouTube.SearchFilter.FILTER_SONG
                }
                viewModel.search(query, filter)
            }
        }

        binding?.musicSearchEditText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                if (query.isNotBlank()) {
                    viewModel.loadSearchSuggestions(query)
                } else {
                    binding?.musicSearchSuggestionsRecycler?.isVisible = false
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding?.musicSearchEditText?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding?.musicSearchEditText?.text?.toString()
                if (!query.isNullOrBlank()) {
                    binding?.musicSearchHeader?.isVisible = false
                    binding?.musicSearchSuggestionsRecycler?.isVisible = false
                    viewModel.search(query)
                    hideKeyboard()
                }
                true
            } else {
                false
            }
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

        observe(viewModel.searchSuggestions) { suggestions ->
            if (suggestions.isNotEmpty() && binding?.musicSearchEditText?.text?.isNotBlank() == true) {
                suggestionAdapter.submitList(suggestions)
                binding?.musicSearchSuggestionsRecycler?.isVisible = true
            } else {
                binding?.musicSearchSuggestionsRecycler?.isVisible = false
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
