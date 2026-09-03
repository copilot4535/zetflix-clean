package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
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
        setupGenreChips()
        observeViewModel()
        
        if (viewModel.homeSections.value !is Resource.Success) {
            viewModel.loadHomeSections()
        }
    }

    private fun setupGenreChips() {
        binding?.musicHomeGenreChips?.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val chip = group.findViewById<com.google.android.material.chip.Chip>(checkedId)
            val title = chip.text.toString()
            val args = Bundle().apply {
                putString("title", title)
                // In a real app, we'd have specific params for these categories
                putString("params", "genre_$title") 
            }
            activity?.navigate(R.id.music_nav_genre, args)
            // Uncheck so it can be clicked again
            group.clearCheck()
        }
    }

    private fun setupRecyclerView() {
        homeAdapter = MusicHomeAdapter { section, index ->
            val item = section.items[index]
            android.util.Log.d("MusicHome", "Clicked item: ${item.title}, type: ${item.type}, id: ${item.id}")
            when (item.type) {
                MusicItemType.SONG -> {
                    // Filter section items to only include songs for the queue
                    val songItems = section.items.filter { it.type == MusicItemType.SONG }
                    val songs = songItems.map {
                        MusicSearchResponse(
                            title = it.title,
                            artist = it.subtitle,
                            videoId = it.id,
                            thumbnailUrl = it.thumbnailUrl
                        )
                    }
                    val songIndex = songItems.indexOf(item).coerceAtLeast(0)
                    viewModel.playQueue(songs, songIndex)
                }
                MusicItemType.ALBUM -> {
                    val args = Bundle().apply {
                        putString("album_id", item.id)
                    }
                    activity?.navigate(R.id.music_nav_detail, args)
                }
                MusicItemType.PLAYLIST -> {
                    val args = Bundle().apply {
                        putString("playlist_id", item.id)
                    }
                    activity?.navigate(R.id.music_nav_detail, args)
                }
                MusicItemType.ARTIST -> {
                    // Could handle artist browsing later
                }
            }
        }
        binding?.musicHomeRecycler?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = homeAdapter
        }
    }



    private fun observeViewModel() {
        observe(viewModel.queueReady) { resource ->
            when (resource) {
                is Resource.Failure -> {
                    Toast.makeText(context, "Queue Error: ${resource.errorString}", Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }

        observe(viewModel.homeSections) { resource ->
            binding?.musicHomeShimmerView?.musicHomeShimmer?.let { shimmer ->
                if (resource is Resource.Loading) {
                    binding?.musicHomeShimmerView?.root?.isVisible = true
                    shimmer.startShimmer()
                } else {
                    shimmer.stopShimmer()
                    binding?.musicHomeShimmerView?.root?.isVisible = false
                }
            }
            binding?.musicHomeErrorText?.isVisible = resource is Resource.Failure
            
            when (resource) {
                is Resource.Success -> {
                    homeAdapter.submitList(resource.value)
                }
                is Resource.Failure -> {
                    binding?.musicHomeErrorText?.isVisible = true
                    binding?.musicHomeErrorText?.text = resource.errorString
                    android.util.Log.e("MusicHome", "Failed to load sections: ${resource.errorString}")
                }
                else -> {}
            }
        }
    }
}
