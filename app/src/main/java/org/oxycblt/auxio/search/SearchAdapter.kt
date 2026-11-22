/*
 * Copyright (c) 2021 Auxio Project
 * SearchAdapter.kt is part of Auxio.
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
 
package org.oxycblt.auxio.search

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.oxycblt.auxio.list.BasicHeader
import org.oxycblt.auxio.list.Item
import org.oxycblt.auxio.list.PlainDivider
import org.oxycblt.auxio.list.SelectableListListener
import org.oxycblt.auxio.list.adapter.SelectionIndicatorAdapter
import org.oxycblt.auxio.list.adapter.SimpleDiffCallback
import org.oxycblt.auxio.list.recycler.AlbumViewHolder
import org.oxycblt.auxio.list.recycler.ArtistViewHolder
import org.oxycblt.auxio.list.recycler.BasicHeaderViewHolder
import org.oxycblt.auxio.list.recycler.ComposerViewHolder
import org.oxycblt.auxio.list.recycler.CustomValueViewHolder
import org.oxycblt.auxio.list.recycler.DividerViewHolder
import org.oxycblt.auxio.list.recycler.GenreViewHolder
import org.oxycblt.auxio.list.recycler.GroupingViewHolder
import org.oxycblt.auxio.list.recycler.MoodViewHolder
import org.oxycblt.auxio.list.recycler.PlaylistViewHolder
import org.oxycblt.auxio.list.recycler.SongViewHolder
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.Composer
import org.oxycblt.musikr.CustomValue
import org.oxycblt.musikr.Genre
import org.oxycblt.musikr.Grouping
import org.oxycblt.musikr.Mood
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song

/**
 * An adapter that displays search results.
 *
 * @param listener An [SelectableListListener] to bind interactions to.
 * @author Alexander Capehart (OxygenCobalt)
 */
class SearchAdapter(
    private val listener: SelectableListListener<Music>,
    private val describeLocation: (Playlist) -> String? = { null },
) : SelectionIndicatorAdapter<Item, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    override fun getItemViewType(position: Int) =
        when (getItem(position)) {
            is Song -> SongViewHolder.VIEW_TYPE
            is Album -> AlbumViewHolder.VIEW_TYPE
            is Artist -> ArtistViewHolder.VIEW_TYPE
            is Genre -> GenreViewHolder.VIEW_TYPE
            is Composer -> ComposerViewHolder.VIEW_TYPE
            is Mood -> MoodViewHolder.VIEW_TYPE
            is Grouping -> GroupingViewHolder.VIEW_TYPE
            is CustomValue -> CustomValueViewHolder.VIEW_TYPE
            is Playlist -> PlaylistViewHolder.VIEW_TYPE
            is PlainDivider -> DividerViewHolder.VIEW_TYPE
            is BasicHeader -> BasicHeaderViewHolder.VIEW_TYPE
            else -> super.getItemViewType(position)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        when (viewType) {
            SongViewHolder.VIEW_TYPE -> SongViewHolder.from(parent)
            AlbumViewHolder.VIEW_TYPE -> AlbumViewHolder.from(parent)
            ArtistViewHolder.VIEW_TYPE -> ArtistViewHolder.from(parent)
            GenreViewHolder.VIEW_TYPE -> GenreViewHolder.from(parent)
            ComposerViewHolder.VIEW_TYPE -> ComposerViewHolder.from(parent)
            MoodViewHolder.VIEW_TYPE -> MoodViewHolder.from(parent)
            GroupingViewHolder.VIEW_TYPE -> GroupingViewHolder.from(parent)
            CustomValueViewHolder.VIEW_TYPE -> CustomValueViewHolder.from(parent)
            PlaylistViewHolder.VIEW_TYPE -> PlaylistViewHolder.from(parent)
            DividerViewHolder.VIEW_TYPE -> DividerViewHolder.from(parent)
            BasicHeaderViewHolder.VIEW_TYPE -> BasicHeaderViewHolder.from(parent)
            else -> error("Invalid item type $viewType")
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is Song -> (holder as SongViewHolder).bind(item, listener)
            is Album -> (holder as AlbumViewHolder).bind(item, listener)
            is Artist -> (holder as ArtistViewHolder).bind(item, listener)
            is Genre -> (holder as GenreViewHolder).bind(item, listener)
            is Composer -> (holder as ComposerViewHolder).bind(item, listener)
            is Mood -> (holder as MoodViewHolder).bind(item, listener)
            is Grouping -> (holder as GroupingViewHolder).bind(item, listener)
            is CustomValue -> (holder as CustomValueViewHolder).bind(item, listener)
            // Nested playlists show where they are, so ones with the same name can be told apart.
            is Playlist ->
                (holder as PlaylistViewHolder).bind(item, listener, 0, describeLocation(item))
            is BasicHeader -> (holder as BasicHeaderViewHolder).bind(item)
        }
    }

    private companion object {
        /** A comparator that can be used with DiffUtil. */
        val DIFF_CALLBACK =
            object : SimpleDiffCallback<Item>() {
                override fun areContentsTheSame(oldItem: Item, newItem: Item) =
                    when (oldItem) {
                        is Song if newItem is Song ->
                            SongViewHolder.DIFF_CALLBACK.areContentsTheSame(oldItem, newItem)

                        is Album if newItem is Album ->
                            AlbumViewHolder.DIFF_CALLBACK.areContentsTheSame(oldItem, newItem)

                        is Artist if newItem is Artist ->
                            ArtistViewHolder.DIFF_CALLBACK.areContentsTheSame(oldItem, newItem)

                        is Genre if newItem is Genre ->
                            GenreViewHolder.DIFF_CALLBACK.areContentsTheSame(oldItem, newItem)
                        is Composer if newItem is Composer ->
                            ComposerViewHolder.DIFF_CALLBACK.areContentsTheSame(oldItem, newItem)
                        is Mood if newItem is Mood ->
                            MoodViewHolder.DIFF_CALLBACK.areContentsTheSame(oldItem, newItem)
                        is Grouping if newItem is Grouping ->
                            GroupingViewHolder.DIFF_CALLBACK.areContentsTheSame(oldItem, newItem)
                        is CustomValue if newItem is CustomValue ->
                            CustomValueViewHolder.DIFF_CALLBACK.areContentsTheSame(oldItem, newItem)

                        is Playlist if newItem is Playlist ->
                            PlaylistViewHolder.DIFF_CALLBACK.areContentsTheSame(oldItem, newItem)

                        is PlainDivider if newItem is PlainDivider ->
                            DividerViewHolder.DIFF_CALLBACK.areContentsTheSame(oldItem, newItem)

                        is BasicHeader if newItem is BasicHeader ->
                            BasicHeaderViewHolder.DIFF_CALLBACK.areContentsTheSame(oldItem, newItem)

                        else -> false
                    }
            }
    }
}
