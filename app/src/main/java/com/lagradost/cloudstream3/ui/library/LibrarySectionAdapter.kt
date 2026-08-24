package com.lagradost.cloudstream3.ui.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.databinding.LibrarySectionBinding
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.ui.search.SearchClickCallback

class LibrarySectionAdapter(
    private val clickCallback: (SearchClickCallback) -> Unit
) : ListAdapter<SyncAPI.Page, LibrarySectionAdapter.SectionViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SectionViewHolder {
        val binding = LibrarySectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SectionViewHolder(binding, clickCallback)
    }

    override fun onBindViewHolder(holder: SectionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SectionViewHolder(
        private val binding: LibrarySectionBinding,
        private val clickCallback: (SearchClickCallback) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(page: SyncAPI.Page) {
            binding.sectionTitle.text = page.title.asString(binding.root.context)
            
            // Re-use PageAdapter logic
            // Note: binding.sectionItemsRecycler is an AutofitRecyclerView
            val adapter = PageAdapter(binding.sectionItemsRecycler, clickCallback)
            binding.sectionItemsRecycler.adapter = adapter
            adapter.submitList(page.items)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SyncAPI.Page>() {
        override fun areItemsTheSame(oldItem: SyncAPI.Page, newItem: SyncAPI.Page): Boolean {
            return oldItem.title == newItem.title
        }

        override fun areContentsTheSame(oldItem: SyncAPI.Page, newItem: SyncAPI.Page): Boolean {
            // Using equals check for list content
            return oldItem.title == newItem.title && oldItem.items.size == newItem.items.size && oldItem.items == newItem.items
        }
    }
}
