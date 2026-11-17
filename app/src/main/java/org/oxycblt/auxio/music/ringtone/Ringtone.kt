/*
 * Copyright (c) 2026 Auxio Project
 * Ringtone.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.ringtone

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import org.oxycblt.auxio.R
import org.oxycblt.auxio.util.showToast
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/**
 * Make a [Song] the ringtone of the device. The first time, this opens the system screen that
 * allows the app to change system settings instead, as the ringtone is one of them.
 *
 * @param song The [Song] to use as the ringtone.
 */
fun Context.setAsRingtone(song: Song) {
    if (!Settings.System.canWrite(this)) {
        L.d("Not allowed to change system settings yet, asking for it")
        showToast(R.string.lng_ringtone_permission)
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            showToast(R.string.err_no_app)
        }
        return
    }

    // The system can only play a ringtone it has indexed itself, not a file picked by folder.
    val uri = song.uri.toMediaUri(this)
    if (uri == null) {
        showToast(R.string.err_ringtone)
        return
    }
    try {
        RingtoneManager.setActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE, uri)
        showToast(R.string.lng_ringtone_set)
    } catch (e: Exception) {
        L.e("Unable to set ringtone to $uri: $e")
        showToast(R.string.err_ringtone)
    }
}

private fun Uri.toMediaUri(context: Context): Uri? {
    if (authority == MediaStore.AUTHORITY) {
        return this
    }
    if (
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            !DocumentsContract.isDocumentUri(context, this)
    ) {
        return null
    }
    return try {
        MediaStore.getMediaUri(context, this)
    } catch (e: Exception) {
        L.e("Unable to find the media uri of $this: $e")
        null
    }
}
