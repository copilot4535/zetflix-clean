package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import android.view.View
import com.lagradost.cloudstream3.databinding.FragmentMusicLibraryBinding
import com.lagradost.cloudstream3.ui.BaseFragment

class MusicLibraryFragment : BaseFragment<FragmentMusicLibraryBinding>(
    BindingCreator.Inflate(FragmentMusicLibraryBinding::inflate)
) {
    override fun fixLayout(view: View) {
        // Implement fixLayout if needed
    }

    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        // Library specific logic
    }
}
