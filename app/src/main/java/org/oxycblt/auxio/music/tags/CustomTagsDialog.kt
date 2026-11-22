/*
 * Copyright (c) 2026 Auxio Project
 * CustomTagsDialog.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.tags

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.oxycblt.auxio.BuildConfig
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogCustomTagsBinding
import org.oxycblt.auxio.databinding.ItemCustomTagBinding
import org.oxycblt.auxio.music.MusicSettings
import org.oxycblt.auxio.ui.ViewBindingMaterialDialogFragment

/**
 * A dialog to name and enable the custom tags, and choose how each is read and shown.
 *
 * Saving reloads the music library, as with the separator setting.
 */
@AndroidEntryPoint
class CustomTagsDialog : ViewBindingMaterialDialogFragment<DialogCustomTagsBinding>() {
    @Inject lateinit var musicSettings: MusicSettings
    private val rows = mutableListOf<Pair<Int, ItemCustomTagBinding>>()

    override fun onCreateBinding(inflater: LayoutInflater) =
        DialogCustomTagsBinding.inflate(inflater)

    override fun onConfigDialog(builder: AlertDialog.Builder) {
        builder
            .setTitle(R.string.lbl_custom_tags)
            .setNegativeButton(R.string.lbl_cancel, null)
            .setPositiveButton(R.string.lbl_save) { _, _ ->
                val tags = currentTags()
                if (tags != musicSettings.customTags) {
                    musicSettings.customTags = tags
                }
            }
    }

    override fun onBindingCreated(binding: DialogCustomTagsBinding, savedInstanceState: Bundle?) {
        val tags =
            savedInstanceState?.getString(KEY_PENDING_TAGS)?.let { CustomTag.parse(it) }
                ?: musicSettings.customTags
        rows.clear()
        binding.customTagRows.removeAllViews()
        for (tag in tags) {
            val row = ItemCustomTagBinding.inflate(layoutInflater, binding.customTagRows, true)
            // Saved view state would restore every row into the last one, as the ids repeat.
            row.root.isSaveFromParentEnabled = false
            row.customTagNameContainer.hint = CustomTag.defaultName(requireContext(), tag.slot)
            row.customTagName.setText(tag.name)
            row.customTagRead.isChecked = tag.enabled
            row.customTagMulti.isChecked = tag.multiValue
            row.customTagTab.isChecked = tag.showTab
            rows.add(tag.slot to row)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PENDING_TAGS, CustomTag.stringify(currentTags()))
    }

    override fun onDestroyBinding(binding: DialogCustomTagsBinding) {
        super.onDestroyBinding(binding)
        rows.clear()
    }

    private fun currentTags() = rows.map { (slot, row) ->
        CustomTag(
            slot = slot,
            name = row.customTagName.text?.toString()?.trim().orEmpty(),
            enabled = row.customTagRead.isChecked,
            multiValue = row.customTagMulti.isChecked,
            showTab = row.customTagTab.isChecked,
        )
    }

    private companion object {
        const val KEY_PENDING_TAGS = BuildConfig.APPLICATION_ID + ".key.PENDING_CUSTOM_TAGS"
    }
}
