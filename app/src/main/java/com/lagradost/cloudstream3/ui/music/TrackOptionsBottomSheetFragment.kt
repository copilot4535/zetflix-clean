package com.lagradost.cloudstream3.ui.music

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentTrackOptionsBottomSheetBinding
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.drawableToBitmap
import coil3.asDrawable
import kotlinx.coroutines.launch
import android.content.Intent
import android.widget.Toast

class TrackOptionsBottomSheetFragment : BottomSheetDialogFragment() {
    private var _binding: FragmentTrackOptionsBottomSheetBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MusicViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrackOptionsBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(view.parent as View)
        val screenHeight = resources.displayMetrics.heightPixels
        behavior.peekHeight = (screenHeight * 0.5).toInt()
        behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED

        observeViewModel()
        setupClickListeners()
    }

    private fun observeViewModel() {
        viewModel.currentPlayingSong.observe(viewLifecycleOwner) { song ->
            if (song != null) {
                binding.trackTitle.text = song.title
                binding.trackArtist.text = song.artist ?: "Unknown Artist"
                binding.trackThumbnail.loadImage(song.thumbnailUrl) {
                    listener(onSuccess = { _, result ->
                        val bitmap = drawableToBitmap(result.image.asDrawable(resources))
                        if (bitmap != null) {
                            lifecycleScope.launch {
                                val palette = MusicColorHelper.getPalette(song.videoId, bitmap)
                                applyDynamicTheming(palette)
                            }
                        }
                    })
                }
                
                viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val liked = MusicPersistence.isSongLiked(song.videoId)
                    val downloaded = MusicPersistence.getDownloadedSongs().any { it.videoId == song.videoId }
                    viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        updateStates(liked, downloaded)
                    }
                }
            }
        }
    }

    private fun updateStates(liked: Boolean, downloaded: Boolean) {
        binding.actionSave.text = if (liked) "Remove from Saved" else "Save to Liked Songs"
        binding.actionSave.setCompoundDrawablesWithIntrinsicBounds(
            if (liked) R.drawable.ic_baseline_favorite_24 else R.drawable.ic_baseline_favorite_border_24,
            0, 0, 0
        )

        binding.actionDownload.text = if (downloaded) "Remove Download" else "Download"
        binding.actionDownload.setCompoundDrawablesWithIntrinsicBounds(
            if (downloaded) R.drawable.download_icon_done else R.drawable.netflix_download,
            0, 0, 0
        )
    }

    private fun applyDynamicTheming(palette: MusicPalette) {
        val lyricsPalette = MusicColorHelper.generateLyricsPalette(palette)
        val bg = lyricsPalette.background
        binding.root.backgroundTintList = ColorStateList.valueOf(bg)
        
        val primary = if (palette.isLight) Color.BLACK else Color.WHITE
        val secondary = if (palette.isLight) 0x99000000.toInt() else 0xB3FFFFFF.toInt()
        
        binding.trackTitle.setTextColor(primary)
        binding.trackArtist.setTextColor(secondary)
        
        val actions = listOf(
            binding.actionPlaylist, binding.actionQueue, binding.actionSave,
            binding.actionDownload, binding.actionShare, binding.actionArtist,
            binding.actionLyrics, binding.actionAbout, binding.actionRadio
        )
        
        actions.forEach { 
            it.setTextColor(primary)
            it.iconTint = ColorStateList.valueOf(primary)
        }
        
        binding.dragHandle.backgroundTintList = ColorStateList.valueOf(secondary)
    }

    private fun setupClickListeners() {
        binding.actionPlaylist.setOnClickListener {
            activity?.navigate(R.id.navigation_import_playlist)
            dismiss()
        }
        binding.actionQueue.setOnClickListener {
            viewModel.currentPlayingSong.value?.let { song ->
                viewModel.addToQueue(song)
                Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
            }
            dismiss()
        }
        binding.actionSave.setOnClickListener {
            viewModel.currentPlayingSong.value?.let { song ->
                viewModel.toggleLikeSong(song)
            }
        }
        binding.actionDownload.setOnClickListener {
            viewModel.currentPlayingSong.value?.let { song ->
                viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val isDownloaded = MusicPersistence.getDownloadedSongs().any { it.videoId == song.videoId }
                    viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        if (isDownloaded) {
                            viewModel.removeDownload(song.videoId)
                        } else {
                            viewModel.downloadSong(song)
                        }
                        dismiss()
                    }
                }
            }
        }
        binding.actionShare.setOnClickListener {
            viewModel.currentPlayingSong.value?.let { song ->
                val shareText = "Listening to ${song.title} by ${song.artist} on ZetFlix Music!\nhttps://www.youtube.com/watch?v=${song.videoId}"
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Share Song")
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                startActivity(Intent.createChooser(intent, "Share via"))
            }
            dismiss()
        }
        binding.actionArtist.setOnClickListener {
            viewModel.currentPlayingSong.value?.let { song ->
                val args = Bundle().apply {
                    putString("artist_name", song.artist)
                    putString("artist_id", song.artist)
                }
                activity?.navigate(R.id.music_nav_artist, args)
            }
            dismiss()
        }
        binding.actionLyrics.setOnClickListener {
            activity?.navigate(R.id.navigation_lyrics)
            dismiss()
        }
        binding.actionAbout.setOnClickListener {
            dismiss()
        }
        binding.actionRadio.setOnClickListener {
            viewModel.currentPlayingSong.value?.let { song ->
                viewModel.startRadio(song.videoId)
            }
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
