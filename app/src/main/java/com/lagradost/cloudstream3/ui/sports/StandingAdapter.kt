package com.lagradost.cloudstream3.ui.sports

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.databinding.ItemStandingBinding
import com.lagradost.cloudstream3.sports.domain.models.Standing
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage

class StandingAdapter : ListAdapter<Standing, StandingAdapter.StandingViewHolder>(StandingDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StandingViewHolder {
        val binding = ItemStandingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StandingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StandingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class StandingViewHolder(private val binding: ItemStandingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(standing: Standing) {
            binding.apply {
                standingPos.text = standing.position.toString()
                standingTeamName.text = standing.teamName
                standingLogo.loadImage(standing.teamLogoUrl)
                standingPlayed.text = standing.played.toString()
                standingGd.text = (if (standing.goalDifference > 0) "+" else "") + standing.goalDifference.toString()
                standingPts.text = standing.points.toString()
            }
        }
    }

    class StandingDiffCallback : DiffUtil.ItemCallback<Standing>() {
        override fun areItemsTheSame(oldItem: Standing, newItem: Standing): Boolean = oldItem.teamId == newItem.teamId
        override fun areContentsTheSame(oldItem: Standing, newItem: Standing): Boolean = oldItem == newItem
    }
}
