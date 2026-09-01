package com.lagradost.cloudstream3.ui.home

import android.annotation.SuppressLint
import android.view.View
import androidx.activity.ComponentActivity
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentLivestreamBinding
import com.lagradost.cloudstream3.ui.settings.Globals.isLandscape
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.attachBackPressedCallback
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.detachBackPressedCallback
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding

class LiveStreamFragment : BaseHomeFragment<FragmentLivestreamBinding>(
    R.layout.fragment_livestream,
    FragmentLivestreamBinding::bind
) {
    override val viewModel: LiveStreamViewModel by viewModels()

    override val masterRecycler: RecyclerView
        get() = binding?.homeMasterRecycler ?: throw Exception("Binding is null")
    override val loadingView: View
        get() = binding?.homeLoading ?: throw Exception("Binding is null")
    override val errorView: View
        get() = binding?.homeLoadingError ?: throw Exception("Binding is null")
    override val loadingShimmer: com.facebook.shimmer.ShimmerFrameLayout?
        get() = binding?.homeLoadingShimmer
    override val errorText: android.widget.TextView?
        get() = binding?.resultErrorText
    override val reloadButton: View?
        get() = binding?.homeReloadConnectionerror

    override fun setupRecyclerView() {
        val adapter = ParentItemAdapter(
            id = "LiveStreamFragment".hashCode(),
            clickCallback = {
                viewModel.click(it)
            },
            moreInfoClickCallback = {
                viewModel.popup(it)
            },
            expandCallback = {
                viewModel.expand(it)
            }
        )
        masterRecycler.adapter = adapter
    }

    override fun onDestroyView() {
        (activity as? ComponentActivity)?.detachBackPressedCallback("LiveStreamFragment_BackPress")
        super.onDestroyView()
    }

    @SuppressLint("SetTextI18n")
    override fun onBindingCreated(binding: FragmentLivestreamBinding) {
        super.onBindingCreated(binding)
        (activity as? ComponentActivity)?.attachBackPressedCallback("LiveStreamFragment_BackPress") {
            runDefault()
        }
    }

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(
            view,
            padTop = false,
            padBottom = isLandscape(),
            padLeft = false
        )

        binding?.stickyHeader?.let {
            fixSystemBarsPadding(
                it,
                padBottom = false,
                padLeft = false,
                padRight = false
            )
        }

        binding?.homeMasterRecycler?.let {
            fixSystemBarsPadding(
                it,
                padTop = true,
                padBottom = false,
                padLeft = false,
                padRight = false
            )
        }
    }
}
