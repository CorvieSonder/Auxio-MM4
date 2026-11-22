/*
 * Copyright (c) 2026 Auxio Project
 * LyricsJNI.kt is part of Auxio.
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

import org.oxycblt.musikr.metadata.NativeInputStream

/** Reads lyrics frames with TagLib. Shares the native library with the metadata reader. */
internal object LyricsJNI {
    init {
        System.loadLibrary("tagJNI")
    }

    /** One lyrics block as returned by the native side. */
    data class Block(
        val kind: String,
        val language: String,
        val description: String,
        val text: String,
    )

    // Mirrored in JLyrics.cpp.
    const val KIND_PLAIN = "plain"
    const val KIND_SYNCED = "synced"

    /**
     * Read every lyrics block in a file. Synced blocks hold one "timeMs\ttext" pair per line.
     *
     * Note: This method is blocking and should be handled as such if calling from a coroutine.
     */
    fun read(inputStream: NativeInputStream): List<Block>? {
        val flat = readNative(inputStream) ?: return null
        return flat.toList().chunked(4).map { Block(it[0], it[1], it[2], it[3]) }
    }

    private external fun readNative(inputStream: NativeInputStream): Array<String>?
}
