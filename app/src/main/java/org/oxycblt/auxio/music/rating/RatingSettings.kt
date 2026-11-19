/*
 * Copyright (c) 2026 Auxio Project
 * RatingSettings.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.rating

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.oxycblt.auxio.R
import org.oxycblt.auxio.settings.Settings

/** User configuration for song ratings. */
interface RatingSettings : Settings<RatingSettings.Listener> {
    /** The [RatingSource] to use first when a song was rated by several programs. */
    val preferredSource: RatingSource
    /** Whether to show ratings in song lists. */
    val showInLists: Boolean

    interface Listener {
        /** Called when a setting that changes what ratings are shown has changed. */
        fun onRatingSettingsChanged()
    }
}

/**
 * A program that writes ratings to ID3v2 popularimeter frames.
 *
 * @param key The key of the program's frame in [org.oxycblt.musikr.Song.extras].
 */
enum class RatingSource(val key: String) {
    MEDIAMONKEY("POPM:NO@EMAIL"),
    MUSICBEE("POPM:MUSICBEE"),
    WINDOWS_MEDIA_PLAYER("POPM:WINDOWS MEDIA PLAYER 9 SERIES"),
}

class RatingSettingsImpl @Inject constructor(@ApplicationContext context: Context) :
    Settings.Impl<RatingSettings.Listener>(context), RatingSettings {
    override val preferredSource: RatingSource
        get() =
            RatingSource.entries.getOrElse(
                sharedPreferences.getInt(getString(R.string.set_key_rating_source), 0)
            ) {
                RatingSource.MEDIAMONKEY
            }

    override val showInLists: Boolean
        get() = sharedPreferences.getBoolean(getString(R.string.set_key_rating_lists), true)

    override fun onSettingChanged(key: String, listener: RatingSettings.Listener) {
        when (key) {
            getString(R.string.set_key_rating_source),
            getString(R.string.set_key_rating_lists) -> listener.onRatingSettingsChanged()
        }
    }
}
