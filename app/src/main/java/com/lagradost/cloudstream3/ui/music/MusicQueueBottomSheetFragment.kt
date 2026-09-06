package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.lagradost.cloudstream3.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.databinding.LayoutMusicQueueBottomSheetBinding
import com.lagradost.cloudstream3.utils.UIHelper
import androidx.media3.common.util.UnstableApi

@UnstableApi
class MusicQueueBottomSheetFragment : BottomSheetDialogFragment() {
    private var _binding: LayoutMusicQueueBottomSheetBinding? = null
    private val binding get() = _binding!!

    override fun getTheme(): Int = R.style.MusicBottomSheetDialogTheme

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutMusicQueueBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            peekHeight = resources.displayMetrics.heightPixels * 3 / 4
            state = BottomSheetBehavior.STATE_HALF_EXPANDED
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Embed the existing MusicQueueFragment
        val queueFragment = MusicQueueFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_IS_CHILD, true) }
        }
        
        childFragmentManager.beginTransaction()
            .replace(R.id.queue_container, queueFragment)
            .commit()

        UIHelper.fixSystemBarsPadding(binding.root, padTop = false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_IS_CHILD = "is_child"
    }
}
