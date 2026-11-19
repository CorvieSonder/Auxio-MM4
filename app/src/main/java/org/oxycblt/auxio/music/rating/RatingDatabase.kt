/*
 * Copyright (c) 2026 Auxio Project
 * RatingDatabase.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.rating

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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.oxycblt.musikr.Music

/**
 * Stores the ratings given to songs in the app. These take priority over the ratings in the audio
 * files, and are written into them when that is turned on.
 */
@Database(entities = [RatedSong::class], version = 2, exportSchema = false)
@TypeConverters(Music.UID.TypeConverters::class)
abstract class RatingDatabase : RoomDatabase() {
    /**
     * Get the current [RatingDao].
     *
     * @return A [RatingDao] providing control of the database's rating table.
     */
    abstract fun ratingDao(): RatingDao
}

@Dao
interface RatingDao {
    /**
     * Get every persisted [RatedSong].
     *
     * @return A list of every persisted [RatedSong].
     */
    @Query("SELECT * FROM RatedSong") suspend fun getRatings(): List<RatedSong>

    /**
     * Insert or replace the [RatedSong] of a song.
     *
     * @param item The [RatedSong] to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRating(item: RatedSong)

    /**
     * Delete the [RatedSong] of a song.
     *
     * @param songUid The [Music.UID] of the song to delete the rating of.
     */
    @Query("DELETE FROM RatedSong WHERE songUid = :songUid")
    suspend fun deleteRating(songUid: Music.UID)

    /** Get every [RatedSong] that has not been written into its file yet. */
    @Query("SELECT * FROM RatedSong WHERE fileWritten = 0")
    suspend fun getUnwritten(): List<RatedSong>

    /** Mark a rating as written, unless it changed after [changedMs]. */
    @Query(
        "UPDATE RatedSong SET fileWritten = 1 WHERE songUid = :songUid AND changedMs = :changedMs"
    )
    suspend fun markWritten(songUid: Music.UID, changedMs: Long)
}

/**
 * A rating given to a song in the app.
 *
 * @param songUid The [Music.UID] of the song.
 * @param halfStars The amount of half stars, or 0 if the song was explicitly left unrated.
 * @param changedMs When the rating was given, in milliseconds since the unix epoch.
 * @param fileWritten Whether this rating has been written into the audio file.
 */
@Entity
data class RatedSong(
    @PrimaryKey val songUid: Music.UID,
    val halfStars: Int,
    val changedMs: Long,
    @ColumnInfo(defaultValue = "1") val fileWritten: Boolean = true,
) {
    companion object {
        /** Ratings given before file writing existed count as written, so no bulk write happens. */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE RatedSong ADD COLUMN fileWritten INTEGER NOT NULL DEFAULT 1"
                    )
                }
            }
    }
}
