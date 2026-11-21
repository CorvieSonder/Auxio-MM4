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
import org.oxycblt.auxio.music.tree.PlaylistDocuments.isPlaylistFile
import org.oxycblt.auxio.music.tree.PlaylistDocuments.stemOf
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.playlist.ExternalPlaylistManager
import org.oxycblt.musikr.playlist.ImportedPlaylist
import timber.log.Timber as L

/**
 * Imports nested folders of M3U8 files as nested playlists, the layout written by
 * [PlaylistFolderExporter].
 *
 * A folder becomes one playlist. A playlist file named like its folder holds that playlist's own
 * songs, other playlist files in the folder become child playlists with only songs, and sub-folders
 * become child playlists. Existing playlists are never changed: a name that is already taken at a
 * level gets a " (2)" suffix.
 *
 * The picked folder is first [scan]ned, so that the user can choose which of its items to import
 * and whether to wrap them in one playlist for the folder itself.
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

    /**
     * The contents of a picked folder.
     *
     * @param name The folder's name.
     * @param own The playlist file holding the folder's own songs, if any.
     * @param items The sub-folders and other playlist files, and [own] first if it exists, that can
     *   be chosen for import.
     */
    data class Scan(
        val treeUri: Uri,
        val folder: PlaylistDocument,
        val own: PlaylistDocument?,
        val items: List<PlaylistDocument>,
    )

    private class Counter {
        var playlists = 0
        var missingSongs = 0
    }

    /** List what the folder at [treeUri] holds, or null if it cannot be read. */
    fun scan(treeUri: Uri): Scan? {
        return try {
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            val rootName =
                PlaylistDocuments.queryName(
                    context.contentResolver,
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId),
                ) ?: "_"
            val folder = PlaylistDocument(rootId, rootName, true)
            val children = PlaylistDocuments.listChildren(context.contentResolver, treeUri, rootId)
            val own = findOwn(folder, children)
            val files =
                children
                    .filter { !it.isFolder && isPlaylistFile(it.name) && it != own }
                    .sortedBy { it.name.lowercase() }
            val folders = children.filter { it.isFolder }.sortedBy { it.name.lowercase() }
            Scan(treeUri, folder, own, listOfNotNull(own) + folders + files)
        } catch (e: Exception) {
            L.e("Unable to read the playlist folder $treeUri")
            L.e(e.stackTraceToString())
            null
        }
    }

    /**
     * Import the [selected] items of [scan] into [parent], or at the top level if null.
     *
     * @param wrap Whether to create one playlist for the folder itself, holding the folder's own
     *   songs and the selected items as children. Otherwise the selected items go straight into
     *   [parent], and the folder's own file becomes a playlist like any other file.
     * @return The [Result], or null if nothing could be imported.
     */
    suspend fun import(
        scan: Scan,
        selected: List<PlaylistDocument>,
        wrap: Boolean,
        parent: Playlist?,
    ): Result? {
        return try {
            val counter = Counter()
            val target =
                if (wrap) {
                    createFolderPlaylist(scan.treeUri, scan.folder, scan.own, parent, counter)
                        ?: return null
                } else {
                    parent
                }
            importItems(scan.treeUri, selected.filter { !wrap || it != scan.own }, target, counter)
            if (counter.playlists > 0) Result(counter.playlists, counter.missingSongs) else null
        } catch (e: Exception) {
            L.e("Unable to import the playlist folder ${scan.treeUri}")
            L.e(e.stackTraceToString())
            null
        }
    }

    /** Import [items] into [parent]: files as playlists, folders as nested playlists. */
    private suspend fun importItems(
        treeUri: Uri,
        items: List<PlaylistDocument>,
        parent: Playlist?,
        counter: Counter,
    ) {
        for (file in items.filter { !it.isFolder }) {
            val imported = read(treeUri, file) ?: continue
            if (create(nameOf(imported, file), match(imported, counter), parent) != null) {
                counter.playlists++
            }
        }
        for (child in items.filter { it.isFolder }) {
            importFolder(treeUri, child, parent, counter)
        }
    }

    private suspend fun importFolder(
        treeUri: Uri,
        folder: PlaylistDocument,
        parent: Playlist?,
        counter: Counter,
    ) {
        val children = PlaylistDocuments.listChildren(context.contentResolver, treeUri, folder.id)
        val own = findOwn(folder, children)
        val playlist = createFolderPlaylist(treeUri, folder, own, parent, counter) ?: return
        val files =
            children
                .filter { !it.isFolder && isPlaylistFile(it.name) && it != own }
                .sortedBy { it.name.lowercase() }
        val folders = children.filter { it.isFolder }.sortedBy { it.name.lowercase() }
        importItems(treeUri, files + folders, playlist, counter)
    }

    /** Create the playlist for [folder], with the songs of its [own] file if it has one. */
    private suspend fun createFolderPlaylist(
        treeUri: Uri,
        folder: PlaylistDocument,
        own: PlaylistDocument?,
        parent: Playlist?,
        counter: Counter,
    ): Playlist? {
        val ownImport = own?.let { read(treeUri, it) }
        val name = ownImport?.let { nameOf(it, own) } ?: folder.name
        val songs = ownImport?.let { match(it, counter) } ?: listOf()
        val playlist = create(name, songs, parent) ?: return null
        counter.playlists++
        return playlist
    }

    /** The playlist file in [children] holding [folder]'s own songs: the one named like it. */
    private fun findOwn(
        folder: PlaylistDocument,
        children: List<PlaylistDocument>,
    ): PlaylistDocument? {
        // The exporter's folder may have been renamed to "<name> (1)" by the provider.
        val baseName = folder.name.replace(COPY_SUFFIX, "")
        return children.find {
            !it.isFolder &&
                isPlaylistFile(it.name) &&
                (stemOf(it.name).equals(folder.name, ignoreCase = true) ||
                    stemOf(it.name).equals(baseName, ignoreCase = true))
        }
    }

    private suspend fun read(treeUri: Uri, file: PlaylistDocument): ImportedPlaylist? =
        externalPlaylistManager.import(
            DocumentsContract.buildDocumentUriUsingTree(treeUri, file.id)
        )

    /**
     * The name of an imported playlist: the #PLAYLIST name if the file has one, otherwise the file
     * name. [ExternalPlaylistManager] makes up a name from the file name when #PLAYLIST is missing,
     * and that made-up name loses characters, so it is replaced by the plain file name.
     */
    private fun nameOf(imported: ImportedPlaylist, file: PlaylistDocument): String {
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

    private companion object {
        val COPY_SUFFIX = Regex(" \\(\\d+\\)$")
    }
}
