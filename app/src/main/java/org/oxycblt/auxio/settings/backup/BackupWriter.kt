/*
 * Copyright (c) 2026 Auxio Project
 * BackupWriter.kt is part of Auxio.
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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import org.oxycblt.auxio.BuildConfig
import timber.log.Timber as L

/** Writes a backup of the app's settings and databases. See [BackupFormat]. */
object BackupWriter {
    /**
     * Write a backup zip to [uri]. Runs blocking file work; call from a background thread.
     *
     * @return The number of databases backed up.
     */
    fun write(context: Context, uri: Uri): Int {
        val databases =
            context.databaseList().filter(BackupFormat::isBackedUpDatabase).sorted().mapNotNull {
                name ->
                val file = context.getDatabasePath(name)
                if (file.isFile) name to checkpoint(file) else null
            }

        val prefs = JSONObject()
        for (name in BackupFormat.preferenceFiles(context)) {
            prefs.put(
                BackupFormat.portablePrefsName(context, name),
                BackupFormat.prefsToJson(context.getSharedPreferences(name, Context.MODE_PRIVATE)),
            )
        }

        val manifest =
            JSONObject()
                .put("format", BackupFormat.FORMAT_VERSION)
                .put("app", BuildConfig.APPLICATION_ID)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("created", System.currentTimeMillis())
                .put(
                    "databases",
                    JSONObject().apply {
                        databases.forEach { (name, version) -> put(name, version) }
                    },
                )

        val stream =
            requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) {
                "Could not open $uri"
            }
        ZipOutputStream(stream.buffered()).use { zip ->
            zip.putText(BackupFormat.MANIFEST, manifest.toString(2))
            zip.putText(BackupFormat.PREFS, prefs.toString(2))
            for ((name, _) in databases) {
                val file = context.getDatabasePath(name)
                zip.putFile(BackupFormat.DB_DIR + name, file)
                val wal = File(file.path + BackupFormat.WAL_SUFFIX)
                if (wal.isFile && wal.length() > 0) {
                    zip.putFile(BackupFormat.DB_DIR + name + BackupFormat.WAL_SUFFIX, wal)
                }
            }
        }
        L.d("Backed up ${databases.size} databases and ${prefs.length()} preference files")
        return databases.size
    }

    /**
     * Move what is waiting in the write-ahead log into the database file, so the copy is complete
     * on its own. If the app holds the database busy, the log is copied next to it instead.
     *
     * @return The database's schema version.
     */
    private fun checkpoint(file: File): Int =
        // Open in WAL mode like Room does, so this connection does not try to change the
        // journal mode of a database the app has open.
        SQLiteDatabase.openDatabase(
                file.path,
                null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING,
            )
            .use { db ->
                db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
                db.version
            }

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray())
        closeEntry()
    }

    private fun ZipOutputStream.putFile(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }
}
