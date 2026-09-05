package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentMusicGenreBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.UIHelper.navigate

@androidx.media3.common.util.UnstableApi
class MusicGenreFragment : BaseFragment<FragmentMusicGenreBinding>(
    BindingCreator.Inflate(FragmentMusicGenreBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var genreAdapter: MusicHomeAdapter

    override fun fixLayout(view: View) {
    }

    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        
        setupRecyclerView()
        observeViewModel()

        val title = arguments?.getString("title") ?: "Moods & Genres"
        binding?.musicGenreToolbar?.title = title

        val params = arguments?.getString("params")
        if (params == "moods_and_genres") {
            viewModel.loadMoodAndGenres()
        } else if (params != null && params != "null") {
            // Structured browse is preferred for See All
            viewModel.loadBrowseSections(null, params)
        } else {
            // Use search to find curated playlists for this mood
            viewModel.search(title, com.maxrave.kotlinytmusicscraper.YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST)
        }

        binding?.musicGenreToolbar?.setNavigationOnClickListener {
            activity?.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        genreAdapter = MusicHomeAdapter({ section, index ->
            val item = section.items[index]
            when (item.type) {
                MusicItemType.SONG -> {
                    val songItems = section.items.filter { it.type == MusicItemType.SONG }
                    val songs = songItems.map {
                        MusicSearchResponse(it.title, it.subtitle, it.id, it.thumbnailUrl)
                    }
                    val songIndex = songItems.indexOf(item).coerceAtLeast(0)
                    viewModel.playQueue(songs, songIndex)
                }
                MusicItemType.ALBUM -> {
                    val args = Bundle().apply {
                        putString("album_id", item.id)
                        putString("album_name", item.title)
                    }
                    activity?.navigate(R.id.music_nav_detail, args)
                }
                MusicItemType.PLAYLIST -> {
                    val args = Bundle().apply {
                        putString("playlist_id", item.id)
                        putString("playlist_name", item.title)
                    }
                    activity?.navigate(R.id.music_nav_detail, args)
                }
                MusicItemType.ARTIST -> {
                    val args = Bundle().apply {
                        putString("artist_id", item.id)
                        putString("artist_name", item.title)
                    }
                    activity?.navigate(R.id.music_nav_artist, args)
                }
            }
        }, { section ->
            // Already in a "See All" style view
        })
        binding?.musicGenreRecycler?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = genreAdapter
        }
    }

    private fun observeViewModel() {
        observe(viewModel.homeSections) { resource ->
            if (resource is Resource.Success) {
                val title = arguments?.getString("title") ?: "Moods & Genres"
                val sections = resource.value
                
                if (title.contains("New", true) || title.contains("Release", true)) {
                    // Flatten sections for New Releases to show a continuous list
                    val allItems = sections.flatMap { it.items }.distinctBy { it.id }
                    if (allItems.isNotEmpty()) {
                        val flatSection = MusicHomeSection(title, allItems)
                        genreAdapter.submitList(listOf(flatSection))
                        return@observe
                    }
                }
                
                genreAdapter.submitList(sections)
            }
        }

        observe(viewModel.searchResult) { resource ->
            if (resource is Resource.Success) {
                // Map Search results (playlists) to Home sections for the adapter
                val section = MusicHomeSection(
                    title = "Recommended",
                    items = resource.value.map { 
                        MusicHomeItem(it.title, it.artist, it.videoId, it.thumbnailUrl, MusicItemType.PLAYLIST)
                    }
                )
                genreAdapter.submitList(listOf(section))
            }
        }
    }
}
