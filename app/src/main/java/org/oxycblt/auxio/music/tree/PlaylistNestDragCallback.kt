/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistNestDragCallback.kt is part of Auxio.
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

import android.graphics.Canvas
import androidx.core.view.children
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.oxycblt.auxio.R
import org.oxycblt.auxio.list.recycler.PlaylistViewHolder
import org.oxycblt.auxio.util.showToast
import org.oxycblt.musikr.Playlist
import timber.log.Timber as L

/**
 * Lets selected playlists be dragged by their handle onto another playlist row, which moves them
 * inside that playlist. Rows are never reordered.
 *
 * @param playlistTreeRepository The [PlaylistTreeRepository] to move the playlists with.
 * @param selectedPlaylists Returns the currently selected [Playlist]s, which are the ones moved.
 * @param playlistAt Returns the [Playlist] shown at an adapter position, or null if there is none.
 * @param onMoved Called after the playlists were moved into a playlist.
 */
class PlaylistNestDragCallback(
    private val playlistTreeRepository: PlaylistTreeRepository,
    private val selectedPlaylists: () -> List<Playlist>,
    private val playlistAt: (Int) -> Playlist?,
    private val onMoved: () -> Unit,
) : ItemTouchHelper.Callback() {
    private var target: RecyclerView.ViewHolder? = null

    override fun isLongPressDragEnabled() = false

    override fun isItemViewSwipeEnabled() = false

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
    ) =
        if (viewHolder is PlaylistViewHolder) {
            makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
        } else {
            0
        }

    // Targets are found in onChildDraw instead, so that rows never swap places.
    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ) = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        if (actionState != ItemTouchHelper.ACTION_STATE_DRAG || !isCurrentlyActive) {
            return
        }
        // Find the row under the middle of the dragged row, ignoring the dragged row itself.
        val dragged = viewHolder.itemView
        val centerY = dragged.top + dY + dragged.height / 2f
        val newTarget =
            recyclerView.children
                .filter { it != dragged && centerY >= it.top && centerY <= it.bottom }
                .map { recyclerView.getChildViewHolder(it) }
                .firstOrNull { canDropInto(it) }
        setTarget(newTarget)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        val dropped = target?.let { playlistAt(it.bindingAdapterPosition) }
        setTarget(null)
        if (dropped == null) {
            return
        }
        val playlists = selectedPlaylists()
        L.d("Dropped ${playlists.size} playlists into $dropped")
        if (playlistTreeRepository.hasNameClash(playlists, dropped)) {
            recyclerView.context.showToast(R.string.lng_playlist_name_taken)
            return
        }
        playlistTreeRepository.move(playlists, dropped)
        onMoved()
    }

    private fun canDropInto(holder: RecyclerView.ViewHolder): Boolean {
        if (holder !is PlaylistViewHolder) {
            return false
        }
        val into = playlistAt(holder.bindingAdapterPosition) ?: return false
        val playlists = selectedPlaylists()
        return playlists.isNotEmpty() &&
            playlists.all { it.uid != into.uid && playlistTreeRepository.canMove(it, into) }
    }

    private fun setTarget(newTarget: RecyclerView.ViewHolder?) {
        if (newTarget == target) {
            return
        }
        // Highlight the row that the playlists would be moved into.
        target?.itemView?.isActivated = false
        newTarget?.itemView?.isActivated = true
        target = newTarget
    }
}
