/*
 * Copyright (c) 2026 Auxio Project
 * PreferenceHighlighter.kt is part of Auxio.
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
 
package org.oxycblt.auxio.settings.search

import android.animation.ValueAnimator
import android.graphics.drawable.ColorDrawable
import androidx.core.animation.doOnEnd
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors

/**
 * Scrolls to a preference and briefly flashes its row so it can be found on the screen.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
object PreferenceHighlighter {
    private const val START_DELAY = 400L
    private const val FADE_DURATION = 1200L
    private const val MAX_ALPHA = 0.3f

    /**
     * Highlight a preference.
     *
     * @param listView The preference list.
     * @param key The key of the preference to highlight.
     */
    fun highlight(listView: RecyclerView, key: String) {
        val callback = listView.adapter as? PreferenceGroup.PreferencePositionCallback ?: return
        val position = callback.getPreferenceAdapterPosition(key)
        if (position == RecyclerView.NO_POSITION) {
            return
        }
        listView.scrollToPosition(position)
        // Wait for the screen transition and scroll to finish before flashing the row.
        listView.postDelayed(
            {
                val view =
                    listView.findViewHolderForAdapterPosition(position)?.itemView
                        ?: return@postDelayed
                val color = MaterialColors.getColor(view, androidx.appcompat.R.attr.colorPrimary)
                val overlay = ColorDrawable(ColorUtils.setAlphaComponent(color, 0))
                val previous = view.foreground
                view.foreground = overlay
                ValueAnimator.ofFloat(MAX_ALPHA, MAX_ALPHA, 0f).apply {
                    duration = FADE_DURATION
                    addUpdateListener {
                        overlay.color =
                            ColorUtils.setAlphaComponent(
                                color,
                                ((it.animatedValue as Float) * 255).toInt(),
                            )
                    }
                    doOnEnd { view.foreground = previous }
                    start()
                }
            },
            START_DELAY,
        )
    }
}
