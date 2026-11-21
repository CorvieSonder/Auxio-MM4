/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistFolderUi.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.tree

import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.oxycblt.auxio.R
import org.oxycblt.auxio.util.collect

/**
 * Let this [Fragment] answer folder import requests from [folderModel] with the system folder
 * picker, and show the outcome of folder exports and imports. Call from onBindingCreated.
 */
fun Fragment.setupPlaylistFolders(folderModel: PlaylistFolderViewModel) {
    val launcher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val request = folderModel.pendingImport
            folderModel.pendingImport = null
            if (uri != null && request != null) {
                folderModel.scanFolder(uri, request.parentUid)
            }
        }
    collect(folderModel.importRequest.flow) { request ->
        if (request != null) {
            folderModel.importRequest.consume()
            folderModel.pendingImport = request
            launcher.launch(null)
        }
    }
    collect(folderModel.importChoices.flow) { choices ->
        if (choices != null) {
            folderModel.importChoices.consume()
            showImportChoices(folderModel, choices)
        }
    }
    collect(folderModel.message.flow) { message ->
        if (message != null) {
            folderModel.message.consume()
            val context = requireContext()
            val text =
                when (message) {
                    is FolderMessage.Exported ->
                        context.getString(R.string.lng_playlist_folder_exported, message.playlists)
                    is FolderMessage.Imported ->
                        context.getString(
                            R.string.lng_playlist_folder_imported,
                            message.playlists,
                            message.missingSongs,
                        )
                    FolderMessage.ExportFailed ->
                        context.getString(R.string.err_export_folder_failed)
                    FolderMessage.ImportFailed ->
                        context.getString(R.string.err_import_folder_failed)
                }
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * Let the user tick which items of a scanned folder to import, and whether to import the folder
 * itself as one playlist holding them. That option starts ticked when the folder has its own
 * playlist file, as exported folders do.
 */
private fun Fragment.showImportChoices(
    folderModel: PlaylistFolderViewModel,
    choices: PlaylistFolderViewModel.ImportChoices,
) {
    val context = requireContext()
    val scan = choices.scan
    val labels =
        listOf(context.getString(R.string.lbl_import_folder_itself, scan.folder.name)) +
            scan.items.map { if (it.isFolder) "${it.name}/" else it.name }
    val checked = BooleanArray(labels.size) { true }
    checked[0] = scan.own != null
    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.lbl_import_choose)
        .setMultiChoiceItems(labels.toTypedArray(), checked) { _, which, isChecked ->
            checked[which] = isChecked
        }
        .setPositiveButton(R.string.lbl_import) { _, _ ->
            val selected = scan.items.filterIndexed { i, _ -> checked[i + 1] }
            folderModel.importFolder(choices, selected, checked[0])
        }
        .setNegativeButton(R.string.lbl_cancel, null)
        .show()
}
