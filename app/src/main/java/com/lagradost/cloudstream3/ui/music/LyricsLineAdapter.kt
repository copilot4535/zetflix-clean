package com.lagradost.cloudstream3.ui.music

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R

class LyricsLineAdapter : ListAdapter<LrcLine, LyricsLineAdapter.LyricsLineViewHolder>(LyricsLineDiffCallback()) {

    var currentLineIndex: Int = -1
        set(value) {
            val oldIndex = field
            field = value
            notifyItemChanged(oldIndex)
            notifyItemChanged(value)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LyricsLineViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_lyrics_line, parent, false)
        return LyricsLineViewHolder(view)
    }

    override fun onBindViewHolder(holder: LyricsLineViewHolder, position: Int) {
        holder.bind(getItem(position), position == currentLineIndex)
    }

    inner class LyricsLineViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.lyrics_line_text)

        fun bind(line: LrcLine, isHighlighted: Boolean) {
            textView.text = line.text
            if (isHighlighted) {
                textView.setTextColor(Color.WHITE)
                textView.setTypeface(null, Typeface.BOLD)
                textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 32f)
            } else {
                textView.setTextColor(Color.parseColor("#B3B3B3"))
                textView.setTypeface(null, Typeface.NORMAL)
                textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20f)
            }
        }
    }

    class LyricsLineDiffCallback : DiffUtil.ItemCallback<LyricLine>() {
        override fun areItemsTheSame(oldItem: LyricLine, newItem: LyricLine): Boolean = oldItem.timestampMs == newItem.timestampMs
        override fun areContentsTheSame(oldItem: LyricLine, newItem: LyricLine): Boolean = oldItem == newItem
    }
}
