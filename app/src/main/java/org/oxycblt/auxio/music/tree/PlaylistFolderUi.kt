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
import androidx.appcompat.app.AlertDialog
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
    collect(folderModel.syncQuestion.flow) { plan ->
        if (plan != null) {
            folderModel.syncQuestion.consume()
            askConflicts(folderModel, plan)
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
                    is FolderMessage.Synced ->
                        context.getString(
                            R.string.lng_playlist_folder_synced,
                            message.result.created,
                            message.result.updated,
                            message.result.conflicts,
                            message.result.deleted,
                            message.result.missingSongs,
                        )
                    is FolderMessage.ChangesExported ->
                        context.getString(
                            R.string.lng_playlist_changes_exported,
                            message.changed,
                            message.deleted,
                        )
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
        mutableListOf(context.getString(R.string.lbl_import_folder_itself, scan.folder.name))
    if (scan.hasIds) {
        labels.add(context.getString(R.string.lbl_import_sync))
    }
    val first = labels.size
    labels.addAll(scan.items.map { if (it.isFolder) "${it.name}/" else it.name })
    val checked = BooleanArray(labels.size) { true }
    checked[0] = scan.own != null
    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.lbl_import_choose)
        .setMultiChoiceItems(labels.toTypedArray(), checked) { _, which, isChecked ->
            checked[which] = isChecked
        }
        .setPositiveButton(R.string.lbl_import) { _, _ ->
            val selected = scan.items.filterIndexed { i, _ -> checked[i + first] }
            folderModel.importFolder(choices, selected, checked[0], scan.hasIds && checked[1])
        }
        .setNegativeButton(R.string.lbl_cancel, null)
        .show()
}

/** Ask how to handle playlists changed on both sides, then whether to delete missing ones. */
private fun Fragment.askConflicts(
    folderModel: PlaylistFolderViewModel,
    plan: PlaylistFolderSync.Plan,
) {
    if (plan.conflicts.isEmpty()) {
        askDeletions(folderModel, plan, PlaylistFolderSync.ConflictChoice.OVERWRITE)
        return
    }
    val context = requireContext()
    val choices =
        listOf(
            PlaylistFolderSync.ConflictChoice.MERGE to R.string.lbl_sync_merge,
            PlaylistFolderSync.ConflictChoice.OVERWRITE to R.string.lbl_sync_overwrite,
            PlaylistFolderSync.ConflictChoice.SKIP to R.string.lbl_sync_skip,
        )
    MaterialAlertDialogBuilder(context)
        .setTitle(
            context.resources.getQuantityString(
                R.plurals.fmt_sync_conflicts,
                plan.conflicts.size,
                plan.conflicts.size,
            )
        )
        .setSingleChoiceItems(choices.map { context.getString(it.second) }.toTypedArray(), 0, null)
        .setPositiveButton(R.string.lbl_ok) { dialog, _ ->
            val which = (dialog as AlertDialog).listView.checkedItemPosition
            askDeletions(folderModel, plan, choices[which].first)
        }
        .setNegativeButton(R.string.lbl_cancel, null)
        .show()
}

private fun Fragment.askDeletions(
    folderModel: PlaylistFolderViewModel,
    plan: PlaylistFolderSync.Plan,
    choice: PlaylistFolderSync.ConflictChoice,
) {
    if (plan.missing.isEmpty()) {
        folderModel.answerSync(plan, choice, false)
        return
    }
    val context = requireContext()
    val checked = BooleanArray(plan.missing.size) { true }
    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.lbl_sync_delete_title)
        .setMultiChoiceItems(
            plan.missing.map { it.name.raw }.toTypedArray(),
            checked,
        ) { _, which, isChecked ->
            checked[which] = isChecked
        }
        .setPositiveButton(R.string.lbl_ok) { _, _ ->
            val selected = plan.missing.filterIndexed { i, _ -> checked[i] }
            folderModel.answerSync(
                PlaylistFolderSync.Plan(plan.scan, plan.parentUid, plan.items, selected),
                choice,
                selected.isNotEmpty(),
            )
        }
        .setNegativeButton(R.string.lbl_sync_keep) { _, _ ->
            folderModel.answerSync(plan, choice, false)
        }
        .show()
}
