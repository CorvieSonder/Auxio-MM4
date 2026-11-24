/*
 * Copyright (c) 2026 Auxio Project
 * MediaStoreSnapshot.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.edit
import androidx.preference.PreferenceManager

/**
 * Remembers the state of the system media database at the end of the last full music load.
 *
 * When loading from the system database, a startup can compare the current state with this one: if
 * they match, no media was added, changed or removed since the last load, so the library built from
 * the cache is already complete and the full load can be skipped.
 */
object MediaStoreSnapshot {
    private const val KEY = "auxio_media_store_snapshot"

    /**
     * Take a snapshot of the system media database.
     *
     * @param context [Context] required to query the system media database.
     * @param config Anything else that decides which songs are loaded (for example the folder
     *   filter and the library revision). A change to it makes the snapshot differ.
     * @return The snapshot, or null if the system cannot provide one (before Android 11, or if the
     *   query fails).
     */
    fun take(context: Context, config: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null
        }
        return try {
            val volumes =
                MediaStore.getExternalVolumeNames(context).sorted().joinToString(";") { volume ->
                    // The version changes when the database is rebuilt, and the generation
                    // increases with every change to an item on the volume.
                    "$volume:${MediaStore.getVersion(context, volume)}:" +
                        "${MediaStore.getGeneration(context, volume)}"
                }
            "$config|$volumes"
        } catch (e: Exception) {
            null
        }
    }

    /** Whether [snapshot] matches the one saved at the end of the last full load. */
    fun isUnchanged(context: Context, snapshot: String?) =
        snapshot != null && prefs(context).getString(KEY, null) == snapshot

    /** Save [snapshot] after a full load, or forget the saved one if it is null. */
    fun save(context: Context, snapshot: String?) {
        prefs(context).edit {
            if (snapshot != null) {
                putString(KEY, snapshot)
            } else {
                remove(KEY)
            }
        }
    }

    private fun prefs(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
}
