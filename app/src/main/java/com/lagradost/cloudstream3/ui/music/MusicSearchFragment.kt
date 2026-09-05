package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentMusicSearchBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.maxrave.kotlinytmusicscraper.YouTube
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@androidx.media3.common.util.UnstableApi
class MusicSearchFragment : BaseFragment<FragmentMusicSearchBinding>(
    BindingCreator.Inflate(FragmentMusicSearchBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var musicAdapter: MusicSearchAdapter
    private lateinit var suggestionAdapter: MusicSuggestionAdapter
    private var isSearching = false

    override fun fixLayout(view: View) {
        // Implement fixLayout if needed
    }

    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        
        setupRecyclerView()
        setupSearch()
        setupHistoryActions()
        observeViewModel()

        val searchQuery = arguments?.getString("search_query")

        if (searchQuery != null && searchQuery != "null") {
            binding?.musicSearchEditText?.setText(searchQuery)
            viewModel.search(searchQuery)
        } else {
            // Show search history or search prompt
            val history = viewModel.searchHistory.value ?: emptyList()
            if (history.isNotEmpty()) {
                binding?.musicSearchHeaderLayout?.isVisible = true
                binding?.musicSearchHeader?.text = "Recent Searches"
                suggestionAdapter.submitList(history)
                binding?.musicSearchSuggestionsRecycler?.isVisible = true
            } else {
                showSearchPrompt()
            }
        }
    }

    private fun showSearchPrompt() {
        binding?.musicRecyclerView?.isVisible = false
        binding?.musicSearchShimmerView?.root?.isVisible = false
        binding?.musicSearchSuggestionsRecycler?.isVisible = false
        binding?.musicSearchHeaderLayout?.isVisible = false
        binding?.musicErrorText?.apply {
            text = "Search for songs, artists, albums"
            isVisible = true
        }
    }

    private fun setupRecyclerView() {
        musicAdapter = MusicSearchAdapter({ index ->
            val song = musicAdapter.currentList[index]
            val id = song.videoId
            
            when {
                id.startsWith("PL") || id.startsWith("VL") -> {
                    val args = Bundle().apply {
                        putString("playlist_id", id.removePrefix("VL"))
                        putString("playlist_name", song.title)
                        putString("params", song.params)
                    }
                    activity?.navigate(R.id.music_nav_detail, args)
                }
                id.startsWith("UC") || id.contains("artist") -> {
                    val args = Bundle().apply {
                        putString("artist_id", id)
                        putString("artist_name", song.title)
                    }
                    activity?.navigate(R.id.music_nav_artist, args)
                }
                id.length > 11 && (id.startsWith("MP") || id.contains("album")) -> {
                    val args = Bundle().apply {
                        putString("album_id", id)
                        putString("album_name", song.title)
                    }
                    activity?.navigate(R.id.music_nav_detail, args)
                }
                else -> {
                    val songs = musicAdapter.currentList
                    viewModel.playQueue(songs, index)
                }
            }
        }, { _, song ->
            showSongMenu(song)
        }, { videoId, params ->
            viewModel.prefetchUrl(videoId, params)
        })
        binding?.musicRecyclerView?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = musicAdapter
        }

        suggestionAdapter = MusicSuggestionAdapter { suggestion ->
            isSearching = true
            binding?.musicSearchEditText?.setText(suggestion)
            binding?.musicSearchHeaderLayout?.isVisible = false
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
                    if (!isSearching) {
                        viewModel.loadSearchSuggestions(query)
                    }
                } else {
                    isSearching = false
                    binding?.musicSearchSuggestionsRecycler?.isVisible = false
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding?.musicSearchEditText?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding?.musicSearchEditText?.text?.toString()
                if (!query.isNullOrBlank()) {
                    isSearching = true
                    binding?.musicSearchHeaderLayout?.isVisible = false
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

    private fun setupHistoryActions() {
        binding?.musicSearchClearHistory?.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                com.lagradost.cloudstream3.CloudStreamApp.removeKey("music_search_history")
                viewModel.loadPersistenceData()
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
            val isLoading = resource is Resource.Loading
            binding?.musicSearchLoading?.isVisible = isLoading
            binding?.musicSearchShimmerView?.musicSearchShimmer?.let { shimmer ->
                if (isLoading) {
                    binding?.musicSearchShimmerView?.root?.isVisible = true
                    shimmer.startShimmer()
                    binding?.musicRecyclerView?.isVisible = false
                } else {
                    shimmer.stopShimmer()
                    binding?.musicSearchShimmerView?.root?.isVisible = false
                    binding?.musicRecyclerView?.isVisible = resource is Resource.Success
                }
            }
            
            binding?.musicErrorText?.isVisible = resource is Resource.Failure

            if (resource !is Resource.Loading) {
                isSearching = false
                binding?.musicSearchSuggestionsRecycler?.isVisible = false
            }

            if (resource is Resource.Success) {
                musicAdapter.submitList(resource.value)
                binding?.musicSearchHeaderLayout?.isVisible = false
            } else if (resource is Resource.Failure) {
                binding?.musicErrorText?.text = resource.errorString
            }
        }

        observe(viewModel.searchSuggestions) { suggestions ->
            if (!isSearching && suggestions.isNotEmpty() && binding?.musicSearchEditText?.text?.isNotBlank() == true) {
                suggestionAdapter.submitList(suggestions)
                binding?.musicSearchHeaderLayout?.isVisible = false
                binding?.musicSearchSuggestionsRecycler?.isVisible = true
            } else if (binding?.musicSearchEditText?.text?.isBlank() == true) {
                // Show history if empty
                val history = viewModel.searchHistory.value ?: emptyList()
                if (history.isNotEmpty()) {
                    binding?.musicSearchHeaderLayout?.isVisible = true
                    binding?.musicSearchHeader?.text = "Recent Searches"
                    binding?.musicSearchClearHistory?.isVisible = true
                    suggestionAdapter.submitList(history)
                    binding?.musicSearchSuggestionsRecycler?.isVisible = true
                } else {
                    showSearchPrompt()
                }
            } else {
                binding?.musicSearchSuggestionsRecycler?.isVisible = false
            }
        }

        viewModel.searchHistory.observe(viewLifecycleOwner) { history ->
            if (binding?.musicSearchEditText?.text?.isBlank() == true) {
                if (history.isNotEmpty()) {
                    binding?.musicSearchHeaderLayout?.isVisible = true
                    binding?.musicSearchHeader?.text = "Recent Searches"
                    binding?.musicSearchClearHistory?.isVisible = true
                    suggestionAdapter.submitList(history)
                    binding?.musicSearchSuggestionsRecycler?.isVisible = true
                    binding?.musicErrorText?.isVisible = false
                } else {
                    showSearchPrompt()
                }
            }
        }

        viewModel.queueReady.observe(viewLifecycleOwner) { event ->
            val content = event.peekContent()
            val (resource, requestId) = content
            if (requestId == viewModel.currentQueueRequestId) {
                if (resource is Resource.Failure) {
                    Toast.makeText(context, "Queue Error: ${resource.errorString}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
