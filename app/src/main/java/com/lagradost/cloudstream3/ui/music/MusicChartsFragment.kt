package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.cloudstream3.databinding.FragmentMusicChartsBinding
import com.lagradost.cloudstream3.mvvm.launchSafe
import com.lagradost.cloudstream3.ui.BaseFragment

@androidx.media3.common.util.UnstableApi
class MusicChartsFragment : BaseFragment<FragmentMusicChartsBinding>(
    BindingCreator.Inflate(FragmentMusicChartsBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var musicAdapter: MusicSearchAdapter
    private val exploreRepository = ExploreRepository()

    override fun fixLayout(view: View) {}

    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        
        setupRecyclerView()
        setupTabs()
        loadCharts()
        
        binding?.chartsToolbar?.setNavigationOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        musicAdapter = MusicSearchAdapter({ index ->
            viewModel.playQueue(musicAdapter.currentList, index)
        }, { _, _ -> })
        
        binding?.chartsRecycler?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = musicAdapter
        }
    }

    private fun setupTabs() {
        binding?.chartsTabs?.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                loadCharts()
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    private fun loadCharts() {
        binding?.chartsLoading?.isVisible = true
        binding?.chartsError?.isVisible = false
        
        lifecycleScope.launchSafe {
            val songs = exploreRepository.getTopCharts()
            binding?.chartsLoading?.isVisible = false
            if (songs.isNotEmpty()) {
                musicAdapter.submitList(songs)
            } else {
                binding?.chartsError?.isVisible = true
            }
        }
    }
}
