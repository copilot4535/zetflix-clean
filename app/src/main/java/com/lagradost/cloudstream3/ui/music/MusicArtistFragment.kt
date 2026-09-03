package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.cloudstream3.databinding.FragmentMusicArtistBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage

@androidx.media3.common.util.UnstableApi
class MusicArtistFragment : BaseFragment<FragmentMusicArtistBinding>(
    BindingCreator.Inflate(FragmentMusicArtistBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var topTracksAdapter: MusicSearchAdapter

    override fun fixLayout(view: View) {
    }

    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        
        setupRecyclerView()
        observeViewModel()

        val artistId = arguments?.getString("artist_id")
        if (artistId != null) {
            // For now, reuse search with artist filter or trending
            // In a full stack app, we'd fetch artist specific data
            viewModel.search(arguments?.getString("artist_name") ?: "", com.maxrave.kotlinytmusicscraper.YouTube.SearchFilter.FILTER_SONG)
        }

        binding?.musicArtistToolbar?.setNavigationOnClickListener {
            activity?.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        topTracksAdapter = MusicSearchAdapter({ index ->
            viewModel.playQueue(topTracksAdapter.currentList, index)
        }, { _, _ -> })
        
        binding?.musicArtistTopTracks?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = topTracksAdapter
        }
    }

    private fun observeViewModel() {
        observe(viewModel.searchResult) { resource ->
            if (resource is Resource.Success) {
                topTracksAdapter.submitList(resource.value.take(5))
                val first = resource.value.firstOrNull()
                binding?.musicArtistName?.text = arguments?.getString("artist_name") ?: first?.artist
                binding?.musicArtistHeaderImage?.loadImage(first?.thumbnailUrl)
            }
        }
    }
}
