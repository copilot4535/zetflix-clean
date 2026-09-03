package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentMusicArtistBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.navigate

@androidx.media3.common.util.UnstableApi
class MusicArtistFragment : BaseFragment<FragmentMusicArtistBinding>(
    BindingCreator.Inflate(FragmentMusicArtistBinding::inflate)
) {
    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var sectionAdapter: MusicHomeAdapter

    override fun fixLayout(view: View) {
    }

    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        
        setupRecyclerView()
        observeViewModel()

        val artistId = arguments?.getString("artist_id")
        if (artistId != null) {
            viewModel.loadArtistDetails(artistId)
        }

        binding?.musicArtistToolbar?.setNavigationOnClickListener {
            activity?.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        sectionAdapter = MusicHomeAdapter({ section, index ->
            val item = section.items[index]
            when (item.type) {
                MusicItemType.SONG -> {
                    val songItems = section.items.filter { it.type == MusicItemType.SONG }
                    val songs = songItems.map {
                        MusicSearchResponse(it.title, it.subtitle, it.id, it.thumbnailUrl)
                    }
                    val songIndex = songItems.indexOf(item).coerceAtLeast(0)
                    viewModel.playQueue(songs, songIndex)
                }
                MusicItemType.ALBUM -> {
                    val args = Bundle().apply {
                        putString("album_id", item.id)
                        putString("album_name", item.title)
                    }
                    activity?.navigate(R.id.music_nav_detail, args)
                }
                MusicItemType.PLAYLIST -> {
                    val args = Bundle().apply {
                        putString("playlist_id", item.id)
                        putString("playlist_name", item.title)
                    }
                    activity?.navigate(R.id.music_nav_detail, args)
                }
                MusicItemType.ARTIST -> {
                    val args = Bundle().apply {
                        putString("artist_id", item.id)
                        putString("artist_name", item.title)
                    }
                    activity?.navigate(R.id.music_nav_artist, args)
                }
            }
        })
        
        binding?.musicArtistTopTracks?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = sectionAdapter
        }
    }

    private fun observeViewModel() {
        observe(viewModel.homeSections) { resource ->
            if (resource is Resource.Success) {
                sectionAdapter.submitList(resource.value)
                val firstSection = resource.value.firstOrNull()
                binding?.musicArtistName?.text = arguments?.getString("artist_name") ?: firstSection?.title
                // Find an item with a thumbnail to use as header image
                val thumb = resource.value.flatMap { it.items }.firstOrNull { it.thumbnailUrl != null }?.thumbnailUrl
                binding?.musicArtistHeaderImage?.loadImage(thumb)
            }
        }
    }
}
