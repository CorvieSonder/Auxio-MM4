/*
 * Copyright (c) 2026 Auxio Project
 * DeleteSongsDialog.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.delete

import android.app.Dialog
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.oxycblt.auxio.R
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.music.MusicViewModel
import org.oxycblt.auxio.music.resolve
import org.oxycblt.auxio.util.getPlural
import org.oxycblt.auxio.util.showToast
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/**
 * A dialog that asks how songs should be deleted: taken out of the playlist they are shown in, or
 * deleted from the device for good, which asks for a second confirmation.
 */
@AndroidEntryPoint
class DeleteSongsDialog : DialogFragment() {
    @Inject lateinit var musicRepository: MusicRepository
    private val musicModel: MusicViewModel by activityViewModels()
    private var deleteLauncher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var songs = listOf<Song>()
    private var playlist: Playlist? = null
    private var choice = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The system asks for consent itself before it deletes files it manages.
        deleteLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                finishDeviceDelete()
            }
        val library = musicRepository.library
        val args = requireArguments()
        songs =
            BundleCompat.getParcelableArrayList(args, KEY_SONGS, Music.UID::class.java)
                .orEmpty()
                .mapNotNull { library?.findSong(it) }
        playlist =
            BundleCompat.getParcelable(args, KEY_PLAYLIST, Music.UID::class.java)?.let {
                library?.findPlaylist(it)
            }
        choice = savedInstanceState?.getInt(KEY_CHOICE) ?: 0
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_CHOICE, choice)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val playlist = playlist
        val options = mutableListOf<Pair<String, () -> Unit>>()
        if (playlist != null) {
            options.add(
                getString(R.string.fmt_delete_from_playlist, playlist.name.resolve(context)) to
                    ::removeFromPlaylist
            )
        }
        options.add(getString(R.string.lbl_delete_from_device) to ::confirmDeviceDelete)
        choice = choice.coerceIn(options.indices)
        val dialog =
            MaterialAlertDialogBuilder(context)
                .setTitle(context.getPlural(R.plurals.fmt_delete_songs, songs.size))
                .setSingleChoiceItems(options.map { it.first }.toTypedArray(), choice) { _, which ->
                    choice = which
                }
                // Set below, as the dialog has to stay open while files are deleted.
                .setPositiveButton(R.string.lbl_delete, null)
                .setNegativeButton(R.string.lbl_cancel, null)
                .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                options[choice].second()
            }
        }
        return dialog
    }

    private fun removeFromPlaylist() {
        val playlist = playlist ?: return
        val removed = songs.toSet()
        L.d("Removing ${removed.size} songs from $playlist")
        val context = requireContext().applicationContext
        // Outlives the dialog, like the other playlist edits.
        musicModel.rewritePlaylist(playlist, playlist.songs.filter { it !in removed })
        context.showToast(R.string.lng_deleted_from_playlist)
        dismiss()
    }

    private fun confirmDeviceDelete() {
        val context = requireContext()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.lbl_delete_from_device)
            .setMessage(context.getPlural(R.plurals.fmt_delete_device_warning, songs.size))
            .setPositiveButton(R.string.lbl_delete) { _, _ -> deleteFromDevice() }
            .setNegativeButton(R.string.lbl_cancel, null)
            .show()
    }

    private fun deleteFromDevice() {
        val context = requireContext().applicationContext
        val resolver = context.contentResolver
        val (documents, media) =
            songs.map { it.uri }.partition { DocumentsContract.isDocumentUri(context, it) }
        L.d("Deleting ${documents.size} documents and ${media.size} media files")
        lifecycleScope.launch {
            val failed =
                withContext(Dispatchers.IO) {
                    documents.count { !deleteDocument(resolver, it) } +
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                            media.count { !deleteMedia(resolver, it) }
                        } else {
                            0
                        }
                }
            if (failed > 0) {
                context.showToast(R.string.err_delete_failed)
            }
            if (media.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val request = MediaStore.createDeleteRequest(resolver, media)
                deleteLauncher?.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } else {
                finishDeviceDelete()
            }
        }
    }

    private fun deleteDocument(resolver: ContentResolver, uri: Uri) =
        try {
            DocumentsContract.deleteDocument(resolver, uri)
        } catch (e: Exception) {
            L.e("Unable to delete $uri: $e")
            false
        }

    private fun deleteMedia(resolver: ContentResolver, uri: Uri) =
        try {
            resolver.delete(uri, null, null) > 0
        } catch (e: Exception) {
            L.e("Unable to delete $uri: $e")
            false
        }

    private fun finishDeviceDelete() {
        // The deleted songs leave the library, queue and playlists with the next load.
        musicModel.refresh()
        dismissAllowingStateLoss()
    }

    companion object {
        const val TAG = "DeleteSongsDialog"
        private const val KEY_SONGS = "songs"
        private const val KEY_PLAYLIST = "playlist"
        private const val KEY_CHOICE = "choice"

        /**
         * Create a new instance.
         *
         * @param songs The [Song]s to delete.
         * @param playlist The [Playlist] the songs are shown in or played from, if any, which they
         *   can be removed from instead.
         */
        fun from(songs: List<Song>, playlist: Playlist?) =
            DeleteSongsDialog().apply {
                arguments =
                    Bundle().apply {
                        putParcelableArrayList(KEY_SONGS, ArrayList(songs.map { it.uid }))
                        putParcelable(KEY_PLAYLIST, playlist?.uid)
                    }
            }
    }
}
