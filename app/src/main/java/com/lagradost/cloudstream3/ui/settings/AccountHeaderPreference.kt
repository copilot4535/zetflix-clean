package com.lagradost.cloudstream3.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.auth.ZetFlixAuthPrefs
import kotlin.math.absoluteValue

class AccountHeaderPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    init {
        layoutResource = R.layout.preference_account_header
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        
        val email = ZetFlixAuthPrefs.getStoredEmail(context) ?: ""
        val username = if (email.isNotEmpty()) email.substringBefore("@") else ""

        val usernameView = holder.findViewById(R.id.account_username) as? TextView
        val emailView = holder.findViewById(R.id.account_email) as? TextView
        val avatarView = holder.findViewById(R.id.account_avatar) as? ImageView

        usernameView?.text = username
        emailView?.text = email

        val backgrounds = listOf(
            R.drawable.profile_bg_blue,
            R.drawable.profile_bg_dark_blue,
            R.drawable.profile_bg_orange,
            R.drawable.profile_bg_pink,
            R.drawable.profile_bg_purple,
            R.drawable.profile_bg_red,
            R.drawable.profile_bg_teal
        )
        val bgIndex = if (username.isNotEmpty()) username.hashCode().absoluteValue % backgrounds.size else 0
        avatarView?.setBackgroundResource(backgrounds[bgIndex])
        avatarView?.setImageResource(R.drawable.ic_outline_account_circle_24)
    }
}
