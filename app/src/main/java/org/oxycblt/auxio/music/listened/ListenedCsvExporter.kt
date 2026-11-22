/*
 * Copyright (c) 2026 Auxio Project
 * ListenedCsvExporter.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.listened

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.music.MusicSettings
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.fs.Path
import org.oxycblt.musikr.tag.Name
import timber.log.Timber as L

/**
 * Writes the listened status of every song in the library to a CSV file, for other apps to read.
 *
 * Columns: path (relative to the music folder that holds the song), artist, title, album, listened
 * (yes/no), first listened (local time, `yyyy-MM-dd HH:mm`, empty if not listened).
 */
class ListenedCsvExporter
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val musicSettings: MusicSettings,
    private val listenedRepository: ListenedRepository,
) {
    /**
     * Export the listened status to the file at [uri].
     *
     * @return The number of songs written, or null if the export failed.
     */
    suspend fun export(uri: Uri): Int? =
        withContext(Dispatchers.IO) {
            try {
                val library = musicRepository.library ?: return@withContext null
                val listened = listenedRepository.getListened()
                val roots = musicSettings.safQuery.source.map { it.path }
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
                val songs = library.songs.sortedBy { it.path.components.unixString }
                val output =
                    context.contentResolver.openOutputStream(uri, "wt") ?: return@withContext null
                output.bufferedWriter(Charsets.UTF_8).use { writer ->
                    // Byte order mark, so that spreadsheet apps read non-Latin text correctly.
                    writer.write("﻿")
                    writer.write("path,artist,title,album,listened,first_listened\r\n")
                    for (song in songs) {
                        val first = listened[song.uid]
                        val fields =
                            listOf(
                                song.relativePath(roots),
                                song.artists.joinToString("; ") { it.name.rawOrEmpty() },
                                song.name.raw,
                                song.album.name.rawOrEmpty(),
                                if (first != null) "yes" else "no",
                                first?.let { dateFormat.format(Date(it)) } ?: "",
                            )
                        writer.write(fields.joinToString(",") { it.csvEscaped() })
                        writer.write("\r\n")
                    }
                }
                songs.size
            } catch (e: Exception) {
                L.e("Unable to export listened status: $e")
                null
            }
        }

    private fun Song.relativePath(roots: List<Path>): String {
        val root =
            roots
                .filter { it.volume == path.volume && it.components.contains(path.components) }
                .maxByOrNull { it.components.components.size }
        return (root?.components?.containing(path.components) ?: path.components).unixString
    }

    private fun Name.rawOrEmpty() = (this as? Name.Known)?.raw ?: ""

    private fun String.csvEscaped() =
        if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${replace("\"", "\"\"")}\""
        } else {
            this
        }
}
