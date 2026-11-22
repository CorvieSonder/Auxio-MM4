/*
 * Copyright (c) 2026 Auxio Project
 * Lyrics.kt is part of Auxio.
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
 
package org.oxycblt.musikr.lyrics

/**
 * The lyrics of a song, read on demand (not stored in the music cache).
 *
 * @param synced Time-stamped lines, from a SYLT frame, a sidecar .lrc file or LRC timestamps in
 *   plain lyrics. Null if there are none.
 * @param plain Every plain lyrics block found in the tags, in tag order. Several blocks are usually
 *   translations (for example one USLT frame per language).
 */
data class Lyrics(val synced: List<SyncedLine>?, val plain: List<PlainLyrics>) {
    /** Whether there is nothing to show. */
    val isEmpty: Boolean
        get() = synced.isNullOrEmpty() && plain.isEmpty()

    companion object {
        /** Lyrics with no content. */
        val EMPTY = Lyrics(null, emptyList())
    }
}

/**
 * One line of synced lyrics.
 *
 * @param timeMs When the line starts, in milliseconds from the start of the song.
 * @param text The line's text. May be blank (an instrumental gap).
 */
data class SyncedLine(val timeMs: Long, val text: String)

/**
 * One block of plain lyrics.
 *
 * @param language The ISO-639-2 language code, if the tag has one (for example "eng"). May be blank
 *   or a placeholder such as "XXX".
 * @param description The tag's content description. Often blank.
 * @param text The lyrics text.
 */
data class PlainLyrics(val language: String, val description: String, val text: String)
