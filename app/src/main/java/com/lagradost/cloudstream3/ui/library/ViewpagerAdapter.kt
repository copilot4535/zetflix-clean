package com.lagradost.cloudstream3.ui.library

import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.doOnAttach
import androidx.recyclerview.widget.RecyclerView.OnFlingListener
import com.lagradost.cloudstream3.databinding.LibraryViewpagerPageBinding
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.ui.BaseAdapter
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.home.getSafeParcelable
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount

class ViewpagerAdapterViewHolderState(val binding: LibraryViewpagerPageBinding) :
    ViewHolderState<Bundle>(binding) {
    override fun save(): Bundle =
        Bundle().apply {
            putParcelable(
                "pageRecyclerview",
                binding.pageRecyclerview.layoutManager?.onSaveInstanceState()
            )
        }

    override fun restore(state: Bundle) {
        state.getSafeParcelable<Parcelable>("pageRecyclerview")?.let { recycle ->
            binding.pageRecyclerview.layoutManager?.onRestoreInstanceState(recycle)
        }
    }
}

class ViewpagerAdapter(
    val scrollCallback: (isScrollingDown: Boolean) -> Unit,
    val clickCallback: (SearchClickCallback) -> Unit
) : BaseAdapter<SyncAPI.Page, Bundle>(
    id = "ViewpagerAdapter".hashCode(),
    diffCallback = BaseDiffCallback(
        itemSame = { a, b ->
            a.title == b.title
        },
        contentSame = { a, b ->
            a.items == b.items && a.title == b.title
        }
    )) {

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Bundle> {
        return ViewpagerAdapterViewHolderState(
            LibraryViewpagerPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onUpdateContent(
        holder: ViewHolderState<Bundle>,
        item: SyncAPI.Page,
        position: Int
    ) {
        val binding = holder.view
        if (binding !is LibraryViewpagerPageBinding) return
        (binding.pageRecyclerview.adapter as? PageAdapter)?.submitList(item.items)
        binding.pageRecyclerview.scrollToPosition(0)
    }

    override fun onBindContent(holder: ViewHolderState<Bundle>, item: SyncAPI.Page, position: Int) {
        val binding = holder.view
        if (binding !is LibraryViewpagerPageBinding) return

        binding.pageRecyclerview.tag = position
        binding.pageRecyclerview.apply {
            spanCount = binding.root.context.getSpanCount()
            if (adapter == null) {
                doOnAttach {
                    adapter = PageAdapter(
                        this,
                        clickCallback
                    ).apply {
                        submitList(item.items)
                    }
                }
            } else {
                (adapter as? PageAdapter)?.submitList(item.items)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                    val diff = scrollY - oldScrollY
                    if (diff == 0) return@setOnScrollChangeListener

                    scrollCallback.invoke(diff > 0)
                }
            } else {
                onFlingListener = object : OnFlingListener() {
                    override fun onFling(velocityX: Int, velocityY: Int): Boolean {
                        scrollCallback.invoke(velocityY > 0)
                        return false
                    }
                }
            }
        }
    }
}
