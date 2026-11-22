/*
 * Copyright (c) 2026 Auxio Project
 * ListenedDatabase.kt is part of Auxio.
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

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.oxycblt.musikr.Music

/** Stores which songs were played to the end, and when that first happened. */
@Database(entities = [ListenedSong::class], version = 1, exportSchema = false)
@TypeConverters(Music.UID.TypeConverters::class)
abstract class ListenedDatabase : RoomDatabase() {
    abstract fun listenedDao(): ListenedDao
}

@Dao
interface ListenedDao {
    /** Get every song that was played to the end. */
    @Query("SELECT * FROM ListenedSong") suspend fun getAll(): List<ListenedSong>

    /** Record a song as listened, keeping the existing date if it already is. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(song: ListenedSong)
}

/**
 * A song that was played to the end at least once.
 *
 * @param songUid The [Music.UID] of the song.
 * @param firstListenedMs When the song was first played to the end, in milliseconds since the unix
 *   epoch.
 */
@Entity data class ListenedSong(@PrimaryKey val songUid: Music.UID, val firstListenedMs: Long)
