/*
 * Copyright (c) 2026 Auxio Project
 * CrossfadeSettings.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.crossfade

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.oxycblt.auxio.R
import org.oxycblt.auxio.settings.Settings

/** User configuration for crossfading between songs. */
interface CrossfadeSettings : Settings<Nothing> {
    /** How long songs overlap, in milliseconds. 0 if crossfade is off or true gapless is on. */
    val crossfadeMs: Long
    /** Whether to play consecutive tracks of the same album gaplessly instead. */
    val skipWithinAlbum: Boolean
}

class CrossfadeSettingsImpl @Inject constructor(@ApplicationContext context: Context) :
    Settings.Impl<Nothing>(context), CrossfadeSettings {
    override val crossfadeMs: Long
        get() =
            if (sharedPreferences.getBoolean(getString(R.string.set_key_true_gapless), false)) {
                // True gapless trims the silence between songs instead, which a crossfade
                // would undo.
                0L
            } else {
                sharedPreferences.getInt(getString(R.string.set_key_crossfade), 0) * 1000L
            }

    override val skipWithinAlbum: Boolean
        get() = sharedPreferences.getBoolean(getString(R.string.set_key_crossfade_skip_album), true)
}
