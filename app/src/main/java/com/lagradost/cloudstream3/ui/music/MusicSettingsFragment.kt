package com.lagradost.cloudstream3.ui.music

import android.content.ComponentName
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentMusicSettingsBinding
import com.lagradost.cloudstream3.services.music.MusicService
import com.lagradost.cloudstream3.ui.BaseFragment
import androidx.media3.session.SessionToken
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@androidx.media3.common.util.UnstableApi
class MusicSettingsFragment : BaseFragment<FragmentMusicSettingsBinding>(
    BindingCreator.Inflate(FragmentMusicSettingsBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportData(it) }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importData(it) }
    }

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
            viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                // Clear in-memory cache and cookies
                YouTubeInstance.youtube.cookie = null
                YouTubeInstance.youtube.visitorData =
                    com.maxrave.kotlinytmusicscraper.YouTube.DEFAULT_VISITOR_DATA
                // Clear persistence
                MusicPersistence.savePlaylists(emptyList())
                MusicPersistence.setLikedSongs(emptyList())
                MusicPersistence.setDownloadedSongs(emptyList())
                // Clear Search History
                com.lagradost.cloudstream3.CloudStreamApp.removeKey("music_search_history")

                viewModel.loadPersistenceData()
                
                viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(context, "Music data cleared", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding?.musicSettingEqualizer?.setOnClickListener {
            launchEqualizer()
        }

        binding?.musicSettingExport?.setOnClickListener {
            exportLauncher.launch("zetflix_music_backup_${System.currentTimeMillis()}.json")
        }

        binding?.musicSettingImport?.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "application/octet-stream"))
        }
    }

    private fun launchEqualizer() {
        try {
            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            if (intent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(context, "Equalizer not available on this device", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Equalizer not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportData(uri: android.net.Uri) {
        val backup = MusicBackupData(
            likedSongs = MusicPersistence.getLikedSongs(),
            history = MusicPersistence.getHistory(),
            playlists = MusicPersistence.getPlaylists(),
            searchHistory = MusicPersistence.getSearchHistory(),
            cookie = YouTubeInstance.youtube.cookie
        )
        
        val json = Json.encodeToString(backup)
        try {
            context?.contentResolver?.openOutputStream(uri)?.use { 
                it.write(json.toByteArray())
            }
            Toast.makeText(context, "Backup exported successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importData(uri: android.net.Uri) {
        try {
            val json = context?.contentResolver?.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (json != null) {
                val backup = Json.decodeFromString<MusicBackupData>(json)
                MusicPersistence.setLikedSongs(backup.likedSongs)
                
                // History overwrite for simplicity in restore
                // (Mergin history might be messy if it's large)
                // backup.history.forEach { song -> MusicPersistence.addSongToHistory(song) }
                
                MusicPersistence.savePlaylists(backup.playlists)
                
                backup.cookie?.let { YouTubeInstance.youtube.cookie = it }
                
                viewModel.loadPersistenceData()
                Toast.makeText(context, "Data restored successfully", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
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
