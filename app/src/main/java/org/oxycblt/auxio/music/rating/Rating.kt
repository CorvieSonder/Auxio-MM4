/*
 * Copyright (c) 2026 Auxio Project
 * Rating.kt is part of Auxio.
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

import android.widget.TextView
import androidx.core.view.isVisible
import java.text.DecimalFormat
import org.oxycblt.auxio.R
import org.oxycblt.musikr.Song

/**
 * A song rating, in half stars.
 *
 * @param halfStars The amount of half stars, from 1 (half a star) to 10 (five stars).
 */
@JvmInline
value class Rating(val halfStars: Int) : Comparable<Rating> {
    /** The amount of stars, from 0.5 to 5. */
    val stars: Float
        get() = halfStars / 2f

    override fun compareTo(other: Rating) = halfStars.compareTo(other.halfStars)

    companion object {
        /** The highest amount of half stars a [Rating] can have. */
        const val MAX_HALF_STARS = 10

        /**
         * Create a [Rating] from an amount of half stars.
         *
         * @param halfStars The amount of half stars. Will be clamped to [MAX_HALF_STARS].
         * @return A [Rating], or null if the amount signifies no rating.
         */
        fun fromHalfStars(halfStars: Int) =
            if (halfStars > 0) Rating(halfStars.coerceAtMost(MAX_HALF_STARS)) else null

        /**
         * Create a [Rating] from the rating byte of an ID3v2 popularimeter frame. Taggers write
         * different values for the same amount of stars, so each amount is a range that covers the
         * values of the common ones (MediaMonkey, MusicBee, Windows Media Player).
         *
         * @param value The rating byte, from 0 to 255.
         * @return A [Rating], or null if the byte signifies no rating.
         */
        fun fromPopularimeter(value: Int) =
            when {
                value <= 0 -> null
                // Windows Media Player writes one star as 1.
                value == 1 -> Rating(2)
                value < 23 -> Rating(1)
                value < 32 -> Rating(2)
                value < 64 -> Rating(3)
                value < 96 -> Rating(4)
                value < 128 -> Rating(5)
                value < 160 -> Rating(6)
                value < 196 -> Rating(7)
                value < 224 -> Rating(8)
                value < 255 -> Rating(9)
                else -> Rating(10)
            }

        /**
         * Create a [Rating] from a fractional rating, as used by the FMPS_RATING field.
         *
         * @param value The rating, from 0.0 to 1.0.
         * @return A [Rating], or null if the value signifies no rating.
         */
        fun fromFraction(value: Float) =
            fromHalfStars((value * MAX_HALF_STARS).toInt().coerceAtLeast(if (value > 0) 1 else 0))

        /**
         * Create a [Rating] from a text rating, such as "4.5/5". A bare number is read as out of 5
         * if it is at most 5, out of 10 if it is at most 10, and out of 100 otherwise.
         *
         * @param value The text rating.
         * @return A [Rating] rounded to the nearest half star, or null if the text is not a rating
         *   or signifies no rating.
         */
        fun fromText(value: String): Rating? {
            val parts = value.split('/')
            val amount = parts[0].trim().replace(',', '.').toFloatOrNull() ?: return null
            val scale =
                when {
                    parts.size == 2 -> parts[1].trim().replace(',', '.').toFloatOrNull()
                    parts.size > 2 -> null
                    amount <= 5f -> 5f
                    amount <= 10f -> 10f
                    else -> 100f
                }
            if (scale == null || scale <= 0f || amount <= 0f) {
                return null
            }
            val halfStars = Math.round(amount / scale * MAX_HALF_STARS).coerceAtLeast(1)
            return fromHalfStars(halfStars)
        }
    }
}

/**
 * Format this [Rating] in a compact form, such as "4.5★".
 *
 * @param context [android.content.Context] required to obtain the format string.
 */
fun Rating.format(context: android.content.Context) =
    context.getString(R.string.fmt_rating, DecimalFormat("0.#").format(stars.toDouble()))

/**
 * Format this [Rating] as a minimum, such as "≥4.5★".
 *
 * @param context [android.content.Context] required to obtain the format string.
 */
fun Rating.formatMinimum(context: android.content.Context) =
    context.getString(R.string.fmt_min_rating, DecimalFormat("0.#").format(stars.toDouble()))

/**
 * Get a list header title, followed by the minimum rating its songs are filtered by, if any.
 *
 * @param context [android.content.Context] required to obtain the strings.
 * @param titleRes The header title.
 * @param minRating The minimum [Rating] of the songs under the header, or null if they are not
 *   filtered.
 */
fun ratingFilteredTitle(
    context: android.content.Context,
    @androidx.annotation.StringRes titleRes: Int,
    minRating: Rating?,
): String {
    val title = context.getString(titleRes)
    return if (minRating != null) {
        context.getString(R.string.fmt_header_min_rating, title, minRating.formatMinimum(context))
    } else {
        title
    }
}

/**
 * Makes the current [RatingRepository] available to code that has no way to have it injected, such
 * as sort modes and list items.
 */
object Ratings {
    @Volatile var repository: RatingRepository? = null
}

/** The current [Rating] of this [Song], or null if it is not rated. */
val Song.rating: Rating?
    get() = Ratings.repository?.ratingOf(this)

/**
 * Show the [Rating] of a [Song] in this view, or hide it if the song is not rated or ratings are
 * not to be shown in lists.
 *
 * @param song The [Song] to show the [Rating] of.
 */
fun TextView.bindRating(song: Song) {
    val repository = Ratings.repository
    val rating = if (repository?.showInLists == true) repository.ratingOf(song) else null
    isVisible = rating != null
    text = rating?.format(context)
}
