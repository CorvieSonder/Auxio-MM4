/*
 * Copyright (c) 2026 Auxio Project
 * MovePlaylistDialog.kt is part of Auxio.
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

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogMusicChoicesBinding
import org.oxycblt.auxio.ui.ViewBindingMaterialDialogFragment
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.auxio.util.showToast
import org.oxycblt.musikr.Playlist
import timber.log.Timber as L

/**
 * A dialog that moves playlists into another playlist, or to the top level. Shows one level of the
 * playlist tree at a time: tapping a row moves the playlists there, and its arrow shows the
 * playlists inside it.
 */
@AndroidEntryPoint
class MovePlaylistDialog :
    ViewBindingMaterialDialogFragment<DialogMusicChoicesBinding>(), TreeChoiceAdapter.Listener {
    private val treeModel: PlaylistTreeViewModel by activityViewModels()
    private val moveModel: MovePlaylistViewModel by viewModels()
    // The playlists to move are passed as UIDs, as that is the only safe way to parcel them.
    private val args: MovePlaylistDialogArgs by navArgs()
    private val choiceAdapter = TreeChoiceAdapter(this)

    override fun onConfigDialog(builder: AlertDialog.Builder) {
        builder.setTitle(R.string.lbl_move_to).setNegativeButton(R.string.lbl_cancel, null)
    }

    override fun onCreateBinding(inflater: LayoutInflater) =
        DialogMusicChoicesBinding.inflate(inflater)

    override fun onBindingCreated(binding: DialogMusicChoicesBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        binding.choiceRecycler.apply {
            itemAnimator = null
            adapter = choiceAdapter
        }

        // --- VIEWMODEL SETUP ---
        treeModel.moveDecision.consume()
        if (moveModel.playlistsToMove.value == null) {
            moveModel.setPlaylistsToMove(args.playlistUids)
        }
        collectImmediately(moveModel.playlistsToMove, ::updatePlaylistsToMove)
        collectImmediately(moveModel.choices) { choiceAdapter.update(it, null) }
    }

    override fun onDestroyBinding(binding: DialogMusicChoicesBinding) {
        super.onDestroyBinding(binding)
        binding.choiceRecycler.adapter = null
    }

    override fun onChoose(choice: TreeChoice) {
        when (choice) {
            is TreeChoice.Up -> moveModel.up()
            is TreeChoice.TopLevel -> moveTo(null)
            is TreeChoice.Node -> moveTo(choice.playlist)
        }
    }

    override fun onOpen(playlist: Playlist) {
        moveModel.open(playlist)
    }

    private fun moveTo(parent: Playlist?) {
        if (!moveModel.move(parent)) {
            // Stay open, so that another place can be picked.
            requireContext().showToast(R.string.lng_playlist_name_taken)
            return
        }
        requireContext().showToast(R.string.lng_playlist_moved)
        findNavController().navigateUp()
    }

    private fun updatePlaylistsToMove(playlists: List<Playlist>?) {
        if (playlists == null) {
            L.d("No playlists to move, navigating away")
            findNavController().navigateUp()
        }
    }
}
