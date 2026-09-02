package com.lagradost.cloudstream3.ui.music

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.databinding.ItemMusicSongBinding
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage

class MusicSearchAdapter(private val onSongClick: (MusicSearchResponse) -> Unit) :
    ListAdapter<MusicSearchResponse, MusicSearchAdapter.MusicViewHolder>(MusicDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val binding = ItemMusicSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MusicViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MusicViewHolder(private val binding: ItemMusicSongBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(song: MusicSearchResponse) {
            binding.musicSongTitle.text = song.title
            binding.musicSongArtist.text = song.artist ?: "Unknown Artist"
            binding.musicSongThumbnail.loadImage(song.thumbnailUrl)
            binding.root.setOnClickListener {
                onSongClick(song)
            }
        }
    }

    class MusicDiffCallback : DiffUtil.ItemCallback<MusicSearchResponse>() {
        override fun areItemsTheSame(oldItem: MusicSearchResponse, newItem: MusicSearchResponse): Boolean {
            return oldItem.videoId == newItem.videoId
        }

        override fun areContentsTheSame(oldItem: MusicSearchResponse, newItem: MusicSearchResponse): Boolean {
            return oldItem == newItem
        }
    }
}
