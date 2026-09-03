package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentMusicDetailBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIconsAndNoStringRes

@androidx.media3.common.util.UnstableApi
class MusicDetailFragment : BaseFragment<FragmentMusicDetailBinding>(
    BindingCreator.Inflate(FragmentMusicDetailBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var musicAdapter: MusicSearchAdapter

    override fun fixLayout(view: View) {
        // Implement fixLayout if needed
    }

    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        
        setupRecyclerView()
        observeViewModel()

        val albumId = arguments?.getString("album_id")
        val playlistId = arguments?.getString("playlist_id")

        if (albumId != null && albumId != "null") {
            viewModel.loadAlbumSongs(albumId)
        } else if (playlistId != null && playlistId != "null") {
            viewModel.loadPlaylistSongs(playlistId)
        }

        binding?.musicDetailToolbar?.setNavigationOnClickListener {
            activity?.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        musicAdapter = MusicSearchAdapter({ index ->
            val songs = musicAdapter.currentList
            viewModel.playQueue(songs, index)
        }, { view, song ->
            showSongMenu(view, song)
        })
        binding?.musicDetailRecycler?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = musicAdapter
        }

        binding?.musicDetailPlayBtn?.setOnClickListener {
            if (musicAdapter.currentList.isNotEmpty()) {
                viewModel.playQueue(musicAdapter.currentList, 0)
            }
        }

        binding?.musicDetailShuffleBtn?.setOnClickListener {
            if (musicAdapter.currentList.isNotEmpty()) {
                viewModel.playQueue(musicAdapter.currentList.shuffled(), 0)
            }
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
        observe(viewModel.searchResult) { resource ->
            // In a real premium app, we would have a separate detail resource with header info
            // For now, we'll extract header info from the first item if available
            if (resource is Resource.Success) {
                musicAdapter.submitList(resource.value)
                val firstSong = resource.value.firstOrNull()
                if (firstSong != null) {
                    binding?.musicDetailHeaderImage?.loadImage(firstSong.thumbnailUrl)
                    binding?.musicDetailTitle?.text = arguments?.getString("album_name") ?: firstSong.title
                    binding?.musicDetailSubtitle?.text = firstSong.artist
                }
            }
        }
    }
}
