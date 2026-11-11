package com.svara.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema change ships with an explicit migration so updates never wipe playlists,
 * favorites, history, the queue or settings. Exported schemas live in app/schemas.
 *
 * Process for the next change: bump the version in [SvaraDatabase], build to export the new
 * schema JSON, add a MIGRATION_N_N+1 here, append it to [ALL_MIGRATIONS], and extend
 * DatabaseMigrationTest.
 */
object Migrations {

    /**
     * v1 → v2: the song cache gained precomputed sort/search keys and a visibility flag, history
     * and favorites got better indexes, and smart playlists were added.
     *
     * `songs` is only a cache of MediaStore metadata, so it is recreated empty and refilled by the
     * next scan (song ids are MediaStore ids, so playlists, favorites and history reattach).
     * Every user-data table is kept as is.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Song cache: rebuilt from MediaStore on the next scan.
            db.execSQL("DROP TABLE IF EXISTS `songs`")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `songs` (`id` INTEGER NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, " +
                    "`album` TEXT NOT NULL, `albumArtist` TEXT, `albumId` INTEGER NOT NULL, `artistId` INTEGER NOT NULL, " +
                    "`durationMs` INTEGER NOT NULL, `trackNumber` INTEGER NOT NULL, `discNumber` INTEGER NOT NULL, `year` INTEGER NOT NULL, " +
                    "`dateAddedSec` INTEGER NOT NULL, `dateModifiedSec` INTEGER NOT NULL, `sizeBytes` INTEGER NOT NULL, `mimeType` TEXT NOT NULL, " +
                    "`genre` TEXT, `volume` TEXT NOT NULL, `relativePath` TEXT NOT NULL, `fileName` TEXT NOT NULL, `dataPath` TEXT, " +
                    "`titleKey` TEXT NOT NULL, `artistKey` TEXT NOT NULL, `albumKey` TEXT NOT NULL, `searchText` TEXT NOT NULL, " +
                    "`visible` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_visible_titleKey` ON `songs` (`visible`, `titleKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_visible_artistKey` ON `songs` (`visible`, `artistKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_visible_albumKey` ON `songs` (`visible`, `albumKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_visible_dateAddedSec` ON `songs` (`visible`, `dateAddedSec`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_visible_durationMs` ON `songs` (`visible`, `durationMs`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_albumId` ON `songs` (`albumId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_volume_relativePath` ON `songs` (`volume`, `relativePath`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_genre` ON `songs` (`genre`)")

            // User data: keep rows, update indexes.
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_entries_songId` ON `playlist_entries` (`songId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_favorites_addedAt` ON `favorites` (`addedAt`)")
            db.execSQL("DROP INDEX IF EXISTS `index_play_events_songId`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_events_songId_playedAt` ON `play_events` (`songId`, `playedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_events_playedAt` ON `play_events` (`playedAt`)")

            // New: smart playlists.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `smart_playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`rule` TEXT NOT NULL, `limitCount` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `smart_playlist_pins` (`smartId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `songId` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`smartId`, `position`), FOREIGN KEY(`smartId`) REFERENCES `smart_playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
        }
    }

    /** v2 → v3: additive only. Saved looks (Theme Studio) and the album palette cache. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `saved_looks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `presetId` TEXT NOT NULL, `overrides` TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `album_palettes` (`albumId` INTEGER NOT NULL, `version` INTEGER NOT NULL, " +
                    "`accent` INTEGER, `muted` INTEGER, PRIMARY KEY(`albumId`))"
            )
        }
    }

    /** v2.1: custom covers (app-private images) and the palette's dominant colour. Additive only. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `cover_override` (`scope` TEXT NOT NULL, `key` INTEGER NOT NULL, `path` TEXT NOT NULL, " +
                    "`version` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`scope`, `key`))"
            )
            db.execSQL("ALTER TABLE `album_palettes` ADD COLUMN `dominant` INTEGER")
        }
    }

    /**
     * V3 core listening: listening time per play, playlist description / chosen cover / recently
     * deleted, rule-builder smart playlists, imported lyrics with timing offsets, and Library
     * Doctor metadata overrides. Additive only: columns are added with defaults, tables created.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `play_events` ADD COLUMN `listenedMs` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `playlists` ADD COLUMN `description` TEXT")
            db.execSQL("ALTER TABLE `playlists` ADD COLUMN `coverSongId` INTEGER")
            db.execSQL("ALTER TABLE `playlists` ADD COLUMN `deletedAt` INTEGER")
            db.execSQL("ALTER TABLE `smart_playlists` ADD COLUMN `rules` TEXT")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `lyrics_override` (`songId` INTEGER NOT NULL, `text` TEXT, " +
                    "`offsetMs` INTEGER NOT NULL DEFAULT 0, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`songId`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `metadata_override` (`songId` INTEGER NOT NULL, `field` TEXT NOT NULL, `value` TEXT NOT NULL, " +
                    "`original` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`songId`, `field`))"
            )
        }
    }

    /** V3 saved looks also keep customization (Now Playing style, visualiser and the rest). Additive. */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `saved_looks` ADD COLUMN `custom` TEXT")
        }
    }

    /** Recap cache: a new table only; history and every other table are untouched. */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `recap_cache` (`periodKey` TEXT NOT NULL, `signature` TEXT NOT NULL, " +
                    "`payload` TEXT NOT NULL, `computedAt` INTEGER NOT NULL, PRIMARY KEY(`periodKey`))"
            )
        }
    }

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
}
