/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistMerge.kt is part of Auxio.
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

/**
 * Three-way merge of song lists, used when a playlist changed both here and in MediaMonkey since
 * the last sync.
 */
object PlaylistMerge {
    /** Whether the songs that [list] shares with [base] are in a different order than in [base]. */
    fun <T> reordered(list: List<T>, base: List<T>): Boolean {
        val inList = list.toSet()
        val inBase = base.toSet()
        return list.filter { it in inBase } != base.filter { it in inList }
    }

    /**
     * Merge [mine] (the importing side) and [theirs] (the incoming side), both changed from [base].
     *
     * A song removed on either side is dropped, a song added on either side is kept. The order
     * comes from the side that reordered its songs, or from [theirs] when both or neither did. A
     * song added on the other side goes right after the song it follows there.
     */
    fun <T> merge(base: List<T>, mine: List<T>, theirs: List<T>): List<T> {
        val inBase = base.toSet()
        val useMine = reordered(mine, base) && !reordered(theirs, base)
        val source = if (useMine) mine else theirs
        val other = if (useMine) theirs else mine
        val inOther = other.toSet()
        val result = source.filter { it !in inBase || it in inOther }.toMutableList()
        val placed = result.toMutableSet()
        for ((i, song) in other.withIndex()) {
            if (song in inBase || song in placed) continue
            val before = other.subList(0, i).lastOrNull { it in placed }
            val at = if (before == null) 0 else result.indexOf(before) + 1
            result.add(at, song)
            placed.add(song)
        }
        return result
    }
}
