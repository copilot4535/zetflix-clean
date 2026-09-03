package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentMusicQueueBinding
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIconsAndNoStringRes

@androidx.media3.common.util.UnstableApi
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
        queueAdapter = MusicSearchAdapter({ index ->
            // In queue view, we might want to jump to this song
            // For now, let's just use playQueue with the same list
            viewModel.queueReady.value?.let { resource ->
                if (resource is com.lagradost.cloudstream3.mvvm.Resource.Success) {
                    val songs = resource.value.first.map { it.first }
                    viewModel.playQueue(songs, index)
                }
            }
        }, { view, song ->
            showSongMenu(view, song)
        })
        binding?.musicQueueRecycler?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = queueAdapter
        }
    }

    private fun showSongMenu(view: View, song: MusicSearchResponse) {
        val isLiked = MusicPersistence.isSongLiked(song.videoId)
        val isDownloaded = MusicPersistence.getDownloadedSongs().any { it.videoId == song.videoId }
        
        val options = mutableListOf<Pair<Int, String>>()
        options.add(0 to if (isLiked) "Remove from Liked" else "Like")
        options.add(1 to if (isDownloaded) "Remove Download" else "Download")
        options.add(2 to "Add to Playlist")

        view.popupMenuNoIconsAndNoStringRes(options) {
            when (itemId) {
                0 -> viewModel.toggleLikeSong(song)
                1 -> {
                    if (isDownloaded) viewModel.removeDownload(song.videoId)
                    else viewModel.downloadSong(song)
                }
                2 -> showAddToPlaylistDialog(song)
            }
        }
    }

    private fun showAddToPlaylistDialog(song: MusicSearchResponse) {
        val playlists = MusicPersistence.getPlaylists()
        if (playlists.isEmpty()) {
            Toast.makeText(context, "No playlists created", Toast.LENGTH_SHORT).show()
            return
        }
        val names = playlists.map { it.name }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
            .setTitle("Add to Playlist")
            .setItems(names) { _, which ->
                viewModel.addSongToPlaylist(names[which], song)
            }
            .show()
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
