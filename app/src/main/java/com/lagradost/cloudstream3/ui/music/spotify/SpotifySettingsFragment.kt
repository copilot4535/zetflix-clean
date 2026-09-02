package com.lagradost.cloudstream3.ui.music.spotify

import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.lagradost.cloudstream3.databinding.FragmentSpotifySettingsBinding
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.DataStoreHelper

class SpotifySettingsFragment : BaseFragment<FragmentSpotifySettingsBinding>(
    BindingCreator.Inflate(FragmentSpotifySettingsBinding::inflate)
) {
    override fun fixLayout(view: View) {}

    override fun onViewReady(view: View, savedInstanceState: Bundle?) {
        super.onViewReady(view, savedInstanceState)
        
        binding?.spotifyCookieEditText?.setText(DataStoreHelper.spotifySpDc)
        
        binding?.spotifySaveButton?.setOnClickListener {
            val cookie = binding?.spotifyCookieEditText?.text?.toString()
            DataStoreHelper.spotifySpDc = if (cookie.isNullOrBlank()) null else cookie
            Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
            activity?.onBackPressedDispatcher?.onBackPressed()
        }
    }
}
