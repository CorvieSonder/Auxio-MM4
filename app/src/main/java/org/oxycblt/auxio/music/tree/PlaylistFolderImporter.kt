/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistFolderImporter.kt is part of Auxio.
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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.playlist.ExternalPlaylistManager
import org.oxycblt.musikr.playlist.ImportedPlaylist
import timber.log.Timber as L

/**
 * Imports nested folders of M3U8 files as nested playlists, the layout written by
 * [PlaylistFolderExporter].
 *
 * The picked folder becomes one playlist. A playlist file named like its folder holds that
 * playlist's own songs, other playlist files in the folder become child playlists with only songs,
 * and sub-folders become child playlists. Existing playlists are never changed: a name that is
 * already taken at a level gets a " (2)" suffix.
 */
class PlaylistFolderImporter
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val playlistTreeRepository: PlaylistTreeRepository,
) {
    private val externalPlaylistManager = ExternalPlaylistManager.from(context)

    /** The outcome of an import. */
    data class Result(val playlists: Int, val missingSongs: Int)

    private data class Entry(val id: String, val name: String, val isFolder: Boolean)

    private class Counter {
        var playlists = 0
        var missingSongs = 0
    }

    /**
     * Import the folder at [treeUri] as a playlist inside [parent], or at the top level if null.
     *
     * @return The [Result], or null if nothing could be imported.
     */
    suspend fun import(treeUri: Uri, parent: Playlist?): Result? {
        return try {
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            val rootName =
                queryName(DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)) ?: "_"
            val counter = Counter()
            importFolder(treeUri, Entry(rootId, rootName, true), parent, counter)
            if (counter.playlists > 0) Result(counter.playlists, counter.missingSongs) else null
        } catch (e: Exception) {
            L.e("Unable to import the playlist folder $treeUri")
            L.e(e.stackTraceToString())
            null
        }
    }

    private suspend fun importFolder(
        treeUri: Uri,
        folder: Entry,
        parent: Playlist?,
        counter: Counter,
    ) {
        val children = listChildren(treeUri, folder.id)
        val files = children.filter { !it.isFolder && isPlaylistFile(it.name) }
        // The exporter's folder may have been renamed to "<name> (1)" by the provider.
        val baseName = folder.name.replace(COPY_SUFFIX, "")
        val own = files.find {
            stemOf(it.name).equals(folder.name, ignoreCase = true) ||
                stemOf(it.name).equals(baseName, ignoreCase = true)
        }
        val ownImport = own?.let { read(treeUri, it) }
        val name = ownImport?.let { nameOf(it, own) } ?: folder.name
        val songs = ownImport?.let { match(it, counter) } ?: listOf()
        val playlist = create(name, songs, parent) ?: return
        counter.playlists++

        for (file in files.filter { it != own }.sortedBy { it.name.lowercase() }) {
            val imported = read(treeUri, file) ?: continue
            if (create(nameOf(imported, file), match(imported, counter), playlist) != null) {
                counter.playlists++
            }
        }
        for (child in children.filter { it.isFolder }.sortedBy { it.name.lowercase() }) {
            importFolder(treeUri, child, playlist, counter)
        }
    }

    private suspend fun read(treeUri: Uri, file: Entry): ImportedPlaylist? =
        externalPlaylistManager.import(
            DocumentsContract.buildDocumentUriUsingTree(treeUri, file.id)
        )

    /**
     * The name of an imported playlist: the #PLAYLIST name if the file has one, otherwise the file
     * name. [ExternalPlaylistManager] makes up a name from the file name when #PLAYLIST is missing,
     * and that made-up name loses characters, so it is replaced by the plain file name.
     */
    private fun nameOf(imported: ImportedPlaylist, file: Entry): String {
        val stem = stemOf(file.name)
        val madeUp = file.name.split(".")[0].replace(Regex("[_-]"), " ").replace(Regex("\\s+"), " ")
        val name = imported.name
        return if (name == null || name == madeUp) stem else name
    }

    private fun match(imported: ImportedPlaylist, counter: Counter): List<Song> {
        val library = musicRepository.library ?: return listOf()
        val songs = imported.paths.mapNotNull { it.firstNotNullOfOrNull(library::findSongByPath) }
        counter.missingSongs += imported.paths.size - songs.size
        return songs
    }

    private suspend fun create(name: String, songs: List<Song>, parent: Playlist?): Playlist? {
        val taken =
            withContext(Dispatchers.Main) {
                playlistTreeRepository.childrenOf(parent).map { it.name.raw }.toSet()
            }
        var unique = name
        var n = 2
        while (unique in taken) {
            unique = "$name (${n++})"
        }
        val existing = musicRepository.library?.playlists?.map { it.uid }?.toSet() ?: return null
        musicRepository.createPlaylist(unique, songs)
        // The new playlist is not returned, so find it as the one that did not exist.
        val created =
            musicRepository.library?.playlists?.find { it.uid !in existing } ?: return null
        if (parent != null) {
            withContext(Dispatchers.Main) { playlistTreeRepository.move(listOf(created), parent) }
        }
        return created
    }

    private fun listChildren(treeUri: Uri, folderId: String): List<Entry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderId)
        val entries = mutableListOf<Entry>()
        context.contentResolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                val isFolder = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                entries.add(Entry(id, name, isFolder))
            }
        }
        return entries
    }

    private fun queryName(uri: Uri): String? =
        context.contentResolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(1) else null
        }

    private companion object {
        val PROJECTION =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
        val COPY_SUFFIX = Regex(" \\(\\d+\\)$")

        fun isPlaylistFile(name: String) =
            name.endsWith(".m3u8", ignoreCase = true) || name.endsWith(".m3u", ignoreCase = true)

        fun stemOf(name: String) = name.substringBeforeLast('.')
    }
}
