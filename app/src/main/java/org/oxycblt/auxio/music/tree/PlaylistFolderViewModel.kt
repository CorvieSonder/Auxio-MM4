/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistFolderViewModel.kt is part of Auxio.
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

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.util.Event
import org.oxycblt.auxio.util.MutableEvent
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.playlist.ExportConfig
import timber.log.Timber as L

/** Drives exporting and importing playlists as nested folders of M3U8 files. */
@HiltViewModel
class PlaylistFolderViewModel
@Inject
constructor(
    private val musicRepository: MusicRepository,
    private val playlistTreeRepository: PlaylistTreeRepository,
    private val exporter: PlaylistFolderExporter,
    private val importer: PlaylistFolderImporter,
) : ViewModel() {
    /** A request to pick a folder to import, awaiting a view that can show the folder picker. */
    val importRequest: Event<ImportRequest>
        field = MutableEvent<ImportRequest>()

    /** A scanned folder whose items the user should choose from before importing. */
    val importChoices: Event<ImportChoices>
        field = MutableEvent<ImportChoices>()

    /** The outcome of the last export or import, awaiting a view that can show it. */
    val message: Event<FolderMessage>
        field = MutableEvent<FolderMessage>()

    /** The folder import waiting for the folder picker to return. */
    var pendingImport: ImportRequest? = null

    /** Whether [playlist] has child playlists that a folder export would include. */
    fun hasChildren(playlist: Playlist) = playlistTreeRepository.childrenOf(playlist).isNotEmpty()

    /**
     * Ask the user for a folder to import.
     *
     * @param parent The [Playlist] to import into, or null for the top level.
     */
    fun requestImport(parent: Playlist?) {
        L.d("Requesting a folder import into $parent")
        importRequest.put(ImportRequest(parent?.uid))
    }

    /** List the folder at [treeUri] so the user can choose what to import from it. */
    fun scanFolder(treeUri: Uri, parentUid: Music.UID?) {
        viewModelScope.launch(Dispatchers.IO) {
            val scan = importer.scan(treeUri)
            if (scan == null) {
                message.put(FolderMessage.ImportFailed)
            } else {
                importChoices.put(ImportChoices(scan, parentUid))
            }
        }
    }

    /** Import the [selected] items of [choices], wrapped in one playlist if [wrap]. */
    fun importFolder(choices: ImportChoices, selected: List<PlaylistDocument>, wrap: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val parent = choices.parentUid?.let { musicRepository.library?.findPlaylist(it) }
            val result = importer.import(choices.scan, selected, wrap, parent)
            message.put(
                if (result != null) {
                    FolderMessage.Imported(result.playlists, result.missingSongs)
                } else {
                    FolderMessage.ImportFailed
                }
            )
        }
    }

    /** Export [playlist] and its descendants into the folder at [treeUri]. */
    fun exportFolder(playlist: Playlist, treeUri: Uri, config: ExportConfig, replace: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = exporter.export(playlist, treeUri, config, replace)
            message.put(
                if (count != null) FolderMessage.Exported(count) else FolderMessage.ExportFailed
            )
        }
    }

    /** A pending folder import into the playlist with [parentUid], or the top level if null. */
    data class ImportRequest(val parentUid: Music.UID?)

    /** A scanned folder to import into the playlist with [parentUid], or the top level if null. */
    data class ImportChoices(val scan: PlaylistFolderImporter.Scan, val parentUid: Music.UID?)
}

/** The outcome of a folder export or import. */
sealed interface FolderMessage {
    data class Exported(val playlists: Int) : FolderMessage

    data object ExportFailed : FolderMessage

    data class Imported(val playlists: Int, val missingSongs: Int) : FolderMessage

    data object ImportFailed : FolderMessage
}
