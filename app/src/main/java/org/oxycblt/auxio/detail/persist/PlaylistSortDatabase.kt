/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistSortDatabase.kt is part of Auxio.
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
 
package org.oxycblt.auxio.detail.persist

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.oxycblt.auxio.list.sort.Sort
import org.oxycblt.musikr.Music

/**
 * Stores the [Sort] chosen for each playlist's song list. This is a view setting only and never
 * changes the saved order of a playlist.
 */
@Database(entities = [PlaylistSortItem::class], version = 1, exportSchema = false)
@TypeConverters(Music.UID.TypeConverters::class)
abstract class PlaylistSortDatabase : RoomDatabase() {
    /**
     * Get the current [PlaylistSortDao].
     *
     * @return A [PlaylistSortDao] providing control of the database's playlist sort table.
     */
    abstract fun playlistSortDao(): PlaylistSortDao
}

@Dao
interface PlaylistSortDao {
    /**
     * Get every persisted [PlaylistSortItem].
     *
     * @return A list of every persisted [PlaylistSortItem].
     */
    @Query("SELECT * FROM PlaylistSortItem") suspend fun getSorts(): List<PlaylistSortItem>

    /**
     * Insert or replace the [PlaylistSortItem] of a playlist.
     *
     * @param item The [PlaylistSortItem] to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSort(item: PlaylistSortItem)

    /**
     * Delete the [PlaylistSortItem]s of the given playlists.
     *
     * @param playlistUids The [Music.UID]s of the playlists to delete the sorts of.
     */
    @Query("DELETE FROM PlaylistSortItem WHERE playlistUid IN (:playlistUids)")
    suspend fun deleteSorts(playlistUids: List<Music.UID>)
}

/**
 * A persisted playlist [Sort].
 *
 * @param playlistUid The [Music.UID] of the playlist.
 * @param sortIntCode The [Sort.intCode] of the playlist's [Sort].
 */
@Entity data class PlaylistSortItem(@PrimaryKey val playlistUid: Music.UID, val sortIntCode: Int)
