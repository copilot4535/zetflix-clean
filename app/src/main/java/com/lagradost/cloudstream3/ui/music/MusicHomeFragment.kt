package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
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
                    activity?.navigate(R.id.music_detail, args)
                }
                MusicItemType.PLAYLIST -> {
                    val args = Bundle().apply {
                        putString("playlist_id", item.id)
                    }
                    activity?.navigate(R.id.music_detail, args)
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
                    activity?.navigate(R.id.music_nav_search, args)
                }
                true
            } else {
                false
            }
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
