/*
 * Copyright (c) 2026 Auxio Project
 * RatingSymbol.kt is part of Auxio.
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

import androidx.annotation.DrawableRes
import org.oxycblt.auxio.R

/**
 * The symbol that ratings are shown with.
 *
 * @param text The symbol used in text, such as "4.5★".
 * @param icon A filled icon of one whole symbol.
 * @param outlineIcon An outlined icon of one whole symbol, for songs that are not rated.
 * @param halfIcon An icon drawn twice side by side to show half a symbol, one half colored and the
 *   other grey, or null to cut [icon] in half instead.
 */
enum class RatingSymbol(
    val text: String,
    @DrawableRes val icon: Int,
    @DrawableRes val outlineIcon: Int,
    @DrawableRes val halfIcon: Int?,
) {
    STAR("★", R.drawable.ic_star_24, R.drawable.ic_star_outline_24, null),
    HEART("♥", R.drawable.ic_heart_24, R.drawable.ic_heart_outline_24, null),
    NOTES("♫", R.drawable.ic_notes_24, R.drawable.ic_notes_outline_24, R.drawable.ic_note_half_24),
}
