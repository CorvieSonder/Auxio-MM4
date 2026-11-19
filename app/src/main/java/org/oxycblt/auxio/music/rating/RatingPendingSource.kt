/*
 * Copyright (c) 2026 Auxio Project
 * RatingPendingSource.kt is part of Auxio.
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

import javax.inject.Inject
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.music.write.PendingTag
import org.oxycblt.auxio.music.write.PendingTagSource
import org.oxycblt.musikr.metadata.TagEdit

/**
 * Turns ratings given in the app into POPM frames. The preferred program's frame is always written;
 * frames of the other known programs are updated only when the file already has them. Clearing a
 * rating removes the frames of every known program.
 */
class RatingPendingSource
@Inject
constructor(
    private val ratingDao: RatingDao,
    private val ratingSettings: RatingSettings,
    private val musicRepository: MusicRepository,
) : PendingTagSource {
    override suspend fun pending(): List<PendingTag> {
        val library = musicRepository.library ?: return emptyList()
        val preferred = ratingSettings.preferredSource
        return ratingDao.getUnwritten().mapNotNull { rated ->
            val song = library.findSong(rated.songUid) ?: return@mapNotNull null
            val value = popularimeterOf(rated.halfStars)
            val popm =
                RatingSource.entries
                    .filter { it == preferred || song.extras.containsKey(it.key) || value == null }
                    .associate { EMAILS.getValue(it) to value }
            PendingTag(rated.songUid, TagEdit(popm = popm), rated.changedMs)
        }
    }

    override suspend fun markWritten(written: List<PendingTag>) {
        for (tag in written) {
            ratingDao.markWritten(tag.uid, tag.changedMs)
        }
    }

    private companion object {
        val EMAILS =
            mapOf(
                RatingSource.MEDIAMONKEY to "no@email",
                RatingSource.MUSICBEE to "MusicBee",
                RatingSource.WINDOWS_MEDIA_PLAYER to "Windows Media Player 9 Series",
            )

        /** MediaMonkey 4.1 values by half stars; MusicBee writes the same. */
        val VALUES = intArrayOf(13, 1, 54, 64, 118, 128, 186, 196, 242, 255)

        /** The POPM byte for an amount of half stars, or null to remove the frame. */
        fun popularimeterOf(halfStars: Int) =
            if (halfStars > 0) VALUES[halfStars.coerceAtMost(VALUES.size) - 1] else null
    }
}
