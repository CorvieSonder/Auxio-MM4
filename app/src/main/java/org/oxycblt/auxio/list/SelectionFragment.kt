/*
 * Copyright (c) 2022 Auxio Project
 * SelectionFragment.kt is part of Auxio.
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
 
package org.oxycblt.auxio.list

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.viewbinding.ViewBinding
import org.oxycblt.auxio.R
import org.oxycblt.auxio.music.MusicViewModel
import org.oxycblt.auxio.music.tree.MovePlaylistDialogArgs
import org.oxycblt.auxio.music.tree.PlaylistTreeViewModel
import org.oxycblt.auxio.playback.PlaybackViewModel
import org.oxycblt.auxio.ui.AuxioToolbar
import org.oxycblt.auxio.ui.ViewBindingFragment
import org.oxycblt.auxio.util.collect
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.auxio.util.showToast
import org.oxycblt.musikr.Playlist

/**
 * A subset of ListFragment that implements aspects of the selection UI.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
abstract class SelectionFragment<VB : ViewBinding> :
    ViewBindingFragment<VB>(), Toolbar.OnMenuItemClickListener {
    protected abstract val listModel: ListViewModel
    protected abstract val musicModel: MusicViewModel
    protected abstract val playbackModel: PlaybackViewModel
    private val treeModel: PlaylistTreeViewModel by activityViewModels()

    open fun getSelectionToolbar(binding: VB): AuxioToolbar? = null

    override fun onBindingCreated(binding: VB, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)
        getSelectionToolbar(binding)?.apply {
            // Add cancel and menu item listeners to manage what occurs with the selection.
            setNavigationOnClickListener { listModel.dropSelection() }
            setOnMenuItemClickListener(this@SelectionFragment)
            setOnOverflowMenuClick {
                val songs = listModel.peekSelection()
                val playlists = listModel.selected.value.filterIsInstance<Playlist>()
                if (playlists.isNotEmpty()) {
                    // Selected playlists can be deleted, even when they have no songs.
                    listModel.openMenu(R.menu.selection_playlist, songs, playlists)
                } else if (songs.isNotEmpty()) {
                    listModel.openMenu(R.menu.selection, songs)
                }
            }
            // Selected playlists can be moved into another playlist and pinned. Pin is offered
            // while any of them is not pinned yet, unpin once all of them are.
            collectImmediately(listModel.selected) { selected ->
                val playlists = selected.filterIsInstance<Playlist>()
                val allPinned = playlists.all(treeModel::isPinned)
                setMenuItemVisible(R.id.action_selection_move, playlists.isNotEmpty())
                setMenuItemVisible(R.id.action_selection_pin, playlists.isNotEmpty() && !allPinned)
                setMenuItemVisible(
                    R.id.action_selection_unpin,
                    playlists.isNotEmpty() && allPinned,
                )
            }
        }
        collect(treeModel.moveDecision.flow) { if (it != null) handleMoveDecision() }
    }

    private fun handleMoveDecision() {
        // Several fragments can be listening at once, so only the first one handles it.
        val playlistUids = treeModel.moveDecision.consume() ?: return
        findNavController()
            .navigate(
                R.id.move_playlists,
                MovePlaylistDialogArgs(playlistUids.toTypedArray()).toBundle(),
            )
    }

    override fun onDestroyBinding(binding: VB) {
        super.onDestroyBinding(binding)
        getSelectionToolbar(binding)?.setOnMenuItemClickListener(null)
    }

    override fun onMenuItemClick(item: MenuItem) =
        when (item.itemId) {
            R.id.action_selection_play_next -> {
                val songs = listModel.peekSelection()
                if (songs.isNotEmpty()) {
                    listModel.dropSelection()
                    playbackModel.playNext(songs)
                    requireContext().showToast(R.string.lng_play_next)
                }
                true
            }
            R.id.action_selection_playlist_add -> {
                val songs = listModel.peekSelection()
                if (songs.isNotEmpty()) {
                    listModel.dropSelection()
                    musicModel.addToPlaylist(songs)
                }
                true
            }
            R.id.action_selection_pin,
            R.id.action_selection_unpin -> {
                val playlists = listModel.selected.value.filterIsInstance<Playlist>()
                listModel.dropSelection()
                treeModel.setPinned(playlists, item.itemId == R.id.action_selection_pin)
                true
            }
            R.id.action_selection_move -> {
                val playlists = listModel.selected.value.filterIsInstance<Playlist>()
                listModel.dropSelection()
                treeModel.requestMove(playlists)
                true
            }
            else -> false
        }

    // TODO: Re-add the automatic selection handling
}
