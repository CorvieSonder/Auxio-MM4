/*
 * Copyright (c) 2026 Auxio Project
 * TreeChoiceAdapter.kt is part of Auxio.
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

import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.ItemTreeChoiceBinding
import org.oxycblt.auxio.list.adapter.FlexibleListAdapter
import org.oxycblt.auxio.list.adapter.SimpleDiffCallback
import org.oxycblt.auxio.music.resolve
import org.oxycblt.auxio.util.context
import org.oxycblt.auxio.util.inflater
import org.oxycblt.musikr.Playlist

/** A row in a playlist picker that shows one level of the playlist tree at a time. */
sealed interface TreeChoice {
    /**
     * Goes back to the level above.
     *
     * @param level The [Playlist] whose children are currently shown.
     */
    data class Up(val level: Playlist) : TreeChoice

    /** Chooses the top level itself, outside of any playlist. */
    data object TopLevel : TreeChoice

    /**
     * Chooses a [Playlist].
     *
     * @param playlist The [Playlist] represented.
     * @param canOpen Whether the [Playlist] has children that can be shown.
     * @param isActive Whether the [Playlist] is highlighted, such as when the songs being added are
     *   already in it.
     */
    data class Node(val playlist: Playlist, val canOpen: Boolean, val isActive: Boolean) :
        TreeChoice
}

/**
 * An adapter for [TreeChoice]s.
 *
 * @param listener A [Listener] to bind interactions to.
 */
class TreeChoiceAdapter(private val listener: Listener) :
    FlexibleListAdapter<TreeChoice, TreeChoiceViewHolder>(TreeChoiceViewHolder.DIFF_CALLBACK) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        TreeChoiceViewHolder.from(parent)

    override fun onBindViewHolder(holder: TreeChoiceViewHolder, position: Int) {
        holder.bind(getItem(position), listener)
    }

    /** Listener for [TreeChoice] interactions. */
    interface Listener {
        /**
         * Called when a row is tapped.
         *
         * @param choice The [TreeChoice] that was tapped.
         */
        fun onChoose(choice: TreeChoice)

        /**
         * Called when the children of a [Playlist] should be shown.
         *
         * @param playlist The [Playlist] to show the children of.
         */
        fun onOpen(playlist: Playlist)
    }
}

class TreeChoiceViewHolder private constructor(private val binding: ItemTreeChoiceBinding) :
    RecyclerView.ViewHolder(binding.root) {
    fun bind(choice: TreeChoice, listener: TreeChoiceAdapter.Listener) {
        binding.root.setOnClickListener { listener.onChoose(choice) }
        when (choice) {
            is TreeChoice.Up -> {
                showIcon(R.drawable.ic_back_24)
                binding.treeName.text = choice.level.name.resolve(binding.context)
                binding.treeOpen.isInvisible = true
            }
            is TreeChoice.TopLevel -> {
                showIcon(R.drawable.ic_playlist_24)
                binding.treeName.text = binding.context.getString(R.string.lbl_top_level)
                binding.treeOpen.isInvisible = true
            }
            is TreeChoice.Node -> {
                binding.treeIcon.isVisible = false
                binding.treeCover.isInvisible = false
                binding.treeCover.bind(choice.playlist)
                binding.treeCover.isActivated = choice.isActive
                binding.treeName.text = choice.playlist.name.resolve(binding.context)
                binding.treeOpen.isInvisible = !choice.canOpen
                binding.treeOpen.setOnClickListener { listener.onOpen(choice.playlist) }
            }
        }
    }

    private fun showIcon(iconRes: Int) {
        binding.treeCover.isInvisible = true
        binding.treeIcon.isVisible = true
        binding.treeIcon.setImageResource(iconRes)
        binding.treeOpen.setOnClickListener(null)
    }

    companion object {
        /**
         * Create a new instance.
         *
         * @param parent The parent to inflate this instance from.
         * @return A new instance.
         */
        fun from(parent: ViewGroup) =
            TreeChoiceViewHolder(
                ItemTreeChoiceBinding.inflate(parent.context.inflater, parent, false)
            )

        /** A comparator that can be used with DiffUtil. */
        val DIFF_CALLBACK =
            object : SimpleDiffCallback<TreeChoice>() {
                override fun areContentsTheSame(oldItem: TreeChoice, newItem: TreeChoice) =
                    when {
                        oldItem is TreeChoice.Node && newItem is TreeChoice.Node ->
                            oldItem.playlist.name == newItem.playlist.name &&
                                oldItem.canOpen == newItem.canOpen &&
                                oldItem.isActive == newItem.isActive
                        else -> oldItem == newItem
                    }
            }
    }
}
