/*
 * Copyright (c) 2026 Auxio Project
 * AutoPlaylistSettings.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.auto

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.oxycblt.auxio.R
import org.oxycblt.auxio.music.rating.Rating
import org.oxycblt.auxio.settings.Settings

/** User configuration for automatic playlists. */
interface AutoPlaylistSettings : Settings<AutoPlaylistSettings.Listener> {
    /** The kinds of automatic playlist to show. */
    val shownKinds: Set<AutoPlaylist.Kind>
    /** How many days a song counts as recently added for. */
    val recentlyAddedDays: Int
    /** The lowest [Rating] a song must have to be in the rated playlist. */
    val ratedMinimum: Rating

    interface Listener {
        /** Called when a setting that changes the automatic playlists has changed. */
        fun onAutoPlaylistSettingsChanged()
    }
}

class AutoPlaylistSettingsImpl @Inject constructor(@ApplicationContext context: Context) :
    Settings.Impl<AutoPlaylistSettings.Listener>(context), AutoPlaylistSettings {
    override val shownKinds: Set<AutoPlaylist.Kind>
        get() {
            val names =
                sharedPreferences.getStringSet(getString(R.string.set_key_auto_playlists), null)
                    ?: return AutoPlaylist.Kind.entries.toSet()
            return AutoPlaylist.Kind.entries.filterTo(mutableSetOf()) { it.name in names }
        }

    override val recentlyAddedDays: Int
        get() = sharedPreferences.getInt(getString(R.string.set_key_auto_recent_days), 30)

    override val ratedMinimum: Rating
        get() =
            Rating.fromHalfStars(
                sharedPreferences.getInt(getString(R.string.set_key_auto_rated_minimum), 8)
            ) ?: Rating(8)

    override fun onSettingChanged(key: String, listener: AutoPlaylistSettings.Listener) {
        when (key) {
            getString(R.string.set_key_auto_playlists),
            getString(R.string.set_key_auto_recent_days),
            getString(R.string.set_key_auto_rated_minimum) ->
                listener.onAutoPlaylistSettingsChanged()
        }
    }
}
