/*
 * Copyright (c) 2026 Auxio Project
 * SleepTimerSettings.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.sleep

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.oxycblt.auxio.R
import org.oxycblt.auxio.settings.Settings

/** The sleep timer options that were last used, offered again the next time. */
interface SleepTimerSettings : Settings<Nothing> {
    /** The duration last used, in minutes. */
    var durationMins: Int
    /** Whether to let the current song finish once the timer runs out. */
    var finishSong: Boolean
    /** Whether to fade the volume out before pausing. */
    var fade: Boolean
}

class SleepTimerSettingsImpl @Inject constructor(@ApplicationContext context: Context) :
    Settings.Impl<Nothing>(context), SleepTimerSettings {
    override var durationMins: Int
        get() = sharedPreferences.getInt(getString(R.string.set_key_sleep_duration), 30)
        set(value) {
            sharedPreferences.edit {
                putInt(getString(R.string.set_key_sleep_duration), value)
                apply()
            }
        }

    override var finishSong: Boolean
        get() = sharedPreferences.getBoolean(getString(R.string.set_key_sleep_finish_song), false)
        set(value) {
            sharedPreferences.edit {
                putBoolean(getString(R.string.set_key_sleep_finish_song), value)
                apply()
            }
        }

    override var fade: Boolean
        get() = sharedPreferences.getBoolean(getString(R.string.set_key_sleep_fade), true)
        set(value) {
            sharedPreferences.edit {
                putBoolean(getString(R.string.set_key_sleep_fade), value)
                apply()
            }
        }
}
