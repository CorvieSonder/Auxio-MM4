/*
 * Copyright (c) 2026 Auxio Project
 * FolderTransformation.kt is part of Auxio.
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
 
package org.oxycblt.auxio.image.coil

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.annotation.ColorInt
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import coil3.size.Size
import coil3.transform.Transformation

/**
 * Draws a cover inside a folder shape: a folder with a tab on its upper left, with the cover laid
 * over the folder's front. Used for playlists that hold other playlists.
 *
 * @param folderColor The color of the folder.
 */
class FolderTransformation(@ColorInt private val folderColor: Int) : Transformation() {
    override val cacheKey = "${FolderTransformation::class.java.name}-$folderColor"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width.toFloat()
        val h = input.height.toFloat()
        val unit = minOf(w, h)
        val radius = unit * CORNER_RATIO
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = folderColor }

        // The tab across the top left, then the folder's body below it.
        val tabBottom = unit * TAB_HEIGHT_RATIO
        val tab = RectF(0f, 0f, w * TAB_WIDTH_RATIO, tabBottom + radius)
        val body = RectF(0f, tabBottom, w, h)

        // The cover sits on the front of the folder, inset so the folder shows around it.
        val inset = unit * INSET_RATIO
        val front = RectF(inset, tabBottom + inset, w - inset, h - inset)
        val frontPath =
            Path().apply { addRoundRect(front, radius / 2, radius / 2, Path.Direction.CW) }

        return createBitmap(input.width, input.height, input.config ?: Bitmap.Config.ARGB_8888)
            .applyCanvas {
                drawRoundRect(tab, radius, radius, paint)
                drawRoundRect(body, radius, radius, paint)
                save()
                clipPath(frontPath)
                drawBitmap(input, null, front, Paint(Paint.FILTER_BITMAP_FLAG))
                restore()
            }
    }

    private companion object {
        const val TAB_WIDTH_RATIO = 0.45f
        const val TAB_HEIGHT_RATIO = 0.12f
        const val INSET_RATIO = 0.07f
        const val CORNER_RATIO = 0.08f
    }
}
