package com.lagradost.cloudstream3.ui.auth

import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lagradost.cloudstream3.R

object BiometricSetupDialog {
    fun show(
        activity: FragmentActivity,
        onEnable: () -> Unit,
        onSkip: () -> Unit
    ) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.fingerprint_setup_title)
            .setMessage(R.string.fingerprint_setup_description)
            .setPositiveButton(R.string.fingerprint_setup_enable) { dialog, _ ->
                onEnable()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.fingerprint_setup_skip) { dialog, _ ->
                onSkip()
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
}
