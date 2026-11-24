/*
 * Copyright (c) 2023 Auxio Project
 * CacheDatabase.kt is part of Auxio.
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
 
package org.oxycblt.musikr.cache.db

import android.content.Context
import android.net.Uri
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.oxycblt.musikr.tag.Date
import org.oxycblt.musikr.util.correctWhitespace
import org.oxycblt.musikr.util.splitEscaped

@Database(entities = [CachedFileData::class], version = 72, exportSchema = false)
internal abstract class CacheDatabase : RoomDatabase() {
    abstract fun readDao(): CacheReadDao

    abstract fun writeDao(): CacheWriteDao

    companion object {
        fun from(context: Context) =
            Room.databaseBuilder(
                    context.applicationContext,
                    CacheDatabase::class.java,
                    "music_cache.db",
                )
                .addMigrations(MIGRATION_70_71, MIGRATION_71_72)
                .fallbackToDestructiveMigration(true)
                .build()

        /**
         * Adds the extras column. Every entry is marked as stale rather than dropped so that the
         * new field is extracted while the date added values are retained.
         */
        private val MIGRATION_70_71 =
            object : Migration(70, 71) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE CachedFileData ADD COLUMN extras TEXT")
                    db.execSQL("UPDATE CachedFileData SET modifiedMs = -1")
                }
            }

        /**
         * Adds the file columns needed to rebuild a library from the cache alone. Every entry is
         * marked as stale so that they are filled in on the next scan while the date added values
         * are retained.
         */
        private val MIGRATION_71_72 =
            object : Migration(71, 72) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE CachedFileData ADD COLUMN fileMimeType TEXT NOT NULL DEFAULT ''"
                    )
                    db.execSQL(
                        "ALTER TABLE CachedFileData ADD COLUMN size INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL(
                        "ALTER TABLE CachedFileData ADD COLUMN pathVolume TEXT NOT NULL DEFAULT ''"
                    )
                    db.execSQL(
                        "ALTER TABLE CachedFileData ADD COLUMN pathComponents TEXT NOT NULL DEFAULT ''"
                    )
                    db.execSQL("UPDATE CachedFileData SET modifiedMs = -1")
                }
            }
    }
}

@Dao
internal interface CacheReadDao {
    @Query("SELECT * FROM CachedFileData") suspend fun selectAllSongs(): List<CachedFileData>
}

@Dao
internal interface CacheWriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun updateSong(data: CachedFileData)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSongs(data: List<CachedFileData>)

    @Transaction
    suspend fun deleteExcludingUris(uris: Set<String>) {
        val delete = selectAllUris().toSet() - uris
        for (chunk in delete.chunked(999)) {
            deleteExcludingUriChunk(chunk)
        }
    }

    @Query("SELECT uri FROM CachedFileData") suspend fun selectAllUris(): List<String>

    @Query("DELETE FROM CachedFileData WHERE uri IN (:uris)")
    suspend fun deleteExcludingUriChunk(uris: List<String>)
}

@Entity
@TypeConverters(CachedFileData.Converters::class)
internal data class CachedFileData(
    @PrimaryKey val uri: Uri,
    val modifiedMs: Long,
    val addedMs: Long,
    val fileMimeType: String,
    val size: Long,
    val pathVolume: String,
    val pathComponents: String,
    val mimeType: String?,
    val durationMs: Long?,
    val bitrateKbps: Int?,
    val sampleRateHz: Int?,
    val musicBrainzId: String?,
    val name: String?,
    val sortName: String?,
    val track: Int?,
    val disc: Int?,
    val subtitle: String?,
    val date: Date?,
    val albumMusicBrainzId: String?,
    val albumName: String?,
    val albumSortName: String?,
    val releaseTypes: List<String>?,
    val artistMusicBrainzIds: List<String>?,
    val artistNames: List<String>?,
    val artistSortNames: List<String>?,
    val albumArtistMusicBrainzIds: List<String>?,
    val albumArtistNames: List<String>?,
    val albumArtistSortNames: List<String>?,
    val genreNames: List<String>?,
    val replayGainTrackAdjustment: Float?,
    val replayGainAlbumAdjustment: Float?,
    val coverId: String?,
    val extras: Map<String, List<String>>?,
) {
    object Converters {
        @TypeConverter
        fun fromMultiValue(values: List<String>) =
            values.joinToString(";") { it.replace(";", "\\;") }

        @TypeConverter
        fun toMultiValue(string: String) = string.splitEscaped { it == ';' }.correctWhitespace()

        @TypeConverter
        fun fromExtras(extras: Map<String, List<String>>?) =
            extras?.entries?.joinToString(EXTRA_ENTRY_SEPARATOR) { (key, values) ->
                (listOf(key) + values).joinToString(EXTRA_VALUE_SEPARATOR) {
                    it.replace(EXTRA_ENTRY_SEPARATOR, "").replace(EXTRA_VALUE_SEPARATOR, "")
                }
            }

        @TypeConverter
        fun toExtras(string: String?) = string?.let {
            if (it.isEmpty()) {
                return@let mapOf()
            }
            it.split(EXTRA_ENTRY_SEPARATOR).associate { entry ->
                val fields = entry.split(EXTRA_VALUE_SEPARATOR)
                fields.first() to fields.drop(1)
            }
        }

        @TypeConverter fun fromDate(date: Date?) = date?.toString()

        @TypeConverter fun toDate(string: String?) = string?.let(Date::from)

        @TypeConverter fun toUri(string: String) = Uri.parse(string)

        @TypeConverter fun fromUri(uri: Uri) = uri.toString()

        // ASCII record and unit separators, which should never appear in tag text.
        private const val EXTRA_ENTRY_SEPARATOR = "\u001E"
        private const val EXTRA_VALUE_SEPARATOR = "\u001F"
    }
}
