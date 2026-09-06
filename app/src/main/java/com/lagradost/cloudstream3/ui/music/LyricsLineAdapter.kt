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

class LyricsLineAdapter : ListAdapter<LyricLine, LyricsLineAdapter.LyricsLineViewHolder>(LyricsLineDiffCallback()) {

    var currentLineIndex: Int = -1
        set(value) {
            if (field == value) return
            val previousIndex = field
            field = value
            
            // Notify affected range to update hierarchy and animations
            // Use payload to prevent "tmp detached" crashes by forcing in-place updates
            val start = (minOf(previousIndex, value) - 5).coerceAtLeast(0)
            val end = (maxOf(previousIndex, value) + 5).coerceAtMost(itemCount - 1)
            if (start <= end) {
                notifyItemRangeChanged(start, end - start + 1, "HIGHLIGHT_UPDATE")
            }
        }

    private var palette: LyricsPalette? = null

    fun setPalette(palette: LyricsPalette) {
        this.palette = palette
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LyricsLineViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_lyrics_line, parent, false)
        return LyricsLineViewHolder(view)
    }

    override fun onBindViewHolder(holder: LyricsLineViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    override fun onBindViewHolder(
        holder: LyricsLineViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            // Partial update: just re-bind to update highlight state
            holder.bind(getItem(position), position)
        }
    }

    override fun onViewRecycled(holder: LyricsLineViewHolder) {
        holder.cancelAnimations()
        super.onViewRecycled(holder)
    }

    inner class LyricsLineViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.lyrics_line_text)

        fun cancelAnimations() {
            textView.animate().cancel()
        }

        fun bind(line: LyricLine, position: Int) {
            textView.text = line.text
            
            val p = palette ?: run {
                textView.setTextColor(Color.WHITE)
                textView.alpha = if (position == currentLineIndex) 1.0f else 0.5f
                return
            }

            val distance = kotlin.math.abs(position - currentLineIndex)
            val isActive = distance == 0 && currentLineIndex != -1

            // Typography Hierarchy
            val targetSize = when {
                isActive -> 32f
                distance <= 3 -> 26f
                else -> 22f
            }
            
            val targetTypeface = when {
                isActive -> Typeface.create("sans-serif-black", Typeface.BOLD)
                distance <= 3 -> Typeface.create("sans-serif-medium", Typeface.BOLD)
                else -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }

            val targetColor = when {
                isActive -> p.accent
                distance <= 3 -> p.foregroundSecondary
                else -> p.foregroundTertiary
            }

            val targetAlpha = when {
                isActive -> 1.0f
                distance <= 3 -> 0.75f
                else -> 0.5f
            }

            val targetScale = if (isActive) 1.05f else 1.0f

            textView.textSize = targetSize
            textView.typeface = targetTypeface
            textView.setTextColor(targetColor)
            
            // Smooth transitions for alpha and scale
            textView.animate()
                .alpha(targetAlpha)
                .scaleX(targetScale)
                .scaleY(targetScale)
                .setDuration(250)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }

    class LyricsLineDiffCallback : DiffUtil.ItemCallback<LyricLine>() {
        override fun areItemsTheSame(oldItem: LyricLine, newItem: LyricLine): Boolean = 
            oldItem.timestampMs == newItem.timestampMs && oldItem.text == newItem.text
        override fun areContentsTheSame(oldItem: LyricLine, newItem: LyricLine): Boolean = 
            oldItem == newItem
    }
}
