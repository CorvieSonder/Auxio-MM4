/*
 * Copyright (c) 2026 Auxio Project
 * NowPlayingBarSettings.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.bar

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.oxycblt.auxio.R
import org.oxycblt.auxio.settings.Settings
import timber.log.Timber as L

/** User configuration for the action bar of the playback panel. */
interface NowPlayingBarSettings : Settings<NowPlayingBarSettings.Listener> {
    /**
     * The actions of the bar in the order chosen by the user, or an empty list if the user has not
     * changed the bar. Actions that are not listed keep their place from the menu resource.
     */
    var barActions: List<NowPlayingBar.Action>

    interface Listener {
        /** Called when [barActions] changes. */
        fun onBarActionsChanged() {}
    }
}

class NowPlayingBarSettingsImpl @Inject constructor(@ApplicationContext context: Context) :
    Settings.Impl<NowPlayingBarSettings.Listener>(context), NowPlayingBarSettings {
    override var barActions: List<NowPlayingBar.Action>
        get() =
            NowPlayingBar.Action.fromCode(
                sharedPreferences.getString(getString(R.string.set_key_now_playing_bar), null)
            )
        set(value) {
            sharedPreferences.edit {
                putString(
                    getString(R.string.set_key_now_playing_bar),
                    NowPlayingBar.Action.toCode(value),
                )
                apply()
            }
        }

    override fun onSettingChanged(key: String, listener: NowPlayingBarSettings.Listener) {
        if (key == getString(R.string.set_key_now_playing_bar)) {
            L.d("Dispatching now playing bar setting change")
            listener.onBarActionsChanged()
        }
    }
}
