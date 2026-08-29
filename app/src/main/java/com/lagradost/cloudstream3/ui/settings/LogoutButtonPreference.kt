package com.lagradost.cloudstream3.ui.settings

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.ZetFlixSessionManager

class LogoutButtonPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    init {
        layoutResource = R.layout.preference_logout_button
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        
        val logoutButton = holder.findViewById(R.id.logout_button)
        logoutButton?.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle(R.string.logout_confirmation_title)
                .setMessage(R.string.logout_confirmation_message)
                .setPositiveButton(R.string.logout_button) { _, _ ->
                    ZetFlixSessionManager.logout(context)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }
}
