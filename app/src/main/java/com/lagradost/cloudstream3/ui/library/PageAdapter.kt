package com.lagradost.cloudstream3.ui.library

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.databinding.LibrarySectionItemHorizontalBinding
import com.lagradost.cloudstream3.databinding.SearchResultGridExpandedBinding
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.ui.AutofitRecyclerView
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.search.SearchResultBuilder
import kotlin.math.roundToInt

class PageAdapter(
    private val resView: RecyclerView,
    val clickCallback: (SearchClickCallback) -> Unit,
    private val isHorizontal: Boolean = false
) :
    NoStateAdapter<SyncAPI.LibraryItem>(diffCallback = BaseDiffCallback(itemSame = { a, b ->
        if (a.id != null || b.id != null) {
            a.id == b.id
        } else {
            a.name == b.name && a.url == b.url
        }
    })) {
    init {
        if (isHorizontal) {
            resView.layoutManager = LinearLayoutManager(
                resView.context,
                LinearLayoutManager.HORIZONTAL,
                false
            )
        }
    }

    private val coverHeight: Int
        get() {
            return if (isHorizontal) {
                val targetWidthPx = (105 * resView.resources.displayMetrics.density).toInt()
                (targetWidthPx / 0.7f).roundToInt()
            } else {
                (((resView as? AutofitRecyclerView)?.itemWidth ?: 0) / 0.68f).roundToInt()
            }
        }

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        return if (isHorizontal) {
            ViewHolderState(
                LibrarySectionItemHorizontalBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        } else {
            ViewHolderState(
                SearchResultGridExpandedBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }
    }

    override fun onClearView(holder: ViewHolderState<Any>) {
        when (val binding = holder.view) {
            is SearchResultGridExpandedBinding -> {
                clearImage(binding.imageView)
            }
            is LibrarySectionItemHorizontalBinding -> {
                clearImage(binding.imageView)
            }
        }
    }

    override fun onBindContent(
        holder: ViewHolderState<Any>,
        item: SyncAPI.LibraryItem,
        position: Int
    ) {
        val (imageView, watchProgress, imageText) = when (val binding = holder.view) {
            is SearchResultGridExpandedBinding -> Triple(binding.imageView, binding.watchProgress, binding.imageText)
            is LibrarySectionItemHorizontalBinding -> Triple(binding.imageView, binding.watchProgress, binding.imageText)
            else -> return
        }

        /** https://stackoverflow.com/questions/8817522/how-to-get-color-code-of-image-view */
        SearchResultBuilder.bind(
            this@PageAdapter.clickCallback,
            item,
            position,
            holder.itemView,
        )

        // See searchAdaptor for this, it basically fixes the height
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            coverHeight
        )
        if (params.height != imageView.layoutParams.height || params.width != imageView.layoutParams.width) {
            imageView.layoutParams = params
        }

        val showProgress = item.episodesCompleted?.let { it > 0 } ?: false && item.episodesTotal != null
        watchProgress.visibility = if (showProgress) android.view.View.VISIBLE else android.view.View.GONE
        if (showProgress) {
            watchProgress.max = item.episodesTotal ?: 0
            watchProgress.progress = item.episodesCompleted ?: 0
        }

        imageText.text = item.name
    }
}