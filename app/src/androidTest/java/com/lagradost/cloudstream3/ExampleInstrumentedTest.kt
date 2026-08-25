package com.lagradost.cloudstream3

import android.app.Activity
import android.os.Bundle
import android.os.PersistableBundle
import android.view.LayoutInflater
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.viewbinding.ViewBinding
import com.lagradost.cloudstream3.databinding.BottomResultviewPreviewBinding
import com.lagradost.cloudstream3.databinding.FragmentHomeBinding
import com.lagradost.cloudstream3.databinding.FragmentLibraryBinding
import com.lagradost.cloudstream3.databinding.FragmentPlayerBinding
import com.lagradost.cloudstream3.databinding.FragmentSearchBinding
import com.lagradost.cloudstream3.databinding.HomeResultGridBinding
import com.lagradost.cloudstream3.databinding.HomepageParentBinding
import com.lagradost.cloudstream3.databinding.PlayerCustomLayoutBinding
import com.lagradost.cloudstream3.databinding.RepositoryItemBinding
import com.lagradost.cloudstream3.databinding.SearchResultGridBinding
import com.lagradost.cloudstream3.databinding.SearchResultGridExpandedBinding
import com.lagradost.cloudstream3.databinding.TrailerCustomLayoutBinding
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.TestingUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith


/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class TestApplication : Activity() {
    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)
    }
}

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    private fun getAllProviders(): Array<MainAPI> {
        println("Providers: ${APIHolder.allProviders.size}")
        return APIHolder.allProviders.toTypedArray()
    }

    @Throws
    private inline fun <reified T : ViewBinding> testAllLayouts(
        activity: Activity,
       vararg layouts: Int
    ) {

        val bind = T::class.java.methods.first { it.name == "bind" }
        val inflater = LayoutInflater.from(activity)
        for (layout in layouts) {
            val root = inflater.inflate(layout, null, false)
            bind.invoke(null, root)
        }
    }

    @Test
    @Throws
    fun layoutTest() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity: MainActivity ->

                testAllLayouts<BottomResultviewPreviewBinding>(activity, R.layout.bottom_resultview_preview)

                testAllLayouts<FragmentPlayerBinding>(activity, R.layout.fragment_player)

                testAllLayouts<PlayerCustomLayoutBinding>(activity, R.layout.player_custom_layout, R.layout.trailer_custom_layout)
                testAllLayouts<TrailerCustomLayoutBinding>(activity, R.layout.player_custom_layout, R.layout.trailer_custom_layout)

                testAllLayouts<RepositoryItemBinding>(activity, R.layout.repository_item)

                testAllLayouts<FragmentHomeBinding>(activity, R.layout.fragment_home)

                testAllLayouts<FragmentSearchBinding>(activity, R.layout.fragment_search)

                testAllLayouts<HomeResultGridBinding>(activity, R.layout.home_result_grid)

                testAllLayouts<SearchResultGridExpandedBinding>(activity, R.layout.search_result_grid, R.layout.search_result_grid_expanded)
                testAllLayouts<SearchResultGridBinding>(activity, R.layout.search_result_grid, R.layout.search_result_grid_expanded)

                testAllLayouts<HomepageParentBinding>(activity, R.layout.homepage_parent)

                testAllLayouts<FragmentLibraryBinding>(activity, R.layout.fragment_library)
            }
        }
    }

    @Test
    @Throws(AssertionError::class)
    fun providerCorrectData() {
        val langTagsIETF = SubtitleHelper.languages.map { it.IETF_tag }
        Assert.assertFalse("IETFTagNames does not contain any languages", langTagsIETF.isNullOrEmpty())
        for (api in getAllProviders()) {
            Assert.assertTrue("Api does not contain a mainUrl", api.mainUrl != "NONE")
            Assert.assertTrue("Api does not contain a name", api.name != "NONE")
            Assert.assertTrue(
                "Api ${api.name} does not contain a valid language code",
                langTagsIETF.contains(api.lang)
            )
            Assert.assertTrue(
                "Api ${api.name} does not contain any supported types",
                api.supportedTypes.isNotEmpty()
            )
        }
        println("Done providerCorrectData")
    }

    @Test
    fun providerCorrectHomepage() {
        runBlocking {
            getAllProviders().toList().amap { api ->
                TestingUtils.testHomepage(api, TestingUtils.Logger())
            }
        }
        println("Done providerCorrectHomepage")
    }

    @Test
    fun testAllProvidersCorrect() {
        runBlocking {
            TestingUtils.getDeferredProviderTests(
                this,
                getAllProviders(),
            ) { _, _ -> }
        }
    }
}
