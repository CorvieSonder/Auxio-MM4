/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistSortRepository.kt is part of Auxio.
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
 
package org.oxycblt.auxio.detail.persist

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.oxycblt.auxio.list.sort.Sort
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/**
 * Manages the [Sort] chosen for each [Playlist]'s song list. Sorts are kept in memory so that they
 * can be read synchronously, and are persisted in the background.
 *
 * A playlist without a chosen [Sort] uses [DEFAULT], which keeps the saved order of the playlist.
 */
interface PlaylistSortRepository {
    /**
     * Get the [Sort] chosen for a [Playlist].
     *
     * @param playlist The [Playlist] to get the [Sort] of.
     * @return The chosen [Sort], or [DEFAULT] if there is none.
     */
    fun getSort(playlist: Playlist): Sort

    /**
     * Choose a new [Sort] for a [Playlist]. This does not change the saved order of the playlist.
     *
     * @param playlist The [Playlist] to sort.
     * @param sort The new [Sort].
     */
    fun setSort(playlist: Playlist, sort: Sort)

    /**
     * Get the songs of a [Playlist] in the order its chosen [Sort] gives.
     *
     * @param playlist The [Playlist] to get the songs of.
     * @return The sorted songs of the [Playlist].
     */
    fun songs(playlist: Playlist): List<Song> = getSort(playlist).songs(playlist.songs)

    /**
     * Add a [Listener] to this instance.
     *
     * @param listener The [Listener] to add.
     */
    fun addListener(listener: Listener)

    /**
     * Remove a [Listener] from this instance.
     *
     * @param listener The [Listener] to remove.
     */
    fun removeListener(listener: Listener)

    /** A listener for changes to the chosen playlist [Sort]s. */
    interface Listener {
        /** Called when the chosen [Sort] of one or more playlists changes. */
        fun onPlaylistSortsChanged()
    }

    companion object {
        /** The default [Sort], which keeps the saved order of a playlist. */
        val DEFAULT = Sort(Sort.Mode.ByCustom, Sort.Direction.ASCENDING)
    }
}

@Singleton
class PlaylistSortRepositoryImpl
@Inject
constructor(
    private val playlistSortDao: PlaylistSortDao,
    private val musicRepository: MusicRepository,
) : PlaylistSortRepository, MusicRepository.UpdateListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listeners = mutableListOf<PlaylistSortRepository.Listener>()
    @Volatile private var sorts = mapOf<Music.UID, Sort>()

    init {
        musicRepository.addUpdateListener(this)
        scope.launch {
            val loaded =
                try {
                    playlistSortDao.getSorts().mapNotNull { item ->
                        Sort.fromIntCode(item.sortIntCode)?.let { item.playlistUid to it }
                    }
                } catch (e: Exception) {
                    L.e("Unable to load playlist sorts")
                    L.e(e.stackTraceToString())
                    return@launch
                }
            L.d("Loaded ${loaded.size} playlist sorts")
            // Sorts chosen while loading take priority over the persisted ones.
            sorts = loaded.toMap() + sorts
            withContext(Dispatchers.Main) { dispatchSortsChanged() }
        }
    }

    override fun getSort(playlist: Playlist) = sorts[playlist.uid] ?: PlaylistSortRepository.DEFAULT

    override fun setSort(playlist: Playlist, sort: Sort) {
        if (getSort(playlist) == sort) {
            return
        }
        L.d("Setting sort of $playlist to $sort")
        sorts = sorts + (playlist.uid to sort)
        dispatchSortsChanged()
        scope.launch {
            try {
                if (sort == PlaylistSortRepository.DEFAULT) {
                    playlistSortDao.deleteSorts(listOf(playlist.uid))
                } else {
                    playlistSortDao.insertSort(PlaylistSortItem(playlist.uid, sort.intCode))
                }
            } catch (e: Exception) {
                L.e("Unable to persist sort of $playlist")
                L.e(e.stackTraceToString())
            }
        }
    }

    @Synchronized
    override fun addListener(listener: PlaylistSortRepository.Listener) {
        listeners.add(listener)
    }

    @Synchronized
    override fun removeListener(listener: PlaylistSortRepository.Listener) {
        listeners.remove(listener)
    }

    override fun onMusicChanges(changes: MusicRepository.Changes) {
        if (!changes.userLibrary) {
            return
        }
        // Forget the sorts of playlists that no longer exist.
        val library = musicRepository.library ?: return
        val existing = library.playlists.mapTo(mutableSetOf()) { it.uid }
        val removed = sorts.keys.filter { it !in existing }
        if (removed.isEmpty()) {
            return
        }
        L.d("Removing sorts of ${removed.size} deleted playlists")
        sorts = sorts - removed.toSet()
        scope.launch {
            try {
                playlistSortDao.deleteSorts(removed)
            } catch (e: Exception) {
                L.e("Unable to remove sorts of deleted playlists")
                L.e(e.stackTraceToString())
            }
        }
    }

    @Synchronized
    private fun dispatchSortsChanged() {
        for (listener in listeners) {
            listener.onPlaylistSortsChanged()
        }
    }
}
