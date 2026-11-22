/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistTreeDatabase.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.tree

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
 * Stores which playlist each nested playlist belongs to. A playlist without a [PlaylistNode] is a
 * top-level playlist.
 */
@Database(
    entities = [PlaylistNode::class, PinnedPlaylist::class, LibraryPlaylist::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Music.UID.TypeConverters::class)
abstract class PlaylistTreeDatabase : RoomDatabase() {
    /**
     * Get the current [PlaylistTreeDao].
     *
     * @return A [PlaylistTreeDao] providing control of the database's playlist tree table.
     */
    abstract fun playlistTreeDao(): PlaylistTreeDao

    companion object {
        /** Adds the table of pinned playlists. */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `PinnedPlaylist` " +
                            "(`playlistUid` TEXT NOT NULL, PRIMARY KEY(`playlistUid`))"
                    )
                }
            }

        /** Adds the table of library playlists. */
        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `LibraryPlaylist` " +
                            "(`playlistUid` TEXT NOT NULL, PRIMARY KEY(`playlistUid`))"
                    )
                }
            }
    }
}

@Dao
interface PlaylistTreeDao {
    /**
     * Get every persisted [PlaylistNode].
     *
     * @return A list of every persisted [PlaylistNode].
     */
    @Query("SELECT * FROM PlaylistNode") suspend fun getNodes(): List<PlaylistNode>

    /**
     * Insert or replace [PlaylistNode]s.
     *
     * @param nodes The [PlaylistNode]s to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<PlaylistNode>)

    /**
     * Delete the [PlaylistNode]s of the given playlists, making them top-level playlists.
     *
     * @param playlistUids The [Music.UID]s of the playlists to delete the nodes of.
     */
    @Query("DELETE FROM PlaylistNode WHERE playlistUid IN (:playlistUids)")
    suspend fun deleteNodes(playlistUids: List<Music.UID>)

    /**
     * Get every persisted [PinnedPlaylist].
     *
     * @return A list of every persisted [PinnedPlaylist].
     */
    @Query("SELECT * FROM PinnedPlaylist") suspend fun getPins(): List<PinnedPlaylist>

    /**
     * Insert or replace [PinnedPlaylist]s.
     *
     * @param pins The [PinnedPlaylist]s to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPins(pins: List<PinnedPlaylist>)

    /**
     * Unpin the given playlists.
     *
     * @param playlistUids The [Music.UID]s of the playlists to unpin.
     */
    @Query("DELETE FROM PinnedPlaylist WHERE playlistUid IN (:playlistUids)")
    suspend fun deletePins(playlistUids: List<Music.UID>)

    /**
     * Get every persisted [LibraryPlaylist].
     *
     * @return A list of every persisted [LibraryPlaylist].
     */
    @Query("SELECT * FROM LibraryPlaylist") suspend fun getLibraryPlaylists(): List<LibraryPlaylist>

    /**
     * Insert or replace [LibraryPlaylist]s.
     *
     * @param libraryPlaylists The [LibraryPlaylist]s to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibraryPlaylists(libraryPlaylists: List<LibraryPlaylist>)

    /**
     * Turn the given playlists back into normal playlists.
     *
     * @param playlistUids The [Music.UID]s of the playlists to change.
     */
    @Query("DELETE FROM LibraryPlaylist WHERE playlistUid IN (:playlistUids)")
    suspend fun deleteLibraryPlaylists(playlistUids: List<Music.UID>)
}

/**
 * The position of a nested playlist in the playlist tree.
 *
 * @param playlistUid The [Music.UID] of the nested playlist.
 * @param parentUid The [Music.UID] of the playlist it belongs to.
 */
@Entity data class PlaylistNode(@PrimaryKey val playlistUid: Music.UID, val parentUid: Music.UID)

/**
 * A playlist that is pinned to the top of the level it is in.
 *
 * @param playlistUid The [Music.UID] of the pinned playlist.
 */
@Entity data class PinnedPlaylist(@PrimaryKey val playlistUid: Music.UID)

/**
 * A library playlist: it holds no songs of its own and shows every song of its child playlists,
 * their children, and so on.
 *
 * @param playlistUid The [Music.UID] of the library playlist.
 */
@Entity data class LibraryPlaylist(@PrimaryKey val playlistUid: Music.UID)
