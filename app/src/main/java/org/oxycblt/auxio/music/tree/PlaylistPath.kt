/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistPath.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.tree

import android.content.Context
import org.oxycblt.auxio.R
import org.oxycblt.auxio.music.resolve
import org.oxycblt.musikr.Playlist

/**
 * Describe where a nested playlist is, such as "In: A › B".
 *
 * @param path The playlists above the playlist, from the top level down, as given by
 *   [PlaylistTreeRepository.pathOf].
 * @return The description, or null if [path] is empty and the playlist is at the top level.
 */
fun Context.describePlaylistPath(path: List<Playlist>): String? {
    if (path.isEmpty()) {
        return null
    }
    return getString(
        R.string.fmt_playlist_parents,
        path.joinToString(getString(R.string.fmt_path_separator)) { it.name.resolve(this) },
    )
}
