/*
 * Copyright (c) 2026 Auxio Project
 * MoodOverrides.kt is part of Auxio.
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

import org.oxycblt.musikr.Interpretation
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.tag.interpret.PreMood
import org.oxycblt.musikr.tag.interpret.PreSong

/**
 * What a [LibraryImpl] needs to rebuild itself with different mood overrides, without reading any
 * files again.
 *
 * @param preSongs The songs exactly as they were read from the files, before any overrides.
 */
internal class MoodOverrideContext(
    val preSongs: List<PreSong>,
    val interpretation: Interpretation,
    val libraryFactory: LibraryFactory,
)

/**
 * Replace the moods of this [PreSong] with the ones in [overrides], if there are any for it. The
 * mood tags in [PreSong.extras] are replaced too, so clients reading the tags see the same moods.
 */
internal fun PreSong.withMoodOverride(
    overrides: Map<Music.UID, List<String>>,
    interpretation: Interpretation,
): PreSong {
    val moods = overrides[v363Uid] ?: return this
    val names = moods.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val fileExtras = extras.filterKeys { it !in MOOD_EXTRA_KEYS }
    return copy(
        preMoods = names.map { PreMood(interpretation.naming.name(it, null), it) },
        extras = if (names.isEmpty()) fileExtras else fileExtras + (OVERRIDE_MOOD_KEY to names),
    )
}

// Must match the mood keys read by TagInterpreter.
private val MOOD_EXTRA_KEYS = setOf("TMOO", "TXXX:MOOD", "MOOD", "----:COM.APPLE.ITUNES:MOOD")
private const val OVERRIDE_MOOD_KEY = "TXXX:MOOD"
