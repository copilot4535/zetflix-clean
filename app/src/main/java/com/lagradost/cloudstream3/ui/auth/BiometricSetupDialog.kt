package com.lagradost.cloudstream3.ui.auth

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.lagradost.cloudstream3.R

object BiometricSetupDialog {
    fun show(context: Context, onEnable: () -> Unit, onSkip: () -> Unit) {
        AlertDialog.Builder(context, R.style.AlertDialogCustom)
            .setTitle(R.string.fingerprint_setup_title)
            .setMessage(R.string.fingerprint_setup_description)
            .setPositiveButton(R.string.fingerprint_setup_enable) { _, _ ->
                onEnable()
            }
            .setNegativeButton(R.string.fingerprint_setup_skip) { _, _ ->
                onSkip()
            }
            .setCancelable(false)
            .show()
    }
}
