/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistSortModule.kt is part of Auxio.
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

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface PlaylistSortModule {
    @Binds
    fun repository(playlistSortRepository: PlaylistSortRepositoryImpl): PlaylistSortRepository
}

@Module
@InstallIn(SingletonComponent::class)
class PlaylistSortRoomModule {
    @Singleton
    @Provides
    fun database(@ApplicationContext context: Context) =
        Room.databaseBuilder(
                context.applicationContext,
                PlaylistSortDatabase::class.java,
                "playlist_sort.db",
            )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun playlistSortDao(database: PlaylistSortDatabase) = database.playlistSortDao()
}
