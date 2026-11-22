/*
 * Copyright (c) 2026 Auxio Project
 * TrueGaplessSettings.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.gapless

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.oxycblt.auxio.R
import org.oxycblt.auxio.settings.Settings

/** User configuration for trimming the silence at the start and end of songs. */
interface TrueGaplessSettings : Settings<Nothing> {
    /** Whether silence at the start and end of songs is trimmed. Crossfade is off while it is. */
    val enabled: Boolean
}

class TrueGaplessSettingsImpl @Inject constructor(@ApplicationContext context: Context) :
    Settings.Impl<Nothing>(context), TrueGaplessSettings {
    override val enabled: Boolean
        get() = sharedPreferences.getBoolean(getString(R.string.set_key_true_gapless), false)
}
