package com.lagradost.cloudstream3.ui.music

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.BottomSheetTrackOptionsBinding
import com.lagradost.cloudstream3.services.music.MusicService
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@UnstableApi
class TrackOptionsBottomSheetFragment : BottomSheetDialogFragment() {
    private var _binding: BottomSheetTrackOptionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MusicViewModel by activityViewModels()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private lateinit var track: MusicSearchResponse

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trackJson = arguments?.getString("track") ?: return dismiss()
        track = Json.decodeFromString(trackJson)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetTrackOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupController()
    }

    private fun setupUI() {
        binding.trackTitle.text = track.title
        binding.trackMetadata.text = "${track.artist ?: "Unknown Artist"}"
        binding.trackThumbnail.loadImage(track.thumbnailUrl)

        binding.optionPlayNext.setOnClickListener {
            viewModel.playNext(track)
            dismiss()
        }

        binding.optionAddQueue.setOnClickListener {
            viewModel.addToQueue(track)
            dismiss()
        }

        binding.optionGoArtist.setOnClickListener {
            val args = Bundle().apply {
                putString("artist_id", track.videoId) 
                putString("artist_name", track.artist)
            }
            activity?.navigate(R.id.music_nav_artist, args)
            dismiss()
        }

        binding.optionGoAlbum.setOnClickListener {
            // Usually we'd need an albumId, if not available this might not work
            dismiss()
        }

        binding.optionAddPlaylist.setOnClickListener {
            showAddToPlaylistDialog()
        }

        binding.optionDownload.setOnClickListener {
            viewModel.downloadSong(track)
            dismiss()
        }

        binding.optionShare.setOnClickListener {
            shareTrack()
            dismiss()
        }
    }

    private fun showAddToPlaylistDialog() {
        viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val playlists = MusicPersistence.getPlaylists()
            
            viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                if (playlists.isEmpty()) {
                    android.widget.Toast.makeText(context, "No playlists created", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    val names = playlists.map { it.name }.toTypedArray()
                    androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
                        .setTitle("Add to Playlist")
                        .setItems(names) { _, which ->
                            viewModel.addSongToPlaylist(names[which], track)
                            dismiss()
                        }
                        .show()
                }
            }
        }
    }

    private fun shareTrack() {
        val shareText = "Check out ${track.title} by ${track.artist} on ZetFlix Music!\nhttps://www.youtube.com/watch?v=${track.videoId}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, "Share via"))
    }

    private fun setupController() {
        val context = context ?: return
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
        }, MoreExecutors.directExecutor())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        _binding = null
    }

    companion object {
        fun newInstance(track: MusicSearchResponse): TrackOptionsBottomSheetFragment {
            return TrackOptionsBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putString("track", Json.encodeToString(track))
                }
            }
        }
    }
}
