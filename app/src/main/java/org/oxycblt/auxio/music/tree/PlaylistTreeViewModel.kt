/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistTreeViewModel.kt is part of Auxio.
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

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.oxycblt.auxio.list.ListSettings
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.util.Event
import org.oxycblt.auxio.util.MutableEvent
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Playlist
import timber.log.Timber as L

/** Requests playlist tree dialogs from anywhere in the app. Scoped to the activity. */
@HiltViewModel
class PlaylistTreeViewModel
@Inject
constructor(private val playlistTreeRepository: PlaylistTreeRepository) : ViewModel() {
    /** The [Music.UID]s of playlists the user wants to move. Shown with the move dialog. */
    val moveDecision: Event<List<Music.UID>>
        field = MutableEvent<List<Music.UID>>()

    /**
     * Ask the user where to move playlists to.
     *
     * @param playlists The [Playlist]s to move.
     */
    fun requestMove(playlists: List<Playlist>) {
        if (playlists.isEmpty()) {
            return
        }
        L.d("Requesting a move of ${playlists.size} playlists")
        moveDecision.put(playlists.map { it.uid })
    }

    /**
     * Whether a playlist is pinned to the top of the level it is in.
     *
     * @param playlist The [Playlist] to look up.
     */
    fun isPinned(playlist: Playlist) = playlistTreeRepository.isPinned(playlist)

    /**
     * Pin playlists to the top of the level they are in, or unpin them.
     *
     * @param playlists The [Playlist]s to pin or unpin.
     * @param pinned true to pin [playlists], false to unpin them.
     */
    fun setPinned(playlists: List<Playlist>, pinned: Boolean) {
        playlistTreeRepository.setPinned(playlists, pinned)
    }
}

/** Drives [MovePlaylistDialog], showing one level of the playlist tree at a time. */
@HiltViewModel
class MovePlaylistViewModel
@Inject
constructor(
    private val musicRepository: MusicRepository,
    private val playlistTreeRepository: PlaylistTreeRepository,
    private val listSettings: ListSettings,
) : ViewModel(), MusicRepository.UpdateListener, PlaylistTreeRepository.Listener {
    /** The [Playlist]s being moved. Null if none are, or if they no longer exist. */
    val playlistsToMove: StateFlow<List<Playlist>?>
        field = MutableStateFlow<List<Playlist>?>(null)

    /** The rows of the level being shown. */
    val choices: StateFlow<List<TreeChoice>>
        field = MutableStateFlow<List<TreeChoice>>(listOf())

    // The playlist whose children are shown, or null for the top level.
    private var level: Playlist? = null

    init {
        musicRepository.addUpdateListener(this)
        playlistTreeRepository.addListener(this)
    }

    override fun onCleared() {
        musicRepository.removeUpdateListener(this)
        playlistTreeRepository.removeListener(this)
    }

    override fun onMusicChanges(changes: MusicRepository.Changes) {
        if (!changes.userLibrary) {
            return
        }
        val library = musicRepository.library ?: return
        playlistsToMove.value =
            playlistsToMove.value?.mapNotNull { library.findPlaylist(it.uid) }?.ifEmpty { null }
        level = level?.let { library.findPlaylist(it.uid) }
        refreshChoices()
    }

    override fun onPlaylistTreeChanged() {
        refreshChoices()
    }

    /**
     * Set the playlists to move.
     *
     * @param playlistUids The [Music.UID]s of the [Playlist]s to move.
     */
    fun setPlaylistsToMove(playlistUids: Array<Music.UID>) {
        val library = musicRepository.library
        playlistsToMove.value = library?.let {
            playlistUids.mapNotNull(it::findPlaylist).ifEmpty { null }
        }
        refreshChoices()
    }

    /**
     * Show the children of a playlist.
     *
     * @param playlist The [Playlist] to show the children of.
     */
    fun open(playlist: Playlist) {
        level = playlist
        refreshChoices()
    }

    /** Show the level above the one currently shown. */
    fun up() {
        level = level?.let { playlistTreeRepository.parentOf(it) }
        refreshChoices()
    }

    /**
     * Move the playlists.
     *
     * @param parent The new parent [Playlist], or null for the top level.
     * @return false if nothing was moved, as a playlist with the same name is already there.
     */
    fun move(parent: Playlist?): Boolean {
        val playlists = playlistsToMove.value ?: return true
        if (playlistTreeRepository.hasNameClash(playlists, parent)) {
            return false
        }
        playlistTreeRepository.move(playlists, parent)
        return true
    }

    private fun refreshChoices() {
        val playlists = playlistsToMove.value ?: return
        val moving = playlists.mapTo(mutableSetOf()) { it.uid }
        val current = level
        val newChoices = mutableListOf<TreeChoice>()
        newChoices.add(if (current == null) TreeChoice.TopLevel else TreeChoice.Up(current))
        // The playlists being moved are left out, which also hides all of their descendants.
        val children =
            playlistTreeRepository.pinnedFirst(
                listSettings.playlistSort.playlists(
                    playlistTreeRepository.childrenOf(current).filter { it.uid !in moving }
                )
            )
        for (child in children) {
            newChoices.add(
                TreeChoice.Node(
                    child,
                    canOpen = playlistTreeRepository.childrenOf(child).any { it.uid !in moving },
                    isActive =
                        playlists.all { playlistTreeRepository.parentOf(it)?.uid == child.uid },
                )
            )
        }
        choices.value = newChoices
    }
}
