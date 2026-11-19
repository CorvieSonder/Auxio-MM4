/*
 * Copyright (c) 2024 Auxio Project
 * HomeGenerator.kt is part of Auxio.
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
 
package org.oxycblt.auxio.home

import javax.inject.Inject
import org.oxycblt.auxio.home.tabs.Tab
import org.oxycblt.auxio.list.ListSettings
import org.oxycblt.auxio.list.adapter.UpdateInstructions
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.music.MusicType
import org.oxycblt.auxio.music.auto.AutoPlaylistRepository
import org.oxycblt.auxio.music.rating.RatingRepository
import org.oxycblt.auxio.music.tree.PlaylistTreeRepository
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.Composer
import org.oxycblt.musikr.Genre
import org.oxycblt.musikr.Grouping
import org.oxycblt.musikr.Mood
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song
import timber.log.Timber as L

interface HomeGenerator {
    fun attach()

    fun release()

    fun empty(): Boolean

    fun songs(): List<Song>

    fun albums(): List<Album>

    fun artists(): List<Artist>

    fun genres(): List<Genre>

    fun composers(): List<Composer>

    fun moods(): List<Mood>

    fun groupings(): List<Grouping>

    fun playlists(): List<Playlist>

    fun tabs(): List<MusicType>

    interface Invalidator {
        fun invalidateEmpty() {}

        fun invalidateMusic(type: MusicType, instructions: UpdateInstructions)

        fun invalidateTabs()
    }

    interface Factory {
        fun create(invalidator: Invalidator): HomeGenerator
    }
}

class HomeGeneratorFactoryImpl
@Inject
constructor(
    private val homeSettings: HomeSettings,
    private val listSettings: ListSettings,
    private val musicRepository: MusicRepository,
    private val playlistTreeRepository: PlaylistTreeRepository,
    private val ratingRepository: RatingRepository,
    private val autoPlaylistRepository: AutoPlaylistRepository,
) : HomeGenerator.Factory {
    override fun create(invalidator: HomeGenerator.Invalidator): HomeGenerator =
        HomeGeneratorImpl(
            invalidator,
            homeSettings,
            listSettings,
            musicRepository,
            playlistTreeRepository,
            ratingRepository,
            autoPlaylistRepository,
        )
}

private class HomeGeneratorImpl(
    private val invalidator: HomeGenerator.Invalidator,
    private val homeSettings: HomeSettings,
    private val listSettings: ListSettings,
    private val musicRepository: MusicRepository,
    private val playlistTreeRepository: PlaylistTreeRepository,
    private val ratingRepository: RatingRepository,
    private val autoPlaylistRepository: AutoPlaylistRepository,
) :
    HomeGenerator,
    HomeSettings.Listener,
    ListSettings.Listener,
    MusicRepository.UpdateListener,
    PlaylistTreeRepository.Listener,
    RatingRepository.Listener,
    AutoPlaylistRepository.Listener {
    override fun attach() {
        homeSettings.registerListener(this)
        listSettings.registerListener(this)
        musicRepository.addUpdateListener(this)
        playlistTreeRepository.addListener(this)
        ratingRepository.addListener(this)
        autoPlaylistRepository.addListener(this)
    }

    override fun onAutoPlaylistsChanged() {
        invalidator.invalidateMusic(MusicType.PLAYLISTS, UpdateInstructions.Diff)
    }

    override fun onPlaylistTreeChanged() {
        // Replace rather than diff, so that the child counts of the playlists are rebound.
        invalidator.invalidateMusic(MusicType.PLAYLISTS, UpdateInstructions.Replace(0))
    }

    override fun onRatingsChanged() {
        // Replace rather than diff, so that the ratings of the songs are rebound.
        invalidator.invalidateMusic(MusicType.SONGS, UpdateInstructions.Replace(0))
    }

    override fun onTabsChanged() {
        invalidator.invalidateTabs()
    }

    override fun onHideCollaboratorsChanged() {
        // Changes in the hide collaborator setting will change the artist contents
        // of the library, consider it a library update.
        L.d("Collaborator setting changed, forwarding update")
        invalidator.invalidateMusic(MusicType.ARTISTS, UpdateInstructions.Diff)
    }

    override fun onSongSortChanged() {
        super.onSongSortChanged()
        invalidator.invalidateMusic(MusicType.SONGS, UpdateInstructions.Replace(0))
    }

    override fun onAlbumSortChanged() {
        super.onAlbumSortChanged()
        invalidator.invalidateMusic(MusicType.ALBUMS, UpdateInstructions.Replace(0))
    }

    override fun onArtistSortChanged() {
        super.onArtistSortChanged()
        invalidator.invalidateMusic(MusicType.ARTISTS, UpdateInstructions.Replace(0))
    }

    override fun onGenreSortChanged() {
        super.onGenreSortChanged()
        invalidator.invalidateMusic(MusicType.GENRES, UpdateInstructions.Replace(0))
    }

    override fun onComposerSortChanged() {
        super.onComposerSortChanged()
        invalidator.invalidateMusic(MusicType.COMPOSERS, UpdateInstructions.Replace(0))
    }

    override fun onMoodSortChanged() {
        super.onMoodSortChanged()
        invalidator.invalidateMusic(MusicType.MOODS, UpdateInstructions.Replace(0))
    }

    override fun onGroupingSortChanged() {
        super.onGroupingSortChanged()
        invalidator.invalidateMusic(MusicType.GROUPINGS, UpdateInstructions.Replace(0))
    }

    override fun onPlaylistSortChanged() {
        super.onPlaylistSortChanged()
        invalidator.invalidateMusic(MusicType.PLAYLISTS, UpdateInstructions.Replace(0))
    }

    override fun onMusicChanges(changes: MusicRepository.Changes) {
        invalidator.invalidateEmpty()

        val library = musicRepository.library
        if (changes.deviceLibrary && library != null) {
            L.d("Refreshing library")
            // Get the each list of items in the library to use as our list data.
            // Applying the preferred sorting to them.
            invalidator.invalidateMusic(MusicType.SONGS, UpdateInstructions.Diff)
            invalidator.invalidateMusic(MusicType.ALBUMS, UpdateInstructions.Diff)
            invalidator.invalidateMusic(MusicType.ARTISTS, UpdateInstructions.Diff)
            invalidator.invalidateMusic(MusicType.GENRES, UpdateInstructions.Diff)
            invalidator.invalidateMusic(MusicType.COMPOSERS, UpdateInstructions.Diff)
            invalidator.invalidateMusic(MusicType.MOODS, UpdateInstructions.Diff)
            invalidator.invalidateMusic(MusicType.GROUPINGS, UpdateInstructions.Diff)
        }

        if (changes.userLibrary && library != null) {
            L.d("Refreshing playlists")
            invalidator.invalidateMusic(MusicType.PLAYLISTS, UpdateInstructions.Diff)
        }
    }

    override fun release() {
        playlistTreeRepository.removeListener(this)
        autoPlaylistRepository.removeListener(this)
        ratingRepository.removeListener(this)
        musicRepository.removeUpdateListener(this)
        listSettings.unregisterListener(this)
        homeSettings.unregisterListener(this)
    }

    override fun empty() = musicRepository.library?.empty() ?: true

    override fun songs() =
        musicRepository.library?.let { listSettings.songSort.songs(it.songs) } ?: emptyList()

    override fun albums() =
        musicRepository.library?.let { listSettings.albumSort.albums(it.albums) } ?: emptyList()

    override fun artists() =
        musicRepository.library?.let { deviceLibrary ->
            val sorted = listSettings.artistSort.artists(deviceLibrary.artists)
            if (homeSettings.shouldHideCollaborators) {
                sorted.filter { it.explicitAlbums.isNotEmpty() }
            } else {
                sorted
            }
        } ?: emptyList()

    override fun composers() =
        musicRepository.library?.let { listSettings.composerSort.composers(it.composers) }
            ?: emptyList()

    override fun moods() =
        musicRepository.library?.let { listSettings.moodSort.moods(it.moods) } ?: emptyList()

    override fun groupings() =
        musicRepository.library?.let { listSettings.groupingSort.groupings(it.groupings) }
            ?: emptyList()

    override fun genres() =
        musicRepository.library?.let { listSettings.genreSort.genres(it.genres) } ?: emptyList()

    override fun playlists() =
        musicRepository.library?.let {
            // Automatic playlists always come first, above any pinned playlists.
            autoPlaylistRepository.playlists(it) +
                playlistTreeRepository.pinnedFirst(
                    listSettings.playlistSort.playlists(playlistTreeRepository.childrenOf(null))
                )
        } ?: emptyList()

    override fun tabs() = homeSettings.homeTabs.filterIsInstance<Tab.Visible>().map { it.type }
}
