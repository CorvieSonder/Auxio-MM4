/*
 * Copyright (c) 2026 Auxio Project
 * ListenedRepository.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.listened

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/** Keeps track of which songs were played to the end ("listened"). */
interface ListenedRepository {
    /**
     * Record that a [Song] was just played to the end. Only the first time is kept.
     *
     * @param song The [Song] that was played to the end.
     */
    fun recordListened(song: Song)

    /**
     * Get when each listened song was first played to the end.
     *
     * @return The first listened time in milliseconds since the unix epoch, by [Music.UID].
     */
    suspend fun getListened(): Map<Music.UID, Long>
}

@Singleton
class ListenedRepositoryImpl @Inject constructor(private val listenedDao: ListenedDao) :
    ListenedRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun recordListened(song: Song) {
        val now = System.currentTimeMillis()
        L.d("Recording $song as listened")
        scope.launch {
            try {
                listenedDao.insert(ListenedSong(song.uid, now))
            } catch (e: Exception) {
                L.e("Unable to record listened song: $e")
            }
        }
    }

    override suspend fun getListened() =
        listenedDao.getAll().associate { it.songUid to it.firstListenedMs }
}
