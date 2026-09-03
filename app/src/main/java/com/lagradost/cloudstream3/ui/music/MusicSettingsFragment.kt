package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentMusicSettingsBinding
import com.lagradost.cloudstream3.ui.BaseFragment

@androidx.media3.common.util.UnstableApi
class MusicSettingsFragment : BaseFragment<FragmentMusicSettingsBinding>(
    BindingCreator.Inflate(FragmentMusicSettingsBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()

    override fun fixLayout(view: View) {}

    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        
        setupUI()
    }

    private fun setupUI() {
        binding?.musicSettingsToolbar?.setNavigationOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }

        binding?.musicSettingQuality?.setOnClickListener {
            showQualityDialog()
        }

        binding?.musicSettingRegion?.setOnClickListener {
            Toast.makeText(context, "Region settings coming soon", Toast.LENGTH_SHORT).show()
        }

        binding?.musicSettingCookie?.setOnClickListener {
            showCookieInputDialog()
        }

        binding?.musicSettingClearCache?.setOnClickListener {
            // Clear in-memory cache and cookies
            YouTubeInstance.youtube.cookie = null
            YouTubeInstance.youtube.visitorData = com.maxrave.kotlinytmusicscraper.YouTube.DEFAULT_VISITOR_DATA
            // Clear persistence
            MusicPersistence.savePlaylists(emptyList())
            MusicPersistence.setLikedSongs(emptyList())
            MusicPersistence.setDownloadedSongs(emptyList())
            // Clear Search History
            com.lagradost.cloudstream3.CloudStreamApp.removeKey("music_search_history")
            
            viewModel.loadPersistenceData()
            Toast.makeText(context, "Music data cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCookieInputDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
        builder.setTitle("YouTube Music Cookie")
        
        val input = android.widget.EditText(requireContext())
        input.hint = "Paste cookie here"
        input.setTextColor(android.graphics.Color.WHITE)
        input.setHintTextColor(android.graphics.Color.GRAY)
        
        val padding = 48
        val container = android.widget.FrameLayout(requireContext())
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(padding, 0, padding, 0)
        input.layoutParams = params
        container.addView(input)
        
        builder.setView(container)

        builder.setPositiveButton("Save") { _, _ ->
            val cookie = input.text.toString()
            if (cookie.isNotBlank()) {
                YouTubeInstance.youtube.cookie = cookie
                Toast.makeText(context, "Cookie saved (Temporary)", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    private fun showQualityDialog() {
        val options = arrayOf("Low (32kbps)", "Normal (128kbps)", "High (256kbps)", "Always Max")
        androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
            .setTitle("Audio Quality")
            .setItems(options) { _, which ->
                binding?.musicSettingQualitySummary?.text = options[which]
            }
            .show()
    }
}
