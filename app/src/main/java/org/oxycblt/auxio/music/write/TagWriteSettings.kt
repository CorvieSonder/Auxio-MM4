/*
 * Copyright (c) 2026 Auxio Project
 * TagWriteSettings.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.write

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.oxycblt.auxio.R
import org.oxycblt.auxio.settings.Settings

/** User configuration for writing edits into audio files. */
interface TagWriteSettings : Settings<TagWriteSettings.Listener> {
    /** Whether edits made in the app are also written into the audio files. */
    val writeToFiles: Boolean

    interface Listener {
        /** Called when [writeToFiles] changed. */
        fun onWriteToFilesChanged()
    }
}

class TagWriteSettingsImpl @Inject constructor(@ApplicationContext context: Context) :
    Settings.Impl<TagWriteSettings.Listener>(context), TagWriteSettings {
    override val writeToFiles: Boolean
        get() = sharedPreferences.getBoolean(getString(R.string.set_key_write_tags), false)

    override fun onSettingChanged(key: String, listener: TagWriteSettings.Listener) {
        if (key == getString(R.string.set_key_write_tags)) {
            listener.onWriteToFilesChanged()
        }
    }
}
