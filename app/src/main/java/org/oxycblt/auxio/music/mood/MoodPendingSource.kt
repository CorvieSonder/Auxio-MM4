/*
 * Copyright (c) 2026 Auxio Project
 * MoodPendingSource.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.mood

import javax.inject.Inject
import org.oxycblt.auxio.music.write.PendingTag
import org.oxycblt.auxio.music.write.PendingTagSource
import org.oxycblt.musikr.metadata.TagEdit

/** Turns moods edited in the app into a TXXX MOOD frame, joined with "; ". */
class MoodPendingSource @Inject constructor(private val moodDao: MoodDao) : PendingTagSource {
    override suspend fun pending() =
        moodDao.getUnwritten().map { row ->
            val moods = row.moods.split(EditedMoods.SEPARATOR).filter { it.isNotEmpty() }
            val value = if (moods.isEmpty()) null else moods.joinToString(FILE_SEPARATOR)
            PendingTag(row.songUid, TagEdit(txxx = mapOf(DESCRIPTION to value)), row.changedMs)
        }

    override suspend fun markWritten(written: List<PendingTag>) {
        for (tag in written) {
            moodDao.markWritten(tag.uid, tag.changedMs)
        }
    }

    private companion object {
        const val DESCRIPTION = "MOOD"
        const val FILE_SEPARATOR = "; "
    }
}
