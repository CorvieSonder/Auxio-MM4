/*
 * Copyright (c) 2026 Auxio Project
 * ExtraTags.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.tags

import org.oxycblt.musikr.Song
import org.oxycblt.musikr.tag.interpret.Separators

/**
 * The composer values of this [Song] exactly as they were tagged, without any separator handling.
 * Cheaper than [composers], so prefer it when only matching against the values.
 */
val Song.rawComposers: List<String>
    get() = rawExtras(COMPOSER_KEYS)

/**
 * The composers of this [Song].
 *
 * @param separators The [Separators] to split multi-value tags with.
 * @return The composer names, or an empty list if the song has none.
 */
fun Song.composers(separators: Separators) = separators.splitExtras(rawComposers)

private fun Song.rawExtras(keys: List<String>): List<String> {
    if (extras.isEmpty()) {
        return emptyList()
    }
    return keys.flatMap { extras[it].orEmpty() }
}

private fun Separators.splitExtras(values: List<String>): List<String> {
    if (values.isEmpty()) {
        return emptyList()
    }
    return split(values).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

private val COMPOSER_KEYS = listOf("TCOM", "COMPOSER", "©wrt")
