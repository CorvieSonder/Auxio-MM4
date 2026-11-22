/*
 * Copyright (c) 2026 Auxio Project
 * BackupReader.kt is part of Auxio.
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
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import java.io.File
import java.util.zip.ZipInputStream
import org.json.JSONObject
import timber.log.Timber as L

/**
 * Checks a backup zip and stages its contents for [BackupRestorer], which puts them in place at the
 * next app start, before any database is opened.
 */
object BackupReader {
    sealed interface Result {
        /** The backup is staged; the app must restart to apply it. */
        data object Staged : Result

        /** The file is not a backup, or is damaged. */
        data object Invalid : Result

        /** The backup holds a database made by a newer version of the app. */
        data class TooNew(val database: String) : Result
    }

    /**
     * Read and stage the backup at [uri]. Runs blocking file work; call from a background thread.
     */
    fun stage(context: Context, uri: Uri): Result {
        val staging = BackupRestorer.stagingDir(context)
        staging.deleteRecursively()
        val databases = File(staging, BackupFormat.DB_DIR).apply { mkdirs() }

        var manifest: JSONObject? = null
        var hasPrefs = false
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return Result.Invalid
            ZipInputStream(stream.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    when {
                        entry.isDirectory -> {}
                        name == BackupFormat.MANIFEST ->
                            manifest = JSONObject(zip.readBytes().decodeToString())
                        name == BackupFormat.PREFS -> {
                            File(staging, BackupFormat.PREFS).outputStream().use { zip.copyTo(it) }
                            hasPrefs = true
                        }
                        name.startsWith(BackupFormat.DB_DIR) -> {
                            val file = name.removePrefix(BackupFormat.DB_DIR)
                            if (
                                BackupFormat.isBackedUpDatabase(
                                    file.removeSuffix(BackupFormat.WAL_SUFFIX)
                                )
                            ) {
                                File(databases, file).outputStream().use { zip.copyTo(it) }
                            } else {
                                L.w("Skipping unexpected backup entry $name")
                            }
                        }
                        else -> L.w("Skipping unexpected backup entry $name")
                    }
                }
            }
        } catch (e: Exception) {
            L.e("Could not read backup: $e")
            staging.deleteRecursively()
            return Result.Invalid
        }

        val result = check(context, manifest, hasPrefs, databases)
        if (result != Result.Staged) {
            staging.deleteRecursively()
            return result
        }
        // Written last, so a backup that was only partly staged is never applied.
        File(staging, BackupRestorer.READY_MARKER).createNewFile()
        return Result.Staged
    }

    private fun check(
        context: Context,
        manifest: JSONObject?,
        hasPrefs: Boolean,
        databases: File,
    ): Result {
        if (
            manifest == null ||
                !hasPrefs ||
                manifest.optInt("format") != BackupFormat.FORMAT_VERSION
        ) {
            return Result.Invalid
        }
        val versions = manifest.optJSONObject("databases") ?: return Result.Invalid
        for (file in databases.listFiles().orEmpty()) {
            val name = file.name
            if (name.endsWith(BackupFormat.WAL_SUFFIX)) continue
            if (!versions.has(name)) return Result.Invalid
            // A database from a newer app version would make Room refuse to open it.
            val live = context.getDatabasePath(name)
            if (live.isFile && versions.getInt(name) > liveVersion(live)) {
                return Result.TooNew(name)
            }
        }
        return Result.Staged
    }

    private fun liveVersion(file: File): Int =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use {
            it.version
        }
}
