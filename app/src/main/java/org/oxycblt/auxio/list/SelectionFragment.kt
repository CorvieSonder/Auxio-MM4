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
import org.oxycblt.auxio.music.delete.DeleteSongsDialog
import org.oxycblt.auxio.music.tree.MovePlaylistDialogArgs
import org.oxycblt.auxio.music.tree.PlaylistTreeViewModel
import org.oxycblt.auxio.playback.PlaybackViewModel
import org.oxycblt.auxio.ui.AuxioToolbar
import org.oxycblt.auxio.ui.ViewBindingFragment
import org.oxycblt.auxio.util.collect
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.auxio.util.share
import org.oxycblt.auxio.util.showToast
import org.oxycblt.musikr.Music
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

    /**
     * Get the playlist the selection is shown in, which songs can be removed from.
     *
     * @return The [Playlist] this fragment shows the songs of, or null if it shows none.
     */
    open fun getSelectionPlaylist(): Playlist? = null

    /**
     * Get the items that "Select all" and "Invert selection" act on.
     *
     * @return The [Music] items currently shown by this fragment.
     */
    open fun getSelectableItems(): List<Music> = listOf()

    override fun onBindingCreated(binding: VB, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)
        getSelectionToolbar(binding)?.apply {
            // Add cancel and menu item listeners to manage what occurs with the selection.
            setNavigationOnClickListener { listModel.dropSelection() }
            setOnMenuItemClickListener(this@SelectionFragment)
            // Kept in case the selection menu dialog is needed again. Everything it offered is
            // a button now, and the overflow button only holds what does not fit, plus delete.
            // setOnOverflowMenuClick {
            //     val songs = listModel.peekSelection()
            //     val playlists = listModel.selected.value.filterIsInstance<Playlist>()
            //     if (playlists.isNotEmpty()) {
            //         // Selected playlists can be deleted, even when they have no songs.
            //         listModel.openMenu(R.menu.selection_playlist, songs, playlists)
            //     } else if (songs.isNotEmpty()) {
            //         listModel.openMenu(R.menu.selection, songs)
            //     }
            // }
            // Selected playlists can be moved into another playlist, pinned and deleted. Pin is
            // offered while any of them is not pinned yet, unpin once all of them are.
            collectImmediately(listModel.selected) { selected ->
                val playlists = selected.filterIsInstance<Playlist>()
                val allPinned = playlists.all(treeModel::isPinned)
                setMenuItemVisible(R.id.action_selection_move, playlists.isNotEmpty())
                setMenuItemVisible(R.id.action_selection_pin, playlists.isNotEmpty() && !allPinned)
                setMenuItemVisible(
                    R.id.action_selection_unpin,
                    playlists.isNotEmpty() && allPinned,
                )
                setMenuItemVisible(R.id.action_selection_delete, playlists.isNotEmpty())
            }
        }
        getSelectionToolbar(binding)?.let { toolbar ->
            // There are more actions than fit beside the title, so they get their own row,
            // which only takes up space while something is selected.
            toolbar.setActionsBelow()
            toolbar.setOverflowPriority(
                listOf(
                    R.id.action_selection_share,
                    R.id.action_selection_play_next,
                    R.id.action_selection_queue_add,
                    R.id.action_selection_playlist_add,
                )
            )
            collectImmediately(listModel.selected) { toolbar.setActionRowVisible(it.isNotEmpty()) }
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
            R.id.action_selection_delete_songs -> {
                val songs = listModel.peekSelection()
                if (songs.size > MAX_DELETED_SONGS) {
                    // More would not fit into the arguments of the dialog.
                    requireContext().showToast(R.string.err_delete_too_many)
                } else if (songs.isNotEmpty()) {
                    listModel.dropSelection()
                    DeleteSongsDialog.from(songs, getSelectionPlaylist())
                        .show(childFragmentManager, DeleteSongsDialog.TAG)
                }
                true
            }
            R.id.action_selection_delete -> {
                val playlists = listModel.selected.value.filterIsInstance<Playlist>()
                listModel.dropSelection()
                musicModel.deletePlaylists(playlists)
                true
            }
            R.id.action_selection_move -> {
                val playlists = listModel.selected.value.filterIsInstance<Playlist>()
                listModel.dropSelection()
                treeModel.requestMove(playlists)
                true
            }
            R.id.action_selection_queue_add -> {
                val songs = listModel.peekSelection()
                if (songs.isNotEmpty()) {
                    listModel.dropSelection()
                    playbackModel.addToQueue(songs)
                    requireContext().showToast(R.string.lng_queue_added)
                }
                true
            }
            R.id.action_selection_play -> {
                // A selection of empty playlists has no songs to act on.
                val songs = listModel.peekSelection()
                if (songs.isNotEmpty()) {
                    listModel.dropSelection()
                    playbackModel.play(songs)
                }
                true
            }
            R.id.action_selection_shuffle -> {
                // A selection of empty playlists has no songs to act on.
                val songs = listModel.peekSelection()
                if (songs.isNotEmpty()) {
                    listModel.dropSelection()
                    playbackModel.shuffle(songs)
                }
                true
            }
            R.id.action_selection_share -> {
                // A selection of empty playlists has no songs to act on.
                val songs = listModel.peekSelection()
                if (songs.isNotEmpty()) {
                    listModel.dropSelection()
                    requireContext().share(songs)
                }
                true
            }
            R.id.action_selection_invert -> {
                listModel.invertSelection(getSelectableItems())
                true
            }
            R.id.action_selection_select_all -> {
                listModel.selectAll(getSelectableItems())
                true
            }
            else -> false
        }

    // TODO: Re-add the automatic selection handling

    private companion object {
        const val MAX_DELETED_SONGS = 1000
    }
}
