package com.lagradost.cloudstream3.ui.sports

import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.activityViewModels
import com.lagradost.cloudstream3.databinding.FragmentLivestreamBinding
import com.lagradost.cloudstream3.ui.home.LiveStreamFragment
import com.lagradost.cloudstream3.ui.home.LiveStreamViewModel
import com.lagradost.cloudstream3.ui.settings.Globals.isLandscape
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding

/**
 * A wrapper around LiveStreamFragment for the ZetSports experience.
 * It reuses the entire LiveStream pipeline but hides the internal header
 * to allow SportsActivity to provide the global Sports navigation shell.
 */
class ZetSportsFragment : LiveStreamFragment() {
    override val viewModel: LiveStreamViewModel by activityViewModels()

    override fun onBindingCreated(binding: FragmentLivestreamBinding) {
        super.onBindingCreated(binding)
        
        // 1. Hide the internal header (LiveStream title and search)
        // because SportsActivity provides its own "ZetSports" title and search icon.
        binding.headerContainer.isVisible = false
        
        // 2. Adjust search bar padding. 
        // In the original fragment, it has top padding for the status bar.
        // Here, it sits below the Activity header, so we remove that extra space.
        binding.livestreamSearchBar.updatePadding(top = 0)
    }

    /**
     * Triggered by the search icon in SportsActivity.
     */
    fun onSearchTriggered() {
        binding?.let { b ->
            b.stickyHeader.isGone = true
            b.livestreamSearchBar.isVisible = true
            b.livestreamSearchView.requestFocus()
        }
    }

    override fun fixLayout(view: View) {
        // Disable padTop because SportsActivity header already handles status bar padding
        fixSystemBarsPadding(
            view,
            padTop = false,
            padBottom = isLandscape(),
            padLeft = false
        )
    }
}
