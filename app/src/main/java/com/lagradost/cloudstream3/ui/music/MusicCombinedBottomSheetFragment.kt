package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.lagradost.cloudstream3.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayoutMediator
import com.lagradost.cloudstream3.databinding.LayoutMusicCombinedPanelBinding
import com.lagradost.cloudstream3.utils.UIHelper
import androidx.media3.common.util.UnstableApi

@UnstableApi
class MusicCombinedBottomSheetFragment : BottomSheetDialogFragment() {
    private var _binding: LayoutMusicCombinedPanelBinding? = null
    private val binding get() = _binding!!

    override fun getTheme(): Int = R.style.MusicBottomSheetDialogTheme

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutMusicCombinedPanelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            peekHeight = resources.displayMetrics.heightPixels / 2
            state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = CombinedPagerAdapter(this)
        binding.musicBottomSheetPager.adapter = adapter

        TabLayoutMediator(binding.musicBottomSheetTabs, binding.musicBottomSheetPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Lyrics"
                1 -> "Up Next"
                else -> ""
            }
        }.attach()

        (dialog as? BottomSheetDialog)?.behavior?.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED || 
                    newState == BottomSheetBehavior.STATE_COLLAPSED || 
                    newState == BottomSheetBehavior.STATE_HALF_EXPANDED) {
                    forceLyricsScroll()
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        binding.musicBottomSheetPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == TAB_LYRICS) {
                    forceLyricsScroll()
                }
            }
        })

        val initialTab = arguments?.getInt(ARG_INITIAL_TAB) ?: TAB_LYRICS
        binding.musicBottomSheetPager.setCurrentItem(initialTab, false)

        UIHelper.fixSystemBarsPadding(binding.root, padTop = false)
    }

    private fun forceLyricsScroll() {
        childFragmentManager.fragments.forEach { fragment ->
            (fragment as? LyricsFragment)?.forceLyricsScroll()
            // ViewPager2 uses childFragmentManager for its fragments
            fragment.childFragmentManager.fragments.filterIsInstance<LyricsFragment>().forEach {
                it.forceLyricsScroll()
            }
        }
    }

    private inner class CombinedPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 2
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> LyricsFragment().apply {
                    arguments = Bundle().apply { putBoolean(ARG_IS_CHILD, true) }
                }
                1 -> MusicQueueFragment().apply {
                    arguments = Bundle().apply { putBoolean(ARG_IS_CHILD, true) }
                }
                else -> Fragment()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_INITIAL_TAB = "initial_tab"
        const val TAB_LYRICS = 0
        const val TAB_QUEUE = 1
        const val ARG_IS_CHILD = "is_child"
    }
}
