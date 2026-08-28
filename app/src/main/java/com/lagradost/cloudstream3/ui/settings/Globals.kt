package com.lagradost.cloudstream3.ui.settings

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources

object Globals {
    var beneneCount = 0

    const val PHONE : Int = 0b001

    fun Context.updateTv() {
    }

    /** Returns true if the current orientation is landscape. */
    fun isLandscape(): Boolean =
        Resources.getSystem().configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    /** Returns true if the layout is PHONE.
     * Valid flag is: PHONE
     * */
    fun isLayout(flags: Int) : Boolean {
        return flags == PHONE
    }
}
