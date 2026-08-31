package com.lagradost.cloudstream3.ui.livestreams

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.home.ParentItemAdapter

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
    override val headers = 0

    override fun onViewDetachedFromWindow(holder: ViewHolderState<Bundle>) {
    }

    override fun onViewAttachedToWindow(holder: ViewHolderState<Bundle>) {
    }
}
