/*
 * Copyright (c) 2022 Auxio Project
 * MusicType.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music

import android.content.Context
import org.oxycblt.auxio.IntegerTable
import org.oxycblt.auxio.R
import org.oxycblt.auxio.music.tags.CustomTag

/**
 * General configuration enum to control what kind of music is being worked with.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
enum class MusicType {
    SONGS,
    ALBUMS,
    ARTISTS,
    GENRES,
    PLAYLISTS,
    COMPOSERS,
    MOODS,
    GROUPINGS,
    CUSTOM_1,
    CUSTOM_2,
    CUSTOM_3,
    CUSTOM_4,
    CUSTOM_5,
    CUSTOM_6;

    /**
     * The integer representation of this instance.
     *
     * @see fromIntCode
     */
    val intCode: Int
        get() =
            when (this) {
                SONGS -> IntegerTable.MUSIC_MODE_SONGS
                ALBUMS -> IntegerTable.MUSIC_MODE_ALBUMS
                ARTISTS -> IntegerTable.MUSIC_MODE_ARTISTS
                GENRES -> IntegerTable.MUSIC_MODE_GENRES
                PLAYLISTS -> IntegerTable.MUSIC_MODE_PLAYLISTS
                COMPOSERS -> IntegerTable.MUSIC_MODE_COMPOSERS
                MOODS -> IntegerTable.MUSIC_MODE_MOODS
                GROUPINGS -> IntegerTable.MUSIC_MODE_GROUPINGS
                CUSTOM_1 -> IntegerTable.MUSIC_MODE_CUSTOM_1
                CUSTOM_2 -> IntegerTable.MUSIC_MODE_CUSTOM_2
                CUSTOM_3 -> IntegerTable.MUSIC_MODE_CUSTOM_3
                CUSTOM_4 -> IntegerTable.MUSIC_MODE_CUSTOM_4
                CUSTOM_5 -> IntegerTable.MUSIC_MODE_CUSTOM_5
                CUSTOM_6 -> IntegerTable.MUSIC_MODE_CUSTOM_6
            }

    val nameRes: Int
        get() =
            when (this) {
                SONGS -> R.string.lbl_songs
                ALBUMS -> R.string.lbl_albums
                ARTISTS -> R.string.lbl_artists
                GENRES -> R.string.lbl_genres
                PLAYLISTS -> R.string.lbl_playlists
                COMPOSERS -> R.string.lbl_composers
                MOODS -> R.string.lbl_moods
                GROUPINGS -> R.string.lbl_groupings
                CUSTOM_1,
                CUSTOM_2,
                CUSTOM_3,
                CUSTOM_4,
                CUSTOM_5,
                CUSTOM_6 -> R.string.lbl_custom_tags
            }

    /** The custom tag slot shown by this type, or null if it is not a custom tag type. */
    val customSlot: Int?
        get() = CUSTOM_TYPES.indexOf(this).takeIf { it >= 0 }?.plus(1)

    /** The name of this type, which is the user-chosen name for custom tag types. */
    fun label(context: Context) =
        customSlot?.let { CustomTag.nameOf(context, it) } ?: context.getString(nameRes)

    companion object {
        /** The custom tag types, in slot order. */
        val CUSTOM_TYPES = listOf(CUSTOM_1, CUSTOM_2, CUSTOM_3, CUSTOM_4, CUSTOM_5, CUSTOM_6)

        /** The type of the custom tag in [slot]. */
        fun customOf(slot: Int) = CUSTOM_TYPES[slot - 1]

        /**
         * Convert a [MusicType] integer representation into an instance.
         *
         * @param intCode An integer representation of a [MusicType]
         * @return The corresponding [MusicType], or null if the [MusicType] is invalid.
         * @see MusicType.intCode
         */
        fun fromIntCode(intCode: Int) =
            when (intCode) {
                IntegerTable.MUSIC_MODE_SONGS -> SONGS
                IntegerTable.MUSIC_MODE_ALBUMS -> ALBUMS
                IntegerTable.MUSIC_MODE_ARTISTS -> ARTISTS
                IntegerTable.MUSIC_MODE_GENRES -> GENRES
                IntegerTable.MUSIC_MODE_PLAYLISTS -> PLAYLISTS
                IntegerTable.MUSIC_MODE_COMPOSERS -> COMPOSERS
                IntegerTable.MUSIC_MODE_MOODS -> MOODS
                IntegerTable.MUSIC_MODE_GROUPINGS -> GROUPINGS
                IntegerTable.MUSIC_MODE_CUSTOM_1 -> CUSTOM_1
                IntegerTable.MUSIC_MODE_CUSTOM_2 -> CUSTOM_2
                IntegerTable.MUSIC_MODE_CUSTOM_3 -> CUSTOM_3
                IntegerTable.MUSIC_MODE_CUSTOM_4 -> CUSTOM_4
                IntegerTable.MUSIC_MODE_CUSTOM_5 -> CUSTOM_5
                IntegerTable.MUSIC_MODE_CUSTOM_6 -> CUSTOM_6
                else -> null
            }
    }
}
