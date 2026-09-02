package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentMusicHomeBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
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
        setupSearch()
        observeViewModel()
        
        if (viewModel.homeSections.value !is Resource.Success) {
            viewModel.loadHomeSections()
        }
    }

    private fun setupRecyclerView() {
        homeAdapter = MusicHomeAdapter { item ->
            when (item.type) {
                MusicItemType.SONG -> {
                    viewModel.loadStreamAndPlay(
                        MusicSearchResponse(
                            title = item.title,
                            artist = item.subtitle,
                            videoId = item.id,
                            thumbnailUrl = item.thumbnailUrl
                        )
                    )
                }
                MusicItemType.ALBUM -> {
                    val args = Bundle().apply {
                        putString("album_id", item.id)
                    }
                    activity?.navigate(R.id.action_navigation_music_home_to_navigation_music, args)
                }
                MusicItemType.PLAYLIST -> {
                    val args = Bundle().apply {
                        putString("playlist_id", item.id)
                    }
                    activity?.navigate(R.id.action_navigation_music_home_to_navigation_music, args)
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

    private fun setupSearch() {
        binding?.musicSearchEditText?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding?.musicSearchEditText?.text?.toString()
                if (!query.isNullOrBlank()) {
                    hideKeyboard()
                    val args = Bundle().apply {
                        putString("search_query", query)
                    }
                    activity?.navigate(R.id.action_navigation_music_home_to_navigation_music, args)
                }
                true
            } else {
                false
            }
        }
    }

    private fun observeViewModel() {
        observe(viewModel.homeSections) { resource ->
            binding?.musicHomeLoading?.isVisible = resource is Resource.Loading
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
