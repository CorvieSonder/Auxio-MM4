/*
 * Copyright (c) 2026 Auxio Project
 * ResumeDatabase.kt is part of Auxio.
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

/** Stores where long songs were left off, so that they can resume from there. */
@Database(entities = [ResumePosition::class], version = 1, exportSchema = false)
@TypeConverters(Music.UID.TypeConverters::class)
abstract class ResumeDatabase : RoomDatabase() {
    abstract fun resumeDao(): ResumeDao
}

@Dao
interface ResumeDao {
    /** Get every saved position. */
    @Query("SELECT * FROM ResumePosition") suspend fun getAll(): List<ResumePosition>

    /** Save a position, replacing any older one for the same song. */
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(position: ResumePosition)

    /** Remove the saved position of a song, if there is one. */
    @Query("DELETE FROM ResumePosition WHERE songUid = :songUid")
    suspend fun delete(songUid: Music.UID)
}

/**
 * Where a long song was left off.
 *
 * @param songUid The [Music.UID] of the song.
 * @param positionMs The position to resume from, in milliseconds.
 * @param savedMs When the position was saved, in milliseconds since the unix epoch.
 */
@Entity
data class ResumePosition(
    @PrimaryKey val songUid: Music.UID,
    val positionMs: Long,
    val savedMs: Long,
)
