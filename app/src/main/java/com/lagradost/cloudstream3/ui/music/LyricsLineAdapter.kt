package com.lagradost.cloudstream3.ui.music

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.databinding.ItemLyricsLineBinding

class LyricsLineAdapter : ListAdapter<LrcLine, LyricsLineAdapter.ViewHolder>(DiffCallback) {
    var currentLineIndex = -1
        set(value) {
            val old = field
            field = value
            if (old != -1) notifyItemChanged(old)
            if (value != -1) notifyItemChanged(value)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLyricsLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val line = getItem(position)
        holder.binding.lyricsLineText.text = line.text
        holder.binding.lyricsLineText.alpha = if (position == currentLineIndex) 1f else 0.5f
    }

    class ViewHolder(val binding: ItemLyricsLineBinding) : RecyclerView.ViewHolder(binding.root)

    object DiffCallback : DiffUtil.ItemCallback<LrcLine>() {
        override fun areItemsTheSame(oldItem: LrcLine, newItem: LrcLine) = oldItem.timeMs == newItem.timeMs
        override fun areContentsTheSame(oldItem: LrcLine, newItem: LrcLine) = oldItem == newItem
    }
}
