/*
 * Copyright (c) 2026 Auxio Project
 * ResumeRepository.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.resume

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.oxycblt.musikr.Music
import timber.log.Timber as L

/**
 * Keeps the positions that long songs were left off at. Positions are held in memory so that they
 * can be read right when a song starts, and written to the database in the background.
 */
interface ResumeRepository {
    /**
     * Get where a song was left off.
     *
     * @param songUid The [Music.UID] of the song.
     * @return The position in milliseconds, or null if none is saved.
     */
    fun get(songUid: Music.UID): Long?

    /**
     * Save where a song was left off.
     *
     * @param songUid The [Music.UID] of the song.
     * @param positionMs The position in milliseconds.
     */
    fun save(songUid: Music.UID, positionMs: Long)

    /**
     * Forget where a song was left off.
     *
     * @param songUid The [Music.UID] of the song.
     */
    fun clear(songUid: Music.UID)
}

@Singleton
class ResumeRepositoryImpl @Inject constructor(private val resumeDao: ResumeDao) :
    ResumeRepository {
    // One write at a time, so that writes reach the database in the order they were made.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val positions = ConcurrentHashMap<Music.UID, Long>()

    init {
        scope.launch {
            try {
                // Positions saved while loading are newer, so keep them.
                resumeDao.getAll().forEach { positions.putIfAbsent(it.songUid, it.positionMs) }
                L.d("Loaded ${positions.size} resume positions")
            } catch (e: Exception) {
                L.e("Unable to load resume positions: $e")
            }
        }
    }

    override fun get(songUid: Music.UID) = positions[songUid]

    override fun save(songUid: Music.UID, positionMs: Long) {
        if (positions.put(songUid, positionMs) == positionMs) {
            return
        }
        val now = System.currentTimeMillis()
        scope.launch {
            try {
                resumeDao.insert(ResumePosition(songUid, positionMs, now))
            } catch (e: Exception) {
                L.e("Unable to save resume position: $e")
            }
        }
    }

    override fun clear(songUid: Music.UID) {
        if (positions.remove(songUid) == null) {
            return
        }
        scope.launch {
            try {
                resumeDao.delete(songUid)
            } catch (e: Exception) {
                L.e("Unable to clear resume position: $e")
            }
        }
    }
}
