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
        val initialLoad = mutableListOf<MainAPI>()
        
        // Always prioritize current home page provider
        val currentHome = DataStoreHelper.currentHomePage
        if (currentHome != null) {
            apis.firstOrNull { it.name == currentHome }?.let {
                initialLoad.add(it)
            }
        }

        val englishApis = apis.filter { it.lang == "en" }
        val priority = englishApis.filter { getPriorityTier(it) == 0 }.shuffled()
        val others = englishApis.filter { getPriorityTier(it) == 1 }.shuffled()

        // Only return English plugins for the initial load
        initialLoad.addAll(priority + others)
        return initialLoad.distinctBy { it.name + it.lang }.take(count)
    }
}
