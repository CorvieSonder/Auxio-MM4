/*
 * Copyright (c) 2026 Auxio Project
 * BackupRestorer.kt is part of Auxio.
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
 
package org.oxycblt.auxio.settings.backup

import android.content.Context
import java.io.File
import org.json.JSONObject

/**
 * Puts a backup staged by [BackupReader] in place. Must run at app start, before anything opens a
 * database or reads a preference, since open databases cannot be replaced safely.
 */
object BackupRestorer {
    const val READY_MARKER = "ready"
    private const val STAGING_DIR = "backup_restore"

    fun stagingDir(context: Context) = File(context.filesDir, STAGING_DIR)

    /** Apply a staged backup, if there is one. Does nothing otherwise. */
    fun applyPending(context: Context) {
        val staging = stagingDir(context)
        if (!File(staging, READY_MARKER).isFile) {
            // Left over from a restore that was cancelled or failed while staging.
            if (staging.exists()) staging.deleteRecursively()
            return
        }
        // Remove the marker first: a backup that crashes the restore is not tried again.
        File(staging, READY_MARKER).delete()
        try {
            applyDatabases(context, File(staging, BackupFormat.DB_DIR))
            applyPrefs(context, File(staging, BackupFormat.PREFS))
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun applyDatabases(context: Context, dir: File) {
        val files = dir.listFiles().orEmpty()
        for (file in files) {
            if (file.name.endsWith(BackupFormat.WAL_SUFFIX)) continue
            val target = context.getDatabasePath(file.name)
            target.parentFile?.mkdirs()
            // Old log and lock files would be replayed into the restored database.
            for (suffix in listOf(BackupFormat.WAL_SUFFIX, "-shm", "-journal")) {
                File(target.path + suffix).delete()
            }
            file.copyTo(target, overwrite = true)
            val wal = File(file.path + BackupFormat.WAL_SUFFIX)
            if (wal.isFile) {
                wal.copyTo(File(target.path + BackupFormat.WAL_SUFFIX), overwrite = true)
            }
        }
    }

    private fun applyPrefs(context: Context, file: File) {
        if (!file.isFile) return
        val json = JSONObject(file.readText())
        for (portable in json.keys()) {
            val name = BackupFormat.localPrefsName(context, portable)
            if (!BackupFormat.isSafeName(name)) continue
            BackupFormat.applyPrefsJson(
                context.getSharedPreferences(name, Context.MODE_PRIVATE),
                json.getJSONObject(portable),
            )
        }
    }
}
