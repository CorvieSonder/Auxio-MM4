/*
 * Copyright (c) 2026 Auxio Project
 * PlayHistoryDatabase.kt is part of Auxio.
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

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.oxycblt.musikr.Music

/** Stores when songs were played, for the recently played and most played playlists. */
@Database(entities = [PlayedSong::class], version = 1, exportSchema = false)
@TypeConverters(Music.UID.TypeConverters::class)
abstract class PlayHistoryDatabase : RoomDatabase() {
    /**
     * Get the current [PlayHistoryDao].
     *
     * @return A [PlayHistoryDao] providing control of the database's play history table.
     */
    abstract fun playHistoryDao(): PlayHistoryDao
}

@Dao
interface PlayHistoryDao {
    /**
     * Get the play count and last play of every played song.
     *
     * @return A list of [PlaySummary], one for each played song.
     */
    @Query(
        "SELECT songUid, COUNT(*) AS playCount, MAX(playedMs) AS lastPlayedMs " +
            "FROM PlayedSong GROUP BY songUid"
    )
    suspend fun getSummaries(): List<PlaySummary>

    /**
     * Insert a new [PlayedSong].
     *
     * @param play The [PlayedSong] to insert.
     */
    @Insert suspend fun insertPlay(play: PlayedSong)
}

/**
 * A single play of a song.
 *
 * @param id The unique ID of the play.
 * @param songUid The [Music.UID] of the song that was played.
 * @param playedMs When the song was played, in milliseconds since the unix epoch.
 */
@Entity
data class PlayedSong(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songUid: Music.UID,
    val playedMs: Long,
)

/**
 * How often and when a song was last played.
 *
 * @param songUid The [Music.UID] of the song.
 * @param playCount How many times the song was played.
 * @param lastPlayedMs When the song was last played, in milliseconds since the unix epoch.
 */
data class PlaySummary(val songUid: Music.UID, val playCount: Int, val lastPlayedMs: Long)
