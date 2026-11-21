/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistSyncDatabase.kt is part of Auxio.
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

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.oxycblt.musikr.Music

/**
 * Stores the link between each playlist and its copy in MediaMonkey, and the state of the playlist
 * at the last folder export or import (the base that later changes are compared with).
 */
@Database(entities = [PlaylistSyncLink::class], version = 1, exportSchema = false)
@TypeConverters(Music.UID.TypeConverters::class)
abstract class PlaylistSyncDatabase : RoomDatabase() {
    abstract fun playlistSyncDao(): PlaylistSyncDao
}

@Dao
interface PlaylistSyncDao {
    @Query("SELECT * FROM PlaylistSyncLink") suspend fun getLinks(): List<PlaylistSyncLink>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(links: List<PlaylistSyncLink>)

    @Query("DELETE FROM PlaylistSyncLink WHERE playlistUid IN (:playlistUids)")
    suspend fun deleteLinks(playlistUids: List<Music.UID>)
}

/**
 * A playlist's link to MediaMonkey and its state at the last sync.
 *
 * @param playlistUid The [Music.UID] of the playlist.
 * @param mmId The ID of the playlist in MediaMonkey, or null if not known yet.
 * @param name The playlist's name at the last sync.
 * @param parentUid The [Music.UID] of the playlist's parent at the last sync, null at the top.
 * @param songs The absolute paths of the playlist's songs at the last sync, one per line.
 */
@Entity
data class PlaylistSyncLink(
    @PrimaryKey val playlistUid: Music.UID,
    val mmId: Long?,
    val name: String,
    val parentUid: Music.UID?,
    val songs: String,
)

@Module
@InstallIn(SingletonComponent::class)
class PlaylistSyncRoomModule {
    @Singleton
    @Provides
    fun database(@ApplicationContext context: Context) =
        Room.databaseBuilder(
                context.applicationContext,
                PlaylistSyncDatabase::class.java,
                "playlist_sync.db",
            )
            .build()

    @Provides fun playlistSyncDao(database: PlaylistSyncDatabase) = database.playlistSyncDao()
}
