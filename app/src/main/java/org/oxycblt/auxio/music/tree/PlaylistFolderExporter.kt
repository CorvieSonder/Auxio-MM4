/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistFolderExporter.kt is part of Auxio.
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

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.playlist.ExportConfig
import org.oxycblt.musikr.playlist.ExternalPlaylistManager
import timber.log.Timber as L

/**
 * Exports a [Playlist] and all of its child playlists as nested folders of M3U8 files.
 *
 * Every playlist becomes a folder named after it. The playlist's own songs go into a file of the
 * same name inside that folder, and its child playlists become sub-folders. A playlist without
 * songs is only a folder. [PlaylistFolderImporter] reads the same layout.
 */
class PlaylistFolderExporter
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val playlistTreeRepository: PlaylistTreeRepository,
) {
    private val externalPlaylistManager = ExternalPlaylistManager.from(context)

    /**
     * Export [playlist] and its descendants into the document tree at [treeUri].
     *
     * @return The number of playlists exported, or null if the export failed.
     */
    suspend fun export(playlist: Playlist, treeUri: Uri, config: ExportConfig): Int? {
        return try {
            val rootUri =
                DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri),
                )
            exportInto(playlist, rootUri, config, mutableSetOf())
        } catch (e: Exception) {
            L.e("Unable to export $playlist as a folder")
            L.e(e.stackTraceToString())
            null
        }
    }

    private suspend fun exportInto(
        playlist: Playlist,
        parentUri: Uri,
        config: ExportConfig,
        seen: MutableSet<Playlist>,
    ): Int {
        if (!seen.add(playlist)) {
            return 0
        }
        val resolver = context.contentResolver
        val name = fileNameOf(playlist.name.raw)
        // The provider renames the folder to "<name> (1)" if one with this name already exists,
        // so nothing gets overwritten.
        val folderUri =
            requireNotNull(
                DocumentsContract.createDocument(
                    resolver,
                    parentUri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    name,
                )
            ) {
                "Unable to create a folder for $playlist"
            }
        if (playlist.songs.isNotEmpty()) {
            val fileUri =
                requireNotNull(
                    DocumentsContract.createDocument(resolver, folderUri, M3U8_MIME, "$name.m3u8")
                ) {
                    "Unable to create a file for $playlist"
                }
            check(externalPlaylistManager.export(playlist, fileUri, config)) {
                "Unable to write $playlist"
            }
        }
        var count = 1
        val children = withContext(Dispatchers.Main) { playlistTreeRepository.childrenOf(playlist) }
        for (child in children) {
            count += exportInto(child, folderUri, config, seen)
        }
        return count
    }

    companion object {
        /** Characters that are not allowed in file names on Android's FAT/exFAT or on Windows. */
        private val UNSAFE_CHARACTERS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

        private val M3U8_MIME =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension("m3u8")
                ?: "application/octet-stream"

        /** Turn a playlist name into a name that is safe to use for a file or folder. */
        fun fileNameOf(name: String): String {
            val safe = name.replace(UNSAFE_CHARACTERS, "_").trim().trimEnd('.', ' ')
            return safe.ifEmpty { "_" }
        }
    }
}
