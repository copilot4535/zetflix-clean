package com.lagradost.cloudstream3.ui.music

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage

class MusicPlaylistAdapter(private val onPlaylistClick: (MusicPlaylist) -> Unit) :
    ListAdapter<MusicPlaylist, MusicPlaylistAdapter.PlaylistViewHolder>(PlaylistDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_music_song, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PlaylistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.music_song_title)
        private val subtitle: TextView = itemView.findViewById(R.id.music_song_artist)
        private val thumbnail: ImageView = itemView.findViewById(R.id.music_song_thumbnail)
        private val menu: View = itemView.findViewById(R.id.music_song_menu)

        fun bind(playlist: MusicPlaylist) {
            title.text = playlist.name
            subtitle.text = "${playlist.songs.size} songs"
            
            val firstSong = playlist.songs.firstOrNull()
            thumbnail.loadImage(firstSong?.thumbnailUrl)
            
            menu.visibility = View.GONE
            itemView.setOnClickListener { onPlaylistClick(playlist) }
        }
    }

    class PlaylistDiffCallback : DiffUtil.ItemCallback<MusicPlaylist>() {
        override fun areItemsTheSame(oldItem: MusicPlaylist, newItem: MusicPlaylist) = oldItem.name == newItem.name
        override fun areContentsTheSame(oldItem: MusicPlaylist, newItem: MusicPlaylist) = oldItem == newItem
    }
}
