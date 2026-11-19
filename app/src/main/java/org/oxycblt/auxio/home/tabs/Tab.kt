/*
 * Copyright (c) 2021 Auxio Project
 * Tab.kt is part of Auxio.
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
 
package org.oxycblt.auxio.home.tabs

import org.oxycblt.auxio.music.MusicType
import timber.log.Timber as L

/**
 * A representation of a library tab suitable for configuration.
 *
 * @param type The type of list in the home view this instance corresponds to.
 * @author Alexander Capehart (OxygenCobalt)
 */
sealed class Tab(open val type: MusicType) {
    /**
     * A visible tab. This will be visible in the home and tab configuration views.
     *
     * @param type The type of list in the home view this instance corresponds to.
     */
    data class Visible(override val type: MusicType) : Tab(type)

    /**
     * A visible tab. This will be visible in the tab configuration view, but not in the home view.
     *
     * @param type The type of list in the home view this instance corresponds to.
     */
    data class Invisible(override val type: MusicType) : Tab(type)

    companion object {
        // Like other IO-bound datatypes in Auxio, tabs are stored in a binary format. However, tabs
        // cannot be serialized on their own. Instead, they are saved as a sequence of tabs as shown
        // below:
        //
        // 0bTAB1_TAB2_TAB3_TAB4_TAB5
        //
        // Where TABN is a chunk representing a tab at position N.
        // Each chunk in a sequence is represented as:
        //
        // VTTT
        //
        // Where V is a bit representing the visibility and T is a 3-bit integer representing the
        // MusicMode for this tab.

        /** The maximum index that a well-formed tab sequence should be. */
        const val MAX_SEQUENCE_IDX = 6

        /** The maximum index of a tab sequence saved before more than five tabs existed. */
        private const val LEGACY_MAX_SEQUENCE_IDX = 4

        /**
         * Marks a sequence that uses [MAX_SEQUENCE_IDX]. Sequences without it are read with
         * [LEGACY_MAX_SEQUENCE_IDX], so tab orders saved before more tabs existed are kept.
         */
        private const val SEQUENCE_EXTENDED = 1 shl 28

        /**
         * Only [MAX_SEQUENCE_IDX] + 1 tabs fit in the sequence. Any tab after that is the one type
         * missing from the sequence, so only its visibility is stored, in this bit.
         */
        private const val LAST_TAB_VISIBLE = 1 shl 29

        /**
         * The default tab sequence, in integer form. This represents a set of four visible tabs
         * ordered as "Song", "Album", "Artist", "Genre", and "Playlists
         */
        const val SEQUENCE_DEFAULT = 0b1000_1001_1010_1011_1100

        /** Maps between the integer code in the tab sequence and it's [MusicType]. */
        private val MODE_TABLE =
            arrayOf(
                MusicType.SONGS,
                MusicType.ALBUMS,
                MusicType.ARTISTS,
                MusicType.GENRES,
                MusicType.PLAYLISTS,
                MusicType.COMPOSERS,
                MusicType.MOODS,
                MusicType.GROUPINGS,
            )

        /**
         * Convert an array of [Tab]s into it's integer representation.
         *
         * @param tabs The array of [Tab]s to convert
         * @return An integer representation of the [Tab] array
         */
        fun toIntCode(tabs: Array<Tab>): Int {
            // Like when deserializing, make sure there are no duplicate tabs for whatever reason.
            val distinct = tabs.distinctBy { it.type }
            if (tabs.size != distinct.size) {
                L.w(
                    "Tab sequences should not have duplicates [old: ${tabs.size} new: ${distinct.size}]"
                )
            }

            var sequence = SEQUENCE_EXTENDED
            var shift = MAX_SEQUENCE_IDX * 4
            for (tab in distinct.take(MAX_SEQUENCE_IDX + 1)) {
                val bin =
                    when (tab) {
                        is Visible -> 1.shl(3) or MODE_TABLE.indexOf(tab.type)
                        is Invisible -> MODE_TABLE.indexOf(tab.type)
                    }

                sequence = sequence or bin.shl(shift)
                shift -= 4
            }

            if (distinct.getOrNull(MAX_SEQUENCE_IDX + 1) is Visible) {
                sequence = sequence or LAST_TAB_VISIBLE
            }

            return sequence
        }

        /**
         * Convert a [Tab] integer representation into it's corresponding array of [Tab]s.
         *
         * @param intCode The integer representation of the [Tab]s.
         * @return An array of [Tab]s corresponding to the sequence.
         */
        fun fromIntCode(intCode: Int): Array<Tab>? {
            val tabs = mutableListOf<Tab>()

            // Try to parse a mode for each chunk in the sequence.
            // If we can't parse one, just skip it.
            val maxIdx =
                if (intCode and SEQUENCE_EXTENDED != 0) MAX_SEQUENCE_IDX
                else LEGACY_MAX_SEQUENCE_IDX
            for (shift in (0..maxIdx * 4).reversed() step 4) {
                val chunk = intCode.shr(shift) and 0b1111
                val mode = MODE_TABLE.getOrNull(chunk and 7) ?: continue

                // Figure out the visibility
                tabs +=
                    if (chunk and 1.shl(3) != 0) {
                        Visible(mode)
                    } else {
                        Invisible(mode)
                    }
            }

            // Make sure there are no duplicate tabs
            val distinct = tabs.distinctBy { it.type }
            if (tabs.size != distinct.size) {
                L.w(
                    "Tab sequences should not have duplicates [old: ${tabs.size} new: ${distinct.size}]"
                )
            }

            // For safety, return null if we have an empty or larger-than-expected tab array.
            if (distinct.isEmpty() || distinct.size < LEGACY_MAX_SEQUENCE_IDX) {
                L.e("Sequence size was ${distinct.size}, which is invalid")
                return null
            }

            // Tabs added after the sequence was saved are appended, hidden. A single missing tab
            // can be the last tab of a full sequence, which stores its visibility separately.
            val missingTypes = MODE_TABLE.filter { mode -> distinct.none { it.type == mode } }
            val lastVisible = intCode and LAST_TAB_VISIBLE != 0 && missingTypes.size == 1
            val missing = missingTypes.map { if (lastVisible) Visible(it) else Invisible(it) }

            return (distinct + missing).toTypedArray()
        }
    }
}
