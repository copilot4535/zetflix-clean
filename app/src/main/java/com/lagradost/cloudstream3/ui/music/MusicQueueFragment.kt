package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.cloudstream3.databinding.FragmentMusicQueueBinding
import com.lagradost.cloudstream3.ui.BaseFragment

class MusicQueueFragment : BaseFragment<FragmentMusicQueueBinding>(
    BindingCreator.Inflate(FragmentMusicQueueBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var queueAdapter: MusicSearchAdapter

    override fun fixLayout(view: View) {}

    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        queueAdapter = MusicSearchAdapter { index ->
            // In queue view, we might want to jump to this song
            // For now, let's just use playQueue with the same list
            viewModel.queueReady.value?.let { resource ->
                if (resource is com.lagradost.cloudstream3.mvvm.Resource.Success) {
                    val songs = resource.value.first.map { it.first }
                    viewModel.playQueue(songs, index)
                }
            }
        }
        binding?.musicQueueRecycler?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = queueAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.queueReady.observe(viewLifecycleOwner) { resource ->
            if (resource is com.lagradost.cloudstream3.mvvm.Resource.Success) {
                val songs = resource.value.first.map { it.first }
                queueAdapter.submitList(songs)
            }
        }
    }
}
