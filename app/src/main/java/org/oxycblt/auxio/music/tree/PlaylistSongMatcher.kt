/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistSongMatcher.kt is part of Auxio.
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

import kotlin.math.abs
import org.oxycblt.musikr.Library
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.tag.Name

/**
 * The tag info that an M3U entry carries before its path: #EXTINF length and title, #EXTART artists
 * and #EXTALB album. Used to find a song when its path matches nothing, for example when the
 * phone's copy of the library uses other folder or file names than the PC.
 */
data class EntryInfo(
    val title: String?,
    val durationMs: Long?,
    val artists: List<String>,
    val album: String?,
) {
    /** One line for a #BASE entry: path and info, separated by tabs. */
    fun toBaseLine(path: String) =
        listOf(
                path,
                durationMs?.toString() ?: "",
                title ?: "",
                artists.joinToString("; "),
                album ?: "",
            )
            .joinToString("\t")

    companion object {
        /** The info of every entry in an M3U [text], in the same order as its paths. */
        fun parseAll(text: String): List<EntryInfo> {
            val entries = mutableListOf<EntryInfo>()
            var title: String? = null
            var duration: Long? = null
            val artists = mutableListOf<String>()
            var album: String? = null
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                when {
                    line.isEmpty() -> {}
                    line.startsWith("#EXTINF:") -> {
                        val value = line.removePrefix("#EXTINF:")
                        duration = value.substringBefore(',').trim().toLongOrNull()
                        title = value.substringAfter(',', "").trim().ifEmpty { null }
                    }
                    line.startsWith("#EXTART:") ->
                        artists.addAll(
                            line
                                .removePrefix("#EXTART:")
                                .split(";")
                                .map { it.trim() }
                                .filter {
                                    it.isNotEmpty()
                                }
                        )
                    line.startsWith("#EXTALB:") ->
                        album = line.removePrefix("#EXTALB:").trim().ifEmpty { null }
                    line.startsWith("#") -> {}
                    else -> {
                        entries.add(EntryInfo(title, duration, artists.toList(), album))
                        title = null
                        duration = null
                        artists.clear()
                        album = null
                    }
                }
            }
            return entries
        }

        fun of(song: Song) =
            EntryInfo(
                song.name.raw,
                song.durationMs,
                song.artists.mapNotNull { (it.name as? Name.Known)?.raw },
                (song.album.name as? Name.Known)?.raw,
            )
    }
}

/** Finds library songs by [EntryInfo] when their paths do not match. */
class PlaylistSongMatcher(library: Library) {
    private val byTitle = library.songs.groupBy { key(it.name.raw) }

    /**
     * The song with the same title and a length within two seconds; artist, then album, decide
     * between several. Null when nothing fits.
     */
    fun find(info: EntryInfo): Song? {
        val title = info.title ?: return null
        val candidates =
            byTitle[key(title)].orEmpty().filter { song ->
                info.durationMs == null || abs(song.durationMs - info.durationMs) <= TOLERANCE_MS
            }
        if (candidates.size <= 1) return candidates.firstOrNull()
        val artists = info.artists.map(::key).toSet()
        val byArtist =
            candidates
                .filter { song -> EntryInfo.of(song).artists.any { key(it) in artists } }
                .ifEmpty { candidates }
        val album = info.album?.let(::key) ?: return byArtist.first()
        return byArtist.find { song -> EntryInfo.of(song).album?.let(::key) == album }
            ?: byArtist.first()
    }

    private companion object {
        const val TOLERANCE_MS = 2000L

        fun key(text: String) = text.trim().lowercase()
    }
}
