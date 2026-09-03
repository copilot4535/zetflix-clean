package com.lagradost.cloudstream3.ui.music

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.databinding.ItemMusicHomeCardBinding
import com.lagradost.cloudstream3.databinding.ItemMusicHomeSectionBinding
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage

class MusicHomeAdapter(private val onSectionItemClick: (MusicHomeSection, Int) -> Unit) :
    ListAdapter<MusicHomeSection, MusicHomeAdapter.SectionViewHolder>(SectionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SectionViewHolder {
        val binding = ItemMusicHomeSectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SectionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SectionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SectionViewHolder(private val binding: ItemMusicHomeSectionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(section: MusicHomeSection) {
            binding.sectionTitle.text = section.title
            val itemAdapter = MusicHomeItemAdapter { index ->
                onSectionItemClick(section, index)
            }
            binding.sectionRecycler.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                adapter = itemAdapter
            }
            itemAdapter.submitList(section.items)
        }
    }

    class SectionDiffCallback : DiffUtil.ItemCallback<MusicHomeSection>() {
        override fun areItemsTheSame(oldItem: MusicHomeSection, newItem: MusicHomeSection) = oldItem.title == newItem.title
        override fun areContentsTheSame(oldItem: MusicHomeSection, newItem: MusicHomeSection) = oldItem == newItem
    }
}

class MusicHomeItemAdapter(private val onItemClick: (Int) -> Unit) :
    ListAdapter<MusicHomeItem, MusicHomeItemAdapter.ItemViewHolder>(ItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = ItemMusicHomeCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class ItemViewHolder(private val binding: ItemMusicHomeCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MusicHomeItem, position: Int) {
            binding.cardTitle.text = item.title
            binding.cardSubtitle.text = item.subtitle
            binding.cardThumbnail.loadImage(item.thumbnailUrl)
            binding.root.setOnClickListener { onItemClick(position) }
        }
    }

    class ItemDiffCallback : DiffUtil.ItemCallback<MusicHomeItem>() {
        override fun areItemsTheSame(oldItem: MusicHomeItem, newItem: MusicHomeItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: MusicHomeItem, newItem: MusicHomeItem) = oldItem == newItem
    }
}
