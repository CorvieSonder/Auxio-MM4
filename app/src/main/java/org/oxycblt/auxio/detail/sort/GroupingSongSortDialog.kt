/*
 * Copyright (c) 2026 Auxio Project
 * GroupingSongSortDialog.kt is part of Auxio.
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
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.databinding.DialogSortBinding
import org.oxycblt.auxio.detail.DetailViewModel
import org.oxycblt.auxio.list.sort.Sort
import org.oxycblt.auxio.list.sort.SortDialog
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.musikr.Grouping
import timber.log.Timber as L

/** A [SortDialog] that controls the [Sort] of [DetailViewModel.groupingSongSort]. */
@AndroidEntryPoint
class GroupingSongSortDialog : SortDialog() {
    private val detailModel: DetailViewModel by activityViewModels()

    override fun onBindingCreated(binding: DialogSortBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        // --- VIEWMODEL SETUP ---
        collectImmediately(detailModel.currentGrouping, ::updateGrouping)
    }

    override fun getInitialSort() = detailModel.groupingSongSort

    override fun applyChosenSort(sort: Sort) {
        detailModel.applyGroupingSongSort(sort)
    }

    override fun getModeChoices() =
        listOf(
            Sort.Mode.ByName,
            Sort.Mode.ByArtist,
            Sort.Mode.ByAlbum,
            Sort.Mode.ByDate,
            Sort.Mode.ByDuration,
        )

    private fun updateGrouping(grouping: Grouping?) {
        if (grouping == null) {
            L.d("No grouping to sort, navigating away")
            findNavController().navigateUp()
        }
    }
}
