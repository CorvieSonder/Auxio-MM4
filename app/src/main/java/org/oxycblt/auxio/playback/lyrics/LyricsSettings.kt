/*
 * Copyright (c) 2026 Auxio Project
 * LyricsSettings.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.lyrics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.oxycblt.auxio.R
import org.oxycblt.auxio.settings.Settings

/** User configuration for the lyrics page. Read each time the page is shown. */
class LyricsSettings @Inject constructor(@ApplicationContext context: Context) :
    Settings.Impl<Unit>(context) {
    private val smallSize = context.resources.getInteger(R.integer.lyrics_text_size_small)
    private val largeSize = context.resources.getInteger(R.integer.lyrics_text_size_large)

    /** The lyrics text size, in sp. */
    val textSizeSp: Float
        get() =
            when (sharedPreferences.getInt(getString(R.string.set_key_lyrics_text_size), -1)) {
                smallSize -> 16f
                largeSize -> 26f
                else -> 20f
            }

    /** Whether to keep the screen on while the lyrics are shown and a song is playing. */
    val keepScreenOn: Boolean
        get() =
            sharedPreferences.getBoolean(getString(R.string.set_key_lyrics_keep_screen_on), false)
}
