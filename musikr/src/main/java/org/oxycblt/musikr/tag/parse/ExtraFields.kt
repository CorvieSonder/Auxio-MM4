/*
 * Copyright (c) 2026 Auxio Project
 * ExtraFields.kt is part of Auxio.
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
 
package org.oxycblt.musikr.tag.parse

import org.oxycblt.musikr.metadata.Metadata

/**
 * Collect the raw tag values that are not interpreted by the library, but are still useful to
 * clients (ex. ratings, moods, tagger-specific fields).
 *
 * Keys are the same as in [Metadata], so ID3v2 frames with a description are in the form of
 * `ID:DESCRIPTION` with the description uppercased.
 */
internal fun Metadata.extras(): Map<String, List<String>> {
    val extras = mutableMapOf<String, List<String>>()
    for ((key, values) in id3v2) {
        if (key in ID3V2_EXTRA_IDS || ID3V2_EXTRA_PREFIXES.any { key.startsWith(it) }) {
            extras[key] = values.capped()
        }
    }
    for ((key, values) in xiph) {
        if (key in XIPH_EXTRA_KEYS) {
            extras[key] = values.capped()
        }
    }
    for ((key, values) in mp4) {
        if (key in MP4_EXTRA_KEYS) {
            extras[key] = values.capped()
        }
    }
    return extras
}

private fun List<String>.capped() = take(MAX_EXTRA_VALUES).map { it.take(MAX_EXTRA_LENGTH) }

private const val MAX_EXTRA_VALUES = 16
private const val MAX_EXTRA_LENGTH = 256

private val ID3V2_EXTRA_PREFIXES = listOf("POPM:", "COMM:", "TXXX:")
private val ID3V2_EXTRA_IDS = setOf("TCOM", "TIT1", "TPE3", "TMOO")
private val XIPH_EXTRA_KEYS =
    setOf("RATING", "FMPS_RATING", "COMPOSER", "CONDUCTOR", "GROUPING", "MOOD")
private val MP4_EXTRA_KEYS =
    setOf(
        "rate",
        "©wrt",
        "©grp",
        "----:COM.APPLE.ITUNES:FMPS_RATING",
        "----:COM.APPLE.ITUNES:CONDUCTOR",
        "----:COM.APPLE.ITUNES:MOOD",
    )
