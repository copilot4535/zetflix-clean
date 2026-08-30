package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.MainAPI

object PluginPriorityManager {
    fun getPriorityTier(api: MainAPI): Int {
        val priorityPlugins = setOf("Netflix", "Prime Video", "HBO Max", "Disney+", "Hotstar")
        if (priorityPlugins.contains(api.name) && api.lang == "en") return 0
        if (api.lang == "en") return 1
        return 2
    }

    fun selectInitialPlugins(
        apis: List<MainAPI>,
        count: Int
    ): List<MainAPI> {
        val englishApis = apis.filter { it.lang == "en" }
        val priority = englishApis.filter { getPriorityTier(it) == 0 }.shuffled()
        val others = englishApis.filter { getPriorityTier(it) == 1 }.shuffled()

        // Only return English plugins for the initial load
        val result = (priority + others)
        return result.distinctBy { it.name + it.lang }.take(count)
    }
}
