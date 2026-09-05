package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.ImageButton
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentMusicLibraryBinding
import com.lagradost.cloudstream3.databinding.ItemMusicLibraryHeaderBinding
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@androidx.media3.common.util.UnstableApi
class MusicLibraryFragment : BaseFragment<FragmentMusicLibraryBinding>(
    BindingCreator.Inflate(FragmentMusicLibraryBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var likedSongsAdapter: MusicSearchAdapter
    private lateinit var historyAdapter: MusicSearchAdapter
    private lateinit var downloadsAdapter: MusicSearchAdapter
    private lateinit var playlistsAdapter: MusicPlaylistAdapter
    
    private lateinit var likedHeader: HeaderAdapter
    private lateinit var downloadsHeader: HeaderAdapter
    private lateinit var playlistsHeader: HeaderAdapter
    private lateinit var historyHeader: HeaderAdapter

    private lateinit var mainAdapter: ConcatAdapter

    override fun fixLayout(view: View) {
        // Implement fixLayout if needed
    }

    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        
        setupRecyclerViews()
        observeViewModel()
        
        viewModel.loadPersistenceData()
    }

    private fun setupRecyclerViews() {
        likedSongsAdapter = MusicSearchAdapter({ index ->
            viewModel.playQueue(likedSongsAdapter.currentList, index)
        }, { _, song ->
            showSongMenu(song)
        })

        downloadsAdapter = MusicSearchAdapter({ index ->
            viewModel.playQueue(downloadsAdapter.currentList, index)
        }, { _, song ->
            showSongMenu(song)
        })

        playlistsAdapter = MusicPlaylistAdapter { playlist ->
            val args = Bundle().apply {
                putString("playlist_name", playlist.name)
                putString("playlist_id", "local_${playlist.name}") 
            }
            activity?.navigate(R.id.music_nav_detail, args)
        }

        historyAdapter = MusicSearchAdapter({ index ->
            viewModel.playQueue(historyAdapter.currentList, index)
        }, { _, song ->
            showSongMenu(song)
        })

        likedHeader = HeaderAdapter("Liked Songs", R.drawable.ic_baseline_favorite_24, onClick = {
            // Toggle logic could be implemented by removing likedSongsAdapter from mainAdapter
        })

        downloadsHeader = HeaderAdapter("Downloads", R.drawable.netflix_download, onClick = {
            activity?.navigate(R.id.music_nav_downloads)
        })

        playlistsHeader = HeaderAdapter("Playlists", null,
            action1 = R.drawable.ic_baseline_add_24 to { showCreatePlaylistDialog() },
            action2 = R.drawable.baseline_save_as_24 to { activity?.navigate(R.id.navigation_import_playlist) }
        )

        historyHeader = HeaderAdapter("Recently Played", null,
            action1 = R.drawable.ic_baseline_arrow_forward_24 to { activity?.navigate(R.id.navigation_music_history) }
        )

        mainAdapter = ConcatAdapter(
            likedHeader, likedSongsAdapter,
            downloadsHeader, downloadsAdapter,
            playlistsHeader, playlistsAdapter,
            historyHeader, historyAdapter
        )

        binding?.libraryMainRecycler?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = mainAdapter
        }

        binding?.librarySettings?.setOnClickListener {
            activity?.navigate(R.id.music_nav_settings)
        }
    }

    class HeaderAdapter(
        private val title: String,
        private val iconRes: Int?,
        private val action1: Pair<Int, () -> Unit>? = null,
        private val action2: Pair<Int, () -> Unit>? = null,
        private val onClick: (() -> Unit)? = null
    ) : RecyclerView.Adapter<HeaderAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemMusicLibraryHeaderBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemMusicLibraryHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.binding.libraryHeaderTitle.text = title
            if (iconRes != null) {
                holder.binding.libraryHeaderIcon.setImageResource(iconRes)
                holder.binding.libraryHeaderIconCard.visibility = View.VISIBLE
            } else {
                holder.binding.libraryHeaderIconCard.visibility = View.GONE
            }

            holder.binding.root.setOnClickListener { onClick?.invoke() }

            if (action1 != null) {
                holder.binding.libraryHeaderAction1.setImageResource(action1.first)
                holder.binding.libraryHeaderAction1.visibility = View.VISIBLE
                holder.binding.libraryHeaderAction1.setOnClickListener { action1.second() }
            } else {
                holder.binding.libraryHeaderAction1.visibility = View.GONE
            }

            if (action2 != null) {
                holder.binding.libraryHeaderAction2.setImageResource(action2.first)
                holder.binding.libraryHeaderAction2.visibility = View.VISIBLE
                holder.binding.libraryHeaderAction2.setOnClickListener { action2.second() }
            } else {
                holder.binding.libraryHeaderAction2.visibility = View.GONE
            }
        }

        override fun getItemCount(): Int = 1
    }

    private fun showSongMenu(song: MusicSearchResponse) {
        val args = Bundle().apply {
            putString("track", Json.encodeToString(song))
        }
        activity?.navigate(R.id.navigation_track_options, args)
    }

    private fun showCreatePlaylistDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
        builder.setTitle("Create Playlist")
        
        val input = android.widget.EditText(requireContext())
        input.hint = "Playlist Name"
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

        builder.setPositiveButton("Create") { _, _ ->
            val name = input.text.toString()
            if (name.isNotBlank()) {
                viewModel.createPlaylist(name)
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    private fun observeViewModel() {
        viewModel.likedSongs.observe(viewLifecycleOwner) { songs ->
            likedSongsAdapter.submitList(songs)
        }

        viewModel.history.observe(viewLifecycleOwner) { history ->
            historyAdapter.submitList(history)
        }

        viewModel.downloadedSongs.observe(viewLifecycleOwner) { downloads ->
            downloadsAdapter.submitList(downloads)
        }

        viewModel.playlists.observe(viewLifecycleOwner) { playlists ->
            playlistsAdapter.submitList(playlists)
        }
    }
}
