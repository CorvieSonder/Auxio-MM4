/*
 * Copyright (c) 2023 Auxio Project
 * Sort.kt is part of Auxio.
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
 
package org.oxycblt.auxio.list.sort

import android.content.Context
import org.oxycblt.auxio.IntegerTable
import org.oxycblt.auxio.R
import org.oxycblt.auxio.music.rating.rating
import org.oxycblt.auxio.music.tags.CustomTag
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.Composer
import org.oxycblt.musikr.CustomValue
import org.oxycblt.musikr.Genre
import org.oxycblt.musikr.Grouping
import org.oxycblt.musikr.Mood
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song

/**
 * A sorting method.
 *
 * This can be used not only to sort items, but also represent a sorting mode within the UI.
 *
 * @param mode A [Mode] dictating how to sort the list.
 * @param direction The [Direction] to sort in.
 * @author Alexander Capehart (OxygenCobalt)
 */
data class Sort(val mode: Mode, val direction: Direction) {
    /**
     * Sort a list of [Song]s.
     *
     * @param songs The list of [Song]s.
     * @return A new list of [Song]s sorted by this [Sort]'s configuration.
     */
    fun songs(songs: Collection<Song>): List<Song> {
        val mutable = songs.toMutableList()
        mode.sortSongs(mutable, direction)
        return mutable
    }

    /**
     * Sort a list of [Album]s.
     *
     * @param albums The list of [Album]s.
     * @return A new list of [Album]s sorted by this [Sort]'s configuration.
     */
    fun albums(albums: Collection<Album>): List<Album> {
        val mutable = albums.toMutableList()
        mode.sortAlbums(mutable, direction)
        return mutable
    }

    /**
     * Sort a list of [Artist]s.
     *
     * @param artists The list of [Artist]s.
     * @return A new list of [Artist]s sorted by this [Sort]'s configuration.
     */
    fun artists(artists: Collection<Artist>): List<Artist> {
        val mutable = artists.toMutableList()
        mode.sortArtists(mutable, direction)
        return mutable
    }

    /**
     * Sort a list of [Genre]s.
     *
     * @param genres The list of [Genre]s.
     * @return A new list of [Genre]s sorted by this [Sort]'s configuration.
     */
    fun genres(genres: Collection<Genre>): List<Genre> {
        val mutable = genres.toMutableList()
        mode.sortGenres(mutable, direction)
        return mutable
    }

    /**
     * Sort a list of [Composer]s.
     *
     * @param composers The list of [Composer]s.
     * @return A new list of [Composer]s sorted by this [Sort]'s configuration.
     */
    fun composers(composers: Collection<Composer>): List<Composer> {
        val mutable = composers.toMutableList()
        mode.sortComposers(mutable, direction)
        return mutable
    }

    /**
     * Sort a list of [Mood]s.
     *
     * @param moods The list of [Mood]s.
     * @return A new list of [Mood]s sorted by this [Sort]'s configuration.
     */
    fun moods(moods: Collection<Mood>): List<Mood> {
        val mutable = moods.toMutableList()
        mode.sortMoods(mutable, direction)
        return mutable
    }

    /**
     * Sort a list of [Grouping]s.
     *
     * @param groupings The list of [Grouping]s.
     * @return A new list of [Grouping]s sorted by this [Sort]'s configuration.
     */
    fun groupings(groupings: Collection<Grouping>): List<Grouping> {
        val mutable = groupings.toMutableList()
        mode.sortGroupings(mutable, direction)
        return mutable
    }

    /**
     * Sort a list of [CustomValue]s.
     *
     * @param custom values The list of [CustomValue]s.
     * @return A new list of [CustomValue]s sorted by this [Sort]'s configuration.
     */
    fun customValues(customValues: Collection<CustomValue>): List<CustomValue> {
        val mutable = customValues.toMutableList()
        mode.sortCustomValues(mutable, direction)
        return mutable
    }

    /**
     * Sort a list of [Playlist]s.
     *
     * @param playlists The list of [Playlist]s.
     * @return A new list of [Playlist]s sorted by this [Sort]'s configuration
     */
    fun playlists(playlists: Collection<Playlist>): List<Playlist> {
        val mutable = playlists.toMutableList()
        mode.sortPlaylists(mutable, direction)
        return mutable
    }

    /**
     * The integer representation of this instance.
     *
     * @see fromIntCode
     */
    val intCode: Int
        // Sort's integer representation is formatted as AMMMM, where A is a bitflag
        // representing if the sort is in ascending or descending order, and M is the
        // integer representation of the sort mode.
        get() =
            mode.intCode.shl(1) or
                when (direction) {
                    Direction.ASCENDING -> 1
                    Direction.DESCENDING -> 0
                }

    sealed interface Mode {
        val intCode: Int
        val stringRes: Int

        /** The name of this mode, as shown in sort dialogs. */
        fun label(context: Context) = context.getString(stringRes)

        fun sortSongs(songs: MutableList<Song>, direction: Direction) {
            throw NotImplementedError("Sorting songs is not supported for this mode")
        }

        fun sortAlbums(albums: MutableList<Album>, direction: Direction) {
            throw NotImplementedError("Sorting albums is not supported for this mode")
        }

        fun sortArtists(artists: MutableList<Artist>, direction: Direction) {
            throw NotImplementedError("Sorting artists is not supported for this mode")
        }

        fun sortGenres(genres: MutableList<Genre>, direction: Direction) {
            throw NotImplementedError("Sorting genres is not supported for this mode")
        }

        fun sortPlaylists(playlists: MutableList<Playlist>, direction: Direction) {
            throw NotImplementedError("Sorting playlists is not supported for this mode")
        }

        fun sortComposers(composers: MutableList<Composer>, direction: Direction) {
            throw NotImplementedError("Sorting composers is not supported for this mode")
        }

        fun sortMoods(moods: MutableList<Mood>, direction: Direction) {
            throw NotImplementedError("Sorting moods is not supported for this mode")
        }

        fun sortGroupings(groupings: MutableList<Grouping>, direction: Direction) {
            throw NotImplementedError("Sorting groupings is not supported for this mode")
        }

        fun sortCustomValues(customValues: MutableList<CustomValue>, direction: Direction) {
            throw NotImplementedError("Sorting customValues is not supported for this mode")
        }

        data object ByName : Mode {
            override val intCode = IntegerTable.SORT_BY_NAME
            override val stringRes = R.string.lbl_name

            override fun sortSongs(songs: MutableList<Song>, direction: Direction) {
                when (direction) {
                    Direction.ASCENDING -> songs.sortBy { it.name }
                    Direction.DESCENDING -> songs.sortByDescending { it.name }
                }
            }

            override fun sortAlbums(albums: MutableList<Album>, direction: Direction) {
                when (direction) {
                    Direction.ASCENDING -> albums.sortBy { it.name }
                    Direction.DESCENDING -> albums.sortByDescending { it.name }
                }
            }

            override fun sortArtists(artists: MutableList<Artist>, direction: Direction) {
                when (direction) {
                    Direction.ASCENDING -> artists.sortBy { it.name }
                    Direction.DESCENDING -> artists.sortByDescending { it.name }
                }
            }

            override fun sortGenres(genres: MutableList<Genre>, direction: Direction) {
                when (direction) {
                    Direction.ASCENDING -> genres.sortBy { it.name }
                    Direction.DESCENDING -> genres.sortByDescending { it.name }
                }
            }

            override fun sortComposers(composers: MutableList<Composer>, direction: Direction) {
                when (direction) {
                    Direction.ASCENDING -> composers.sortBy { it.name }
                    Direction.DESCENDING -> composers.sortByDescending { it.name }
                }
            }

            override fun sortMoods(moods: MutableList<Mood>, direction: Direction) {
                when (direction) {
                    Direction.ASCENDING -> moods.sortBy { it.name }
                    Direction.DESCENDING -> moods.sortByDescending { it.name }
                }
            }

            override fun sortGroupings(groupings: MutableList<Grouping>, direction: Direction) {
                // Decade groupings (ex. "1980s") come first in date order, then the rest by name.
                val comparator =
                    compareBy<Grouping> { it.decade ?: Int.MAX_VALUE }.thenBy { it.name }
                when (direction) {
                    Direction.ASCENDING -> groupings.sortWith(comparator)
                    Direction.DESCENDING -> groupings.sortWith(comparator.reversed())
                }
            }

            private val Grouping.decade: Int?
                get() = DECADE_REGEX.matchEntire(name.raw)?.groupValues?.get(1)?.toIntOrNull()

            override fun sortCustomValues(
                customValues: MutableList<CustomValue>,
                direction: Direction,
            ) {
                when (direction) {
                    Direction.ASCENDING -> customValues.sortBy { it.name }
                    Direction.DESCENDING -> customValues.sortByDescending { it.name }
                }
            }

            override fun sortPlaylists(playlists: MutableList<Playlist>, direction: Direction) {
                when (direction) {
                    Direction.ASCENDING -> playlists.sortBy { it.name }
                    Direction.DESCENDING -> playlists.sortByDescending { it.name }
                }
            }
        }

        data object ByAlbum : Mode {
            override val intCode = IntegerTable.SORT_BY_ALBUM
            override val stringRes = R.string.lbl_album

            override fun sortSongs(songs: MutableList<Song>, direction: Direction) {
                songs.sortBy { it.name }
                songs.sortBy { it.track }
                songs.sortBy { it.disc }
                when (direction) {
                    Direction.ASCENDING -> songs.sortBy { it.album.name }
                    Direction.DESCENDING -> songs.sortByDescending { it.album.name }
                }
            }
        }

        data object ByArtist : Mode {
            override val intCode = IntegerTable.SORT_BY_ARTIST
            override val stringRes = R.string.lbl_artist

            override fun sortSongs(songs: MutableList<Song>, direction: Direction) {
                songs.sortBy { it.name }
                songs.sortBy { it.track }
                songs.sortBy { it.disc }
                songs.sortBy { it.album.name }
                songs.sortByDescending { it.album.dates }
                when (direction) {
                    Direction.ASCENDING -> songs.sortBy { it.artists.firstOrNull()?.name }
                    Direction.DESCENDING ->
                        songs.sortByDescending { it.artists.firstOrNull()?.name }
                }
            }

            override fun sortAlbums(albums: MutableList<Album>, direction: Direction) {
                albums.sortBy { it.name }
                albums.sortByDescending { it.dates }
                when (direction) {
                    Direction.ASCENDING -> albums.sortBy { it.artists.firstOrNull()?.name }
                    Direction.DESCENDING ->
                        albums.sortByDescending { it.artists.firstOrNull()?.name }
                }
            }
        }

        data object ByDate : Mode {
            override val intCode = IntegerTable.SORT_BY_YEAR
            override val stringRes = R.string.lbl_date

            override fun sortSongs(songs: MutableList<Song>, direction: Direction) {
                songs.sortBy { it.name }
                songs.sortBy { it.track }
                songs.sortBy { it.disc }
                songs.sortByDescending { it.album.name }
                when (direction) {
                    Direction.ASCENDING -> songs.sortBy { it.album.dates }
                    Direction.DESCENDING -> songs.sortByDescending { it.album.dates }
                }
            }

            override fun sortAlbums(albums: MutableList<Album>, direction: Direction) {
                albums.sortBy { it.name }
                when (direction) {
                    Direction.ASCENDING -> albums.sortBy { it.dates }
                    Direction.DESCENDING -> albums.sortByDescending { it.dates }
                }
            }
        }

        data object ByDuration : Mode {
            override val intCode = IntegerTable.SORT_BY_DURATION
            override val stringRes = R.string.lbl_duration

            override fun sortSongs(songs: MutableList<Song>, direction: Direction) {
                songs.sortBy { it.name }
                when (direction) {
                    Direction.ASCENDING -> songs.sortBy { it.durationMs }
                    Direction.DESCENDING -> songs.sortByDescending { it.durationMs }
                }
            }

            override fun sortAlbums(albums: MutableList<Album>, direction: Direction) {
                albums.sortBy { it.name }
                when (direction) {
                    Direction.ASCENDING -> albums.sortBy { it.durationMs }
                    Direction.DESCENDING -> albums.sortByDescending { it.durationMs }
                }
            }

            override fun sortArtists(artists: MutableList<Artist>, direction: Direction) {
                artists.sortBy { it.name }
                when (direction) {
                    Direction.ASCENDING -> artists.sortBy { it.durationMs }
                    Direction.DESCENDING -> artists.sortByDescending { it.durationMs }
                }
            }

            override fun sortGenres(genres: MutableList<Genre>, direction: Direction) {
                genres.sortBy { it.name }
                when (direction) {
                    Direction.ASCENDING -> genres.sortBy { it.durationMs }
                    Direction.DESCENDING -> genres.sortByDescending { it.durationMs }
                }
            }

            override fun sortComposers(composers: MutableList<Composer>, direction: Direction) {
                composers.sortBy { it.name }
                when (direction) {
                    Direction.ASCENDING -> composers.sortBy { it.durationMs }
                    Direction.DESCENDING -> composers.sortByDescending { it.durationMs }
                }
            }

            override fun sortMoods(moods: MutableList<Mood>, direction: Direction) {
                moods.sortBy { it.name }
                when (direction) {
                    Direction.ASCENDING -> moods.sortBy { it.durationMs }
                    Direction.DESCENDING -> moods.sortByDescending { it.durationMs }
                }
            }

            override fun sortGroupings(groupings: MutableList<Grouping>, direction: Direction) {
                groupings.sortBy { it.name }
                when (direction) {
                    Direction.ASCENDING -> groupings.sortBy { it.durationMs }
                    Direction.DESCENDING -> groupings.sortByDescending { it.durationMs }
                }
            }

            override fun sortCustomValues(
                customValues: MutableList<CustomValue>,
                direction: Direction,
            ) {
                customValues.sortBy { it.name }
                when (direction) {
                    Direction.ASCENDING -> customValues.sortBy { it.durationMs }
                    Direction.DESCENDING -> customValues.sortByDescending { it.durationMs }
                }
            }

            override fun sortPlaylists(playlists: MutableList<Playlist>, direction: Direction) {
                playlists.sortBy { it.name }
                when (direction) {
                    Direction.ASCENDING -> playlists.sortBy { it.durationMs }
                    Direction.DESCENDING -> playlists.sortByDescending { it.durationMs }
                }
            }
        }

        data object ByCount : Mode {
            override val intCode = IntegerTable.SORT_BY_COUNT
            override val stringRes = R.string.lbl_song_count

            override fun sortAlbums(albums: MutableList<Album>, direction: Direction) {
                albums.sortBy { it.name }
                when (direction) {
                    Direction.ASCENDING -> albums.sortBy { it.songs.size }
                    Direction.DESCENDING -> albums.sortByDescending { it.songs.size }
                }
            }

            override fun sortArtists(artists: MutableList<Artist>, direction: Direction) {
                artists.sortBy { it.name }
                when (direction) {
                    Direction.ASCENDING -> artists.sortBy { it.songs.size }
                    Direction.DESCENDING -> artists.sortByDescending { it.songs.size }
                }
            }

            override fun sortGenres(genres: MutableList<Genre>, direction: Direction) {
                genres.sortBy { it.name }
                when (direction) {
                    Direction.ASCENDING -> genres.sortBy { it.songs.size }
                    Direction.DESCENDING -> genres.sortByDescending { it.songs.size }
                }
            }

            override fun sortComposers(composers: MutableList<Composer>, direction: Direction) {
                composers.sortBy { it.name }
                when (direction) {
                    Direction.ASCENDING -> composers.sortBy { it.songs.size }
                    Direction.DESCENDING -> composers.sortByDescending { it.songs.size }
                }
            }

            override fun sortMoods(moods: MutableList<Mood>, direction: Direction) {
                moods.sortBy { it.name }
                when (direction) {
                    Direction.ASCENDING -> moods.sortBy { it.songs.size }
                    Direction.DESCENDING -> moods.sortByDescending { it.songs.size }
                }
            }

            override fun sortGroupings(groupings: MutableList<Grouping>, direction: Direction) {
                groupings.sortBy { it.name }
                when (direction) {
                    Direction.ASCENDING -> groupings.sortBy { it.songs.size }
                    Direction.DESCENDING -> groupings.sortByDescending { it.songs.size }
                }
            }

            override fun sortCustomValues(
                customValues: MutableList<CustomValue>,
                direction: Direction,
            ) {
                customValues.sortBy { it.name }
                when (direction) {
                    Direction.ASCENDING -> customValues.sortBy { it.songs.size }
                    Direction.DESCENDING -> customValues.sortByDescending { it.songs.size }
                }
            }

            override fun sortPlaylists(playlists: MutableList<Playlist>, direction: Direction) {
                playlists.sortBy { it.name }
                when (direction) {
                    Direction.ASCENDING -> playlists.sortBy { it.songs.size }
                    Direction.DESCENDING -> playlists.sortByDescending { it.songs.size }
                }
            }
        }

        data object ByDisc : Mode {
            override val intCode = IntegerTable.SORT_BY_DISC
            override val stringRes = R.string.lbl_disc

            override fun sortSongs(songs: MutableList<Song>, direction: Direction) {
                songs.sortBy { it.name }
                songs.sortBy { it.track }
                when (direction) {
                    Direction.ASCENDING -> songs.sortBy { it.disc }
                    Direction.DESCENDING -> songs.sortByDescending { it.disc }
                }
            }
        }

        data object ByTrack : Mode {
            override val intCode = IntegerTable.SORT_BY_TRACK
            override val stringRes = R.string.lbl_track

            override fun sortSongs(songs: MutableList<Song>, direction: Direction) {
                songs.sortBy { it.name }
                when (direction) {
                    Direction.ASCENDING -> songs.sortBy { it.track }
                    Direction.DESCENDING -> songs.sortByDescending { it.track }
                }
                songs.sortBy { it.disc }
            }
        }

        data object ByDateAdded : Mode {
            override val intCode = IntegerTable.SORT_BY_DATE_ADDED
            override val stringRes = R.string.lbl_date_added

            override fun sortSongs(songs: MutableList<Song>, direction: Direction) {
                songs.sortBy { it.name }
                when (direction) {
                    Direction.ASCENDING -> songs.sortBy { it.addedMs }
                    Direction.DESCENDING -> songs.sortByDescending { it.addedMs }
                }
            }

            override fun sortAlbums(albums: MutableList<Album>, direction: Direction) {
                albums.sortBy { it.name }
                when (direction) {
                    Direction.ASCENDING -> albums.sortBy { it.addedMs }
                    Direction.DESCENDING -> albums.sortByDescending { it.addedMs }
                }
            }
        }

        /**
         * Keep the given order of the items. Only meaningful for playlists, which have their own
         * user-defined order. Descending reverses that order.
         */
        data object ByCustom : Mode {
            override val intCode = IntegerTable.SORT_BY_CUSTOM
            override val stringRes = R.string.lbl_custom_order

            override fun sortSongs(songs: MutableList<Song>, direction: Direction) {
                if (direction == Direction.DESCENDING) {
                    songs.reverse()
                }
            }
        }

        /** Sort by the rating of an item, with unrated items considered the lowest. */
        data object ByRating : Mode {
            override val intCode = IntegerTable.SORT_BY_RATING
            override val stringRes = R.string.lbl_rating

            override fun sortSongs(songs: MutableList<Song>, direction: Direction) {
                songs.sortBy { it.name }
                when (direction) {
                    Direction.ASCENDING -> songs.sortBy { it.rating?.halfStars ?: 0 }
                    Direction.DESCENDING -> songs.sortByDescending { it.rating?.halfStars ?: 0 }
                }
            }
        }

        /**
         * Sort songs by their first value of the custom tag in [slot]. Songs without a value come
         * last in either direction.
         */
        data class ByCustomTag(val slot: Int) : Mode {
            override val intCode = IntegerTable.SORT_BY_CUSTOM_TAG + slot - 1
            override val stringRes = R.string.lbl_custom_value

            override fun label(context: Context) = CustomTag.nameOf(context, slot)

            override fun sortSongs(songs: MutableList<Song>, direction: Direction) {
                songs.sortBy { it.name }
                val value = { song: Song -> song.customValues.firstOrNull { it.slot == slot } }
                when (direction) {
                    Direction.ASCENDING ->
                        songs.sortWith(compareBy(nullsLast()) { value(it)?.name })
                    Direction.DESCENDING ->
                        songs.sortWith(compareBy(nullsLast(reverseOrder())) { value(it)?.name })
                }
            }
        }

        companion object {
            fun fromIntCode(intCode: Int): Mode? =
                when (intCode) {
                    in CUSTOM_TAG_CODES ->
                        ByCustomTag(intCode - IntegerTable.SORT_BY_CUSTOM_TAG + 1)
                    ByName.intCode -> ByName
                    ByArtist.intCode -> ByArtist
                    ByAlbum.intCode -> ByAlbum
                    ByDate.intCode -> ByDate
                    ByDuration.intCode -> ByDuration
                    ByCount.intCode -> ByCount
                    ByDisc.intCode -> ByDisc
                    ByTrack.intCode -> ByTrack
                    ByDateAdded.intCode -> ByDateAdded
                    ByCustom.intCode -> ByCustom
                    ByRating.intCode -> ByRating
                    else -> null
                }

            private val CUSTOM_TAG_CODES =
                IntegerTable.SORT_BY_CUSTOM_TAG until
                    IntegerTable.SORT_BY_CUSTOM_TAG + CustomTag.SLOTS.last
        }
    }

    /** The direction to sort items in. */
    enum class Direction {
        ASCENDING,
        DESCENDING,
    }

    companion object {
        /**
         * Convert a [Sort] integer representation into an instance.
         *
         * @param intCode An integer representation of a [Sort]
         * @return The corresponding [Sort], or null if the [Sort] is invalid.
         * @see intCode
         */
        fun fromIntCode(intCode: Int): Sort? {
            // Sort's integer representation is formatted as AMMMM, where A is a bitflag
            // representing on if the mode is ascending or descending, and M is the integer
            // representation of the sort mode.
            val direction = if ((intCode and 1) == 1) Direction.ASCENDING else Direction.DESCENDING
            val mode = Mode.fromIntCode(intCode.shr(1)) ?: return null
            return Sort(mode, direction)
        }
    }
}

/** Matches decade groupings like "1980s"; the group is the decade's first year. */
private val DECADE_REGEX = Regex("""(\d{4})s""")
