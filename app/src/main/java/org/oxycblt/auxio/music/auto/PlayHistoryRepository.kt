/*
 * Copyright (c) 2026 Auxio Project
 * PlayHistoryRepository.kt is part of Auxio.
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

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/**
 * Manages the history of played songs. The history is kept in memory so that it can be read
 * synchronously, and is persisted in the background.
 */
interface PlayHistoryRepository {
    /** How often and when each played song was last played, by the [Music.UID] of the song. */
    val summaries: Map<Music.UID, PlaySummary>

    /**
     * Record that a [Song] was played just now.
     *
     * @param song The [Song] that was played.
     */
    fun recordPlay(song: Song)

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

    /** A listener for changes to the play history. */
    interface Listener {
        /** Called when the play history has changed. */
        fun onPlayHistoryChanged()
    }
}

@Singleton
class PlayHistoryRepositoryImpl @Inject constructor(private val playHistoryDao: PlayHistoryDao) :
    PlayHistoryRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listeners = mutableListOf<PlayHistoryRepository.Listener>()
    @Volatile
    override var summaries = mapOf<Music.UID, PlaySummary>()
        private set

    init {
        scope.launch {
            val loaded =
                try {
                    playHistoryDao.getSummaries().associateBy { it.songUid }
                } catch (e: Exception) {
                    L.e("Unable to load play history: $e")
                    return@launch
                }
            withContext(Dispatchers.Main) {
                // Add anything that was played while the load was still running.
                summaries =
                    loaded +
                        summaries.mapValues { (uid, played) ->
                            val before = loaded[uid] ?: return@mapValues played
                            played.copy(playCount = before.playCount + played.playCount)
                        }
                dispatchChange()
            }
        }
    }

    override fun recordPlay(song: Song) {
        val playedMs = System.currentTimeMillis()
        L.d("Recording play of ${song.uid}")
        val before = summaries[song.uid]
        summaries =
            summaries + (song.uid to PlaySummary(song.uid, (before?.playCount ?: 0) + 1, playedMs))
        scope.launch {
            try {
                playHistoryDao.insertPlay(PlayedSong(songUid = song.uid, playedMs = playedMs))
            } catch (e: Exception) {
                L.e("Unable to save play: $e")
            }
        }
        dispatchChange()
    }

    @Synchronized
    override fun addListener(listener: PlayHistoryRepository.Listener) {
        listeners.add(listener)
    }

    @Synchronized
    override fun removeListener(listener: PlayHistoryRepository.Listener) {
        listeners.remove(listener)
    }

    private fun dispatchChange() {
        val current = synchronized(this) { listeners.toList() }
        for (listener in current) {
            listener.onPlayHistoryChanged()
        }
    }
}
