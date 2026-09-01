package com.lagradost.cloudstream3.providers

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class FredTVProvider : MainAPI() {
    override var name = "Fred TV"
    override var mainUrl = "https://github.com/Fredolx/open-tv"
    override val supportedTypes = setOf(TvType.Live)

    override val hasMainPage = true

    private val sources = listOf(
        "https://iptv-org.github.io/iptv/index.m3u",
        "https://iptv-org.github.io/iptv/categories/sports.m3u",
        "https://iptv-org.github.io/iptv/categories/news.m3u",
        "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8",
        "https://raw.githubusercontent.com/freetv-org/samsung-tv-plus/main/playlists/samsung-us.m3u"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse = coroutineScope {
        val deferreds = sources.map { url ->
            async {
                try {
                    val response = app.get(url, timeout = 30).text
                    parseM3U(response)
                } catch (e: Exception) {
                    emptyList<HomePageList>()
                }
            }
        }
        
        val results = deferreds.awaitAll().flatten()
        
        // Merge lists with the same name
        val merged = results.groupBy { it.name }.map { (name, lists) ->
            HomePageList(name, lists.flatMap { it.list }.distinctBy { it.url })
        }

        newHomePageResponse(merged, false)
    }

    private fun parseM3U(m3u: String): List<HomePageList> {
        val categories = mutableMapOf<String, MutableList<SearchResponse>>()
        val lines = m3u.split("\n")
        
        var currentName = ""
        var currentLogo = ""
        var currentGroup = "General"

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith("#EXTINF")) {
                currentName = trimmedLine.substringAfterLast(",").trim()
                currentLogo = trimmedLine.substringAfter("tvg-logo=\"", "").substringBefore("\"")
                if (currentLogo == trimmedLine) currentLogo = ""
                
                currentGroup = trimmedLine.substringAfter("group-title=\"", "").substringBefore("\"")
                if (currentGroup == trimmedLine || currentGroup.isEmpty()) currentGroup = "General"
            } else if (trimmedLine.startsWith("http")) {
                val url = trimmedLine
                val name = currentName.ifEmpty { url.substringAfterLast("/").substringBefore(".") }
                
                val item = newLiveSearchResponse(name, url, TvType.Live) {
                    this.posterUrl = currentLogo
                }
                
                categories.getOrPut(currentGroup) { mutableListOf() }.add(item)
                
                // Reset for next item
                currentName = ""
                currentLogo = ""
                currentGroup = "General"
            }
        }
        
        return categories.map { (name, items) ->
            HomePageList(name, items)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val name = url.substringAfterLast("/").substringBefore("?").substringBefore(".")
        return newLiveStreamLoadResponse(
            name.ifEmpty { "Live Stream" },
            url,
            url,
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                data,
                type = ExtractorLinkType.M3U8
            ) {
                this.quality = getQualityFromName("")
            }
        )
        return true
    }
}
