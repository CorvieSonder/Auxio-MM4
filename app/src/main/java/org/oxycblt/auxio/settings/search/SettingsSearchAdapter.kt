/*
 * Copyright (c) 2026 Auxio Project
 * SettingsSearchAdapter.kt is part of Auxio.
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
 
package org.oxycblt.auxio.settings.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import org.oxycblt.auxio.databinding.ItemSettingsSearchResultBinding

/**
 * Displays [SettingsSearchEntry] results.
 *
 * @param onClick Called when a result is clicked.
 * @author Alexander Capehart (OxygenCobalt)
 */
class SettingsSearchAdapter(private val onClick: (SettingsSearchEntry) -> Unit) :
    RecyclerView.Adapter<SettingsSearchAdapter.ViewHolder>() {
    private var entries = listOf<SettingsSearchEntry>()

    /**
     * Replace the displayed results.
     *
     * @param newEntries The new results.
     */
    @Suppress("NotifyDataSetChanged")
    fun submit(newEntries: List<SettingsSearchEntry>) {
        entries = newEntries
        notifyDataSetChanged()
    }

    override fun getItemCount() = entries.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(
            ItemSettingsSearchResultBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        holder.binding.apply {
            settingsSearchTitle.text = entry.title
            settingsSearchSummary.text = entry.summary
            settingsSearchSummary.isVisible = entry.summary != null
            settingsSearchPath.text = entry.path
            root.setOnClickListener { onClick(entry) }
        }
    }

    /** A [RecyclerView.ViewHolder] for a single [SettingsSearchEntry]. */
    class ViewHolder(val binding: ItemSettingsSearchResultBinding) :
        RecyclerView.ViewHolder(binding.root)
}
