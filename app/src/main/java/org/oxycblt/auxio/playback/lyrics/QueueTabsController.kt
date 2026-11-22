/*
 * Copyright (c) 2026 Auxio Project
 * QueueTabsController.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.lyrics

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.R as MR
import org.oxycblt.auxio.databinding.ViewQueueTabsBinding
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.auxio.util.getAttrColorCompat

/**
 * Drives the Queue | Lyrics handle at the top of the queue sheet. Tapping or dragging a half opens
 * the sheet on that page. The Lyrics half is dimmed when the song has no lyrics.
 *
 * @param showPills Whether to show the drag pills (only where the sheet can be dragged).
 * @param onOpen Opens the queue sheet.
 */
class QueueTabsController(
    private val binding: ViewQueueTabsBinding,
    fragment: Fragment,
    private val lyricsModel: LyricsViewModel,
    showPills: Boolean,
    onOpen: () -> Unit,
) {
    init {
        binding.queueTabQueuePill.isVisible = showPills
        binding.queueTabLyricsPill.isVisible = showPills
        setUpHalf(binding.queueTabQueue, LyricsViewModel.TAB_QUEUE, onOpen)
        setUpHalf(binding.queueTabLyrics, LyricsViewModel.TAB_LYRICS, onOpen)
        fragment.collectImmediately(lyricsModel.selectedTab, lyricsModel.lyrics) { tab, lyrics ->
            style(tab, lyrics?.isEmpty == true)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setUpHalf(half: View, tab: Int, onOpen: () -> Unit) {
        half.setOnClickListener {
            lyricsModel.selectTab(tab)
            onOpen()
        }
        // A drag that starts on a half opens the sheet on that half's page.
        half.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                lyricsModel.selectTab(tab)
            }
            false
        }
    }

    private fun style(selectedTab: Int, noLyrics: Boolean) {
        val context = binding.root.context
        val selected = context.getAttrColorCompat(androidx.appcompat.R.attr.colorPrimary)
        val normal = context.getAttrColorCompat(MR.attr.colorOnSurfaceVariant)
        val queueSelected = selectedTab == LyricsViewModel.TAB_QUEUE
        binding.queueTabQueueLabel.setTextColor(if (queueSelected) selected else normal)
        binding.queueTabQueuePill.backgroundTintList = if (queueSelected) selected else normal
        binding.queueTabLyricsLabel.setTextColor(if (queueSelected) normal else selected)
        binding.queueTabLyricsPill.backgroundTintList = if (queueSelected) normal else selected
        binding.queueTabLyrics.alpha = if (noLyrics) DIMMED_ALPHA else 1f
    }

    private companion object {
        const val DIMMED_ALPHA = 0.38f
    }
}
