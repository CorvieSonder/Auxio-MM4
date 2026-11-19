/*
 * Copyright (c) 2026 Auxio Project
 * TagWriter.kt is part of Auxio.
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
 
package org.oxycblt.musikr.metadata

import android.content.Context
import android.net.Uri
import java.io.File
import org.oxycblt.musikr.fs.saf.contentResolverSafe

/**
 * Changes to make to the ID3v2 tag of an MP3 file.
 *
 * @param popm Popularimeter ratings (0-255) by email. A null rating removes that email's frame.
 * @param txxx User text values by description (matched ignoring case). A null value removes every
 *   frame with that description.
 */
data class TagEdit(
    val popm: Map<String, Int?> = emptyMap(),
    val txxx: Map<String, String?> = emptyMap(),
) {
    /** Combine with another edit. Values in [other] win. */
    operator fun plus(other: TagEdit) = TagEdit(popm + other.popm, txxx + other.txxx)

    fun isEmpty() = popm.isEmpty() && txxx.isEmpty()
}

/** Writes [TagEdit]s into audio files. */
object TagWriter {
    sealed interface Result {
        /** The file was changed. */
        data object Success : Result

        /** The file is not an MP3 file and can never be written. */
        data object Unsupported : Result

        /** Writing failed. The original file was not changed unless [originalTouched] is true. */
        data class Failed(val reason: String, val originalTouched: Boolean = false) : Result
    }

    /**
     * Write [edit] into the document at [uri].
     *
     * The file is copied to the cache, edited and checked there, and only then copied back over the
     * original. Blocking, call from an IO dispatcher.
     */
    fun write(context: Context, uri: Uri, edit: TagEdit): Result {
        if (edit.isEmpty()) {
            return Result.Success
        }
        val resolver = context.contentResolverSafe
        val temp = File.createTempFile("tagwrite", ".mp3", context.cacheDir)
        var replacing = false
        try {
            val copied =
                resolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                } ?: return Result.Failed("Unable to open $uri")

            val popm = edit.popm.entries.toList()
            val txxx = edit.txxx.entries.toList()
            val code =
                TagWriterJNI.writeId3Native(
                    temp.path,
                    popm.map { it.key }.toTypedArray(),
                    popm.map { it.value ?: -1 }.toIntArray(),
                    txxx.map { it.key }.toTypedArray(),
                    txxx.map { it.value }.toTypedArray(),
                )
            when (code) {
                TagWriterJNI.RESULT_OK -> {}
                TagWriterJNI.RESULT_UNSUPPORTED -> return Result.Unsupported
                else -> return Result.Failed("Native write of $uri failed with code $code")
            }

            val length = temp.length()
            if (length < copied / 2) {
                // Sanity check: a tag edit never removes half of the file.
                return Result.Failed("Edited copy of $uri is too small ($length of $copied)")
            }
            replacing = true
            val written =
                resolver.openOutputStream(uri, "wt")?.use { output ->
                    temp.inputStream().use { input -> input.copyTo(output) }
                } ?: return Result.Failed("Unable to write $uri")
            if (written != length) {
                return Result.Failed("Wrote $written of $length bytes to $uri", true)
            }
            return Result.Success
        } catch (e: Exception) {
            return Result.Failed("Unable to write $uri: $e", replacing)
        } finally {
            temp.delete()
        }
    }
}

internal object TagWriterJNI {
    const val RESULT_OK = 0
    const val RESULT_UNSUPPORTED = 1

    init {
        System.loadLibrary("tagJNI")
    }

    external fun writeId3Native(
        path: String,
        popmEmails: Array<String>,
        popmRatings: IntArray,
        txxxDescriptions: Array<String>,
        txxxValues: Array<String?>,
    ): Int
}
