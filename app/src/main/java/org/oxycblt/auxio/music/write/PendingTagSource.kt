/*
 * Copyright (c) 2026 Auxio Project
 * PendingTagSource.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.write

import org.oxycblt.musikr.Music
import org.oxycblt.musikr.metadata.TagEdit

/** Something that has edits waiting to be written into audio files, such as ratings or moods. */
interface PendingTagSource {
    /** The edits that have not been written yet. */
    suspend fun pending(): List<PendingTag>

    /**
     * Mark these edits as written. An edit that changed again since [pending] stays pending, which
     * the source can tell from [PendingTag.changedMs].
     */
    suspend fun markWritten(written: List<PendingTag>)
}

/**
 * One song's edit waiting to be written.
 *
 * @param uid The song.
 * @param edit What to write.
 * @param changedMs When the edit was made in the app.
 */
data class PendingTag(val uid: Music.UID, val edit: TagEdit, val changedMs: Long)
