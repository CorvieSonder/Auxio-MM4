/*
 * Copyright (c) 2026 Auxio Project
 * MoodDatabase.kt is part of Auxio.
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

import androidx.room.ColumnInfo
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

/** Stores the moods of songs that were edited in the app. */
@Database(entities = [EditedMoods::class], version = 1, exportSchema = false)
@TypeConverters(Music.UID.TypeConverters::class)
abstract class MoodDatabase : RoomDatabase() {
    abstract fun moodDao(): MoodDao
}

@Dao
interface MoodDao {
    @Query("SELECT * FROM EditedMoods") suspend fun getAll(): List<EditedMoods>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(items: List<EditedMoods>)

    @Query("DELETE FROM EditedMoods WHERE songUid IN (:songUids)")
    suspend fun delete(songUids: List<Music.UID>)

    @Query("SELECT * FROM EditedMoods WHERE fileWritten = 0")
    suspend fun getUnwritten(): List<EditedMoods>

    /** Mark an edit as written, unless it changed after [changedMs]. */
    @Query(
        "UPDATE EditedMoods SET fileWritten = 1 WHERE songUid = :songUid AND changedMs = :changedMs"
    )
    suspend fun markWritten(songUid: Music.UID, changedMs: Long)
}

/**
 * The edited moods of one song.
 *
 * @param songUid The [Music.UID] of the song.
 * @param moods The moods of the song, separated by [SEPARATOR]. Empty if all moods were removed.
 * @param fileWritten Whether these moods have been written into the audio file.
 * @param changedMs When the moods were last edited.
 */
@Entity
data class EditedMoods(
    @PrimaryKey val songUid: Music.UID,
    val moods: String,
    @ColumnInfo(defaultValue = "0") val fileWritten: Boolean,
    val changedMs: Long,
) {
    companion object {
        /** Separates moods in [moods]. Mood names cannot contain line breaks. */
        const val SEPARATOR = "\n"
    }
}
