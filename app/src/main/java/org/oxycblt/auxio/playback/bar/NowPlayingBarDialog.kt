/*
 * Copyright (c) 2026 Auxio Project
 * NowPlayingBarDialog.kt is part of Auxio.
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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.oxycblt.auxio.BuildConfig
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogTabsBinding
import org.oxycblt.auxio.list.EditClickListListener
import org.oxycblt.auxio.ui.ViewBindingMaterialDialogFragment

/**
 * A dialog for choosing which actions the playback panel bar shows, and in what order. Hidden
 * actions move into the song menu.
 */
@AndroidEntryPoint
class NowPlayingBarDialog :
    ViewBindingMaterialDialogFragment<DialogTabsBinding>(), EditClickListListener<MenuItem> {
    private val barAdapter = NowPlayingBarAdapter(this)
    private var touchHelper: ItemTouchHelper? = null
    @Inject lateinit var barSettings: NowPlayingBarSettings

    override fun onCreateBinding(inflater: LayoutInflater) = DialogTabsBinding.inflate(inflater)

    override fun onConfigDialog(builder: AlertDialog.Builder) {
        builder
            .setTitle(R.string.set_now_playing_bar)
            .setPositiveButton(R.string.lbl_ok) { _, _ ->
                barSettings.barActions =
                    NowPlayingBar.toActions(requireContext(), barAdapter.actions)
            }
            .setNegativeButton(R.string.lbl_cancel, null)
    }

    override fun onBindingCreated(binding: DialogTabsBinding, savedInstanceState: Bundle?) {
        val context = requireContext()
        // Restore a pending configuration if this dialog was re-created.
        val actions =
            savedInstanceState?.getString(KEY_ACTIONS)?.let { NowPlayingBar.Action.fromCode(it) }
                ?: barSettings.barActions
        barAdapter.submitActions(
            NowPlayingBar.arrange(context, NowPlayingBar.inflate(context), actions)
        )
        binding.tabRecycler.apply {
            adapter = barAdapter
            touchHelper =
                ItemTouchHelper(NowPlayingBarDragCallback(barAdapter)).also {
                    it.attachToRecyclerView(this)
                }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(
            KEY_ACTIONS,
            NowPlayingBar.Action.toCode(
                NowPlayingBar.toActions(requireContext(), barAdapter.actions)
            ),
        )
    }

    override fun onDestroyBinding(binding: DialogTabsBinding) {
        super.onDestroyBinding(binding)
        binding.tabRecycler.adapter = null
        touchHelper = null
    }

    override fun onClick(item: MenuItem, viewHolder: RecyclerView.ViewHolder) {
        barAdapter.toggle(item)
    }

    override fun onPickUp(viewHolder: RecyclerView.ViewHolder) {
        requireNotNull(touchHelper) { "ItemTouchHelper was not available" }.startDrag(viewHolder)
    }

    private companion object {
        const val KEY_ACTIONS = BuildConfig.APPLICATION_ID + ".key.PENDING_BAR_ACTIONS"
    }
}
