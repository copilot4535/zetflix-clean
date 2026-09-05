package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.databinding.BottomSheetSongCreditsBinding
import com.lagradost.cloudstream3.mvvm.launchSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@UnstableApi
class SongCreditsBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetSongCreditsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MusicViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSongCreditsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Extract technical specs from player
        val controller = (activity as? MusicActivity)?.getMediaControllerMedia3()
        val specs = SongMetadataParser.extractSpecs(controller)
        
        binding.creditsAudioSpecs.text = "${specs.codec ?: "Unknown"} • ${specs.container ?: "Unknown"} • ${specs.sampleRateKHz ?: ""} • ${specs.bitrateKbps ?: ""}"

        // Fetch credits from InnerTube
        viewModel.currentPlayingSong.value?.let { song ->
            fetchCredits(song.videoId)
        }
    }

    private fun fetchCredits(videoId: String) {
        // Placeholder for credits fetching logic
        // We'll try to find songwriters/producers in the metadata
        lifecycle.run {
            // In a real implementation, we would query InnerTube's track credits endpoint
            // For now, we'll set some placeholders or try to parse from existing data
            binding.creditsSongwriters.text = "Fetching from YouTube Music..."
            binding.creditsProducers.text = "Fetching from YouTube Music..."
            binding.creditsLabel.text = "Fetching from YouTube Music..."
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
