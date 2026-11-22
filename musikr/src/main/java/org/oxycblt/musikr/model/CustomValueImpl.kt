/*
 * Copyright (c) 2026 Auxio Project
 * CustomValueImpl.kt is part of Auxio.
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
import org.oxycblt.musikr.CustomValue
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.covers.CoverCollection
import org.oxycblt.musikr.tag.interpret.PreCustomValue
import org.oxycblt.musikr.util.update

internal interface CustomValueCore {
    val preCustomValue: PreCustomValue
    val songs: Set<Song>
    val artists: Set<Artist>
}

/** Library-backed implementation of [CustomValue]. */
internal class CustomValueImpl(private val core: CustomValueCore) : CustomValue {
    override val uid =
        Music.UID.auxio(Music.UID.Item.CUSTOM_VALUE) {
            update(core.preCustomValue.slot)
            update(core.preCustomValue.rawName.lowercase())
        }
    override val name = core.preCustomValue.name
    override val slot = core.preCustomValue.slot

    override val songs = core.songs
    override val artists = core.artists
    override val durationMs = core.songs.sumOf { it.durationMs }
    override val covers = CoverCollection.from(core.songs.mapNotNull { it.cover })

    private val hashCode =
        31 * (31 * uid.hashCode() + core.preCustomValue.hashCode()) + songs.hashCode()

    override fun hashCode() = hashCode

    override fun equals(other: Any?) =
        other is CustomValueImpl &&
            uid == other.uid &&
            core.preCustomValue == other.core.preCustomValue &&
            songs == other.songs

    override fun toString() = "CustomValue(uid=$uid, name=$name)"
}
