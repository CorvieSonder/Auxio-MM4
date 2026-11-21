/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistChangesExporter.kt is part of Auxio.
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
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.musikr.playlist.ExportConfig
import org.oxycblt.musikr.playlist.ExternalPlaylistManager
import timber.log.Timber as L

/**
 * Writes the playlists that changed since the last sync into a flat "changes" folder, for
 * MediaMonkey's "Apply phone changes" script.
 *
 * Each new or changed playlist becomes one M3U8 file with absolute paths, its IDs here and in
 * MediaMonkey, its parent's IDs, and its songs at the last sync (so MediaMonkey can merge).
 * Playlists deleted here since the last sync are listed in "_deleted.txt".
 */
class PlaylistChangesExporter
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val playlistSyncRepository: PlaylistSyncRepository,
) {
    private val externalPlaylistManager = ExternalPlaylistManager.from(context)

    /** The outcome of an export. */
    data class Result(val changed: Int, val deleted: Int)

    /**
     * Write the changes into a "changes" folder inside the document tree at [treeUri], replacing
     * the playlist files of an earlier one.
     */
    suspend fun export(treeUri: Uri): Result? {
        return try {
            val library = musicRepository.library ?: return null
            val resolver = context.contentResolver
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            val existing =
                PlaylistDocuments.listChildren(resolver, treeUri, rootId).find {
                    it.isFolder && it.name == FOLDER_NAME
                }
            val folderUri =
                if (existing != null) {
                    PlaylistDocuments.clean(resolver, treeUri, existing.id)
                    // Also remove an old deletion list.
                    PlaylistDocuments.listChildren(resolver, treeUri, existing.id)
                        .filter { it.name == DELETED_NAME }
                        .forEach {
                            DocumentsContract.deleteDocument(
                                resolver,
                                DocumentsContract.buildDocumentUriUsingTree(treeUri, it.id),
                            )
                        }
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, existing.id)
                } else {
                    requireNotNull(
                        DocumentsContract.createDocument(
                            resolver,
                            DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId),
                            DocumentsContract.Document.MIME_TYPE_DIR,
                            FOLDER_NAME,
                        )
                    ) {
                        "Unable to create the changes folder"
                    }
                }

            val links = playlistSyncRepository.links()
            val written = mutableListOf<PlaylistSyncLink>()
            val usedNames = mutableSetOf<String>()
            for (playlist in library.playlists) {
                val state = playlistSyncRepository.stateOf(playlist)
                val link = links[playlist.uid]
                if (link != null && PlaylistState.of(link) == state) {
                    continue
                }
                val base = link?.let(PlaylistState::of)
                val stem = PlaylistFolderExporter.fileNameOf(state.name)
                var name = stem
                var n = 2
                while (!usedNames.add(name.lowercase())) {
                    name = "$stem (${n++})"
                }
                val fileUri =
                    requireNotNull(
                        DocumentsContract.createDocument(
                            resolver,
                            folderUri,
                            M3U8_MIME,
                            "$name.m3u8",
                        )
                    ) {
                        "Unable to create a file for $playlist"
                    }
                check(externalPlaylistManager.export(playlist, fileUri, ABSOLUTE)) {
                    "Unable to write $playlist"
                }
                val ids =
                    PlaylistIds(
                        auxioId = playlist.uid.toString(),
                        auxioParent = state.parentUid?.toString(),
                        mmId = link?.mmId,
                        mmParent = state.parentUid?.let { links[it]?.mmId },
                        baseName = base?.name,
                        base = base?.songs,
                    )
                PlaylistSyncRepository.appendText(resolver, fileUri, ids.toLines())
                written.add(state.toLink(playlist.uid, link?.mmId))
            }

            val deleted = links.values.filter { library.findPlaylist(it.playlistUid) == null }
            if (deleted.isNotEmpty()) {
                val uri =
                    requireNotNull(
                        DocumentsContract.createDocument(
                            resolver,
                            folderUri,
                            "text/plain",
                            DELETED_NAME,
                        )
                    ) {
                        "Unable to create the deletion list"
                    }
                val text =
                    deleted.joinToString("") {
                        "${it.mmId ?: ""}\t${it.playlistUid}\t${it.name}\n"
                    }
                resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            }

            // What was sent is the new base: MediaMonkey will match it after applying.
            playlistSyncRepository.save(written)
            playlistSyncRepository.forget(deleted.map { it.playlistUid })
            Result(written.size, deleted.size)
        } catch (e: Exception) {
            L.e("Unable to export the playlist changes")
            L.e(e.stackTraceToString())
            null
        }
    }

    private companion object {
        const val FOLDER_NAME = "changes"
        const val DELETED_NAME = "_deleted.txt"
        val ABSOLUTE = ExportConfig(absolute = true, windowsPaths = false)
        val M3U8_MIME =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension("m3u8")
                ?: "application/octet-stream"
    }
}
