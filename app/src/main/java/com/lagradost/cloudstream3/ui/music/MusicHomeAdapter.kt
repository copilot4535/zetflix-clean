package com.lagradost.cloudstream3.ui.music

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import com.lagradost.cloudstream3.databinding.ItemMusicHomeCardBinding
import com.lagradost.cloudstream3.databinding.ItemMusicHomeSectionBinding
import com.lagradost.cloudstream3.databinding.ItemMusicArtistCardBinding
import com.lagradost.cloudstream3.databinding.ItemMusicGenreCardBinding
import com.lagradost.cloudstream3.databinding.ItemMusicChartCardBinding
import com.lagradost.cloudstream3.databinding.ItemMusicPodcastCardBinding
import com.lagradost.cloudstream3.databinding.ItemMusicPodcastCardVerticalBinding
import com.lagradost.cloudstream3.databinding.ItemMusicTrendingCardBinding
import com.lagradost.cloudstream3.databinding.LayoutMusicHomeHeaderBinding
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import coil3.asDrawable
import com.lagradost.cloudstream3.utils.drawableToBitmap

class MusicHomeAdapter(
    private val onSectionItemClick: (MusicHomeSection, Int) -> Unit,
    private val onSeeAllClick: (MusicHomeSection) -> Unit = {},
    private val onPrefetch: (String, String?) -> Unit = { _, _ -> },
    private val onHeaderClick: (Int) -> Unit = {},
    private val onChipChecked: (Int) -> Unit = {}
) : ListAdapter<MusicHomeSection, RecyclerView.ViewHolder>(SectionDiffCallback()) {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_SECTION = 1
        const val HEADER_ID = "HEADER_UNIQUE_ID"
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).title == HEADER_ID) TYPE_HEADER else TYPE_SECTION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(LayoutMusicHomeHeaderBinding.inflate(inflater, parent, false))
        } else {
            SectionViewHolder(ItemMusicHomeSectionBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is HeaderViewHolder) {
            holder.bind()
        } else if (holder is SectionViewHolder) {
            holder.bind(item)
        }
    }

    inner class HeaderViewHolder(private val binding: LayoutMusicHomeHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            binding.musicHomeSearch.setOnClickListener { onHeaderClick(R.id.music_home_search) }
            binding.musicHomeHistory.setOnClickListener { onHeaderClick(R.id.music_home_history) }
            binding.musicHomeSettings.setOnClickListener { onHeaderClick(R.id.music_home_settings) }
            binding.musicHomeProfile.setOnClickListener { onHeaderClick(R.id.music_home_profile) }
            
            binding.musicHomeChipGroup.setOnCheckedChangeListener { _, checkedId ->
                onChipChecked(checkedId)
            }
        }
    }

    inner class SectionViewHolder(private val binding: ItemMusicHomeSectionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val viewPool = RecyclerView.RecycledViewPool()

        fun bind(section: MusicHomeSection) {
            binding.sectionTitle.text = section.title
            
            // "See All" logic - only show if params are present and section is large
            val showSeeAll = section.params != null && section.items.size > 5
            binding.sectionSeeAll.visibility = if (showSeeAll) View.VISIBLE else View.GONE
            binding.sectionSeeAll.setOnClickListener { onSeeAllClick(section) }

            val isPodcast = section.title.contains("Podcast", true)
            val isTrending = section.title.contains("Trending", true)
            val itemViewType = when {
                section.title.contains("Artist", true) -> ItemViewType.ARTIST
                section.title.contains("Genre", true) || section.title.contains("Mood", true) -> ItemViewType.GENRE
                section.title.contains("Chart", true) -> ItemViewType.CHART
                isPodcast -> ItemViewType.PODCAST_VERTICAL
                isTrending -> ItemViewType.TRENDING
                else -> ItemViewType.NORMAL
            }

            val itemAdapter = MusicHomeItemAdapter(itemViewType, { index ->
                onSectionItemClick(section, index)
            }, onPrefetch)
            
            binding.sectionRecycler.apply {
                setRecycledViewPool(viewPool)
                layoutManager = when {
                    isPodcast -> LinearLayoutManager(context)
                    isTrending -> GridLayoutManager(context, 5, GridLayoutManager.HORIZONTAL, false)
                    else -> LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                }
                onFlingListener = null
                if (isTrending) {
                    PagerSnapHelper().attachToRecyclerView(this)
                }
                adapter = itemAdapter
                isNestedScrollingEnabled = false
            }
            itemAdapter.submitList(section.items)
        }
    }

    enum class ItemViewType {
        NORMAL, ARTIST, GENRE, CHART, PODCAST, PODCAST_VERTICAL, TRENDING
    }

    class SectionDiffCallback : DiffUtil.ItemCallback<MusicHomeSection>() {
        override fun areItemsTheSame(oldItem: MusicHomeSection, newItem: MusicHomeSection) = oldItem.title == newItem.title
        override fun areContentsTheSame(oldItem: MusicHomeSection, newItem: MusicHomeSection) = oldItem == newItem
    }
}

class MusicHomeItemAdapter(
    private val viewType: MusicHomeAdapter.ItemViewType,
    private val onItemClick: (Int) -> Unit,
    private val onPrefetch: (String, String?) -> Unit = { _, _ -> }
) : ListAdapter<MusicHomeItem, RecyclerView.ViewHolder>(ItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, ignoredViewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            MusicHomeAdapter.ItemViewType.ARTIST -> ArtistViewHolder(ItemMusicArtistCardBinding.inflate(inflater, parent, false))
            MusicHomeAdapter.ItemViewType.GENRE -> GenreViewHolder(ItemMusicGenreCardBinding.inflate(inflater, parent, false))
            MusicHomeAdapter.ItemViewType.CHART -> ChartViewHolder(ItemMusicChartCardBinding.inflate(inflater, parent, false))
            MusicHomeAdapter.ItemViewType.PODCAST -> PodcastViewHolder(ItemMusicPodcastCardBinding.inflate(inflater, parent, false))
            MusicHomeAdapter.ItemViewType.PODCAST_VERTICAL -> PodcastVerticalViewHolder(ItemMusicPodcastCardVerticalBinding.inflate(inflater, parent, false))
            MusicHomeAdapter.ItemViewType.TRENDING -> TrendingViewHolder(ItemMusicTrendingCardBinding.inflate(inflater, parent, false))
            else -> NormalViewHolder(ItemMusicHomeCardBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (item.type == MusicItemType.SONG) {
            onPrefetch(item.id, item.params)
        }
        
        if (viewType == MusicHomeAdapter.ItemViewType.TRENDING) {
            val params = holder.itemView.layoutParams
            val recyclerWidth = (holder.itemView.parent as? RecyclerView)?.width ?: 0
            if (recyclerWidth > 0) {
                params.width = (recyclerWidth * 0.85).toInt()
                holder.itemView.layoutParams = params
            }
        }

        when (holder) {
            is NormalViewHolder -> holder.bind(item, position)
            is ArtistViewHolder -> holder.bind(item, position)
            is GenreViewHolder -> holder.bind(item, position)
            is ChartViewHolder -> holder.bind(item, position)
            is PodcastViewHolder -> holder.bind(item, position)
            is PodcastVerticalViewHolder -> holder.bind(item, position)
            is TrendingViewHolder -> holder.bind(item, position)
        }
    }

    inner class NormalViewHolder(private val binding: ItemMusicHomeCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MusicHomeItem, position: Int) {
            binding.cardTitle.text = item.title
            binding.cardSubtitle.text = item.subtitle
            binding.cardThumbnail.loadImage(item.thumbnailUrl)
            binding.cardPlayOverlayContainer.visibility = if (item.type == MusicItemType.SONG) View.VISIBLE else View.GONE
            binding.root.setOnClickListener { onItemClick(position) }
        }
    }

    inner class PodcastViewHolder(private val binding: ItemMusicPodcastCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MusicHomeItem, position: Int) {
            binding.podcastTitle.text = item.title
            binding.podcastSubtitle.text = item.subtitle
            binding.podcastThumbnail.loadImage(item.thumbnailUrl)
            binding.root.setOnClickListener { onItemClick(position) }
        }
    }

    inner class PodcastVerticalViewHolder(private val binding: ItemMusicPodcastCardVerticalBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MusicHomeItem, position: Int) {
            binding.podcastTitle.text = item.title
            binding.podcastSubtitle.text = item.subtitle
            binding.podcastAuthor.text = item.subtitle?.split("•")?.firstOrNull()?.trim() ?: "ZetFlix Podcast"
            
            binding.podcastThumbnail.loadImage(item.thumbnailUrl) {
                listener(onSuccess = { _, result ->
                    val bitmap = drawableToBitmap(result.image.asDrawable(binding.root.resources))
                    if (bitmap != null) {
                        androidx.palette.graphics.Palette.from(bitmap).generate { palette ->
                            val color = palette?.getDarkMutedColor(Color.parseColor("#121212")) ?: Color.parseColor("#121212")
                            binding.root.setCardBackgroundColor(color)
                        }
                    }
                })
            }
            binding.root.setOnClickListener { onItemClick(position) }
        }
    }

    inner class TrendingViewHolder(private val binding: ItemMusicTrendingCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MusicHomeItem, position: Int) {
            binding.trendingTitle.text = item.title
            binding.trendingArtist.text = item.subtitle
            binding.trendingThumbnail.loadImage(item.thumbnailUrl)
            binding.root.setOnClickListener { onItemClick(position) }
        }
    }

    inner class ArtistViewHolder(private val binding: ItemMusicArtistCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MusicHomeItem, position: Int) {
            binding.artistName.text = item.title
            binding.artistImage.loadImage(item.thumbnailUrl)
            binding.root.setOnClickListener { onItemClick(position) }
        }
    }

    inner class GenreViewHolder(private val binding: ItemMusicGenreCardBinding) : RecyclerView.ViewHolder(binding.root) {
        private val colors = listOf(
            intArrayOf(Color.parseColor("#E50914"), Color.parseColor("#80070F")),
            intArrayOf(Color.parseColor("#1ED760"), Color.parseColor("#159743")),
            intArrayOf(Color.parseColor("#00D2FF"), Color.parseColor("#3a7bd5")),
            intArrayOf(Color.parseColor("#f8ff00"), Color.parseColor("#3ad59f")),
            intArrayOf(Color.parseColor("#B06AB3"), Color.parseColor("#4568DC")),
            intArrayOf(Color.parseColor("#FF512F"), Color.parseColor("#DD2476"))
        )

        fun bind(item: MusicHomeItem, position: Int) {
            binding.genreName.text = item.title
            val gradient = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors[position % colors.size])
            gradient.cornerRadius = 16f * binding.root.resources.displayMetrics.density
            binding.genreBackground.background = gradient
            binding.root.setOnClickListener { onItemClick(position) }
        }
    }

    inner class ChartViewHolder(private val binding: ItemMusicChartCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MusicHomeItem, position: Int) {
            binding.chartTitle.text = item.title
            binding.chartSubtitle.text = item.subtitle
            binding.chartThumbnail.loadImage(item.thumbnailUrl)
            binding.chartRank.text = (position + 1).toString()
            binding.root.setOnClickListener { onItemClick(position) }
        }
    }

    class ItemDiffCallback : DiffUtil.ItemCallback<MusicHomeItem>() {
        override fun areItemsTheSame(oldItem: MusicHomeItem, newItem: MusicHomeItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: MusicHomeItem, newItem: MusicHomeItem) = oldItem == newItem
    }
}
