package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.lagradost.cloudstream3.databinding.DialogImportPlaylistBinding
import com.lagradost.cloudstream3.mvvm.launchSafe

class ImportPlaylistDialogFragment : DialogFragment() {
    private var _binding: DialogImportPlaylistBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MusicViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogImportPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.importConfirmBtn.setOnClickListener {
            val url = binding.importPlaylistUrl.text.toString()
            val listId = PlaylistImporter.extractListId(url)
            if (listId != null) {
                importPlaylist(listId)
            } else {
                Toast.makeText(context, "Invalid Playlist URL", Toast.LENGTH_SHORT).show()
            }
        }

        binding.importCancelBtn.setOnClickListener {
            dismiss()
        }
    }

    private fun importPlaylist(listId: String) {
        binding.importConfirmBtn.isEnabled = false
        binding.importConfirmBtn.text = "Importing..."
        
        lifecycleScope.launchSafe {
            val playlist = PlaylistImporter.importPlaylist(listId)
            if (playlist != null) {
                MusicPersistence.savePlaylists(MusicPersistence.getPlaylists() + playlist)
                viewModel.loadPersistenceData()
                Toast.makeText(context, "Playlist imported: ${playlist.name}", Toast.LENGTH_SHORT).show()
                dismiss()
            } else {
                Toast.makeText(context, "Failed to import playlist", Toast.LENGTH_SHORT).show()
                binding.importConfirmBtn.isEnabled = true
                binding.importConfirmBtn.text = "Import"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
