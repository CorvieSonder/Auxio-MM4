/*
 * Copyright (c) 2023 Auxio Project
 * ListViewModel.kt is part of Auxio.
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
 
package org.oxycblt.auxio.list

import androidx.annotation.MenuRes
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.oxycblt.auxio.detail.persist.PlaylistSortRepository
import org.oxycblt.auxio.list.menu.Menu
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.playback.PlaySong
import org.oxycblt.auxio.util.Event
import org.oxycblt.auxio.util.MutableEvent
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.Genre
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.MusicParent
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/**
 * A [ViewModel] that orchestrates menu dialogs and selection state.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@HiltViewModel
class ListViewModel
@Inject
constructor(
    private val listSettings: ListSettings,
    private val musicRepository: MusicRepository,
    private val playlistSortRepository: PlaylistSortRepository,
) : ViewModel(), MusicRepository.UpdateListener {
    /** The currently selected items. These are ordered in earliest selected and latest selected. */
    val selected: StateFlow<List<Music>>
        field = MutableStateFlow(listOf<Music>())

    private val _menu = MutableEvent<Menu>()
    /**
     * A [Menu] command that is awaiting a view capable of responding to it. Null if none currently.
     */
    val menu: Event<Menu> = _menu

    init {
        musicRepository.addUpdateListener(this)
    }

    override fun onMusicChanges(changes: MusicRepository.Changes) {
        val library = musicRepository.library ?: return
        // Sanitize the selection to remove items that no longer exist and thus
        // won't appear in any list.
        selected.value =
            selected.value.mapNotNull {
                when (it) {
                    is Song -> library.findSong(it.uid)
                    is Album -> library.findAlbum(it.uid)
                    is Artist -> library.findArtist(it.uid)
                    is Genre -> library.findGenre(it.uid)
                    is Playlist -> library.findPlaylist(it.uid)
                }
            }
    }

    override fun onCleared() {
        musicRepository.removeUpdateListener(this)
    }

    /**
     * Select a new [Music] item. If this item is already within the selected items, the item will
     * be removed. Otherwise, it will be added.
     *
     * @param music The [Music] item to select.
     */
    fun select(music: Music) {
        // Empty playlists can still be selected, as they can hold other playlists.
        if (music is MusicParent && music !is Playlist && music.songs.isEmpty()) {
            L.d("Cannot select empty parent, ignoring operation")
            return
        }

        val selected = selected.value.toMutableList()
        if (!selected.remove(music)) {
            L.d("Adding $music to selection")
            selected.add(music)
        } else {
            L.d("Removed $music from selection")
        }

        this.selected.value = selected
    }

    /**
     * Select every one of the given [Music] items that is not selected yet.
     *
     * @param items The [Music] items currently shown.
     */
    fun selectAll(items: List<Music>) {
        val selected = selected.value
        val selectedSet = selected.toSet()
        val added = items.distinct().filter { it !in selectedSet && it.isSelectable() }
        L.d("Adding ${added.size} items to selection")
        this.selected.value = selected + added
    }

    /**
     * Invert the selection within the given [Music] items: selected items become unselected and the
     * other way around. Selected items outside of [items] are left as they are.
     *
     * @param items The [Music] items currently shown.
     */
    fun invertSelection(items: List<Music>) {
        val selected = selected.value
        val selectedSet = selected.toSet()
        val itemSet = items.toSet()
        val kept = selected.filter { it !in itemSet }
        val added = items.distinct().filter { it !in selectedSet && it.isSelectable() }
        L.d("Inverting selection [kept=${kept.size} added=${added.size}]")
        this.selected.value = kept + added
    }

    // Empty playlists can still be selected, as they can hold other playlists.
    private fun Music.isSelectable() =
        this !is MusicParent || this is Playlist || songs.isNotEmpty()

    /**
     * Get the songs of the current selection without clearing it.
     *
     * @return A list of [Song]s collated from each item selected. A song that is part of several
     *   selected items is only included once, except for a playlist, which always contributes all
     *   of its songs.
     */
    fun peekSelection(): List<Song> {
        val songs = mutableListOf<Song>()
        val seen = mutableSetOf<Song>()
        for (music in selected.value) {
            when (music) {
                is Song -> if (seen.add(music)) songs.add(music)
                is Album -> listSettings.albumSongSort.songs(music.songs).filterTo(songs, seen::add)
                is Artist ->
                    listSettings.artistSongSort.songs(music.songs).filterTo(songs, seen::add)
                is Genre -> listSettings.genreSongSort.songs(music.songs).filterTo(songs, seen::add)
                is Playlist -> {
                    // A selected playlist adds only its own songs, in the order shown on its page.
                    val own = playlistSortRepository.songs(music)
                    songs.addAll(own)
                    seen.addAll(own)
                }
            }
        }
        return songs
    }

    /**
     * Clear the current selection and return it.
     *
     * @return A list of [Song]s collated from each item selected.
     */
    fun takeSelection(): List<Song> {
        L.d("Taking selection")
        return peekSelection().also { selected.value = listOf() }
    }

    /**
     * Clear the current selection.
     *
     * @return true if the prior selection was non-empty, false otherwise.
     */
    fun dropSelection(): Boolean {
        L.d("Dropping selection [empty=${selected.value.isEmpty()}]")
        return selected.value.isNotEmpty().also { selected.value = listOf() }
    }

    /**
     * Open a menu for a [Song]. This is not a popup menu, instead actually a dialog of menu options
     * with additional information.
     *
     * @param menuRes The resource of the menu to use.
     * @param song The [Song] to show.
     * @param playWith A [PlaySong] command to give context to what "Play" and "Shuffle" actions
     *   should do.
     */
    fun openMenu(@MenuRes menuRes: Int, song: Song, playWith: PlaySong) {
        L.d("Opening menu for $song")
        openImpl(Menu.ForSong(menuRes, song, playWith))
    }

    /**
     * Open a menu for a [Album]. This is not a popup menu, instead actually a dialog of menu
     * options with additional information.
     *
     * @param menuRes The resource of the menu to use.
     * @param album The [Album] to show.
     */
    fun openMenu(@MenuRes menuRes: Int, album: Album) {
        L.d("Opening menu for $album")
        openImpl(Menu.ForAlbum(menuRes, album))
    }

    /**
     * Open a menu for a [Artist]. This is not a popup menu, instead actually a dialog of menu
     * options with additional information.
     *
     * @param menuRes The resource of the menu to use.
     * @param artist The [Artist] to show.
     */
    fun openMenu(@MenuRes menuRes: Int, artist: Artist) {
        L.d("Opening menu for $artist")
        openImpl(Menu.ForArtist(menuRes, artist))
    }

    /**
     * Open a menu for a [Genre]. This is not a popup menu, instead actually a dialog of menu
     * options with additional information.
     *
     * @param menuRes The resource of the menu to use.
     * @param genre The [Genre] to show.
     */
    fun openMenu(@MenuRes menuRes: Int, genre: Genre) {
        L.d("Opening menu for $genre")
        openImpl(Menu.ForGenre(menuRes, genre))
    }

    /**
     * Open a menu for a [Playlist]. This is not a popup menu, instead actually a dialog of menu
     * options with additional information.
     *
     * @param menuRes The resource of the menu to use.
     * @param playlist The [Playlist] to show.
     */
    fun openMenu(@MenuRes menuRes: Int, playlist: Playlist) {
        L.d("Opening menu for $playlist")
        openImpl(Menu.ForPlaylist(menuRes, playlist))
    }

    /**
     * Open a menu for a [Song] selection. This is not a popup menu, instead actually a dialog of
     * menu options with additional information.
     *
     * @param menuRes The resource of the menu to use.
     * @param songs The [Song] selection to show.
     */
    fun openMenu(
        @MenuRes menuRes: Int,
        songs: List<Song>,
        playlists: List<Playlist> = listOf(),
    ) {
        L.d("Opening menu for ${songs.size} songs and ${playlists.size} playlists")
        openImpl(Menu.ForSelection(menuRes, songs, playlists))
    }

    private fun openImpl(menu: Menu) {
        val existing = _menu.flow.value
        if (existing != null) {
            L.w("Already opening $existing, ignoring $menu")
            return
        }
        _menu.put(menu)
    }
}
