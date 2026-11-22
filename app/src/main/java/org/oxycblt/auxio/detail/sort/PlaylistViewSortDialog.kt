/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistViewSortDialog.kt is part of Auxio.
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
 
package org.oxycblt.auxio.detail.sort

import android.os.Bundle
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BackportBottomSheetBehavior
import com.google.android.material.bottomsheet.BackportBottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.databinding.DialogSortBinding
import org.oxycblt.auxio.detail.DetailViewModel
import org.oxycblt.auxio.list.sort.Sort
import org.oxycblt.auxio.list.sort.SortDialog
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.musikr.Playlist
import timber.log.Timber as L

/**
 * A [SortDialog] that controls the [Sort] of [DetailViewModel.playlistSongSort]. Unlike
 * [PlaylistSongSortDialog], this only changes how the playlist is shown, not its saved order.
 */
@AndroidEntryPoint
class PlaylistViewSortDialog : SortDialog() {
    private val detailModel: DetailViewModel by activityViewModels()

    override fun onBindingCreated(binding: DialogSortBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        // --- VIEWMODEL SETUP ---
        collectImmediately(detailModel.currentPlaylist, ::updatePlaylist)
    }

    override fun onStart() {
        super.onStart()
        // There are more sort modes than fit in the collapsed sheet, which would hide the
        // buttons. Open the sheet fully instead, letting the mode list scroll if needed.
        (dialog as? BackportBottomSheetDialog)?.behavior?.state =
            BackportBottomSheetBehavior.STATE_EXPANDED
    }

    override fun getInitialSort() = detailModel.playlistSongSort

    override fun applyChosenSort(sort: Sort) {
        detailModel.applyPlaylistViewSort(sort)
    }

    override val showsRatingFilter = true

    override fun getModeChoices() =
        listOfNotNull(
            // A library playlist has no saved order.
            Sort.Mode.ByCustom.takeUnless { detailModel.isLibraryPlaylist },
            Sort.Mode.ByName,
            Sort.Mode.ByArtist,
            Sort.Mode.ByAlbum,
            Sort.Mode.ByDate,
            Sort.Mode.ByDuration,
            Sort.Mode.ByDateAdded,
        )

    private fun updatePlaylist(playlist: Playlist?) {
        if (playlist == null) {
            L.d("No playlist to sort, navigating away")
            findNavController().navigateUp()
        }
    }
}
