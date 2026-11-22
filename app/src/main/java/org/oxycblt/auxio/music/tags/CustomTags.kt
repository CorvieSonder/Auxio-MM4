/*
 * Copyright (c) 2026 Auxio Project
 * CustomTags.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.tags

import android.content.Context
import androidx.preference.PreferenceManager
import org.oxycblt.auxio.R
import org.oxycblt.auxio.music.MusicType
import org.oxycblt.musikr.CustomTagSpec

/**
 * A user-configured custom tag, read from the fields MediaMonkey stores in described comments.
 *
 * @param slot Which field this is, from [SLOTS]. Slots 1-5 are MediaMonkey's Custom 1-5, slot 6 is
 *   its Occasion field.
 * @param name The display name chosen by the user, or blank for the default name.
 * @param enabled Whether values of this tag are read at all.
 * @param multiValue Whether values are split with the separator setting.
 * @param showTab Whether this tag gets its own library tab.
 */
data class CustomTag(
    val slot: Int,
    val name: String,
    val enabled: Boolean,
    val multiValue: Boolean,
    val showTab: Boolean,
) {
    /** The [MusicType] of this tag's library tab. */
    val musicType: MusicType
        get() = MusicType.customOf(slot)

    /** The name to show for this tag. */
    fun resolveName(context: Context) = name.trim().ifEmpty { defaultName(context, slot) }

    /** The tag reader configuration for this tag. */
    fun toSpec() = CustomTagSpec(slot, keysOf(slot), multiValue)

    companion object {
        /** Every slot, in display order. */
        val SLOTS = 1..6

        private const val OCCASION_SLOT = 6

        /** The tags in the user's settings, one per slot. */
        fun load(context: Context): List<CustomTag> {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return parse(prefs.getString(context.getString(R.string.set_key_custom_tags), null))
        }

        /** The name of the tag in [slot], as set by the user. */
        fun nameOf(context: Context, slot: Int) =
            load(context).firstOrNull { it.slot == slot }?.resolveName(context)
                ?: defaultName(context, slot)

        fun defaultName(context: Context, slot: Int) =
            if (slot == OCCASION_SLOT) context.getString(R.string.lbl_occasion)
            else context.getString(R.string.fmt_custom_tag, slot)

        /**
         * Read the tags from their settings form: one line per slot, in the form
         * `slot|enabled|multiValue|showTab|name`. Missing slots get the default (disabled) tag.
         */
        fun parse(string: String?): List<CustomTag> {
            val stored =
                string
                    .orEmpty()
                    .lines()
                    .mapNotNull { line ->
                        val parts = line.split("|", limit = 5)
                        if (parts.size < 5) return@mapNotNull null
                        val slot = parts[0].toIntOrNull()?.takeIf { it in SLOTS }
                        slot?.let {
                            CustomTag(
                                it,
                                parts[4],
                                parts[1] == "1",
                                parts[2] == "1",
                                parts[3] == "1",
                            )
                        }
                    }
                    .associateBy { it.slot }
            return SLOTS.map { slot -> stored[slot] ?: CustomTag(slot, "", false, false, false) }
        }

        fun stringify(tags: List<CustomTag>) =
            tags.joinToString("\n") { tag ->
                listOf(tag.slot, tag.enabled.bit, tag.multiValue.bit, tag.showTab.bit, tag.name)
                    .joinToString("|")
            }

        private val Boolean.bit
            get() = if (this) "1" else "0"

        /** MediaMonkey writes its fields as described comments, and some taggers as TXXX. */
        private fun keysOf(slot: Int): List<String> {
            val description =
                if (slot == OCCASION_SLOT) "SONGS-DB_OCCASION" else "SONGS-DB_CUSTOM$slot"
            return listOf("COMM:$description", "TXXX:$description")
        }
    }
}
