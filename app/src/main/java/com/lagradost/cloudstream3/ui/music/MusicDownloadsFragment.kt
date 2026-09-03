package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentMusicDownloadsBinding
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@androidx.media3.common.util.UnstableApi
class MusicDownloadsFragment : BaseFragment<FragmentMusicDownloadsBinding>(
    BindingCreator.Inflate(FragmentMusicDownloadsBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var downloadsAdapter: MusicSearchAdapter

    override fun fixLayout(view: View) {}

    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        
        setupRecyclerView()
        observeViewModel()

        binding?.musicDownloadsToolbar?.setNavigationOnClickListener {
            activity?.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        downloadsAdapter = MusicSearchAdapter({ index ->
            viewModel.playQueue(downloadsAdapter.currentList, index)
        }, { _, song ->
            showSongMenu(song)
        })
        binding?.musicDownloadsRecycler?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = downloadsAdapter
        }
    }

    private fun showSongMenu(song: MusicSearchResponse) {
        val args = Bundle().apply {
            putString("track", Json.encodeToString(song))
        }
        activity?.navigate(R.id.navigation_track_options, args)
    }

    private fun observeViewModel() {
        viewModel.downloadedSongs.observe(viewLifecycleOwner) { downloads ->
            downloadsAdapter.submitList(downloads)
            binding?.musicDownloadsEmpty?.visibility = if (downloads.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
