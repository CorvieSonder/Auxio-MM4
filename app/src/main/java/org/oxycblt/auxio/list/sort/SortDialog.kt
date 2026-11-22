/*
 * Copyright (c) 2023 Auxio Project
 * SortDialog.kt is part of Auxio.
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
 
package org.oxycblt.auxio.list.sort

import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogSortBinding
import org.oxycblt.auxio.list.ClickableListListener
import org.oxycblt.auxio.list.adapter.UpdateInstructions
import org.oxycblt.auxio.music.rating.Rating
import org.oxycblt.auxio.music.rating.Ratings
import org.oxycblt.auxio.music.rating.formatMinimum
import org.oxycblt.auxio.ui.ViewBindingBottomSheetDialogFragment
import org.oxycblt.auxio.util.systemBarInsetsCompat

abstract class SortDialog :
    ViewBindingBottomSheetDialogFragment<DialogSortBinding>(), ClickableListListener<Sort.Mode> {
    private val modeAdapter = SortModeAdapter(@Suppress("LeakingThis") this)

    abstract fun getInitialSort(): Sort?

    abstract fun applyChosenSort(sort: Sort)

    abstract fun getModeChoices(): List<Sort.Mode>

    /** Whether to also offer a minimum rating for the songs shown on detail pages. */
    open val showsRatingFilter = false

    private var initialMinRating: Rating? = null

    override fun onCreateBinding(inflater: LayoutInflater) = DialogSortBinding.inflate(inflater)

    override fun onBindingCreated(binding: DialogSortBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        // --- UI SETUP ---
        binding.root.setOnApplyWindowInsetsListener { v, insets ->
            v.updatePadding(bottom = insets.systemBarInsetsCompat.bottom)
            insets
        }
        binding.sortModeRecycler.adapter = modeAdapter
        binding.sortCancel.setOnClickListener { dismiss() }
        binding.sortSave.setOnClickListener {
            val sort = getCurrentSort()
            if (sort != null && sort != getInitialSort()) {
                applyChosenSort(sort)
            }
            if (showsRatingFilter) {
                Ratings.repository?.detailMinRating = getCurrentMinRating()
            }
            dismiss()
        }
        if (showsRatingFilter) {
            initialMinRating = Ratings.repository?.detailMinRating
            binding.sortRatingHeader.isVisible = true
            binding.sortRatingSlider.apply {
                isVisible = true
                value = (initialMinRating?.stars ?: 0f)
                setLabelFormatter { stars ->
                    Rating.fromHalfStars((stars * 2).roundToInt())?.formatMinimum(context)
                        ?: context.getString(R.string.lbl_any_rating)
                }
                addOnChangeListener { _, _, _ -> updateButtons() }
            }
        }
        binding.sortDirectionAsc.setOnClickListener { updateDirection(Sort.Direction.ASCENDING) }
        binding.sortDirectionDsc.setOnClickListener { updateDirection(Sort.Direction.DESCENDING) }

        // --- STATE SETUP ---
        modeAdapter.update(getModeChoices(), UpdateInstructions.Diff)

        val initial = getInitialSort()
        if (initial != null) {
            modeAdapter.setSelected(initial.mode)
            updateDirection(initial.direction)
        }
        updateButtons()
    }

    override fun onClick(item: Sort.Mode, viewHolder: RecyclerView.ViewHolder) {
        modeAdapter.setSelected(item)
        updateButtons()
    }

    private fun updateDirection(direction: Sort.Direction) {
        val binding = requireBinding()
        binding.sortDirectionAsc.isChecked = direction == Sort.Direction.ASCENDING
        binding.sortDirectionDsc.isChecked = direction == Sort.Direction.DESCENDING
        updateButtons()
    }

    private fun updateButtons() {
        val binding = requireBinding()
        val sortChanged = getCurrentSort().let { it != null && it != getInitialSort() }
        val ratingChanged = showsRatingFilter && getCurrentMinRating() != initialMinRating
        binding.sortSave.isEnabled = sortChanged || ratingChanged
    }

    private fun getCurrentMinRating() =
        Rating.fromHalfStars((requireBinding().sortRatingSlider.value * 2).roundToInt())

    private fun getCurrentSort(): Sort? {
        val binding = requireBinding()
        val mode = modeAdapter.currentMode ?: return null
        val direction =
            if (binding.sortDirectionAsc.isChecked) Sort.Direction.ASCENDING
            else Sort.Direction.DESCENDING
        return Sort(mode, direction)
    }
}
