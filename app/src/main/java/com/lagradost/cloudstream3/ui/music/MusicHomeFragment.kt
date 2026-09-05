package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentMusicHomeBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.UIHelper.navigate

class MusicHomeFragment : BaseFragment<FragmentMusicHomeBinding>(
    BindingCreator.Inflate(FragmentMusicHomeBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var homeAdapter: MusicHomeAdapter

    override fun fixLayout(view: View) {
        // Any layout fixes
    }

    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        
        setupRecyclerView()
        observeViewModel()
        
        if (viewModel.homeSections.value !is Resource.Success) {
            viewModel.loadHomeSections()
        }
        
        binding?.musicHomeSwipeRefresh?.setOnRefreshListener {
            viewModel.loadHomeSections()
            binding?.musicHomeSwipeRefresh?.isRefreshing = false
        }
    }

    private fun setupRecyclerView() {
        homeAdapter = MusicHomeAdapter({ section, index ->
            val item = section.items[index]
            when (item.type) {
                MusicItemType.SONG -> {
                    val songItems = section.items.filter { it.type == MusicItemType.SONG }
                    val songs = songItems.map {
                        MusicSearchResponse(it.title, it.subtitle, it.id, it.thumbnailUrl, it.params)
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
                        putString("params", item.params)
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
            val params = section.params
            if (params != null) {
                val args = Bundle().apply {
                    putString("title", section.title)
                    putString("params", params)
                }
                activity?.navigate(R.id.music_nav_genre, args)
            }
        }, { videoId, params ->
            viewModel.prefetchUrl(videoId, params)
        }, { viewId ->
            when (viewId) {
                R.id.music_home_search -> activity?.navigate(R.id.music_nav_search)
                R.id.music_home_history -> activity?.navigate(R.id.navigation_music_history)
                R.id.music_home_settings -> activity?.navigate(R.id.music_nav_settings)
                R.id.music_home_profile -> { /* Profile */ }
            }
        }, { checkedId ->
            when (checkedId) {
                R.id.chip_overview -> scrollToSection(null)
                R.id.chip_charts -> scrollToSection("Charts")
                R.id.chip_artists -> scrollToSection("Top Artists")
                R.id.chip_podcasts -> scrollToSection("Podcasts")
                R.id.chip_moods -> scrollToSection("Moods & Genres")
                R.id.chip_trending -> scrollToSection("Trending")
            }
        })
        binding?.musicHomeRecycler?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = homeAdapter
        }
    }

    private fun scrollToSection(title: String?) {
        val recycler = binding?.musicHomeRecycler ?: return
        if (title == null) {
            recycler.smoothScrollToPosition(0)
            return
        }

        val sections = homeAdapter.currentList
        val index = sections.indexOfFirst { it.title.contains(title, true) }
        if (index != -1) {
            val scroller = object : androidx.recyclerview.widget.LinearSmoothScroller(context) {
                override fun getVerticalSnapPreference(): Int = SNAP_TO_START
            }
            scroller.targetPosition = index
            recycler.layoutManager?.startSmoothScroll(scroller)
        }
    }



    private fun observeViewModel() {
        viewModel.queueReady.observe(viewLifecycleOwner) { event ->
            val content = event.peekContent()
            val (resource, requestId) = content
            if (requestId == viewModel.currentQueueRequestId) {
                if (resource is Resource.Failure) {
                    Toast.makeText(context, "Queue Error: ${resource.errorString}", Toast.LENGTH_LONG).show()
                }
            }
        }

        observe(viewModel.homeSections) { resource ->
            binding?.musicHomeShimmerView?.musicHomeShimmer?.let { shimmer ->
                if (resource is Resource.Loading) {
                    binding?.musicHomeShimmerView?.root?.isVisible = true
                    shimmer.startShimmer()
                    binding?.musicHomeRecycler?.isVisible = false
                    binding?.musicHomeEmptyState?.isVisible = false
                } else {
                    shimmer.stopShimmer()
                    binding?.musicHomeShimmerView?.root?.isVisible = false
                    binding?.musicHomeRecycler?.isVisible = resource is Resource.Success
                }
            }
            binding?.musicHomeErrorText?.isVisible = resource is Resource.Failure
            
            when (resource) {
                is Resource.Success -> {
                    val sections = resource.value
                    if (sections.isEmpty()) {
                        binding?.musicHomeEmptyState?.isVisible = true
                        binding?.musicHomeRecycler?.isVisible = false
                    } else {
                        binding?.musicHomeEmptyState?.isVisible = false
                        binding?.musicHomeRecycler?.isVisible = true
                        
                        val listWithHeader = mutableListOf<MusicHomeSection>()
                        listWithHeader.add(MusicHomeSection(MusicHomeAdapter.HEADER_ID, emptyList()))
                        listWithHeader.addAll(sections)
                        homeAdapter.submitList(listWithHeader)
                    }
                }
                is Resource.Failure -> {
                    binding?.musicHomeErrorText?.isVisible = true
                    binding?.musicHomeErrorText?.text = resource.errorString
                    binding?.musicHomeEmptyState?.isVisible = false
                    android.util.Log.e("MusicHome", "Failed to load sections: ${resource.errorString}")
                }
                else -> {}
            }
        }
    }
}
