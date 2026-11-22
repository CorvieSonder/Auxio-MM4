/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistSync.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package org.oxycblt.auxio.music.tree

import android.content.ContentResolver
import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song

/**
 * The IDs that link a playlist file to playlists here and in MediaMonkey, read from or written as
 * extra "#" lines at the end of the file. Other M3U readers skip them.
 *
 * @param base The song paths at the last sync, only in files of an "Export changes" folder.
 * @param library Whether the playlist is a library playlist. Its file then lists no songs, as its
 *   songs come from its children on both sides.
 */
data class PlaylistIds(
    val auxioId: String? = null,
    val auxioParent: String? = null,
    val mmId: Long? = null,
    val mmParent: Long? = null,
    val baseName: String? = null,
    val base: List<String>? = null,
    val library: Boolean = false,
) {
    /** The lines to append to a playlist file. */
    fun toLines(): String = buildString {
        auxioId?.let { append("$AUXIO_ID$it\n") }
        auxioParent?.let { append("$AUXIO_PARENT$it\n") }
        mmId?.let { append("$MM_ID$it\n") }
        mmParent?.let { append("$MM_PARENT$it\n") }
        if (library) append("$LIBRARY\n")
        if (base != null) {
            append("$BASE_NAME${baseName ?: ""}\n")
            append("$BASE_START\n")
            base.forEach { append("$BASE$it\n") }
        }
    }

    companion object {
        private const val AUXIO_ID = "#AUXIO-ID:"
        private const val AUXIO_PARENT = "#AUXIO-PARENT:"
        private const val MM_ID = "#MM-ID:"
        private const val MM_PARENT = "#MM-PARENT:"
        private const val BASE_NAME = "#BASE-NAME:"
        private const val BASE_START = "#BASE-START"
        private const val BASE = "#BASE:"
        private const val LIBRARY = "#LIBRARY-PLAYLIST"

        /** Read the ID lines of a playlist file's [text]. */
        fun parse(text: String): PlaylistIds {
            var ids = PlaylistIds()
            var base: MutableList<String>? = null
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                when {
                    line.startsWith(AUXIO_ID) -> ids = ids.copy(auxioId = line.after(AUXIO_ID))
                    line.startsWith(AUXIO_PARENT) ->
                        ids = ids.copy(auxioParent = line.after(AUXIO_PARENT))
                    line.startsWith(MM_ID) ->
                        ids = ids.copy(mmId = line.after(MM_ID)?.toLongOrNull())
                    line.startsWith(MM_PARENT) ->
                        ids = ids.copy(mmParent = line.after(MM_PARENT)?.toLongOrNull())
                    line.startsWith(BASE_NAME) ->
                        ids = ids.copy(baseName = line.removePrefix(BASE_NAME))
                    line == LIBRARY -> ids = ids.copy(library = true)
                    line == BASE_START -> base = base ?: mutableListOf()
                    line.startsWith(BASE) -> base?.add(line.removePrefix(BASE))
                }
            }
            return ids.copy(base = base)
        }

        private fun String.after(prefix: String) = removePrefix(prefix).trim().ifEmpty { null }
    }
}

/** A playlist's name, parent and songs, as compared with the state at the last sync. */
data class PlaylistState(val name: String, val parentUid: Music.UID?, val songs: List<String>) {
    fun toLink(playlistUid: Music.UID, mmId: Long?) =
        PlaylistSyncLink(playlistUid, mmId, name, parentUid, songs.joinToString("\n"))

    companion object {
        fun of(link: PlaylistSyncLink) =
            PlaylistState(
                link.name,
                link.parentUid,
                if (link.songs.isEmpty()) listOf() else link.songs.split("\n"),
            )
    }
}

/** Keeps the links between playlists here and in MediaMonkey, and their state at the last sync. */
@Singleton
class PlaylistSyncRepository
@Inject
constructor(
    private val dao: PlaylistSyncDao,
    private val playlistTreeRepository: PlaylistTreeRepository,
) {
    suspend fun links(): Map<Music.UID, PlaylistSyncLink> =
        dao.getLinks().associateBy { it.playlistUid }

    suspend fun save(links: List<PlaylistSyncLink>) {
        if (links.isNotEmpty()) dao.insertLinks(links)
    }

    suspend fun forget(playlistUids: List<Music.UID>) {
        if (playlistUids.isNotEmpty()) dao.deleteLinks(playlistUids)
    }

    /** The current state of [playlist]. */
    suspend fun stateOf(playlist: Playlist): PlaylistState {
        val parent = withContext(Dispatchers.Main) { playlistTreeRepository.parentOf(playlist) }
        return PlaylistState(playlist.name.raw, parent?.uid, playlist.songs.map(::pathOf))
    }

    companion object {
        /** The absolute path of [song], as written in playlist files with absolute paths. */
        fun pathOf(song: Song): String {
            val volume = song.path.volume.components?.let { it.unixString + "/" } ?: ""
            return "/$volume${song.path.components.unixString}"
        }

        fun readText(resolver: ContentResolver, uri: Uri): String =
            resolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: ""

        /**
         * Add [text] to the end of the file at [uri]. Rewrites it, as not every provider can
         * append.
         */
        fun appendText(resolver: ContentResolver, uri: Uri, text: String) {
            if (text.isEmpty()) return
            val current = readText(resolver, uri)
            val joined = if (current.isEmpty() || current.endsWith("\n")) current else "$current\n"
            requireNotNull(resolver.openOutputStream(uri, "wt")) { "Unable to write to $uri" }
                .use { it.write((joined + text).toByteArray()) }
        }
    }
}
