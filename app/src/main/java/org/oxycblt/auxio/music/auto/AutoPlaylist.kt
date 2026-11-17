/*
 * Copyright (c) 2026 Auxio Project
 * AutoPlaylist.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.auto

import androidx.annotation.StringRes
import org.oxycblt.auxio.R
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.covers.CoverCollection
import org.oxycblt.musikr.tag.Name
import org.oxycblt.musikr.tag.Token

/**
 * A [Playlist] whose songs are chosen automatically, such as the most played songs. It is not
 * stored anywhere and cannot be edited.
 *
 * @param kind The [Kind] of automatic playlist this is.
 * @param name The name to show for the playlist.
 * @param songs The songs currently in the playlist.
 */
class AutoPlaylist(val kind: Kind, name: String, override val songs: List<Song>) : Playlist {
    override val uid = kind.uid
    override val name: Name.Known = AutoName(name)
    override val durationMs = songs.sumOf { it.durationMs }
    override val covers = CoverCollection.from(songs.mapNotNull { it.cover })

    private val hashCode = 31 * (31 * kind.hashCode() + name.hashCode()) + songs.hashCode()

    override fun hashCode() = hashCode

    override fun equals(other: Any?) =
        other is AutoPlaylist && kind == other.kind && name == other.name && songs == other.songs

    override fun toString() = "AutoPlaylist(kind=$kind, songs=${songs.size})"

    /**
     * The kinds of automatic playlist, in the order they are shown.
     *
     * @param uid The fixed [Music.UID] of the playlist.
     * @param nameRes The string resource of the playlist's name.
     */
    enum class Kind(val uid: Music.UID, @StringRes val nameRes: Int) {
        RECENTLY_ADDED(fixedUid(1), R.string.lbl_auto_recently_added),
        RECENTLY_PLAYED(fixedUid(2), R.string.lbl_auto_recently_played),
        MOST_PLAYED(fixedUid(3), R.string.lbl_auto_most_played),
        RATED(fixedUid(4), R.string.lbl_auto_rated),
        NOW_PLAYING(fixedUid(5), R.string.lbl_auto_now_playing),
    }

    /** A plain name. It has no tokens, so it sorts before the names of other playlists. */
    private class AutoName(override val raw: String) : Name.Known() {
        override val sort: String? = null
        override val tokens = listOf<Token>()

        override fun hashCode() = raw.hashCode()

        override fun equals(other: Any?) = other is AutoName && raw == other.raw

        override fun toString() = raw
    }

    private companion object {
        fun fixedUid(index: Int) =
            requireNotNull(Music.UID.fromString("uap00000000-0000-4000-8000-00000000000$index")) {
                "Invalid automatic playlist UID"
            }
    }
}
