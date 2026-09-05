package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentMusicHistoryBinding
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@androidx.media3.common.util.UnstableApi
class MusicHistoryFragment : BaseFragment<FragmentMusicHistoryBinding>(
    BindingCreator.Inflate(FragmentMusicHistoryBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var historyAdapter: MusicSearchAdapter

    override fun fixLayout(view: View) {}

    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        
        setupRecyclerView()
        observeViewModel()
        
        binding?.musicHistoryToolbar?.setNavigationOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        historyAdapter = MusicSearchAdapter({ index ->
            viewModel.playQueue(historyAdapter.currentList, index)
        }, { _, song ->
            showSongMenu(song)
        }, { videoId, params ->
            viewModel.prefetchUrl(videoId, params)
        })
        binding?.musicHistoryRecycler?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = historyAdapter
        }
    }

    private fun showSongMenu(song: MusicSearchResponse) {
        val args = Bundle().apply {
            putString("track", Json.encodeToString(song))
        }
        activity?.navigate(R.id.navigation_track_options, args)
    }

    private fun observeViewModel() {
        viewModel.history.observe(viewLifecycleOwner) { history ->
            historyAdapter.submitList(history)
        }
    }
}
