/*
 * Copyright (c) 2022 Auxio Project
 * ViewHolders.kt is part of Auxio.
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
 
package org.oxycblt.auxio.list.recycler

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.divider.MaterialDivider
import org.oxycblt.auxio.IntegerTable
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.ItemHeaderBinding
import org.oxycblt.auxio.databinding.ItemParentBinding
import org.oxycblt.auxio.databinding.ItemSongBinding
import org.oxycblt.auxio.list.BasicHeader
import org.oxycblt.auxio.list.PlainDivider
import org.oxycblt.auxio.list.SelectableListListener
import org.oxycblt.auxio.list.adapter.SelectionIndicatorAdapter
import org.oxycblt.auxio.list.adapter.SimpleDiffCallback
import org.oxycblt.auxio.music.areNamesTheSame
import org.oxycblt.auxio.music.auto.AutoPlaylist
import org.oxycblt.auxio.music.rating.bindRating
import org.oxycblt.auxio.music.resolve
import org.oxycblt.auxio.music.resolveNames
import org.oxycblt.auxio.util.context
import org.oxycblt.auxio.util.getPlural
import org.oxycblt.auxio.util.inflater
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.Composer
import org.oxycblt.musikr.Genre
import org.oxycblt.musikr.Grouping
import org.oxycblt.musikr.Mood
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song

/**
 * A [RecyclerView.ViewHolder] that displays a [Song]. Use [from] to create an instance.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
class SongViewHolder private constructor(private val binding: ItemSongBinding) :
    SelectionIndicatorAdapter.ViewHolder(binding.root) {
    /**
     * Bind new data to this instance.
     *
     * @param song The new [Song] to bind.
     * @param listener An [SelectableListListener] to bind interactions to.
     */
    fun bind(song: Song, listener: SelectableListListener<Song>) {
        listener.bind(song, this, menuButton = binding.songMenu)
        binding.songAlbumCover.bind(song)
        binding.songName.text = song.name.resolve(binding.context)
        binding.songInfo.text = song.artists.resolveNames(binding.context)
        binding.songRating.bindRating(song)
    }

    override fun updatePlayingIndicator(isActive: Boolean, isPlaying: Boolean) {
        binding.root.isSelected = isActive
        binding.songAlbumCover.setPlaying(isPlaying)
    }

    override fun updateSelectionIndicator(isSelected: Boolean) {
        binding.root.isActivated = isSelected
    }

    companion object {
        /** Unique ID for this ViewHolder type. */
        const val VIEW_TYPE = IntegerTable.VIEW_TYPE_SONG

        /**
         * Create a new instance.
         *
         * @param parent The parent to inflate this instance from.
         * @return A new instance.
         */
        fun from(parent: ViewGroup) =
            SongViewHolder(ItemSongBinding.inflate(parent.context.inflater, parent, false))

        /** A comparator that can be used with DiffUtil. */
        val DIFF_CALLBACK =
            object : SimpleDiffCallback<Song>() {
                override fun areContentsTheSame(oldItem: Song, newItem: Song) =
                    oldItem.name == newItem.name && oldItem.artists.areNamesTheSame(newItem.artists)
            }
    }
}

/**
 * A [RecyclerView.ViewHolder] that displays a [Album]. Use [from] to create an instance.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
class AlbumViewHolder private constructor(private val binding: ItemParentBinding) :
    SelectionIndicatorAdapter.ViewHolder(binding.root) {
    /**
     * Bind new data to this instance.
     *
     * @param album The new [Album] to bind.
     * @param listener An [SelectableListListener] to bind interactions to.
     */
    fun bind(album: Album, listener: SelectableListListener<Album>) {
        listener.bind(album, this, menuButton = binding.parentMenu)
        binding.parentImage.bind(album)
        binding.parentName.text = album.name.resolve(binding.context)
        binding.parentInfo.text = album.artists.resolveNames(binding.context)
    }

    override fun updatePlayingIndicator(isActive: Boolean, isPlaying: Boolean) {
        binding.root.isSelected = isActive
        binding.parentImage.setPlaying(isPlaying)
    }

    override fun updateSelectionIndicator(isSelected: Boolean) {
        binding.root.isActivated = isSelected
    }

    companion object {
        /** Unique ID for this ViewHolder type. */
        const val VIEW_TYPE = IntegerTable.VIEW_TYPE_ALBUM

        /**
         * Create a new instance.
         *
         * @param parent The parent to inflate this instance from.
         * @return A new instance.
         */
        fun from(parent: ViewGroup) =
            AlbumViewHolder(ItemParentBinding.inflate(parent.context.inflater, parent, false))

        /** A comparator that can be used with DiffUtil. */
        val DIFF_CALLBACK =
            object : SimpleDiffCallback<Album>() {
                override fun areContentsTheSame(oldItem: Album, newItem: Album) =
                    oldItem.name == newItem.name &&
                        oldItem.artists.areNamesTheSame(newItem.artists) &&
                        oldItem.releaseType == newItem.releaseType
            }
    }
}

/**
 * A [RecyclerView.ViewHolder] that displays a [Artist]. Use [from] to create an instance.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
class ArtistViewHolder private constructor(private val binding: ItemParentBinding) :
    SelectionIndicatorAdapter.ViewHolder(binding.root) {
    /**
     * Bind new data to this instance.
     *
     * @param artist The new [Artist] to bind.
     * @param listener An [SelectableListListener] to bind interactions to.
     */
    fun bind(artist: Artist, listener: SelectableListListener<Artist>) {
        listener.bind(artist, this, menuButton = binding.parentMenu)
        binding.parentImage.bind(artist)
        binding.parentName.text = artist.name.resolve(binding.context)
        binding.parentInfo.text =
            binding.context.getString(
                R.string.fmt_two,
                if (artist.explicitAlbums.isNotEmpty()) {
                    binding.context.getPlural(R.plurals.fmt_album_count, artist.explicitAlbums.size)
                } else {
                    binding.context.getString(R.string.def_album_count)
                },
                if (artist.songs.isNotEmpty()) {
                    binding.context.getPlural(R.plurals.fmt_song_count, artist.songs.size)
                } else {
                    binding.context.getString(R.string.def_song_count)
                },
            )
    }

    override fun updatePlayingIndicator(isActive: Boolean, isPlaying: Boolean) {
        binding.root.isSelected = isActive
        binding.parentImage.setPlaying(isPlaying)
    }

    override fun updateSelectionIndicator(isSelected: Boolean) {
        binding.root.isActivated = isSelected
    }

    companion object {
        /** Unique ID for this ViewHolder type. */
        const val VIEW_TYPE = IntegerTable.VIEW_TYPE_ARTIST

        /**
         * Create a new instance.
         *
         * @param parent The parent to inflate this instance from.
         * @return A new instance.
         */
        fun from(parent: ViewGroup) =
            ArtistViewHolder(ItemParentBinding.inflate(parent.context.inflater, parent, false))

        /** A comparator that can be used with DiffUtil. */
        val DIFF_CALLBACK =
            object : SimpleDiffCallback<Artist>() {
                override fun areContentsTheSame(oldItem: Artist, newItem: Artist) =
                    oldItem.name == newItem.name &&
                        oldItem.explicitAlbums.size == newItem.explicitAlbums.size &&
                        oldItem.songs.size == newItem.songs.size
            }
    }
}

/**
 * A [RecyclerView.ViewHolder] that displays a [Genre]. Use [from] to create an instance.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
class GenreViewHolder private constructor(private val binding: ItemParentBinding) :
    SelectionIndicatorAdapter.ViewHolder(binding.root) {
    /**
     * Bind new data to this instance.
     *
     * @param genre The new [Genre] to bind.
     * @param listener An [SelectableListListener] to bind interactions to.
     */
    fun bind(genre: Genre, listener: SelectableListListener<Genre>) {
        listener.bind(genre, this, menuButton = binding.parentMenu)
        binding.parentImage.bind(genre)
        binding.parentName.text = genre.name.resolve(binding.context)
        binding.parentInfo.text =
            binding.context.getString(
                R.string.fmt_two,
                binding.context.getPlural(R.plurals.fmt_artist_count, genre.artists.size),
                binding.context.getPlural(R.plurals.fmt_song_count, genre.songs.size),
            )
    }

    override fun updatePlayingIndicator(isActive: Boolean, isPlaying: Boolean) {
        binding.root.isSelected = isActive
        binding.parentImage.setPlaying(isPlaying)
    }

    override fun updateSelectionIndicator(isSelected: Boolean) {
        binding.root.isActivated = isSelected
    }

    companion object {
        /** Unique ID for this ViewHolder type. */
        const val VIEW_TYPE = IntegerTable.VIEW_TYPE_GENRE

        /**
         * Create a new instance.
         *
         * @param parent The parent to inflate this instance from.
         * @return A new instance.
         */
        fun from(parent: ViewGroup) =
            GenreViewHolder(ItemParentBinding.inflate(parent.context.inflater, parent, false))

        /** A comparator that can be used with DiffUtil. */
        val DIFF_CALLBACK =
            object : SimpleDiffCallback<Genre>() {
                override fun areContentsTheSame(oldItem: Genre, newItem: Genre) =
                    oldItem.name == newItem.name &&
                        oldItem.artists.size == newItem.artists.size &&
                        oldItem.songs.size == newItem.songs.size
            }
    }
}

/** A [RecyclerView.ViewHolder] that displays a [Composer]. Use [from] to create an instance. */
class ComposerViewHolder private constructor(private val binding: ItemParentBinding) :
    SelectionIndicatorAdapter.ViewHolder(binding.root) {
    /**
     * Bind new data to this instance.
     *
     * @param composer The new [Composer] to bind.
     * @param listener An [SelectableListListener] to bind interactions to.
     */
    fun bind(composer: Composer, listener: SelectableListListener<Composer>) {
        listener.bind(composer, this, menuButton = binding.parentMenu)
        binding.parentImage.bind(composer)
        binding.parentName.text = composer.name.resolve(binding.context)
        binding.parentInfo.text =
            binding.context.getString(
                R.string.fmt_two,
                binding.context.getPlural(R.plurals.fmt_artist_count, composer.artists.size),
                binding.context.getPlural(R.plurals.fmt_song_count, composer.songs.size),
            )
    }

    override fun updatePlayingIndicator(isActive: Boolean, isPlaying: Boolean) {
        binding.root.isSelected = isActive
        binding.parentImage.setPlaying(isPlaying)
    }

    override fun updateSelectionIndicator(isSelected: Boolean) {
        binding.root.isActivated = isSelected
    }

    companion object {
        /** Unique ID for this ViewHolder type. */
        const val VIEW_TYPE = IntegerTable.VIEW_TYPE_COMPOSER

        /**
         * Create a new instance.
         *
         * @param parent The parent to inflate this instance from.
         * @return A new instance.
         */
        fun from(parent: ViewGroup) =
            ComposerViewHolder(ItemParentBinding.inflate(parent.context.inflater, parent, false))

        /** A comparator that can be used with DiffUtil. */
        val DIFF_CALLBACK =
            object : SimpleDiffCallback<Composer>() {
                override fun areContentsTheSame(oldItem: Composer, newItem: Composer) =
                    oldItem.name == newItem.name &&
                        oldItem.artists.size == newItem.artists.size &&
                        oldItem.songs.size == newItem.songs.size
            }
    }
}

/** A [RecyclerView.ViewHolder] that displays a [Mood]. Use [from] to create an instance. */
class MoodViewHolder private constructor(private val binding: ItemParentBinding) :
    SelectionIndicatorAdapter.ViewHolder(binding.root) {
    /**
     * Bind new data to this instance.
     *
     * @param mood The new [Mood] to bind.
     * @param listener An [SelectableListListener] to bind interactions to.
     */
    fun bind(mood: Mood, listener: SelectableListListener<Mood>) {
        listener.bind(mood, this, menuButton = binding.parentMenu)
        binding.parentImage.bind(mood)
        binding.parentName.text = mood.name.resolve(binding.context)
        binding.parentInfo.text =
            binding.context.getString(
                R.string.fmt_two,
                binding.context.getPlural(R.plurals.fmt_artist_count, mood.artists.size),
                binding.context.getPlural(R.plurals.fmt_song_count, mood.songs.size),
            )
    }

    override fun updatePlayingIndicator(isActive: Boolean, isPlaying: Boolean) {
        binding.root.isSelected = isActive
        binding.parentImage.setPlaying(isPlaying)
    }

    override fun updateSelectionIndicator(isSelected: Boolean) {
        binding.root.isActivated = isSelected
    }

    companion object {
        /** Unique ID for this ViewHolder type. */
        const val VIEW_TYPE = IntegerTable.VIEW_TYPE_MOOD

        /**
         * Create a new instance.
         *
         * @param parent The parent to inflate this instance from.
         * @return A new instance.
         */
        fun from(parent: ViewGroup) =
            MoodViewHolder(ItemParentBinding.inflate(parent.context.inflater, parent, false))

        /** A comparator that can be used with DiffUtil. */
        val DIFF_CALLBACK =
            object : SimpleDiffCallback<Mood>() {
                override fun areContentsTheSame(oldItem: Mood, newItem: Mood) =
                    oldItem.name == newItem.name &&
                        oldItem.artists.size == newItem.artists.size &&
                        oldItem.songs.size == newItem.songs.size
            }
    }
}

/** A [RecyclerView.ViewHolder] that displays a [Grouping]. Use [from] to create an instance. */
class GroupingViewHolder private constructor(private val binding: ItemParentBinding) :
    SelectionIndicatorAdapter.ViewHolder(binding.root) {
    /**
     * Bind new data to this instance.
     *
     * @param grouping The new [Grouping] to bind.
     * @param listener An [SelectableListListener] to bind interactions to.
     */
    fun bind(grouping: Grouping, listener: SelectableListListener<Grouping>) {
        listener.bind(grouping, this, menuButton = binding.parentMenu)
        binding.parentImage.bind(grouping)
        binding.parentName.text = grouping.name.resolve(binding.context)
        binding.parentInfo.text =
            binding.context.getString(
                R.string.fmt_two,
                binding.context.getPlural(R.plurals.fmt_artist_count, grouping.artists.size),
                binding.context.getPlural(R.plurals.fmt_song_count, grouping.songs.size),
            )
    }

    override fun updatePlayingIndicator(isActive: Boolean, isPlaying: Boolean) {
        binding.root.isSelected = isActive
        binding.parentImage.setPlaying(isPlaying)
    }

    override fun updateSelectionIndicator(isSelected: Boolean) {
        binding.root.isActivated = isSelected
    }

    companion object {
        /** Unique ID for this ViewHolder type. */
        const val VIEW_TYPE = IntegerTable.VIEW_TYPE_GROUPING

        /**
         * Create a new instance.
         *
         * @param parent The parent to inflate this instance from.
         * @return A new instance.
         */
        fun from(parent: ViewGroup) =
            GroupingViewHolder(ItemParentBinding.inflate(parent.context.inflater, parent, false))

        /** A comparator that can be used with DiffUtil. */
        val DIFF_CALLBACK =
            object : SimpleDiffCallback<Grouping>() {
                override fun areContentsTheSame(oldItem: Grouping, newItem: Grouping) =
                    oldItem.name == newItem.name &&
                        oldItem.artists.size == newItem.artists.size &&
                        oldItem.songs.size == newItem.songs.size
            }
    }
}

/**
 * A [RecyclerView.ViewHolder] that displays a [Playlist]. Use [from] to create an instance.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
class PlaylistViewHolder private constructor(private val binding: ItemParentBinding) :
    SelectionIndicatorAdapter.ViewHolder(binding.root) {
    /**
     * Called when the drag handle of a selected row is touched. While set, the menu button of a
     * selected row turns into a drag handle. Null if playlists cannot be dragged in this list.
     */
    var onPickUp: ((RecyclerView.ViewHolder) -> Unit)? = null

    /**
     * Bind new data to this instance.
     *
     * @param playlist The new [Playlist] to bind.
     * @param listener An [SelectableListListener] to bind interactions to.
     */
    fun bind(playlist: Playlist, listener: SelectableListListener<Playlist>) =
        bind(playlist, listener, 0)

    /**
     * Bind new data to this instance.
     *
     * @param playlist The new [Playlist] to bind.
     * @param listener An [SelectableListListener] to bind interactions to.
     * @param childCount The number of playlists nested inside [playlist].
     * @param location Where [playlist] is nested, shown instead of its counts. Null to show them.
     * @param isPinned Whether [playlist] is pinned to the top of its level.
     */
    fun bind(
        playlist: Playlist,
        listener: SelectableListListener<Playlist>,
        childCount: Int,
        location: String? = null,
        isPinned: Boolean = false,
    ) {
        listener.bind(playlist, this, menuButton = binding.parentMenu)
        binding.parentImage.bind(playlist)
        binding.parentName.text = playlist.name.resolve(binding.context)
        binding.parentName.compoundDrawablePadding =
            binding.context.resources.getDimensionPixelSize(R.dimen.spacing_tiny)
        binding.parentName.setCompoundDrawablesRelativeWithIntrinsicBounds(
            when {
                playlist is AutoPlaylist -> R.drawable.ic_auto_playlist_16
                isPinned -> R.drawable.ic_pin_16
                else -> 0
            },
            0,
            0,
            0,
        )
        val context = binding.context
        val songs =
            if (playlist.songs.isNotEmpty()) {
                context.getPlural(R.plurals.fmt_song_count, playlist.songs.size)
            } else {
                context.getString(R.string.def_song_count)
            }
        binding.parentInfo.text =
            when {
                location != null -> location
                childCount == 0 -> songs
                playlist.songs.isEmpty() ->
                    context.getPlural(R.plurals.fmt_playlist_count, childCount)
                else ->
                    context.getString(
                        R.string.fmt_playlist_info,
                        context.getPlural(R.plurals.fmt_playlist_count, childCount),
                        songs,
                    )
            }
    }

    override fun updatePlayingIndicator(isActive: Boolean, isPlaying: Boolean) {
        binding.root.isSelected = isActive
        binding.parentImage.setPlaying(isPlaying)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun updateSelectionIndicator(isSelected: Boolean) {
        binding.root.isActivated = isSelected
        val pickUp = onPickUp
        if (isSelected && pickUp != null) {
            // The handle sits where the menu button is, so scrolling never starts a drag.
            binding.parentMenu.setIconResource(R.drawable.ic_handle_24)
            binding.parentMenu.contentDescription =
                binding.context.getString(R.string.desc_playlist_handle)
            binding.parentMenu.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    pickUp(this)
                    true
                } else {
                    false
                }
            }
        } else {
            binding.parentMenu.setIconResource(R.drawable.ic_more_24)
            binding.parentMenu.contentDescription = null
            binding.parentMenu.setOnTouchListener(null)
        }
    }

    companion object {
        /** Unique ID for this ViewHolder type. */
        const val VIEW_TYPE = IntegerTable.VIEW_TYPE_PLAYLIST

        /**
         * Create a new instance.
         *
         * @param parent The parent to inflate this instance from.
         * @return A new instance.
         */
        fun from(parent: ViewGroup) =
            PlaylistViewHolder(ItemParentBinding.inflate(parent.context.inflater, parent, false))

        /** A comparator that can be used with DiffUtil. */
        val DIFF_CALLBACK =
            object : SimpleDiffCallback<Playlist>() {
                override fun areContentsTheSame(oldItem: Playlist, newItem: Playlist) =
                    oldItem.name == newItem.name && oldItem.songs.size == newItem.songs.size
            }
    }
}

/**
 * A [RecyclerView.ViewHolder] that displays a [BasicHeader]. Use [from] to create an instance.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
class BasicHeaderViewHolder private constructor(private val binding: ItemHeaderBinding) :
    RecyclerView.ViewHolder(binding.root) {
    /**
     * Bind new data to this instance.
     *
     * @param basicHeader The new [BasicHeader] to bind.
     */
    fun bind(basicHeader: BasicHeader) {
        binding.title.text = binding.context.getString(basicHeader.titleRes)
    }

    companion object {
        /** Unique ID for this ViewHolder type. */
        const val VIEW_TYPE = IntegerTable.VIEW_TYPE_BASIC_HEADER

        /**
         * Create a new instance.
         *
         * @param parent The parent to inflate this instance from.
         * @return A new instance.
         */
        fun from(parent: ViewGroup) =
            BasicHeaderViewHolder(ItemHeaderBinding.inflate(parent.context.inflater, parent, false))

        /** A comparator that can be used with DiffUtil. */
        val DIFF_CALLBACK =
            object : SimpleDiffCallback<BasicHeader>() {
                override fun areContentsTheSame(oldItem: BasicHeader, newItem: BasicHeader) =
                    oldItem.titleRes == newItem.titleRes
            }
    }
}

/**
 * A [RecyclerView.ViewHolder] that displays a [PlainDivider]. Use [from] to create an instance.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
class DividerViewHolder private constructor(divider: MaterialDivider) :
    RecyclerView.ViewHolder(divider) {

    companion object {
        /** Unique ID for this ViewHolder type. */
        const val VIEW_TYPE = IntegerTable.VIEW_TYPE_DIVIDER

        /**
         * Create a new instance.
         *
         * @param parent The parent to inflate this instance from.
         * @return A new instance.
         */
        fun from(parent: ViewGroup) =
            DividerViewHolder(
                MaterialDivider(parent.context).apply {
                    layoutParams =
                        RecyclerView.LayoutParams(
                            RecyclerView.LayoutParams.MATCH_PARENT,
                            RecyclerView.LayoutParams.WRAP_CONTENT,
                        )
                }
            )

        /** A comparator that can be used with DiffUtil. */
        val DIFF_CALLBACK =
            object : SimpleDiffCallback<PlainDivider>() {
                @SuppressLint("DiffUtilEquals")
                override fun areContentsTheSame(oldItem: PlainDivider, newItem: PlainDivider) =
                    oldItem.anchor == newItem.anchor
            }
    }
}
