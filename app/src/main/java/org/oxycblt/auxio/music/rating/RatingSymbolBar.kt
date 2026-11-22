/*
 * Copyright (c) 2026 Auxio Project
 * RatingSymbolBar.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.rating

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.R as MR
import com.google.android.material.color.MaterialColors
import kotlin.math.ceil
import org.oxycblt.auxio.util.unlikelyToBeNull

/**
 * A row of five rating symbols that the user can tap or drag across to pick a rating in half
 * symbols. Unlike [android.widget.RatingBar], half a symbol can be drawn with its own icon, as
 * chosen by [RatingSymbol].
 */
class RatingSymbolBar
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    View(context, attrs, defStyleAttr) {
    private val slotSize = (SLOT_SIZE_DP * resources.displayMetrics.density).toInt()
    private val slotGap = (SLOT_GAP_DP * resources.displayMetrics.density).toInt()
    private val filledColor = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
    private val emptyColor =
        ColorUtils.setAlphaComponent(
            MaterialColors.getColor(this, MR.attr.colorOnSurfaceVariant),
            EMPTY_ALPHA,
        )
    private var filledIcon: Drawable? = null
    private var emptyIcon: Drawable? = null
    private var filledHalfIcon: Drawable? = null
    private var emptyHalfIcon: Drawable? = null

    /** The symbol to show the rating with. */
    var symbol = RatingSymbol.STAR
        set(value) {
            field = value
            loadIcons()
            invalidate()
        }

    /** The picked rating in half symbols, from 0 (not rated) to [Rating.MAX_HALF_STARS]. */
    var halfStars = 0
        set(value) {
            val clamped = value.coerceIn(0, Rating.MAX_HALF_STARS)
            if (field != clamped) {
                field = clamped
                invalidate()
                onChange?.invoke(clamped)
            }
        }

    /** Called with the new value of [halfStars] whenever it changes. */
    var onChange: ((Int) -> Unit)? = null

    init {
        loadIcons()
        isClickable = true
        isFocusable = true
    }

    private fun loadIcons() {
        fun tinted(res: Int, color: Int) =
            unlikelyToBeNull(ContextCompat.getDrawable(context, res)).mutate().apply {
                setTint(color)
            }
        filledIcon = tinted(symbol.icon, filledColor)
        emptyIcon = tinted(symbol.icon, emptyColor)
        filledHalfIcon = symbol.halfIcon?.let { tinted(it, filledColor) }
        emptyHalfIcon = symbol.halfIcon?.let { tinted(it, emptyColor) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = paddingLeft + paddingRight + SLOTS * slotSize + (SLOTS - 1) * slotGap
        val height = paddingTop + paddingBottom + slotSize
        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val filled = unlikelyToBeNull(filledIcon)
        val empty = unlikelyToBeNull(emptyIcon)
        val top = paddingTop
        for (slot in 0 until SLOTS) {
            val left = paddingLeft + slot * (slotSize + slotGap)
            val right = left + slotSize
            val bottom = top + slotSize
            when (halfStars - slot * 2) {
                1 -> {
                    val filledHalf = filledHalfIcon
                    val emptyHalf = emptyHalfIcon
                    val middle = left + slotSize / 2
                    if (filledHalf != null && emptyHalf != null) {
                        // Two half icons side by side, the first one colored.
                        filledHalf.setBounds(left, top, middle, bottom)
                        filledHalf.draw(canvas)
                        emptyHalf.setBounds(middle, top, right, bottom)
                        emptyHalf.draw(canvas)
                    } else {
                        // A grey symbol with its first half colored.
                        empty.setBounds(left, top, right, bottom)
                        empty.draw(canvas)
                        canvas.save()
                        canvas.clipRect(left, top, middle, bottom)
                        filled.setBounds(left, top, right, bottom)
                        filled.draw(canvas)
                        canvas.restore()
                    }
                }
                in 2..Int.MAX_VALUE -> {
                    filled.setBounds(left, top, right, bottom)
                    filled.draw(canvas)
                }
                else -> {
                    empty.setBounds(left, top, right, bottom)
                    empty.draw(canvas)
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                halfStars = halfStarsAt(event.x)
            }
            MotionEvent.ACTION_UP -> {
                halfStars = halfStarsAt(event.x)
                performClick()
            }
        }
        return true
    }

    override fun performClick() = super.performClick()

    /** The rating picked by touching the given horizontal position, rounded up to half a symbol. */
    private fun halfStarsAt(x: Float): Int {
        val slots = (x - paddingLeft) / (slotSize + slotGap)
        return if (slots <= 0f) 0 else ceil(slots * 2).toInt()
    }

    override fun onSaveInstanceState(): Parcelable =
        Bundle().apply {
            putParcelable(KEY_SUPER, super.onSaveInstanceState())
            putInt(KEY_HALF_STARS, halfStars)
        }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            @Suppress("DEPRECATION") super.onRestoreInstanceState(state.getParcelable(KEY_SUPER))
            halfStars = state.getInt(KEY_HALF_STARS)
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    private companion object {
        const val SLOTS = 5
        const val SLOT_SIZE_DP = 40
        const val SLOT_GAP_DP = 6
        const val EMPTY_ALPHA = 0x61
        const val KEY_SUPER = "super"
        const val KEY_HALF_STARS = "half_stars"
    }
}
