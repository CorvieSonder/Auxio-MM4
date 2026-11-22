/*
 * Copyright (c) 2026 Auxio Project
 * BackupUi.kt is part of Auxio.
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
 
package org.oxycblt.auxio.settings.backup

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.oxycblt.auxio.R
import timber.log.Timber as L

/**
 * The backup and restore actions of the settings screen. Create in the fragment's onCreate, since
 * it registers the file pickers.
 */
class BackupUi(private val fragment: Fragment) {
    private val exportLauncher =
        fragment.registerForActivityResult(
            ActivityResultContracts.CreateDocument(BackupFormat.MIME_TYPE)
        ) { uri ->
            if (uri != null) {
                runInBackground(R.string.err_backup_failed) {
                    BackupWriter.write(it, uri)
                    R.string.lng_backup_saved
                }
            }
        }

    private val importLauncher =
        fragment.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runInBackground(R.string.err_backup_invalid) {
                    when (val result = BackupReader.stage(it, uri)) {
                        BackupReader.Result.Staged -> {
                            restart(it)
                            null
                        }
                        BackupReader.Result.Invalid -> R.string.err_backup_invalid
                        is BackupReader.Result.TooNew -> R.string.err_backup_too_new
                    }
                }
            }
        }

    /** Ask where to save a new backup, then write it. */
    fun export() {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        exportLauncher.launch("auxio-backup-$date.zip")
    }

    /** Warn that a restore replaces everything, then ask for the backup file. */
    fun import() {
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.lbl_backup_restore)
            .setMessage(R.string.lng_backup_restore_warning)
            .setPositiveButton(R.string.lbl_ok) { _, _ ->
                // Some file managers do not mark zip files as application/zip.
                importLauncher.launch(arrayOf(BackupFormat.MIME_TYPE, "application/octet-stream"))
            }
            .setNegativeButton(R.string.lbl_cancel, null)
            .show()
    }

    /**
     * Run [work] off the main thread and show the message it returns, or [errorRes] if it fails.
     */
    private fun runInBackground(errorRes: Int, work: (Context) -> Int?) {
        val context = fragment.requireContext().applicationContext
        fragment.lifecycleScope.launch {
            val message =
                withContext(Dispatchers.IO) {
                    try {
                        work(context)
                    } catch (e: Exception) {
                        L.e("Backup action failed: $e")
                        errorRes
                    }
                }
            if (message != null) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Restart the app, so the staged backup is applied by [BackupRestorer] before any database
     * opens.
     */
    private fun restart(context: Context) {
        val intent =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        if (intent != null) context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
