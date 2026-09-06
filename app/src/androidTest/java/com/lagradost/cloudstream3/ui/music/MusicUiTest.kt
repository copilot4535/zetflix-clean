package com.lagradost.cloudstream3.ui.music

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lagradost.cloudstream3.R
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicUiTest {

    @Test
    fun musicActivity_opensAndShowsBottomNav() {
        ActivityScenario.launch(MusicActivity::class.java).use {
            onView(withId(R.id.music_bottom_nav)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun miniPlayer_isHiddenInitially_whenNoMedia() {
        ActivityScenario.launch(MusicActivity::class.java).use {
            // By default, it should be gone if no media is playing
            onView(withId(R.id.global_mini_player)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }
}
