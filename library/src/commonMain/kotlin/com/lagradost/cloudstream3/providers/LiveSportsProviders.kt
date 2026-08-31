package com.lagradost.cloudstream3.providers

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class CricHDProvider : MainAPI() {
    override var name = "CricHD"
    override var mainUrl = "https://crichd.mobile"
    override val supportedTypes = setOf(TvType.Live)
    override var hasMainPage = true

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val rows = document.select("table.table tr").drop(1)
        val items = rows.mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size < 4) return@mapNotNull null
            
            val title = cells[1].text()
            val href = cells[3].select("a").attr("href")
            val status = cells[4].text()
            
            if (status.lowercase().contains("finished")) return@mapNotNull null

            newLiveSearchResponse(title, href)
        }
        
        return newHomePageResponse(listOf(HomePageList("CricHD Live", items)), hasNext = false)
    }

    override suspend fun load(url: String): LoadResponse {
        return newLiveStreamLoadResponse(url, url, url)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val iframe = document.select("iframe").attr("src")
        
        if (iframe.isNotEmpty()) {
            val frameDoc = app.get(iframe, referer = data).document
            val script = frameDoc.select("script").html()
            val m3u8 = Regex("""source:\s*['"](.*\.m3u8.*)['"]""").find(script)?.groupValues?.get(1)
            
            if (m3u8 != null) {
                callback(newExtractorLink(this.name, this.name, m3u8, type = ExtractorLinkType.M3U8) {
                    referer = iframe
                })
            }
        }
        return true
    }
}

class IptvSportsProvider : MainAPI() {
    override var name = "IPTV Sports"
    override var mainUrl = "https://iptv-org.github.io"
    override val supportedTypes = setOf(TvType.Live)
    override var hasMainPage = true

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get("https://iptv-org.github.io/iptv/categories/sports.m3u").text
        val items = mutableListOf<LiveSearchResponse>()
        val lines = response.split("\n")
        var currentName = ""
        var currentIcon = ""
        
        lines.forEach { line ->
            if (line.startsWith("#EXTINF")) {
                currentName = line.substringAfter("tvg-name=\"").substringBefore("\"")
                if (currentName.isEmpty()) currentName = line.substringAfter(",").trim()
                currentIcon = line.substringAfter("tvg-logo=\"").substringBefore("\"")
            } else if (line.startsWith("http")) {
                items.add(newLiveSearchResponse(currentName, line) {
                    posterUrl = currentIcon
                })
            }
        }
        
        return newHomePageResponse(listOf(HomePageList("World Sports", items)), hasNext = false)
    }

    override suspend fun load(url: String): LoadResponse {
        return newLiveStreamLoadResponse(url, url, url)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback(newExtractorLink(this.name, this.name, data, type = ExtractorLinkType.M3U8))
        return true
    }
}

class SportsAggregatorProvider : MainAPI() {
    override var name = "Sports Aggregator"
    override var mainUrl = "https://crichd.live"
    override val supportedTypes = setOf(TvType.Live)
    override var hasMainPage = true
    
    // List of additional sites provided by user
    private val extraSites = listOf(
        "https://www.cricfree.tv/",
        "https://hd.cricfree.io/",
        "https://crictime.ch/",
        "https://webcric.eu/",
        "https://soccertvhd.com/",
        "https://cricty.net/",
        "https://allstream.cc/",
        "https://streameast.ec/",
        "https://crackstreams.ms/",
        "https://sportsurge.net/",
        "https://methstreams.ms/",
        "https://buffstreamsapp.com/",
        "https://buffsports.io/",
        "https://viprow.nu/",
        "https://vipleague.pm/",
        "https://streamed.pk/",
        "https://dofustream.cloud/",
        "https://www.strikeout.cc/",
        "https://livetv.sx/"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = extraSites.map { url ->
            newLiveSearchResponse(url.substringAfter("://").substringBefore("/"), url)
        }
        return newHomePageResponse(listOf(HomePageList("More Sports Sources", items)), hasNext = false)
    }

    override suspend fun load(url: String): LoadResponse {
        return newLiveStreamLoadResponse(url, url, url)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Generic iframe scraping
        val document = app.get(data).document
        val iframes = document.select("iframe")
        iframes.forEach { iframe ->
            val src = iframe.attr("src")
            if (src.contains(".m3u8")) {
                 callback(newExtractorLink(this.name, this.name, src, type = ExtractorLinkType.M3U8) {
                     referer = data
                 })
            }
        }
        return true
    }
}

class LegalSportsProvider : MainAPI() {
    override var name = "Legal Sports"
    override var mainUrl = "https://tubitv.com"
    override val supportedTypes = setOf(TvType.Live)
    override var hasMainPage = true

    private val legalLinks = listOf(
        "https://tubitv.com/category/sports_on_tubi",
        "https://pluto.tv/",
        "https://therokuchannel.roku.com/",
        "https://www.sling.com/freestream",
        "https://xumo.com/",
        "https://www.redbull.com/int-en/tv",
        "https://www.fifa.com/fifaplus",
        "https://www.cbssports.com/",
        "https://olympics.com/",
        "https://www.uefa.tv/",
        "https://www.nwslsoccer.com/nwsl-plus",
        "https://www.pwhl.com/",
        "https://www.worldsurfleague.com/",
        "https://victoryplus.com/",
        "https://www.rugbypass.tv/",
        "https://www.servustv.com/"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = legalLinks.map { url ->
            newLiveSearchResponse(url.substringAfter("://").replace("www.", "").substringBefore("/"), url)
        }
        return newHomePageResponse(listOf(HomePageList("Official Platforms", items)), hasNext = false)
    }

    override suspend fun load(url: String): LoadResponse {
        return newLiveStreamLoadResponse(url, url, url)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // These are mostly placeholders for now
        return false
    }
}
