package com.lagradost.cloudstream3.ui.music

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R

class MusicSuggestionAdapter(private val onSuggestionClick: (String) -> Unit) :
    ListAdapter<String, MusicSuggestionAdapter.SuggestionViewHolder>(SuggestionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.search_history_item, parent, false)
        return SuggestionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SuggestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.home_history_title)
        private val removeIcon: View = itemView.findViewById(R.id.home_history_remove)

        fun bind(suggestion: String) {
            textView.text = suggestion
            removeIcon.visibility = View.GONE
            itemView.setOnClickListener { onSuggestionClick(suggestion) }
        }
    }

    class SuggestionDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
    }
}
