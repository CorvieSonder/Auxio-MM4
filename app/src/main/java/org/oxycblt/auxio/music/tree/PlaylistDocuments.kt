/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistDocuments.kt is part of Auxio.
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

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

/** A file or folder inside a document tree picked with the system folder picker. */
data class PlaylistDocument(val id: String, val name: String, val isFolder: Boolean)

/** Helpers for reading and cleaning playlist folders in a document tree. */
object PlaylistDocuments {
    private val PROJECTION =
        arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )

    /** The files and folders directly inside the folder [folderId] of the tree [treeUri]. */
    fun listChildren(
        resolver: ContentResolver,
        treeUri: Uri,
        folderId: String,
    ): List<PlaylistDocument> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderId)
        val entries = mutableListOf<PlaylistDocument>()
        resolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                val isFolder = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                entries.add(PlaylistDocument(id, name, isFolder))
            }
        }
        return entries
    }

    /** The display name of the document at [uri]. */
    fun queryName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(1) else null
        }

    /**
     * Remove the playlist files inside the folder [folderId], and its sub-folders that are empty
     * afterwards. Other files, and the folders holding them, are kept.
     *
     * @return Whether the folder itself is empty afterwards.
     */
    fun clean(resolver: ContentResolver, treeUri: Uri, folderId: String): Boolean {
        var empty = true
        for (child in listChildren(resolver, treeUri, folderId)) {
            val removable =
                if (child.isFolder) clean(resolver, treeUri, child.id)
                else isPlaylistFile(child.name)
            if (removable) {
                DocumentsContract.deleteDocument(
                    resolver,
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, child.id),
                )
            } else {
                empty = false
            }
        }
        return empty
    }

    fun isPlaylistFile(name: String) =
        name.endsWith(".m3u8", ignoreCase = true) || name.endsWith(".m3u", ignoreCase = true)

    fun stemOf(name: String) = name.substringBeforeLast('.')
}
