package com.lagradost.cloudstream3.ui.livestreams

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import androidx.viewpager2.widget.ViewPager2
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentHomeHeadBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.home.HomeParentItemAdapterPreview
import com.lagradost.cloudstream3.ui.home.HomeScrollAdapter
import com.lagradost.cloudstream3.ui.home.HomeScrollTransformer
import com.lagradost.cloudstream3.ui.home.ParentItemAdapter
import com.lagradost.cloudstream3.ui.result.getId
import com.lagradost.cloudstream3.ui.result.START_ACTION_RESUME_LATEST
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbarView
import com.lagradost.cloudstream3.ui.home.HomeViewModel

class LiveParentItemAdapterPreview(
    private val viewModel: LiveStreamViewModel,
) : ParentItemAdapter(
    id = "LiveParentItemAdapterPreview".hashCode(),
    clickCallback = {
        viewModel.click(it)
    }, moreInfoClickCallback = {
        // viewModel.popup(it)
    },
    expandCallback = {
        viewModel.expand(it)
    },
) {
    override val headers = 1
    override fun onCreateHeader(parent: ViewGroup): ViewHolderState<Bundle> {
        val inflater = LayoutInflater.from(parent.context)
        val binding = FragmentHomeHeadBinding.inflate(inflater, parent, false)

        return HeaderViewHolder(binding, viewModel)
    }

    override fun onBindHeader(holder: ViewHolderState<Bundle>) {
        (holder as? HeaderViewHolder)?.bind()
    }

    override fun onViewDetachedFromWindow(holder: ViewHolderState<Bundle>) {
        when (holder) {
            is HeaderViewHolder -> {
                holder.onViewDetachedFromWindow()
            }
        }
    }

    override fun onViewAttachedToWindow(holder: ViewHolderState<Bundle>) {
        when (holder) {
            is HeaderViewHolder -> {
                holder.onViewAttachedToWindow()
            }
        }
    }

    private class HeaderViewHolder(
        val binding: ViewBinding,
        val viewModel: LiveStreamViewModel,
    ) :
        ViewHolderState<Bundle>(binding) {

        override fun save(): Bundle = Bundle()
        override fun restore(state: Bundle) {}

        val previewAdapter = HomeScrollAdapter { view, position, item ->
            viewModel.click(
                LiveStreamViewModel.Companion.LoadClickCallback(0, view, position, item)
            )
        }

        private val previewViewpager: ViewPager2 =
            itemView.findViewById(R.id.home_preview_viewpager)

        private val previewViewpagerText: ViewGroup =
            itemView.findViewById(R.id.home_preview_viewpager_text)

        private val resumeHolder: View = itemView.findViewById(R.id.home_watch_holder)
        private val bookmarkHolder: View = itemView.findViewById(R.id.home_bookmarked_holder)
        private val homeNonePadding: View = itemView.findViewById(R.id.home_none_padding)
        private val toggleListHolder: View? = itemView.findViewById(R.id.home_type_holder)

        private val previewCallback: ViewPager2.OnPageChangeCallback =
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    previewAdapter.apply {
                        if ((position >= (itemCount - 1)) && hasMoreItems) {
                            hasMoreItems = false
                            viewModel.loadMoreHomeScrollResponses()
                        }
                    }
                    val item = previewAdapter.getItemOrNull(position) ?: return
                    onSelect(item, position)
                }
            }

        fun onSelect(item: LoadResponse, position: Int) {
            (binding as? FragmentHomeHeadBinding)?.apply {
                homePreviewPlay.setOnClickListener { view ->
                    viewModel.click(
                        LiveStreamViewModel.Companion.LoadClickCallback(
                            START_ACTION_RESUME_LATEST,
                            view,
                            position,
                            item
                        )
                    )
                }

                homePreviewInfo.setOnClickListener { view ->
                    viewModel.click(
                        LiveStreamViewModel.Companion.LoadClickCallback(0, view, position, item)
                    )
                }
                
                // Hide bookmark for now in Live
                homePreviewBookmark.isVisible = false
            }
        }

        fun onViewDetachedFromWindow() {
            previewViewpager.unregisterOnPageChangeCallback(previewCallback)
        }

        fun bind() = Unit

        init {
            previewViewpager.setPageTransformer(HomeScrollTransformer())
            previewViewpager.adapter = previewAdapter
            
            // Hide Home-specific sections
            resumeHolder.isVisible = false
            bookmarkHolder.isVisible = false
            toggleListHolder?.isVisible = false
        }

        private fun updatePreview(preview: Resource<Pair<Boolean, List<LoadResponse>>>) {
            if (preview is Resource.Success) {
                homeNonePadding.apply {
                    val params = layoutParams
                    params.height = 0
                    layoutParams = params
                }
            } else fixPaddingStatusbarView(homeNonePadding)

            when (preview) {
                is Resource.Success -> {
                    previewAdapter.submitList(preview.value.second)
                    previewAdapter.hasMoreItems = preview.value.first

                    previewViewpager.isVisible = true
                    previewViewpagerText.isVisible = true
                    val currentPos = previewViewpager.currentItem
                    val item = preview.value.second.getOrNull(currentPos)
                    item?.let {
                        onSelect(it, currentPos)
                    }
                }

                else -> {
                    previewAdapter.submitList(listOf())
                    previewViewpager.setCurrentItem(0, false)
                    previewViewpager.isVisible = false
                    previewViewpagerText.isVisible = false
                }
            }
        }

        fun onViewAttachedToWindow() {
            previewViewpager.registerOnPageChangeCallback(previewCallback)

            previewViewpager.apply {
                observe(viewModel.preview) {
                    updatePreview(it)
                }
            }
        }
    }
}
