/*
 * Copyright (c) 2026 Auxio Project
 * PlaybackOverlayController.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.ui

import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors

/**
 * Controls the overlays laid over the cover in the landscape playback panel: a scrim behind each
 * overlay so its contents stay readable on any cover, and hiding/showing both overlays on a tap.
 *
 * @param top The overlay along the top edge of the cover.
 * @param bottom The overlay along the bottom edge of the cover.
 * @param cornerRadius The corner radius of the cover, so the scrims follow its shape.
 */
class PlaybackOverlayController(
    private val top: View,
    private val bottom: View,
    cornerRadius: Float,
) {
    /** Whether the overlays are currently shown. */
    var isShown = true
        private set

    init {
        val surface = MaterialColors.getColor(top, com.google.android.material.R.attr.colorSurface)
        top.background =
            scrim(surface, GradientDrawable.Orientation.TOP_BOTTOM, TOP_ALPHAS).apply {
                cornerRadii =
                    floatArrayOf(
                        cornerRadius,
                        cornerRadius,
                        cornerRadius,
                        cornerRadius,
                        0f,
                        0f,
                        0f,
                        0f,
                    )
            }
        bottom.background =
            scrim(surface, GradientDrawable.Orientation.BOTTOM_TOP, BOTTOM_ALPHAS).apply {
                cornerRadii =
                    floatArrayOf(
                        0f,
                        0f,
                        0f,
                        0f,
                        cornerRadius,
                        cornerRadius,
                        cornerRadius,
                        cornerRadius,
                    )
            }
    }

    /** Hide the overlays if they are shown, or show them if they are hidden. */
    fun toggle() {
        isShown = !isShown
        fade(top, isShown)
        fade(bottom, isShown)
    }

    private fun scrim(color: Int, orientation: GradientDrawable.Orientation, alphas: IntArray) =
        GradientDrawable(
            orientation,
            alphas.map { ColorUtils.setAlphaComponent(color, it) }.toIntArray(),
        )

    private fun fade(view: View, show: Boolean) {
        view.animate().cancel()
        if (show) {
            view.visibility = View.VISIBLE
            view.animate().alpha(1f).setDuration(FADE_MS).start()
        } else {
            // Invisible views take no touches, so hidden buttons cannot be pressed by accident.
            view
                .animate()
                .alpha(0f)
                .setDuration(FADE_MS)
                .withEndAction { view.visibility = View.INVISIBLE }
                .start()
        }
    }

    private companion object {
        // Alphas from the cover's edge inwards, spread evenly over each overlay.
        val TOP_ALPHAS = intArrayOf(0xE0, 0x70, 0x00)
        // The bottom overlay keeps its full strength up to the title and artist, then fades out
        // above them, so the text stays readable on light covers.
        val BOTTOM_ALPHAS = intArrayOf(0xE8, 0xE8, 0xD0, 0xA0, 0x00)
        const val FADE_MS = 150L
    }
}
