package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.MainAPI

object PluginPriorityManager {
    fun getPriorityTier(api: MainAPI): Int {
        val priorityPlugins = setOf("Netflix", "Prime Video", "HBO Max", "Disney+", "Hotstar")
        if (priorityPlugins.contains(api.name)) return 0
        if (api.lang == "en") return 1
        return 2
    }

    fun selectInitialPlugins(
        apis: List<MainAPI>,
        count: Int
    ): List<MainAPI> {
        val priority = apis.filter { getPriorityTier(it) == 0 }.shuffled()
        val english = apis.filter { getPriorityTier(it) == 1 }.shuffled()
        val others = apis.filter { getPriorityTier(it) == 2 }.shuffled()

        val result = (priority + english + others)
        return result.distinctBy { it.name + it.lang }.take(count)
    }
}
