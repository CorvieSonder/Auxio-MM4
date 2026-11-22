/*
 * Copyright (c) 2026 Auxio Project
 * BackupFormat.kt is part of Auxio.
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
import android.content.SharedPreferences
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * The layout of a backup file, shared by [BackupWriter], [BackupReader] and [BackupRestorer].
 *
 * A backup is a zip holding:
 * - [MANIFEST]: format version, app id and the schema version of every database.
 * - [PREFS]: every app preference file, with value types kept.
 * - [DB_DIR]: copies of the app's databases, except the ones in [EXCLUDED_DATABASES].
 *
 * Databases are picked up by file name, so a database added later is backed up without changes
 * here.
 */
object BackupFormat {
    const val FORMAT_VERSION = 1
    const val MANIFEST = "manifest.json"
    const val PREFS = "prefs.json"
    const val DB_DIR = "databases/"
    const val WAL_SUFFIX = "-wal"
    const val MIME_TYPE = "application/zip"

    /**
     * Databases that are not backed up: the music cache is rebuilt by a scan, and the playback
     * queue and resume positions belong to the device they were made on.
     */
    private val EXCLUDED_DATABASES = setOf("music_cache.db", "playback_persistence.db", "resume.db")

    /** Values holding folder permissions. These only work in the app that was granted them. */
    private const val URI_MARKER = "content://"

    /** Whether [name] is a database file this backup should include. */
    fun isBackedUpDatabase(name: String) =
        name.endsWith(".db") &&
            !name.startsWith("androidx.") &&
            name !in EXCLUDED_DATABASES &&
            isSafeName(name)

    /** Whether [name] is a plain file name with no path parts. */
    fun isSafeName(name: String) =
        name.isNotEmpty() && !name.contains('/') && !name.contains('\\') && name != ".."

    /** The app's preference files, by the name passed to getSharedPreferences. */
    fun preferenceFiles(context: Context): List<String> {
        val dir = File(context.applicationInfo.dataDir, "shared_prefs")
        return dir.listFiles()
            .orEmpty()
            .map { it.name }
            .filter { it.endsWith(".xml") && !it.startsWith("androidx.") }
            .map { it.removeSuffix(".xml") }
            .sorted()
    }

    /**
     * Preference files can be named after the app id (the default one is `<app id>_preferences`).
     * Backups store that part as a placeholder, so they restore into an install with another app
     * id, such as a debug build's backup into a release build.
     */
    private const val APP_ID_PLACEHOLDER = "{app}"

    fun portablePrefsName(context: Context, name: String) =
        if (name.startsWith(context.packageName)) {
            APP_ID_PLACEHOLDER + name.removePrefix(context.packageName)
        } else {
            name
        }

    fun localPrefsName(context: Context, portable: String) =
        if (portable.startsWith(APP_ID_PLACEHOLDER)) {
            context.packageName + portable.removePrefix(APP_ID_PLACEHOLDER)
        } else {
            portable
        }

    /** Whether a preference value is tied to this app's folder permissions. */
    fun isFolderValue(value: Any?) =
        when (value) {
            is String -> value.contains(URI_MARKER)
            is Set<*> -> value.any { it is String && it.contains(URI_MARKER) }
            else -> false
        }

    /** Every value of [prefs] as JSON, with its type, leaving out folder values. */
    fun prefsToJson(prefs: SharedPreferences): JSONObject {
        val json = JSONObject()
        for ((key, value) in prefs.all) {
            if (isFolderValue(value)) continue
            val entry =
                when (value) {
                    is Boolean -> typed("boolean", value)
                    is Int -> typed("int", value)
                    is Long -> typed("long", value)
                    is Float -> typed("float", value.toDouble())
                    is String -> typed("string", value)
                    is Set<*> -> typed("set", JSONArray(value.filterIsInstance<String>()))
                    else -> continue
                }
            json.put(key, entry)
        }
        return json
    }

    /**
     * Replace the contents of [prefs] with [json]. Folder values already in [prefs] are kept, since
     * the backup cannot carry their permissions.
     */
    fun applyPrefsJson(prefs: SharedPreferences, json: JSONObject) {
        val editor = prefs.edit()
        for ((key, value) in prefs.all) {
            if (!isFolderValue(value)) editor.remove(key)
        }
        for (key in json.keys()) {
            val entry = json.optJSONObject(key) ?: continue
            when (entry.optString("type")) {
                "boolean" -> editor.putBoolean(key, entry.getBoolean("value"))
                "int" -> editor.putInt(key, entry.getInt("value"))
                "long" -> editor.putLong(key, entry.getLong("value"))
                "float" -> editor.putFloat(key, entry.getDouble("value").toFloat())
                "string" -> editor.putString(key, entry.getString("value"))
                "set" -> {
                    val array = entry.getJSONArray("value")
                    editor.putStringSet(key, (0 until array.length()).map(array::getString).toSet())
                }
            }
        }
        editor.commit()
    }

    private fun typed(type: String, value: Any) = JSONObject().put("type", type).put("value", value)
}
