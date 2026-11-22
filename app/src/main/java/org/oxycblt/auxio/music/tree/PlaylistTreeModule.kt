/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistTreeModule.kt is part of Auxio.
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
interface PlaylistTreeModule {
    @Binds
    fun repository(playlistTreeRepository: PlaylistTreeRepositoryImpl): PlaylistTreeRepository
}

@Module
@InstallIn(SingletonComponent::class)
class PlaylistTreeRoomModule {
    @Singleton
    @Provides
    fun database(@ApplicationContext context: Context) =
        Room.databaseBuilder(
                context.applicationContext,
                PlaylistTreeDatabase::class.java,
                "playlist_tree.db",
            )
            .addMigrations(
                PlaylistTreeDatabase.MIGRATION_1_2,
                PlaylistTreeDatabase.MIGRATION_2_3,
            )
            .build()

    @Provides fun playlistTreeDao(database: PlaylistTreeDatabase) = database.playlistTreeDao()
}
