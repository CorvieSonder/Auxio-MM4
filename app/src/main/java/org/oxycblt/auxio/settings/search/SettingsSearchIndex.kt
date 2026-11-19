/*
 * Copyright (c) 2026 Auxio Project
 * SettingsSearchIndex.kt is part of Auxio.
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
 
package org.oxycblt.auxio.settings.search

import android.content.Context
import androidx.annotation.IdRes
import androidx.annotation.XmlRes
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.children
import org.oxycblt.auxio.R
import org.oxycblt.auxio.music.MusicType
import org.oxycblt.auxio.settings.ui.IntListPreference

/**
 * A single searchable setting.
 *
 * @param key The preference key, used to highlight the setting once its screen is opened.
 * @param title The title of the setting.
 * @param summary The summary of the setting, if any.
 * @param path The screen and category the setting is found in, for display.
 * @param destination The navigation destination of the screen holding the setting.
 * @param keywords Extra text to match, such as list options or the items inside a dialog.
 */
data class SettingsSearchEntry(
    val key: String,
    val title: String,
    val summary: String?,
    val path: String,
    @IdRes val destination: Int,
    val keywords: List<String> = listOf(),
)

/**
 * Builds a searchable index of every setting by inflating each preference screen.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
object SettingsSearchIndex {
    private data class Screen(@XmlRes val xml: Int, @IdRes val destination: Int)

    private val SCREENS =
        listOf(
            Screen(R.xml.preferences_root, R.id.root_preferences_fragment),
            Screen(R.xml.preferences_ui, R.id.ui_preferences_fragment),
            Screen(R.xml.preferences_personalize, R.id.personalize_preferences_fragment),
            Screen(R.xml.preferences_music, R.id.music_preferences_fragment),
            Screen(R.xml.preferences_audio, R.id.audio_preferences_fragment),
        )

    /**
     * Build the index.
     *
     * @param context [Context] required to inflate the preference screens.
     * @return Every visible setting with a key and a title.
     */
    fun build(context: Context): List<SettingsSearchEntry> {
        val manager = PreferenceManager(context)
        val searchKey = context.getString(R.string.set_key_search)
        val entries = mutableListOf<SettingsSearchEntry>()
        for (screen in SCREENS) {
            val root = manager.inflateFromResource(context, screen.xml, null)
            val path = root.title?.toString() ?: ""
            collect(root, path, screen.destination, searchKey, entries)
        }
        return entries
    }

    /**
     * Search the given index.
     *
     * @param entries The index from [build].
     * @param query The query to match against titles and summaries, ignoring case.
     * @return Matching entries: title matches first, then summary, location and keyword matches.
     */
    fun search(entries: List<SettingsSearchEntry>, query: String): List<SettingsSearchEntry> {
        val needle = query.trim()
        if (needle.isEmpty()) {
            return emptyList()
        }
        val titleStarts = mutableListOf<SettingsSearchEntry>()
        val titleContains = mutableListOf<SettingsSearchEntry>()
        val summaryContains = mutableListOf<SettingsSearchEntry>()
        val otherContains = mutableListOf<SettingsSearchEntry>()
        for (entry in entries) {
            when {
                entry.title.startsWith(needle, ignoreCase = true) -> titleStarts.add(entry)
                entry.title.contains(needle, ignoreCase = true) -> titleContains.add(entry)
                entry.summary?.contains(needle, ignoreCase = true) == true ->
                    summaryContains.add(entry)
                entry.path.contains(needle, ignoreCase = true) ||
                    entry.keywords.any { it.contains(needle, ignoreCase = true) } ->
                    otherContains.add(entry)
            }
        }
        return titleStarts + titleContains + summaryContains + otherContains
    }

    private fun collect(
        group: PreferenceGroup,
        path: String,
        @IdRes destination: Int,
        searchKey: String,
        entries: MutableList<SettingsSearchEntry>,
    ) {
        for (preference in group.children) {
            if (!preference.isVisible) {
                continue
            }
            if (preference is PreferenceCategory) {
                val title = preference.title?.toString()
                val childPath = if (title.isNullOrEmpty()) path else "$path › $title"
                collect(preference, childPath, destination, searchKey, entries)
                continue
            }
            val key = preference.key ?: continue
            val title = preference.title?.toString() ?: continue
            if (key == searchKey) {
                continue
            }
            entries.add(
                SettingsSearchEntry(
                    key,
                    title,
                    summaryOf(preference),
                    path,
                    destination,
                    keywordsOf(preference),
                )
            )
        }
    }

    private fun keywordsOf(preference: Preference): List<String> {
        val context = preference.context
        return when {
            preference.key == context.getString(R.string.set_key_home_tabs) ->
                MusicType.entries.map { context.getString(it.nameRes) }
            preference is IntListPreference -> preference.entries.map { it.toString() }
            preference is ListPreference -> preference.entries.orEmpty().map { it.toString() }
            preference is MultiSelectListPreference ->
                preference.entries.orEmpty().map { it.toString() }
            else -> listOf()
        }
    }

    private fun summaryOf(preference: Preference) =
        // Summary providers may depend on state that is unavailable here.
        try {
            preference.summary?.toString()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
}
