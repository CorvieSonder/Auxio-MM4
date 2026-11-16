/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistTreeRepository.kt is part of Auxio.
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

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Playlist
import timber.log.Timber as L

/**
 * Nests playlists inside other playlists. Any playlist can hold child playlists as well as its own
 * songs, to any depth. Only the hierarchy is stored here; the playlists themselves stay in the
 * music library.
 *
 * A playlist whose parent no longer exists is treated as a top-level playlist.
 */
interface PlaylistTreeRepository {
    /**
     * Get the playlist that holds a playlist.
     *
     * @param playlist The [Playlist] to look up.
     * @return The parent [Playlist], or null if [playlist] is a top-level playlist.
     */
    fun parentOf(playlist: Playlist): Playlist?

    /**
     * Get the child playlists of a playlist, unsorted.
     *
     * @param playlist The [Playlist] to look up, or null for the top-level playlists.
     * @return The [Playlist]s directly inside [playlist].
     */
    fun childrenOf(playlist: Playlist?): List<Playlist>

    /**
     * Get the playlists above a playlist.
     *
     * @param playlist The [Playlist] to look up.
     * @return The [Playlist]s from the top level down to the parent of [playlist]. Empty if
     *   [playlist] is a top-level playlist.
     */
    fun pathOf(playlist: Playlist): List<Playlist>

    /**
     * Get every playlist below a playlist.
     *
     * @param playlist The [Playlist] to look up.
     * @return The children of [playlist], their children, and so on.
     */
    fun descendantsOf(playlist: Playlist): List<Playlist>

    /**
     * Check whether a playlist can be moved into another one.
     *
     * @param playlist The [Playlist] to move.
     * @param parent The new parent [Playlist], or null for the top level.
     * @return false if [parent] is [playlist] itself or one of its descendants.
     */
    fun canMove(playlist: Playlist, parent: Playlist?): Boolean

    /**
     * Whether moving playlists into a level would leave two playlists with the same name in it.
     *
     * @param playlists The [Playlist]s to move.
     * @param parent The new parent [Playlist], or null for the top level.
     * @return true if two of [playlists] share a name, or one shares a name with a playlist that is
     *   already in [parent].
     */
    fun hasNameClash(playlists: List<Playlist>, parent: Playlist?): Boolean

    /**
     * Move playlists into another playlist. Playlists that cannot be moved there (see [canMove])
     * are left where they are.
     *
     * @param playlists The [Playlist]s to move.
     * @param parent The new parent [Playlist], or null for the top level.
     */
    fun move(playlists: List<Playlist>, parent: Playlist?)

    /**
     * Forget the position of deleted playlists.
     *
     * @param playlists The [Playlist]s that were deleted.
     */
    fun forget(playlists: List<Playlist>)

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

    /** A listener for changes to the playlist tree. */
    interface Listener {
        /** Called when a playlist was moved, or when the tree was loaded. */
        fun onPlaylistTreeChanged()
    }
}

@Singleton
class PlaylistTreeRepositoryImpl
@Inject
constructor(
    private val playlistTreeDao: PlaylistTreeDao,
    private val musicRepository: MusicRepository,
) : PlaylistTreeRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listeners = mutableListOf<PlaylistTreeRepository.Listener>()
    // Child playlist UID -> parent playlist UID. Entries of deleted playlists are only removed by
    // forget(), never on library changes, so a library that is still loading cannot wipe the tree.
    @Volatile private var parents = mapOf<Music.UID, Music.UID>()

    init {
        scope.launch {
            val loaded =
                try {
                    playlistTreeDao.getNodes().associate { it.playlistUid to it.parentUid }
                } catch (e: Exception) {
                    L.e("Unable to load playlist tree")
                    L.e(e.stackTraceToString())
                    return@launch
                }
            L.d("Loaded ${loaded.size} nested playlists")
            // Moves made while loading take priority over the persisted ones.
            parents = loaded + parents
            withContext(Dispatchers.Main) { dispatchTreeChanged() }
        }
    }

    override fun parentOf(playlist: Playlist): Playlist? {
        val parentUid = parents[playlist.uid] ?: return null
        return musicRepository.library?.findPlaylist(parentUid)
    }

    override fun childrenOf(playlist: Playlist?): List<Playlist> {
        val library = musicRepository.library ?: return listOf()
        return library.playlists.filter { parentOf(it)?.uid == playlist?.uid }
    }

    override fun pathOf(playlist: Playlist): List<Playlist> {
        val path = mutableListOf<Playlist>()
        val seen = mutableSetOf(playlist.uid)
        var parent = parentOf(playlist)
        // The seen set guards against a cycle, which move() never creates.
        while (parent != null && seen.add(parent.uid)) {
            path.add(0, parent)
            parent = parentOf(parent)
        }
        return path
    }

    override fun descendantsOf(playlist: Playlist): List<Playlist> {
        val library = musicRepository.library ?: return listOf()
        val byParent = library.playlists.groupBy { parentOf(it)?.uid }
        val descendants = mutableListOf<Playlist>()
        val seen = mutableSetOf(playlist.uid)
        val queue = ArrayDeque(listOf(playlist))
        while (queue.isNotEmpty()) {
            for (child in byParent[queue.removeFirst().uid].orEmpty()) {
                if (seen.add(child.uid)) {
                    descendants.add(child)
                    queue.add(child)
                }
            }
        }
        return descendants
    }

    override fun canMove(playlist: Playlist, parent: Playlist?): Boolean {
        if (parent == null) {
            return true
        }
        return parent.uid != playlist.uid && pathOf(parent).none { it.uid == playlist.uid }
    }

    override fun hasNameClash(playlists: List<Playlist>, parent: Playlist?): Boolean {
        val moving = playlists.mapTo(mutableSetOf()) { it.uid }
        val names = playlists.map { it.name.raw }
        if (names.size != names.toSet().size) {
            return true
        }
        return childrenOf(parent).any { it.uid !in moving && it.name.raw in names }
    }

    override fun move(playlists: List<Playlist>, parent: Playlist?) {
        val moved = playlists.filter { canMove(it, parent) && parentOf(it)?.uid != parent?.uid }
        if (moved.isEmpty()) {
            return
        }
        L.d("Moving ${moved.size} playlists into $parent")
        parents =
            if (parent == null) {
                parents - moved.map { it.uid }.toSet()
            } else {
                parents + moved.associate { it.uid to parent.uid }
            }
        dispatchTreeChanged()
        scope.launch {
            try {
                if (parent == null) {
                    playlistTreeDao.deleteNodes(moved.map { it.uid })
                } else {
                    playlistTreeDao.insertNodes(moved.map { PlaylistNode(it.uid, parent.uid) })
                }
            } catch (e: Exception) {
                L.e("Unable to persist the move of ${moved.size} playlists")
                L.e(e.stackTraceToString())
            }
        }
    }

    override fun forget(playlists: List<Playlist>) {
        val uids = playlists.mapTo(mutableSetOf()) { it.uid }
        // Children of a forgotten playlist become top-level playlists.
        val removed = parents.filter { (child, parent) -> child in uids || parent in uids }.keys
        if (removed.isEmpty()) {
            return
        }
        L.d("Forgetting ${removed.size} tree entries")
        parents = parents - removed
        dispatchTreeChanged()
        scope.launch {
            try {
                playlistTreeDao.deleteNodes(removed.toList())
            } catch (e: Exception) {
                L.e("Unable to forget ${removed.size} tree entries")
                L.e(e.stackTraceToString())
            }
        }
    }

    @Synchronized
    override fun addListener(listener: PlaylistTreeRepository.Listener) {
        listeners.add(listener)
    }

    @Synchronized
    override fun removeListener(listener: PlaylistTreeRepository.Listener) {
        listeners.remove(listener)
    }

    @Synchronized
    private fun dispatchTreeChanged() {
        for (listener in listeners) {
            listener.onPlaylistTreeChanged()
        }
    }
}
