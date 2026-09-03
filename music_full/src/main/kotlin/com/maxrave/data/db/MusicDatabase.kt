package com.maxrave.data.db

import DatabaseDao
import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.execSQL
import androidx.room.migration.Migration
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.maxrave.common.DB_NAME
import com.maxrave.domain.data.entities.AlbumEntity
import com.maxrave.domain.data.entities.ArtistEntity
import com.maxrave.domain.data.entities.AutoEqCurveEntity
import com.maxrave.domain.data.entities.AutoEqEntryEntity
import com.maxrave.domain.data.entities.AutoEqIndexMetaEntity
import com.maxrave.domain.data.entities.EpisodeEntity
import com.maxrave.domain.data.entities.FollowedArtistSingleAndAlbum
import com.maxrave.domain.data.entities.GoogleAccountEntity
import com.maxrave.domain.data.entities.LocalPlaylistEntity
import com.maxrave.domain.data.entities.LyricsEntity
import com.maxrave.domain.data.entities.NewFormatEntity
import com.maxrave.domain.data.entities.NotificationEntity
import com.maxrave.domain.data.entities.PairSongLocalPlaylist
import com.maxrave.domain.data.entities.PlaylistEntity
import com.maxrave.domain.data.entities.PodcastsEntity
import com.maxrave.domain.data.entities.QueueEntity
import com.maxrave.domain.data.entities.SearchHistory
import com.maxrave.domain.data.entities.SetVideoIdEntity
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.data.entities.SongInfoEntity
import com.maxrave.domain.data.entities.TranslatedLyricsEntity
import com.maxrave.domain.data.entities.YourYouTubePlaylistList
import com.maxrave.domain.data.entities.analytics.EventArtistEntity
import com.maxrave.domain.data.entities.analytics.PlaybackEventEntity
import com.maxrave.logger.Logger
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import org.koin.mp.KoinPlatform.getKoin
import kotlin.time.ExperimentalTime

@Database(
    entities = [
        NewFormatEntity::class, SongInfoEntity::class, SearchHistory::class, SongEntity::class, ArtistEntity::class,
        AlbumEntity::class, PlaylistEntity::class, LocalPlaylistEntity::class, LyricsEntity::class, QueueEntity::class,
        SetVideoIdEntity::class, PairSongLocalPlaylist::class, GoogleAccountEntity::class, FollowedArtistSingleAndAlbum::class,
        NotificationEntity::class, TranslatedLyricsEntity::class, PodcastsEntity::class, EpisodeEntity::class,
        YourYouTubePlaylistList::class, PlaybackEventEntity::class, EventArtistEntity::class,
        AutoEqEntryEntity::class, AutoEqIndexMetaEntity::class, AutoEqCurveEntity::class
    ],
    version = 25,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 2, to = 3), AutoMigration(
            from = 1,
            to = 3,
        ), AutoMigration(from = 3, to = 4), AutoMigration(from = 2, to = 4), AutoMigration(
            from = 3,
            to = 5,
        ), AutoMigration(4, 5), AutoMigration(6, 7), AutoMigration(
            7,
            8,
            spec = AutoMigration7_8::class,
        ), AutoMigration(8, 9),
        AutoMigration(9, 10),
        AutoMigration(from = 11, to = 12, spec = AutoMigration11_12::class),
        AutoMigration(13, 14),
        AutoMigration(14, 15),
        AutoMigration(15, 16),
        AutoMigration(16, 17),
        AutoMigration(17, 18),
        AutoMigration(16, 18),
        AutoMigration(15, 18),
        AutoMigration(18, 19),
        AutoMigration(17, 19),
        AutoMigration(16, 19),
        AutoMigration(19, 20),
        AutoMigration(18, 20),
        AutoMigration(17, 20),
        AutoMigration(20, 21),
        AutoMigration(19, 21),
        AutoMigration(18, 21),
        AutoMigration(21, 22),
        AutoMigration(20, 22),
        AutoMigration(19, 22),
        AutoMigration(22, 23),
        AutoMigration(21, 23),
        AutoMigration(20, 23),
        AutoMigration(23, 24),
        AutoMigration(22, 24),
        AutoMigration(21, 24),
        // 25 adds the AutoEq cache. Three new tables and nothing else, so Room generates
        // the migration itself — no spec, and no path by which existing rows can be touched.
        AutoMigration(24, 25),
        AutoMigration(23, 25),
        AutoMigration(22, 25),
    ],
)
@TypeConverters(Converters::class)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun getDatabaseDao(): DatabaseDao

    /**
     * Rewrite the database file so the pages a bulk delete freed go back to the filesystem.
     *
     * It lives here rather than on the DAO because the DAO has no way to ask for a **writer**
     * connection. `DatabaseDao.raw()` is the only door out to arbitrary SQL, and Room cannot parse
     * what a `@RawQuery` will do, so it generates `performSuspending(__db, isReadOnly = true, ...)`
     * for it — while a parsed `@Query` that deletes gets `isReadOnly = false`. Reader connections
     * are opened with `PRAGMA query_only = 1`, under which VACUUM fails outright with "attempt to
     * write a readonly database". `PRAGMA wal_checkpoint` is accepted on that very same connection,
     * which is why the sibling `DatabaseDao.checkpoint()` works and hid this for so long.
     *
     * [execSQL] prepares and steps the statement without opening a transaction, which is required:
     * SQLite refuses VACUUM inside one. Do not wrap this call in [androidx.room.Transactor.withTransaction].
     */
    suspend fun vacuum() {
        useWriterConnection { it.execSQL("VACUUM") }
    }
}

@OptIn(ExperimentalTime::class)
fun getDatabaseBuilder(converters: Converters) : RoomDatabase.Builder<MusicDatabase> {
    val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
    return Room
        .databaseBuilder(getKoin().get(), MusicDatabase::class.java, DB_NAME)
        .addTypeConverter(converters)
        .addMigrations(
            object : Migration(5, 6) {
                override fun migrate(connection: SQLiteConnection) {
                    val playlistSongMaps = mutableListOf<PairSongLocalPlaylist>()
                    connection.prepare("SELECT * FROM local_playlist").use { cursor ->
                        while (cursor.step()) {
                            if (!cursor.isNull(8)) {
                                val input = cursor.getText(8)
                                val tracks =
                                    json.decodeFromString<ArrayList<String?>?>(input)
                                Logger.w("MIGRATION_5_6", "tracks: $tracks")
                                tracks?.mapIndexed { index, track ->
                                    if (track != null) {
                                        playlistSongMaps.add(
                                            PairSongLocalPlaylist(
                                                playlistId = cursor.getLong(0),
                                                songId = track,
                                                position = index,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    connection.execSQL("ALTER TABLE `format` ADD COLUMN `lengthSeconds` INTEGER DEFAULT NULL")
                    connection.execSQL("ALTER TABLE `format` ADD COLUMN `youtubeCaptionsUrl` TEXT DEFAULT NULL")
                    connection.execSQL("ALTER TABLE `format` ADD COLUMN `cpn` TEXT DEFAULT NULL")
                    connection.execSQL(
                        "CREATE TABLE IF NOT EXISTS `pair_song_local_playlist` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `playlistId` INTEGER NOT NULL, `songId` TEXT NOT NULL, `position` INTEGER NOT NULL, `inPlaylist` INTEGER NOT NULL, FOREIGN KEY(`playlistId`) REFERENCES `local_playlist`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`songId`) REFERENCES `song`(`videoId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                    )
                    connection.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_pair_song_local_playlist_playlistId` ON `pair_song_local_playlist` (`playlistId`)",
                    )
                    connection.execSQL("CREATE INDEX IF NOT EXISTS `index_pair_song_local_playlist_songId` ON `pair_song_local_playlist` (`songId`)")
                    playlistSongMaps.forEach { pair ->
                        connection.execSQL(
                            "INSERT OR IGNORE INTO pair_song_local_playlist (playlistId, songId, position, inPlaylist) VALUES (${pair.playlistId}, '${pair.songId}', ${pair.position}, ${pair.inPlaylist.toInstant(TimeZone.UTC).toEpochMilliseconds()})"
                        )
                    }
                }
            },
            object : Migration(10, 11) {
                override fun migrate(connection: SQLiteConnection) {
                    val listYouTubeSyncedId = mutableListOf<Pair<String, List<String>>>() // Pair<youtubePlaylistId, listVideoId>
                    connection
                        .prepare(
                            "SELECT youtubePlaylistId, tracks FROM local_playlist WHERE synced_with_youtube_playlist = 1 AND youtubePlaylistId NOT NULL"
                        ).use { cursor ->
                            while (cursor.step()) {
                                val youtubePlaylistId = cursor.getText(0)
                                val input = cursor.getText(1)
                                val tracks =
                                    json.decodeFromString<ArrayList<String?>?>(input)
                                listYouTubeSyncedId.add(Pair(youtubePlaylistId, tracks?.toMutableList()?.filterNotNull() ?: emptyList()))
                            }
                        }
                    val setVideoIdList = mutableListOf<SetVideoIdEntity>()
                    connection.prepare("SELECT * FROM set_video_id").use { cursor ->
                        while (cursor.step()) {
                            val videoId = cursor.getText(0)
                            val setVideoId = cursor.getText(1)
                            for (pair in listYouTubeSyncedId) {
                                if (pair.second.contains(videoId)) {
                                    setVideoIdList.add(SetVideoIdEntity(videoId, setVideoId, pair.first))
                                    break
                                }
                            }
                        }
                    }
                    connection.execSQL("DROP TABLE set_video_id")
                    connection.execSQL(
                        "CREATE TABLE IF NOT EXISTS `set_video_id` (`videoId` TEXT NOT NULL, `setVideoId` TEXT, `youtubePlaylistId` TEXT NOT NULL, PRIMARY KEY(`videoId`, `youtubePlaylistId`))",
                    )
                    setVideoIdList.forEach { setVideoIdEntity ->
                        connection.execSQL(
                            "INSERT OR IGNORE INTO set_video_id (videoId, setVideoId, youtubePlaylistId) VALUES ('${setVideoIdEntity.videoId}', '${setVideoIdEntity.setVideoId}', '${setVideoIdEntity.youtubePlaylistId}')"
                        )
                    }
                }
            },
            object : Migration(12, 13) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE song ADD COLUMN canvasUrl TEXT")
                }
            },
        ).addCallback(
            object : RoomDatabase.Callback() {
                override fun onOpen(connection: SQLiteConnection) {
                    super.onOpen(connection)
                    connection.execSQL(
                        "CREATE TRIGGER  IF NOT EXISTS on_delete_pair_song_local_playlist AFTER DELETE ON pair_song_local_playlist\n" +
                            "FOR EACH ROW\n" +
                            "BEGIN\n" +
                            "    UPDATE pair_song_local_playlist\n" +
                            "    SET position = position - 1\n" +
                            "    WHERE playlistId = OLD.playlistId AND position > OLD.position;\n" +
                            "END;",
                    )
                }
            },
        )
}

fun getDatabasePath(): String {
    return getKoin().get<Context>().getDatabasePath(DB_NAME).path
}
