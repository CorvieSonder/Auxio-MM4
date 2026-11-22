/*
 * Copyright (c) 2024 Auxio Project
 * DetailGenerator.kt is part of Auxio.
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
 
package org.oxycblt.auxio.detail

import androidx.annotation.StringRes
import javax.inject.Inject
import org.oxycblt.auxio.R
import org.oxycblt.auxio.detail.persist.PlaylistSortRepository
import org.oxycblt.auxio.list.ListSettings
import org.oxycblt.auxio.list.sort.Sort
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.music.MusicType
import org.oxycblt.auxio.music.auto.AutoPlaylistRepository
import org.oxycblt.auxio.music.rating.RatingRepository
import org.oxycblt.auxio.music.tree.PlaylistTreeRepository
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.Composer
import org.oxycblt.musikr.CustomValue
import org.oxycblt.musikr.Genre
import org.oxycblt.musikr.Grouping
import org.oxycblt.musikr.Mood
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.MusicParent
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.tag.Disc
import org.oxycblt.musikr.tag.ReleaseType
import timber.log.Timber as L

interface DetailGenerator {
    fun any(uid: Music.UID): Detail<out MusicParent>?

    fun album(uid: Music.UID): Detail<Album>?

    fun artist(uid: Music.UID): Detail<Artist>?

    fun genre(uid: Music.UID): Detail<Genre>?

    fun composer(uid: Music.UID): Detail<Composer>?

    fun mood(uid: Music.UID): Detail<Mood>?

    fun grouping(uid: Music.UID): Detail<Grouping>?

    fun customValue(uid: Music.UID): Detail<CustomValue>?

    fun playlist(uid: Music.UID): Detail<Playlist>?

    fun attach()

    fun release()

    interface Factory {
        fun create(invalidator: Invalidator): DetailGenerator
    }

    interface Invalidator {
        fun invalidate(type: MusicType, replace: Int?)
    }
}

class DetailGeneratorFactoryImpl
@Inject
constructor(
    private val listSettings: ListSettings,
    private val musicRepository: MusicRepository,
    private val playlistSortRepository: PlaylistSortRepository,
    private val playlistTreeRepository: PlaylistTreeRepository,
    private val ratingRepository: RatingRepository,
    private val autoPlaylistRepository: AutoPlaylistRepository,
) : DetailGenerator.Factory {
    override fun create(invalidator: DetailGenerator.Invalidator): DetailGenerator =
        DetailGeneratorImpl(
            invalidator,
            listSettings,
            musicRepository,
            playlistSortRepository,
            playlistTreeRepository,
            ratingRepository,
            autoPlaylistRepository,
        )
}

private class DetailGeneratorImpl(
    private val invalidator: DetailGenerator.Invalidator,
    private val listSettings: ListSettings,
    private val musicRepository: MusicRepository,
    private val playlistSortRepository: PlaylistSortRepository,
    private val playlistTreeRepository: PlaylistTreeRepository,
    private val ratingRepository: RatingRepository,
    private val autoPlaylistRepository: AutoPlaylistRepository,
) :
    DetailGenerator,
    MusicRepository.UpdateListener,
    ListSettings.Listener,
    PlaylistSortRepository.Listener,
    PlaylistTreeRepository.Listener,
    RatingRepository.Listener,
    AutoPlaylistRepository.Listener {
    override fun attach() {
        listSettings.registerListener(this)
        musicRepository.addUpdateListener(this)
        playlistSortRepository.addListener(this)
        playlistTreeRepository.addListener(this)
        ratingRepository.addListener(this)
        autoPlaylistRepository.addListener(this)
    }

    override fun onAutoPlaylistsChanged() {
        invalidator.invalidate(MusicType.PLAYLISTS, null)
    }

    override fun onRatingsChanged() {
        // Replace the lists so that the ratings of the songs are rebound.
        invalidator.invalidate(MusicType.ALBUMS, -1)
        invalidator.invalidate(MusicType.ARTISTS, -1)
        invalidator.invalidate(MusicType.GENRES, -1)
        invalidator.invalidate(MusicType.PLAYLISTS, 0)
        invalidator.invalidate(MusicType.COMPOSERS, -1)
        invalidator.invalidate(MusicType.MOODS, -1)
    }

    override fun onAlbumSongSortChanged() {
        super.onAlbumSongSortChanged()
        invalidator.invalidate(MusicType.ALBUMS, -1)
    }

    override fun onArtistSongSortChanged() {
        super.onArtistSongSortChanged()
        invalidator.invalidate(MusicType.ARTISTS, -1)
    }

    override fun onGenreSongSortChanged() {
        super.onGenreSongSortChanged()
        invalidator.invalidate(MusicType.GENRES, -1)
    }

    override fun onPlaylistSortsChanged() {
        invalidator.invalidate(MusicType.PLAYLISTS, -1)
    }

    override fun onPlaylistSortChanged() {
        super.onPlaylistSortChanged()
        // Child playlists are listed in the order of the playlist tab.
        invalidator.invalidate(MusicType.PLAYLISTS, null)
    }

    override fun onPlaylistTreeChanged() {
        invalidator.invalidate(MusicType.PLAYLISTS, 0)
    }

    override fun onComposerSongSortChanged() {
        super.onComposerSongSortChanged()
        invalidator.invalidate(MusicType.COMPOSERS, -1)
    }

    override fun onMoodSongSortChanged() {
        super.onMoodSongSortChanged()
        invalidator.invalidate(MusicType.MOODS, -1)
    }

    override fun onGroupingSongSortChanged() {
        super.onGroupingSongSortChanged()
        invalidator.invalidate(MusicType.GROUPINGS, -1)
    }

    override fun onCustomValueSongSortChanged() {
        super.onCustomValueSongSortChanged()
        // Every custom tag shares one detail view, so any custom type refreshes it.
        invalidator.invalidate(MusicType.CUSTOM_1, -1)
    }

    override fun onMusicChanges(changes: MusicRepository.Changes) {
        if (changes.deviceLibrary) {
            invalidator.invalidate(MusicType.COMPOSERS, null)
            invalidator.invalidate(MusicType.MOODS, null)
            invalidator.invalidate(MusicType.GROUPINGS, null)
            invalidator.invalidate(MusicType.CUSTOM_1, null)
            invalidator.invalidate(MusicType.ALBUMS, null)
            invalidator.invalidate(MusicType.ARTISTS, null)
            invalidator.invalidate(MusicType.GENRES, null)
        }
        if (changes.userLibrary) {
            invalidator.invalidate(MusicType.PLAYLISTS, null)
        }
    }

    override fun release() {
        autoPlaylistRepository.removeListener(this)
        ratingRepository.removeListener(this)
        listSettings.unregisterListener(this)
        musicRepository.removeUpdateListener(this)
        playlistSortRepository.removeListener(this)
        playlistTreeRepository.removeListener(this)
    }

    override fun any(uid: Music.UID): Detail<out MusicParent>? {
        val music = musicRepository.find(uid) ?: return null
        return when (music) {
            is Album -> album(uid)
            is Artist -> artist(uid)
            is Genre -> genre(uid)
            is Composer -> composer(uid)
            is Mood -> mood(uid)
            is Grouping -> grouping(uid)
            is CustomValue -> customValue(uid)
            is Playlist -> playlist(uid)
            else -> null
        }
    }

    override fun album(uid: Music.UID): Detail<Album>? {
        val album = musicRepository.library?.findAlbum(uid) ?: return null
        val songs = listSettings.albumSongSort.songs(album.songs).filterByRating()
        val discs = songs.groupBy { it.disc }
        val section =
            if (discs.size > 1) {
                DetailSection.Discs(discs)
            } else {
                DetailSection.Songs(songs)
            }
        return Detail(album, listOf(section))
    }

    override fun artist(uid: Music.UID): Detail<Artist>? {
        val artist = musicRepository.library?.findArtist(uid) ?: return null
        val grouping =
            artist.explicitAlbums.groupByTo(sortedMapOf()) {
                // Remap the complicated ReleaseType data structure into detail sections
                when (it.releaseType.refinement) {
                    ReleaseType.Refinement.LIVE -> DetailSection.Albums.Category.LIVE
                    ReleaseType.Refinement.REMIX -> DetailSection.Albums.Category.REMIXES
                    null ->
                        when (it.releaseType) {
                            is ReleaseType.Album -> DetailSection.Albums.Category.ALBUMS
                            is ReleaseType.EP -> DetailSection.Albums.Category.EPS
                            is ReleaseType.Single -> DetailSection.Albums.Category.SINGLES
                            is ReleaseType.Compilation -> DetailSection.Albums.Category.COMPILATIONS
                            is ReleaseType.Soundtrack -> DetailSection.Albums.Category.SOUNDTRACKS
                            is ReleaseType.Mix -> DetailSection.Albums.Category.DJ_MIXES
                            is ReleaseType.Mixtape -> DetailSection.Albums.Category.MIXTAPES
                            is ReleaseType.Demo -> DetailSection.Albums.Category.DEMOS
                        }
                }
            }

        if (artist.implicitAlbums.isNotEmpty()) {
            L.d("Implicit albums present, adding to list")
            grouping[DetailSection.Albums.Category.APPEARANCES] =
                artist.implicitAlbums.toMutableList()
        }

        val sections =
            grouping.mapTo(mutableListOf<DetailSection>()) { (category, albums) ->
                DetailSection.Albums(category, ARTIST_ALBUM_SORT.albums(albums))
            }
        if (artist.songs.isNotEmpty()) {
            val songs =
                DetailSection.Songs(
                    listSettings.artistSongSort.songs(artist.songs).filterByRating()
                )
            sections.add(songs)
        }
        return Detail(artist, sections)
    }

    override fun composer(uid: Music.UID): Detail<Composer>? {
        val composer = musicRepository.library?.findComposer(uid) ?: return null
        val artists = DetailSection.Artists(GENRE_ARTIST_SORT.artists(composer.artists))
        val songs =
            DetailSection.Songs(
                listSettings.composerSongSort.songs(composer.songs).filterByRating()
            )
        return Detail(composer, listOf(artists, songs))
    }

    override fun mood(uid: Music.UID): Detail<Mood>? {
        val mood = musicRepository.library?.findMood(uid) ?: return null
        val artists = DetailSection.Artists(GENRE_ARTIST_SORT.artists(mood.artists))
        val songs =
            DetailSection.Songs(listSettings.moodSongSort.songs(mood.songs).filterByRating())
        return Detail(mood, listOf(artists, songs))
    }

    override fun grouping(uid: Music.UID): Detail<Grouping>? {
        val grouping = musicRepository.library?.findGrouping(uid) ?: return null
        val artists = DetailSection.Artists(GENRE_ARTIST_SORT.artists(grouping.artists))
        val songs = DetailSection.Songs(listSettings.groupingSongSort.songs(grouping.songs))
        return Detail(grouping, listOf(artists, songs))
    }

    override fun customValue(uid: Music.UID): Detail<CustomValue>? {
        val customValue = musicRepository.library?.findCustomValue(uid) ?: return null
        val artists = DetailSection.Artists(GENRE_ARTIST_SORT.artists(customValue.artists))
        val songs = DetailSection.Songs(listSettings.customValueSongSort.songs(customValue.songs))
        return Detail(customValue, listOf(artists, songs))
    }

    override fun genre(uid: Music.UID): Detail<Genre>? {
        val genre = musicRepository.library?.findGenre(uid) ?: return null
        val artists = DetailSection.Artists(GENRE_ARTIST_SORT.artists(genre.artists))
        val songs =
            DetailSection.Songs(listSettings.genreSongSort.songs(genre.songs).filterByRating())
        return Detail(genre, listOf(artists, songs))
    }

    override fun playlist(uid: Music.UID): Detail<Playlist>? {
        val playlist = musicRepository.library?.findPlaylist(uid) ?: return null
        val sections = mutableListOf<DetailSection>()
        val children = playlistTreeRepository.childrenOf(playlist)
        if (children.isNotEmpty()) {
            sections.add(
                DetailSection.Playlists(
                    playlistTreeRepository.pinnedFirst(
                        listSettings.playlistSort.playlists(children)
                    )
                )
            )
        }
        val songs = playlistSortRepository.songs(playlist)
        if (songs.isNotEmpty()) {
            val label =
                if (playlistTreeRepository.isLibrary(playlist)) R.string.lbl_songs_in_playlists
                else R.string.lbl_songs
            sections.add(DetailSection.Songs(songs.filterByRating(), label))
        }
        return Detail(playlist, sections)
    }

    private fun List<Song>.filterByRating() =
        ratingRepository.filterSongs(this, ratingRepository.detailMinRating)

    private companion object {
        val ARTIST_ALBUM_SORT = Sort(Sort.Mode.ByDate, Sort.Direction.DESCENDING)
        val GENRE_ARTIST_SORT = Sort(Sort.Mode.ByName, Sort.Direction.ASCENDING)
    }
}

data class Detail<P : MusicParent>(val parent: P, val sections: List<DetailSection>)

sealed interface DetailSection {
    val order: Int
    val stringRes: Int

    abstract class PlainSection<T : Music> : DetailSection {
        abstract val items: List<T>
    }

    data class Artists(override val items: List<Artist>) : PlainSection<Artist>() {
        override val order = 0
        override val stringRes = R.string.lbl_artists
    }

    data class Albums(val category: Category, override val items: List<Album>) :
        PlainSection<Album>() {
        override val order = 1 + category.ordinal
        override val stringRes = category.stringRes

        enum class Category(@StringRes val stringRes: Int) {
            ALBUMS(R.string.lbl_albums),
            EPS(R.string.lbl_eps),
            SINGLES(R.string.lbl_singles),
            COMPILATIONS(R.string.lbl_compilations),
            SOUNDTRACKS(R.string.lbl_soundtracks),
            DJ_MIXES(R.string.lbl_mixes),
            MIXTAPES(R.string.lbl_mixtapes),
            DEMOS(R.string.lbl_demos),
            APPEARANCES(R.string.lbl_appears_on),
            LIVE(R.string.lbl_live_group),
            REMIXES(R.string.lbl_remix_group),
        }
    }

    data class Playlists(override val items: List<Playlist>) : PlainSection<Playlist>() {
        override val order = 11
        override val stringRes = R.string.lbl_playlists
    }

    data class Songs(
        override val items: List<Song>,
        override val stringRes: Int = R.string.lbl_songs,
    ) : PlainSection<Song>() {
        override val order = 12
    }

    data class Discs(val discs: Map<Disc?, List<Song>>) : DetailSection {
        override val order = 13
        override val stringRes = R.string.lbl_songs
    }
}
