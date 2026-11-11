package com.svara.app.data.repository

import com.svara.app.data.db.MetadataOverrideEntity
import com.svara.app.data.db.SongEntity
import com.svara.app.domain.library.MetadataField
import com.svara.app.domain.library.TextKeys
import com.svara.app.domain.model.Song

/** Reads and writes one correctable field of a cached song row, keeping its sort and search keys in step. */
object MetadataFixes {

    fun read(song: SongEntity, field: MetadataField): String = when (field) {
        MetadataField.TITLE -> song.title
        MetadataField.ARTIST -> song.artist
        MetadataField.ALBUM -> song.album
    }

    fun write(song: SongEntity, field: MetadataField, value: String): SongEntity {
        val updated = when (field) {
            MetadataField.TITLE -> song.copy(title = value)
            MetadataField.ARTIST -> song.copy(artist = value)
            MetadataField.ALBUM -> song.copy(album = value)
        }
        return updated.copy(
            titleKey = TextKeys.sort(updated.title),
            artistKey = TextKeys.sort(updated.artist.ifBlank { Song.UNKNOWN_ARTIST }),
            albumKey = TextKeys.sort(updated.album.ifBlank { Song.UNKNOWN_ALBUM }),
            searchText = TextKeys.search("${updated.title} ${updated.artist} ${updated.album}"),
        )
    }

    /** Applies every stored correction for one song (after a rescan rewrote it). */
    fun apply(song: SongEntity, fixes: List<MetadataOverrideEntity>): SongEntity =
        fixes.fold(song) { s, fix -> MetadataField.entries.firstOrNull { it.name == fix.field }?.let { write(s, it, fix.value) } ?: s }
}
