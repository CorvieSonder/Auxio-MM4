/*
 * Copyright (c) 2026 Auxio Project
 * AutoPlaylistRepository.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.auto

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.oxycblt.auxio.music.rating.RatingRepository
import org.oxycblt.auxio.playback.state.PlaybackStateManager
import org.oxycblt.auxio.playback.state.QueueChange
import org.oxycblt.musikr.Library
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.MusicParent
import org.oxycblt.musikr.MutableLibrary
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song

/**
 * Builds the [AutoPlaylist]s of a [Library]. They are built on demand and kept until something that
 * they depend on changes.
 */
interface AutoPlaylistRepository {
    /**
     * Get the [AutoPlaylist]s to show for a [Library].
     *
     * @param library The [Library] to build the playlists from.
     * @return The [AutoPlaylist]s that are turned on, in the order they should be shown.
     */
    fun playlists(library: Library): List<AutoPlaylist>

    /**
     * Find an [AutoPlaylist] by its [Music.UID].
     *
     * @param uid The [Music.UID] to search for.
     * @param library The [Library] to build the playlist from.
     * @return The [AutoPlaylist] with the [Music.UID], or null if it is not one of an automatic
     *   playlist.
     */
    fun find(uid: Music.UID, library: Library): AutoPlaylist?

    /**
     * Make the [AutoPlaylist]s of a [MutableLibrary] findable through [Library.findPlaylist], so
     * that they can be shown and played like any other playlist. They are not part of
     * [Library.playlists], and attempts to edit them are ignored.
     *
     * @param library The [MutableLibrary] to wrap.
     * @return A [MutableLibrary] that also finds [AutoPlaylist]s.
     */
    fun wrap(library: MutableLibrary): MutableLibrary

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

    /** A listener for changes to the [AutoPlaylist]s. */
    interface Listener {
        /** Called when the songs of one or more [AutoPlaylist]s may have changed. */
        fun onAutoPlaylistsChanged()
    }
}

@Singleton
class AutoPlaylistRepositoryImpl
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val autoPlaylistSettings: AutoPlaylistSettings,
    private val ratingRepository: RatingRepository,
    private val playHistoryRepository: PlayHistoryRepository,
    private val playbackManager: PlaybackStateManager,
) :
    AutoPlaylistRepository,
    AutoPlaylistSettings.Listener,
    RatingRepository.Listener,
    PlayHistoryRepository.Listener,
    PlaybackStateManager.Listener {
    private val listeners = mutableListOf<AutoPlaylistRepository.Listener>()
    private val built = mutableMapOf<AutoPlaylist.Kind, AutoPlaylist>()
    private var builtFrom: Library? = null

    init {
        autoPlaylistSettings.registerListener(this)
        ratingRepository.addListener(this)
        playHistoryRepository.addListener(this)
        playbackManager.addListener(this)
    }

    override fun playlists(library: Library): List<AutoPlaylist> {
        val shown = autoPlaylistSettings.shownKinds
        return AutoPlaylist.Kind.entries.filter { it in shown }.map { get(it, library) }
    }

    override fun find(uid: Music.UID, library: Library): AutoPlaylist? {
        val kind = AutoPlaylist.Kind.entries.find { it.uid == uid } ?: return null
        return get(kind, library)
    }

    override fun wrap(library: MutableLibrary): MutableLibrary =
        if (library is AutoLibrary) library else AutoLibrary(library)

    override fun onAutoPlaylistSettingsChanged() = invalidate()

    override fun onRatingsChanged() = invalidate(AutoPlaylist.Kind.RATED)

    override fun onPlayHistoryChanged() =
        invalidate(AutoPlaylist.Kind.RECENTLY_PLAYED, AutoPlaylist.Kind.MOST_PLAYED)

    override fun onQueueChanged(queue: List<Song>, index: Int, change: QueueChange) =
        invalidate(AutoPlaylist.Kind.NOW_PLAYING)

    override fun onQueueReordered(queue: List<Song>, index: Int, isShuffled: Boolean) =
        invalidate(AutoPlaylist.Kind.NOW_PLAYING)

    override fun onNewPlayback(
        parent: MusicParent?,
        queue: List<Song>,
        index: Int,
        isShuffled: Boolean,
    ) = invalidate(AutoPlaylist.Kind.NOW_PLAYING)

    override fun onSessionEnded() = invalidate(AutoPlaylist.Kind.NOW_PLAYING)

    @Synchronized
    override fun addListener(listener: AutoPlaylistRepository.Listener) {
        listeners.add(listener)
    }

    @Synchronized
    override fun removeListener(listener: AutoPlaylistRepository.Listener) {
        listeners.remove(listener)
    }

    private fun invalidate(vararg kinds: AutoPlaylist.Kind) {
        val current =
            synchronized(this) {
                if (kinds.isEmpty()) {
                    built.clear()
                } else {
                    kinds.forEach(built::remove)
                }
                listeners.toList()
            }
        for (listener in current) {
            listener.onAutoPlaylistsChanged()
        }
    }

    @Synchronized
    private fun get(kind: AutoPlaylist.Kind, library: Library): AutoPlaylist {
        if (builtFrom !== library) {
            // A different library, nothing built before can be used.
            built.clear()
            builtFrom = library
        }
        return built.getOrPut(kind) {
            AutoPlaylist(kind, context.getString(kind.nameRes), songsOf(kind, library))
        }
    }

    private fun songsOf(kind: AutoPlaylist.Kind, library: Library): List<Song> =
        when (kind) {
            AutoPlaylist.Kind.RECENTLY_ADDED -> {
                val since =
                    System.currentTimeMillis() - autoPlaylistSettings.recentlyAddedDays * DAY_MS
                library.songs
                    .filter { it.addedMs >= since }
                    .sortedWith(compareByDescending<Song> { it.addedMs }.thenBy { it.name })
            }
            AutoPlaylist.Kind.RECENTLY_PLAYED ->
                playHistoryRepository.summaries.values
                    .sortedByDescending { it.lastPlayedMs }
                    .asSequence()
                    .mapNotNull { library.findSong(it.songUid) }
                    .take(MAX_PLAYED_SONGS)
                    .toList()
            AutoPlaylist.Kind.MOST_PLAYED ->
                playHistoryRepository.summaries.values
                    .sortedWith(
                        compareByDescending<PlaySummary> { it.playCount }
                            .thenByDescending { it.lastPlayedMs }
                    )
                    .asSequence()
                    .mapNotNull { library.findSong(it.songUid) }
                    .take(MAX_PLAYED_SONGS)
                    .toList()
            AutoPlaylist.Kind.RATED -> {
                val minimum = autoPlaylistSettings.ratedMinimum
                library.songs
                    .mapNotNull { song ->
                        ratingRepository
                            .ratingOf(song)
                            ?.takeIf { it >= minimum }
                            ?.let { song to it.halfStars }
                    }
                    .sortedWith(
                        compareByDescending<Pair<Song, Int>> { it.second }.thenBy { it.first.name }
                    )
                    .map { it.first }
            }
            AutoPlaylist.Kind.NOW_PLAYING -> playbackManager.queue
        }

    /** A [MutableLibrary] that also finds [AutoPlaylist]s, and ignores attempts to edit them. */
    private inner class AutoLibrary(private val inner: MutableLibrary) : MutableLibrary by inner {
        override fun findPlaylist(uid: Music.UID) = inner.findPlaylist(uid) ?: find(uid, inner)

        override suspend fun renamePlaylist(playlist: Playlist, name: String) =
            if (playlist is AutoPlaylist) this else inner.renamePlaylist(playlist, name)

        override suspend fun addToPlaylist(playlist: Playlist, songs: List<Song>) =
            if (playlist is AutoPlaylist) this else inner.addToPlaylist(playlist, songs)

        override suspend fun rewritePlaylist(playlist: Playlist, songs: List<Song>) =
            if (playlist is AutoPlaylist) this else inner.rewritePlaylist(playlist, songs)

        override suspend fun deletePlaylist(playlist: Playlist) =
            if (playlist is AutoPlaylist) this else inner.deletePlaylist(playlist)
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val MAX_PLAYED_SONGS = 100
    }
}
