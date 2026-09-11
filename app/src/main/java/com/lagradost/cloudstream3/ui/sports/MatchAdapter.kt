package com.lagradost.cloudstream3.ui.sports

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.databinding.ItemMatchBinding
import com.lagradost.cloudstream3.sports.domain.models.Match

class MatchAdapter : ListAdapter<Match, MatchAdapter.MatchViewHolder>(MatchDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
        val binding = ItemMatchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MatchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MatchViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MatchViewHolder(private val binding: ItemMatchBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(match: Match) {
            binding.apply {
                matchHomeTeamName.text = match.homeTeam.name
                matchAwayTeamName.text = match.awayTeam.name
                matchScore.text = if (match.homeScore != null && match.awayScore != null) {
                    "${match.homeScore} - ${match.awayScore}"
                } else {
                    "vs"
                }
                matchStatus.text = match.status.name
            }
        }
    }

    class MatchDiffCallback : DiffUtil.ItemCallback<Match>() {
        override fun areItemsTheSame(oldItem: Match, newItem: Match): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Match, newItem: Match): Boolean = oldItem == newItem
    }
}
