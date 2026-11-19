/*
 * Copyright (c) 2026 Auxio Project
 * GroupingImpl.kt is part of Auxio.
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
 
package org.oxycblt.musikr.model

import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.Grouping
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.covers.CoverCollection
import org.oxycblt.musikr.tag.interpret.PreGrouping
import org.oxycblt.musikr.util.update

internal interface GroupingCore {
    val preGrouping: PreGrouping
    val songs: Set<Song>
    val artists: Set<Artist>
}

/** Library-backed implementation of [Grouping]. */
internal class GroupingImpl(private val core: GroupingCore) : Grouping {
    override val uid =
        Music.UID.auxio(Music.UID.Item.GROUPING) { update(core.preGrouping.rawName.lowercase()) }
    override val name = core.preGrouping.name

    override val songs = core.songs
    override val artists = core.artists
    override val durationMs = core.songs.sumOf { it.durationMs }
    override val covers = CoverCollection.from(core.songs.mapNotNull { it.cover })

    private val hashCode =
        31 * (31 * uid.hashCode() + core.preGrouping.hashCode()) + songs.hashCode()

    override fun hashCode() = hashCode

    override fun equals(other: Any?) =
        other is GroupingImpl &&
            uid == other.uid &&
            core.preGrouping == other.core.preGrouping &&
            songs == other.songs

    override fun toString() = "Grouping(uid=$uid, name=$name)"
}
