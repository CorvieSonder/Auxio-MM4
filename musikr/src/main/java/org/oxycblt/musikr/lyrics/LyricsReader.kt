/*
 * Copyright (c) 2026 Auxio Project
 * LyricsReader.kt is part of Auxio.
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

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.fs.AddedMs
import org.oxycblt.musikr.fs.File
import org.oxycblt.musikr.metadata.NativeInputStream

/** Reads a song's lyrics from its tags, or from a sidecar .lrc file next to it. */
interface LyricsReader {
    /**
     * Read the lyrics of [song]. Synced lyrics come from, in order: a SYLT frame, a sidecar .lrc
     * file with the same name, or LRC timestamps inside plain lyrics.
     *
     * @return The lyrics, or [Lyrics.EMPTY] if there are none or the file can't be read.
     */
    suspend fun read(song: Song): Lyrics

    companion object {
        fun from(context: Context): LyricsReader = LyricsReaderImpl(context.contentResolver)
    }
}

private class LyricsReaderImpl(private val contentResolver: ContentResolver) : LyricsReader {
    override suspend fun read(song: Song): Lyrics =
        withContext(Dispatchers.IO) {
            val blocks =
                try {
                    readBlocks(song)
                } catch (e: Exception) {
                    null
                } ?: emptyList()

            val plain =
                blocks
                    .filter { it.kind == LyricsJNI.KIND_PLAIN }
                    .map { PlainLyrics(it.language, it.description, it.text) }

            val synced =
                blocks.firstNotNullOfOrNull { block ->
                    if (block.kind == LyricsJNI.KIND_SYNCED) parseSynced(block.text) else null
                }
                    ?: readSidecar(song.uri)?.let { LrcParser.parse(it) }
                    ?: plain.firstNotNullOfOrNull { LrcParser.parse(it.text) }

            Lyrics(synced, plain)
        }

    private fun readBlocks(song: Song): List<LyricsJNI.Block>? =
        contentResolver.openFileDescriptor(song.uri, "r")?.use { fd ->
            FileInputStream(fd.fileDescriptor).use { fis ->
                // The native stream only needs the file for its name (used in logs).
                val file = File(song.uri, song.path, NoAddedMs, 0, "", 0, null)
                val inputStream = NativeInputStream(file, fis)
                LyricsJNI.read(inputStream).also { inputStream.close() }
            }
        }

    private fun parseSynced(text: String): List<SyncedLine>? =
        text
            .lineSequence()
            .mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab < 0) return@mapNotNull null
                val timeMs = line.substring(0, tab).toLongOrNull() ?: return@mapNotNull null
                SyncedLine(timeMs, line.substring(tab + 1))
            }
            .sortedBy { it.timeMs }
            .toList()
            .takeIf { it.isNotEmpty() }

    /**
     * Read "Song.lrc" next to "Song.mp3". Only works for songs found through the storage access
     * framework, whose document ids are paths such as "primary:Music/Album/Song.mp3".
     */
    private fun readSidecar(songUri: Uri): String? =
        try {
            val documentId = DocumentsContract.getDocumentId(songUri)
            val dot = documentId.lastIndexOf('.')
            if (dot <= documentId.lastIndexOf('/')) {
                null
            } else {
                val lrcUri =
                    DocumentsContract.buildDocumentUriUsingTree(
                        songUri,
                        documentId.substring(0, dot) + ".lrc",
                    )
                contentResolver.openInputStream(lrcUri)?.use {
                    it.readBytes().toString(Charsets.UTF_8).removePrefix("﻿")
                }
            }
        } catch (e: Exception) {
            // Not a tree document, or there is no sidecar file.
            null
        }

    private object NoAddedMs : AddedMs {
        override suspend fun resolve(): Long? = null
    }
}
