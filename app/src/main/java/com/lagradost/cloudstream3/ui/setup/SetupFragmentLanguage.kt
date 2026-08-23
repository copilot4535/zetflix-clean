package com.lagradost.cloudstream3.ui.setup

import android.view.View
import androidx.navigation.fragment.findNavController
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.databinding.FragmentSetupLanguageBinding
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding

const val HAS_DONE_SETUP_KEY = "HAS_DONE_SETUP"

class SetupFragmentLanguage : BaseFragment<FragmentSetupLanguageBinding>(
    BaseFragment.BindingCreator.Inflate(FragmentSetupLanguageBinding::inflate)
) {

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(view)
    }

    override fun onBindingCreated(binding: FragmentSetupLanguageBinding) {
        // Redirect if already setup or just go home
        setKey(HAS_DONE_SETUP_KEY, true)
        findNavController().navigate(R.id.navigation_home)
    }
}
