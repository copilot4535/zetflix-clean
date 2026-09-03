package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.cloudstream3.databinding.FragmentMusicGenreBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.BaseFragment

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

        // Fetch curated sections for this genre if logic existed
        // Fallback to home sections
        viewModel.loadHomeSections()

        binding?.musicGenreToolbar?.setNavigationOnClickListener {
            activity?.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        genreAdapter = MusicHomeAdapter { section, index ->
            // Handle clicks
        }
        binding?.musicGenreRecycler?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = genreAdapter
        }
    }

    private fun observeViewModel() {
        observe(viewModel.homeSections) { resource ->
            if (resource is Resource.Success) {
                genreAdapter.submitList(resource.value)
            }
        }
    }
}
