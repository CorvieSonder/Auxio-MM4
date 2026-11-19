/*
 * Copyright (c) 2026 Auxio Project
 * MoodRepository.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.mood

import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.oxycblt.auxio.music.write.TagFileWriter
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Song

/** Keeps the moods of songs that were edited in the app. These replace the tagged moods. */
interface MoodRepository {
    /**
     * Get every edited song and its moods.
     *
     * @return The moods of each edited song, by song [Music.UID].
     */
    suspend fun overrides(): Map<Music.UID, List<String>>

    /**
     * Whether the moods of a [Song] were edited in the app. Only valid after [overrides] was called
     * once, which happens when music is loaded.
     */
    fun isEdited(song: Song): Boolean

    /**
     * Save edited moods.
     *
     * @param changes The new moods of each song, by song [Music.UID]. Null resets a song to the
     *   moods in its file.
     * @return Every edited song and its moods after the change.
     */
    suspend fun save(changes: Map<Music.UID, List<String>?>): Map<Music.UID, List<String>>
}

@Singleton
class MoodRepositoryImpl
@Inject
constructor(private val moodDao: MoodDao, private val tagFileWriter: Lazy<TagFileWriter>) :
    MoodRepository {
    private val mutex = Mutex()
    @Volatile private var cached: Map<Music.UID, List<String>>? = null

    override suspend fun overrides() = mutex.withLock { loadLocked() }

    override fun isEdited(song: Song) = cached?.containsKey(song.uid) == true

    override suspend fun save(changes: Map<Music.UID, List<String>?>) = mutex.withLock {
        val current = loadLocked().toMutableMap()
        val now = System.currentTimeMillis()
        val reset = changes.filterValues { it == null }.keys.toList()
        val edited = changes.mapNotNull { (uid, moods) -> moods?.let { uid to it } }
        if (reset.isNotEmpty()) {
            moodDao.delete(reset)
            reset.forEach { current.remove(it) }
        }
        if (edited.isNotEmpty()) {
            moodDao.insert(
                edited.map { (uid, moods) ->
                    EditedMoods(uid, moods.joinToString(EditedMoods.SEPARATOR), false, now)
                }
            )
            current.putAll(edited)
            tagFileWriter.get().flush()
        }
        current.also { cached = it }
    }

    private suspend fun loadLocked(): Map<Music.UID, List<String>> =
        cached
            ?: moodDao
                .getAll()
                .associate { row ->
                    row.songUid to row.moods.split(EditedMoods.SEPARATOR).filter { it.isNotEmpty() }
                }
                .also { cached = it }
}
