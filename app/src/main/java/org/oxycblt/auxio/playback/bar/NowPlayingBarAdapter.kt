/*
 * Copyright (c) 2026 Auxio Project
 * NowPlayingBarAdapter.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.bar

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.view.MenuItem
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.oxycblt.auxio.databinding.ItemTabBinding
import org.oxycblt.auxio.list.EditClickListListener
import org.oxycblt.auxio.util.inflater

/**
 * A [RecyclerView.Adapter] that displays the actions of the playback panel bar open for
 * configuration.
 *
 * @param listener A [EditClickListListener] for action interactions.
 */
class NowPlayingBarAdapter(private val listener: EditClickListListener<MenuItem>) :
    RecyclerView.Adapter<NowPlayingBarViewHolder>() {
    /** The current actions, each with whether it is shown on the bar. */
    val actions = mutableListOf<Pair<MenuItem, Boolean>>()

    override fun getItemCount() = actions.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        NowPlayingBarViewHolder.from(parent)

    override fun onBindViewHolder(holder: NowPlayingBarViewHolder, position: Int) {
        val (item, visible) = actions[position]
        holder.bind(item, visible, listener)
    }

    /**
     * Replace the actions. This should be used when initializing the list.
     *
     * @param newActions The new actions to show.
     */
    @SuppressLint("NotifyDataSetChanged")
    fun submitActions(newActions: List<Pair<MenuItem, Boolean>>) {
        actions.clear()
        actions.addAll(newActions)
        notifyDataSetChanged()
    }

    /**
     * Show or hide an action on the bar.
     *
     * @param item The menu item of the action.
     */
    fun toggle(item: MenuItem) {
        val index = actions.indexOfFirst { it.first.itemId == item.itemId }
        if (index < 0) return
        actions[index] = item to !actions[index].second
        // Use a payload to avoid an item change animation.
        notifyItemChanged(index, PAYLOAD_ACTION_CHANGED)
    }

    /**
     * Swap two actions with each other.
     *
     * @param a The position of the first action.
     * @param b The position of the second action.
     */
    fun swap(a: Int, b: Int) {
        val tmp = actions[b]
        actions[b] = actions[a]
        actions[a] = tmp
        notifyItemMoved(a, b)
    }

    private companion object {
        val PAYLOAD_ACTION_CHANGED = Any()
    }
}

/** A [RecyclerView.ViewHolder] that displays a bar action. Use [from] to create an instance. */
class NowPlayingBarViewHolder private constructor(private val binding: ItemTabBinding) :
    RecyclerView.ViewHolder(binding.root) {
    /**
     * Bind new data to this instance.
     *
     * @param item The menu item of the action.
     * @param visible Whether the action is shown on the bar.
     * @param listener A [EditClickListListener] to bind interactions to.
     */
    fun bind(item: MenuItem, visible: Boolean, listener: EditClickListListener<MenuItem>) {
        listener.bind(item, this, dragHandle = binding.tabDragHandle)
        binding.tabCheckBox.apply {
            text = item.title
            setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, item.icon, null)
            isChecked = visible
        }
    }

    companion object {
        /**
         * Create a new instance.
         *
         * @param parent The parent to inflate this instance from.
         * @return A new instance.
         */
        fun from(parent: ViewGroup) =
            NowPlayingBarViewHolder(ItemTabBinding.inflate(parent.context.inflater, parent, false))
    }
}

/** An [ItemTouchHelper.Callback] that lets bar actions be dragged into a new order. */
class NowPlayingBarDragCallback(private val adapter: NowPlayingBarAdapter) :
    ItemTouchHelper.Callback() {
    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
    ) = makeFlag(ItemTouchHelper.ACTION_STATE_DRAG, ItemTouchHelper.UP or ItemTouchHelper.DOWN)

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        viewHolder.itemView.translationX = dX
        viewHolder.itemView.translationY = dY
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        viewHolder.itemView.translationX = 0f
        viewHolder.itemView.translationY = 0f
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean {
        adapter.swap(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        throw IllegalStateException()
    }

    // A drag handle is used instead.
    override fun isLongPressDragEnabled() = false
}
