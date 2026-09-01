package com.lagradost.cloudstream3.providers

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink

class SportsIPTVProvider : MainAPI() {
    override var name = "IPTV-Org Sports"
    override var mainUrl = "https://iptv-org.github.io"
    override val supportedTypes = setOf(TvType.Live)

    override val hasMainPage = true

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get("https://iptv-org.github.io/iptv/categories/sports.m3u").text
        val items = mutableListOf<SearchResponse>()
        
        val lines = response.split("\n")
        var currentName = ""
        var currentLogo = ""
        
        for (line in lines) {
            if (line.startsWith("#EXTINF")) {
                currentName = line.substringAfterLast(",").trim()
                currentLogo = line.substringAfter("tvg-logo=\"", "").substringBefore("\"")
                if (currentLogo == line) currentLogo = ""
            } else if (line.startsWith("http")) {
                val url = line.trim()
                val logo = currentLogo
                items.add(newLiveSearchResponse(
                    currentName,
                    url,
                    TvType.Live,
                ) {
                    this.posterUrl = logo
                })
            }
        }
        
        return newHomePageResponse("Sports", items)
    }

    override suspend fun load(url: String): LoadResponse {
        return newLiveStreamLoadResponse(
            url.substringAfterLast("/").substringBefore(".m3u8"),
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
            ) {
                this.quality = getQualityFromName("")
            }
        )
        return true
    }
}
