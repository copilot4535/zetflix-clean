package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.syncproviders.SyncIdName

class AniListApi : SyncAPI() {
    override val name: String = "AniList"
    override val idPrefix: String = "anilist"
    override val syncIdName: SyncIdName = SyncIdName.Anilist
}

class MalApi : SyncAPI() {
    override val name: String = "MyAnimeList"
    override val idPrefix: String = "mal"
    override val syncIdName: SyncIdName = SyncIdName.MyAnimeList
}

class TraktApi : SyncAPI() {
    override val name: String = "Trakt"
    override val idPrefix: String = "trakt"
    override val syncIdName: SyncIdName = SyncIdName.Trakt
}

class KitsuApi : SyncAPI() {
    override val name: String = "Kitsu"
    override val idPrefix: String = "kitsu"
    override val syncIdName: SyncIdName = SyncIdName.Kitsu
}

class SimklApi : SyncAPI() {
    override val name: String = "Simkl"
    override val idPrefix: String = "simkl"
    override val syncIdName: SyncIdName = SyncIdName.Simkl
}
