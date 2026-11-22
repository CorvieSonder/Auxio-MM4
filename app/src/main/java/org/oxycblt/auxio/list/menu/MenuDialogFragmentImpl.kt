/*
 * Copyright (c) 2023 Auxio Project
 * MenuDialogFragmentImpl.kt is part of Auxio.
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
 
package org.oxycblt.auxio.list.menu

import android.view.MenuItem
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogMenuBinding
import org.oxycblt.auxio.detail.DetailViewModel
import org.oxycblt.auxio.list.ListViewModel
import org.oxycblt.auxio.music.MusicViewModel
import org.oxycblt.auxio.music.auto.AutoPlaylist
import org.oxycblt.auxio.music.delete.DeleteSongsDialog
import org.oxycblt.auxio.music.mood.MoodEditDialog
import org.oxycblt.auxio.music.rating.RatingDialog
import org.oxycblt.auxio.music.resolve
import org.oxycblt.auxio.music.resolveNames
import org.oxycblt.auxio.music.ringtone.setAsRingtone
import org.oxycblt.auxio.music.tags.CustomTag
import org.oxycblt.auxio.music.tree.PlaylistFolderViewModel
import org.oxycblt.auxio.music.tree.PlaylistTreeViewModel
import org.oxycblt.auxio.playback.PlaySong
import org.oxycblt.auxio.playback.PlaybackViewModel
import org.oxycblt.auxio.playback.bar.NowPlayingBar
import org.oxycblt.auxio.playback.bar.NowPlayingBarRequests
import org.oxycblt.auxio.playback.bar.NowPlayingBarSettings
import org.oxycblt.auxio.playback.formatDurationMs
import org.oxycblt.auxio.util.getPlural
import org.oxycblt.auxio.util.share
import org.oxycblt.auxio.util.showToast
import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.Composer
import org.oxycblt.musikr.CustomValue
import org.oxycblt.musikr.Genre
import org.oxycblt.musikr.Grouping
import org.oxycblt.musikr.Mood
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song

/**
 * [MenuDialogFragment] implementation for a [Song].
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class SongMenuDialogFragment : MenuDialogFragment<Menu.ForSong>() {
    override val menuModel: MenuViewModel by activityViewModels()
    override val listModel: ListViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val args: SongMenuDialogFragmentArgs by navArgs()
    @Inject lateinit var barSettings: NowPlayingBarSettings
    @Inject lateinit var barRequests: NowPlayingBarRequests
    private var barItemIds = setOf<Int>()

    override val parcel
        get() = args.parcel

    override fun addLeadingItems(menu: Menu.ForSong, builder: android.view.Menu) {
        if (menu.res != R.menu.playback_song) {
            return
        }
        // Actions hidden from the playback panel bar are shown here instead, unless the menu
        // already has them.
        val context = requireContext()
        val ownIds = NowPlayingBar.itemIds(context, R.menu.playback_song)
        val entries =
            NowPlayingBar.hiddenEntries(context, barSettings.barActions).filter {
                it.first !in ownIds
            }
        for ((itemId, title, parent) in entries) {
            builder.add(parent.groupId, itemId, android.view.Menu.NONE, title).icon = parent.icon
        }
        barItemIds = entries.mapTo(mutableSetOf()) { it.first }
    }

    // Nothing to disable in song menus.
    override fun getDisabledItemIds(menu: Menu.ForSong) = setOf<Int>()

    // Automatic playlists cannot be edited.
    override fun getHiddenItemIds(menu: Menu.ForSong) =
        if ((menu.playWith as? PlaySong.FromPlaylist)?.which is AutoPlaylist) {
            setOf(R.id.action_playlist_remove)
        } else {
            setOf()
        }

    override fun updateMenu(binding: DialogMenuBinding, menu: Menu.ForSong) {
        val context = requireContext()
        binding.menuCover.bind(menu.song)
        binding.menuType.text = getString(R.string.lbl_song)
        binding.menuName.text = menu.song.name.resolve(context)
        binding.menuInfo.text = menu.song.artists.resolveNames(context)
    }

    override fun onClick(item: MenuItem, menu: Menu.ForSong) {
        when (item.itemId) {
            R.id.action_play -> playbackModel.playExplicit(menu.song, menu.playWith)
            R.id.action_shuffle -> playbackModel.shuffleExplicit(menu.song, menu.playWith)
            R.id.action_play_next -> {
                playbackModel.playNext(menu.song)
                requireContext().showToast(R.string.lng_play_next)
            }
            R.id.action_queue_add -> {
                playbackModel.addToQueue(menu.song)
                requireContext().showToast(R.string.lng_queue_added)
            }
            R.id.action_playlist_add -> musicModel.addToPlaylist(menu.song)
            R.id.action_edit_moods ->
                MoodEditDialog.from(listOf(menu.song))
                    .show(parentFragmentManager, MoodEditDialog.TAG)
            R.id.action_artist_details -> detailModel.showArtist(menu.song)
            R.id.action_album_details -> detailModel.showAlbum(menu.song.album)
            R.id.action_share -> requireContext().share(menu.song)
            R.id.action_rate ->
                RatingDialog.from(menu.song).show(parentFragmentManager, RatingDialog.TAG)
            R.id.action_detail -> detailModel.showSong(menu.song)
            R.id.action_open_equalizer -> playbackModel.openEqualizer()
            R.id.action_delete_song ->
                // Only the playback panel has this item, so a playlist being played is the
                // one the song can be removed from.
                DeleteSongsDialog.from(listOf(menu.song), playbackModel.parent.value as? Playlist)
                    .show(parentFragmentManager, DeleteSongsDialog.TAG)
            R.id.action_ringtone -> requireContext().setAsRingtone(menu.song)
            R.id.action_playlist_remove ->
                // Only playlist pages have this item.
                DeleteSongsDialog.from(
                        listOf(menu.song),
                        (menu.playWith as? PlaySong.FromPlaylist)?.which,
                    )
                    .show(parentFragmentManager, DeleteSongsDialog.TAG)
            in barItemIds -> barRequests.run(item.itemId)
            else -> error("Unexpected menu item selected $item")
        }
    }
}

/**
 * [MenuDialogFragment] implementation for a [AlbumMenuDialogFragment].
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class AlbumMenuDialogFragment : MenuDialogFragment<Menu.ForAlbum>() {
    override val menuModel: MenuViewModel by viewModels()
    override val listModel: ListViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val args: AlbumMenuDialogFragmentArgs by navArgs()

    override val parcel
        get() = args.parcel

    // Nothing to disable in album menus.
    override fun getDisabledItemIds(menu: Menu.ForAlbum) = setOf<Int>()

    override fun updateMenu(binding: DialogMenuBinding, menu: Menu.ForAlbum) {
        val context = requireContext()
        binding.menuCover.bind(menu.album)
        binding.menuType.text = menu.album.releaseType.resolve(context)
        binding.menuName.text = menu.album.name.resolve(context)
        binding.menuInfo.text = menu.album.artists.resolveNames(context)
    }

    override fun onClick(item: MenuItem, menu: Menu.ForAlbum) {
        when (item.itemId) {
            R.id.action_play -> playbackModel.play(menu.album)
            R.id.action_shuffle -> playbackModel.shuffle(menu.album)
            R.id.action_detail -> detailModel.showAlbum(menu.album)
            R.id.action_play_next -> {
                playbackModel.playNext(menu.album)
                requireContext().showToast(R.string.lng_play_next)
            }
            R.id.action_queue_add -> {
                playbackModel.addToQueue(menu.album)
                requireContext().showToast(R.string.lng_queue_added)
            }
            R.id.action_artist_details -> detailModel.showArtist(menu.album)
            R.id.action_playlist_add -> musicModel.addToPlaylist(menu.album)
            R.id.action_share -> requireContext().share(menu.album)
            else -> error("Unexpected menu item selected $item")
        }
    }
}

/**
 * [MenuDialogFragment] implementation for a [Artist].
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class ArtistMenuDialogFragment : MenuDialogFragment<Menu.ForArtist>() {
    override val menuModel: MenuViewModel by viewModels()
    override val listModel: ListViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val args: ArtistMenuDialogFragmentArgs by navArgs()

    override val parcel
        get() = args.parcel

    override fun getDisabledItemIds(menu: Menu.ForArtist) =
        if (menu.artist.songs.isEmpty()) {
            // Disable any operations that require some kind of songs to work with, as there won't
            // be any in an empty artist.
            setOf(
                R.id.action_play,
                R.id.action_shuffle,
                R.id.action_play_next,
                R.id.action_queue_add,
                R.id.action_playlist_add,
                R.id.action_share,
            )
        } else {
            setOf()
        }

    override fun updateMenu(binding: DialogMenuBinding, menu: Menu.ForArtist) {
        val context = requireContext()
        binding.menuCover.bind(menu.artist)
        binding.menuType.text = getString(R.string.lbl_artist)
        binding.menuName.text = menu.artist.name.resolve(context)
        binding.menuInfo.text =
            getString(
                R.string.fmt_two,
                if (menu.artist.explicitAlbums.isNotEmpty()) {
                    context.getPlural(R.plurals.fmt_album_count, menu.artist.explicitAlbums.size)
                } else {
                    context.getString(R.string.def_album_count)
                },
                if (menu.artist.songs.isNotEmpty()) {
                    context.getPlural(R.plurals.fmt_song_count, menu.artist.songs.size)
                } else {
                    getString(R.string.def_song_count)
                },
            )
    }

    override fun onClick(item: MenuItem, menu: Menu.ForArtist) {
        when (item.itemId) {
            R.id.action_play -> playbackModel.play(menu.artist)
            R.id.action_shuffle -> playbackModel.shuffle(menu.artist)
            R.id.action_detail -> detailModel.showArtist(menu.artist)
            R.id.action_play_next -> {
                playbackModel.playNext(menu.artist)
                requireContext().showToast(R.string.lng_play_next)
            }
            R.id.action_queue_add -> {
                playbackModel.addToQueue(menu.artist)
                requireContext().showToast(R.string.lng_queue_added)
            }
            R.id.action_playlist_add -> musicModel.addToPlaylist(menu.artist)
            R.id.action_share -> requireContext().share(menu.artist)
            else -> error("Unexpected menu item $item")
        }
    }
}

/**
 * [MenuDialogFragment] implementation for a [Genre].
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class GenreMenuDialogFragment : MenuDialogFragment<Menu.ForGenre>() {
    override val menuModel: MenuViewModel by viewModels()
    override val listModel: ListViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val args: GenreMenuDialogFragmentArgs by navArgs()

    override val parcel
        get() = args.parcel

    override fun getDisabledItemIds(menu: Menu.ForGenre) = setOf<Int>()

    override fun updateMenu(binding: DialogMenuBinding, menu: Menu.ForGenre) {
        val context = requireContext()
        binding.menuCover.bind(menu.genre)
        binding.menuType.text = getString(R.string.lbl_genre)
        binding.menuName.text = menu.genre.name.resolve(context)
        binding.menuInfo.text =
            getString(
                R.string.fmt_two,
                context.getPlural(R.plurals.fmt_artist_count, menu.genre.artists.size),
                context.getPlural(R.plurals.fmt_song_count, menu.genre.songs.size),
            )
    }

    override fun onClick(item: MenuItem, menu: Menu.ForGenre) {
        when (item.itemId) {
            R.id.action_play -> playbackModel.play(menu.genre)
            R.id.action_shuffle -> playbackModel.shuffle(menu.genre)
            R.id.action_detail -> detailModel.showGenre(menu.genre)
            R.id.action_play_next -> {
                playbackModel.playNext(menu.genre)
                requireContext().showToast(R.string.lng_play_next)
            }
            R.id.action_queue_add -> {
                playbackModel.addToQueue(menu.genre)
                requireContext().showToast(R.string.lng_queue_added)
            }
            R.id.action_playlist_add -> musicModel.addToPlaylist(menu.genre)
            R.id.action_share -> requireContext().share(menu.genre)
            else -> error("Unexpected menu item $item")
        }
    }
}

/** [MenuDialogFragment] implementation for a [Composer]. */
@AndroidEntryPoint
class ComposerMenuDialogFragment : MenuDialogFragment<Menu.ForComposer>() {
    override val menuModel: MenuViewModel by viewModels()
    override val listModel: ListViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val args: ComposerMenuDialogFragmentArgs by navArgs()

    override val parcel
        get() = args.parcel

    override fun getDisabledItemIds(menu: Menu.ForComposer) = setOf<Int>()

    override fun updateMenu(binding: DialogMenuBinding, menu: Menu.ForComposer) {
        val context = requireContext()
        binding.menuCover.bind(menu.composer)
        binding.menuType.text = getString(R.string.lbl_composer)
        binding.menuName.text = menu.composer.name.resolve(context)
        binding.menuInfo.text =
            getString(
                R.string.fmt_two,
                context.getPlural(R.plurals.fmt_artist_count, menu.composer.artists.size),
                context.getPlural(R.plurals.fmt_song_count, menu.composer.songs.size),
            )
    }

    override fun onClick(item: MenuItem, menu: Menu.ForComposer) {
        when (item.itemId) {
            R.id.action_play -> playbackModel.play(menu.composer)
            R.id.action_shuffle -> playbackModel.shuffle(menu.composer)
            R.id.action_detail -> detailModel.showComposer(menu.composer)
            R.id.action_play_next -> {
                playbackModel.playNext(menu.composer)
                requireContext().showToast(R.string.lng_play_next)
            }
            R.id.action_queue_add -> {
                playbackModel.addToQueue(menu.composer)
                requireContext().showToast(R.string.lng_queue_added)
            }
            R.id.action_playlist_add -> musicModel.addToPlaylist(menu.composer)
            R.id.action_share -> requireContext().share(menu.composer)
            else -> error("Unexpected menu item $item")
        }
    }
}

/** [MenuDialogFragment] implementation for a [Mood]. */
@AndroidEntryPoint
class MoodMenuDialogFragment : MenuDialogFragment<Menu.ForMood>() {
    override val menuModel: MenuViewModel by viewModels()
    override val listModel: ListViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val args: MoodMenuDialogFragmentArgs by navArgs()

    override val parcel
        get() = args.parcel

    override fun getDisabledItemIds(menu: Menu.ForMood) = setOf<Int>()

    override fun updateMenu(binding: DialogMenuBinding, menu: Menu.ForMood) {
        val context = requireContext()
        binding.menuCover.bind(menu.mood)
        binding.menuType.text = getString(R.string.lbl_mood)
        binding.menuName.text = menu.mood.name.resolve(context)
        binding.menuInfo.text =
            getString(
                R.string.fmt_two,
                context.getPlural(R.plurals.fmt_artist_count, menu.mood.artists.size),
                context.getPlural(R.plurals.fmt_song_count, menu.mood.songs.size),
            )
    }

    override fun onClick(item: MenuItem, menu: Menu.ForMood) {
        when (item.itemId) {
            R.id.action_play -> playbackModel.play(menu.mood)
            R.id.action_shuffle -> playbackModel.shuffle(menu.mood)
            R.id.action_detail -> detailModel.showMood(menu.mood)
            R.id.action_play_next -> {
                playbackModel.playNext(menu.mood)
                requireContext().showToast(R.string.lng_play_next)
            }
            R.id.action_queue_add -> {
                playbackModel.addToQueue(menu.mood)
                requireContext().showToast(R.string.lng_queue_added)
            }
            R.id.action_playlist_add -> musicModel.addToPlaylist(menu.mood)
            R.id.action_share -> requireContext().share(menu.mood)
            else -> error("Unexpected menu item $item")
        }
    }
}

/** [MenuDialogFragment] implementation for a [Grouping]. */
@AndroidEntryPoint
class GroupingMenuDialogFragment : MenuDialogFragment<Menu.ForGrouping>() {
    override val menuModel: MenuViewModel by viewModels()
    override val listModel: ListViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val args: GroupingMenuDialogFragmentArgs by navArgs()

    override val parcel
        get() = args.parcel

    override fun getDisabledItemIds(menu: Menu.ForGrouping) = setOf<Int>()

    override fun updateMenu(binding: DialogMenuBinding, menu: Menu.ForGrouping) {
        val context = requireContext()
        binding.menuCover.bind(menu.grouping)
        binding.menuType.text = getString(R.string.lbl_grouping)
        binding.menuName.text = menu.grouping.name.resolve(context)
        binding.menuInfo.text =
            getString(
                R.string.fmt_two,
                context.getPlural(R.plurals.fmt_artist_count, menu.grouping.artists.size),
                context.getPlural(R.plurals.fmt_song_count, menu.grouping.songs.size),
            )
    }

    override fun onClick(item: MenuItem, menu: Menu.ForGrouping) {
        when (item.itemId) {
            R.id.action_play -> playbackModel.play(menu.grouping)
            R.id.action_shuffle -> playbackModel.shuffle(menu.grouping)
            R.id.action_detail -> detailModel.showGrouping(menu.grouping)
            R.id.action_play_next -> {
                playbackModel.playNext(menu.grouping)
                requireContext().showToast(R.string.lng_play_next)
            }
            R.id.action_queue_add -> {
                playbackModel.addToQueue(menu.grouping)
                requireContext().showToast(R.string.lng_queue_added)
            }
            R.id.action_playlist_add -> musicModel.addToPlaylist(menu.grouping)
            R.id.action_share -> requireContext().share(menu.grouping)
            else -> error("Unexpected menu item $item")
        }
    }
}

/** [MenuDialogFragment] implementation for a [CustomValue]. */
@AndroidEntryPoint
class CustomValueMenuDialogFragment : MenuDialogFragment<Menu.ForCustomValue>() {
    override val menuModel: MenuViewModel by viewModels()
    override val listModel: ListViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val args: CustomValueMenuDialogFragmentArgs by navArgs()

    override val parcel
        get() = args.parcel

    override fun getDisabledItemIds(menu: Menu.ForCustomValue) = setOf<Int>()

    override fun updateMenu(binding: DialogMenuBinding, menu: Menu.ForCustomValue) {
        val context = requireContext()
        binding.menuCover.bind(menu.customValue)
        binding.menuType.text = CustomTag.nameOf(context, menu.customValue.slot)
        binding.menuName.text = menu.customValue.name.resolve(context)
        binding.menuInfo.text =
            getString(
                R.string.fmt_two,
                context.getPlural(R.plurals.fmt_artist_count, menu.customValue.artists.size),
                context.getPlural(R.plurals.fmt_song_count, menu.customValue.songs.size),
            )
    }

    override fun onClick(item: MenuItem, menu: Menu.ForCustomValue) {
        when (item.itemId) {
            R.id.action_play -> playbackModel.play(menu.customValue)
            R.id.action_shuffle -> playbackModel.shuffle(menu.customValue)
            R.id.action_detail -> detailModel.showCustomValue(menu.customValue)
            R.id.action_play_next -> {
                playbackModel.playNext(menu.customValue)
                requireContext().showToast(R.string.lng_play_next)
            }
            R.id.action_queue_add -> {
                playbackModel.addToQueue(menu.customValue)
                requireContext().showToast(R.string.lng_queue_added)
            }
            R.id.action_playlist_add -> musicModel.addToPlaylist(menu.customValue)
            R.id.action_share -> requireContext().share(menu.customValue)
            else -> error("Unexpected menu item $item")
        }
    }
}

/**
 * [MenuDialogFragment] implementation for a [Playlist].
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class PlaylistMenuDialogFragment : MenuDialogFragment<Menu.ForPlaylist>() {
    override val menuModel: MenuViewModel by viewModels()
    override val listModel: ListViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val treeModel: PlaylistTreeViewModel by activityViewModels()
    private val folderModel: PlaylistFolderViewModel by activityViewModels()
    private val args: PlaylistMenuDialogFragmentArgs by navArgs()

    override val parcel
        get() = args.parcel

    override fun getDisabledItemIds(menu: Menu.ForPlaylist): Set<Int> {
        val disabled = mutableSetOf<Int>()
        if (treeModel.isLibrary(menu.playlist)) {
            // Library playlists stay at the top level.
            disabled.add(R.id.action_move)
        }
        if (detailModel.songsOf(menu.playlist).isEmpty()) {
            // Disable any operations that require some kind of songs to work with, as there won't
            // be any in an empty playlist.
            disabled.addAll(
                listOf(
                    R.id.action_play,
                    R.id.action_shuffle,
                    R.id.action_play_next,
                    R.id.action_queue_add,
                    R.id.action_playlist_add,
                    R.id.action_export,
                    R.id.action_share,
                )
            )
        }
        return disabled
    }

    override fun getHiddenItemIds(menu: Menu.ForPlaylist) =
        if (menu.playlist is AutoPlaylist) {
            // An automatic playlist cannot be edited or placed anywhere else.
            setOf(
                R.id.action_rename,
                R.id.action_new_child,
                R.id.action_pin,
                R.id.action_unpin,
                R.id.action_move,
                R.id.action_import,
                R.id.action_import_folder,
                R.id.action_export,
                R.id.action_delete,
                R.id.action_library_on,
                R.id.action_library_off,
            )
        } else {
            val hidden =
                mutableSetOf(
                    if (treeModel.isPinned(menu.playlist)) R.id.action_pin else R.id.action_unpin
                )
            when {
                treeModel.isLibrary(menu.playlist) -> {
                    // A library playlist gets its songs from its children only.
                    hidden.add(R.id.action_library_on)
                    hidden.add(R.id.action_import)
                }
                treeModel.canBeLibrary(menu.playlist) -> hidden.add(R.id.action_library_off)
                else -> {
                    hidden.add(R.id.action_library_on)
                    hidden.add(R.id.action_library_off)
                }
            }
            hidden
        }

    override fun updateMenu(binding: DialogMenuBinding, menu: Menu.ForPlaylist) {
        val context = requireContext()
        binding.menuCover.bind(menu.playlist)
        binding.menuType.text = getString(R.string.lbl_playlist)
        binding.menuName.text = menu.playlist.name.resolve(context)
        val songs = detailModel.songsOf(menu.playlist)
        binding.menuInfo.text =
            if (songs.isNotEmpty()) {
                context.getPlural(R.plurals.fmt_song_count, songs.size)
            } else {
                getString(R.string.def_song_count)
            }
    }

    override fun onClick(item: MenuItem, menu: Menu.ForPlaylist) {
        when (item.itemId) {
            R.id.action_play -> playbackModel.play(menu.playlist)
            R.id.action_shuffle -> playbackModel.shuffle(menu.playlist)
            R.id.action_detail -> detailModel.showPlaylist(menu.playlist)
            R.id.action_play_next -> {
                playbackModel.playNext(menu.playlist)
                requireContext().showToast(R.string.lng_play_next)
            }
            R.id.action_queue_add -> {
                playbackModel.addToQueue(menu.playlist)
                requireContext().showToast(R.string.lng_queue_added)
            }
            R.id.action_rename -> musicModel.renamePlaylist(menu.playlist)
            R.id.action_move -> treeModel.requestMove(listOf(menu.playlist))
            R.id.action_pin -> treeModel.setPinned(listOf(menu.playlist), true)
            R.id.action_unpin -> treeModel.setPinned(listOf(menu.playlist), false)
            R.id.action_library_on ->
                treeModel.makeLibrary(
                    menu.playlist,
                    getString(R.string.fmt_library_own_songs, menu.playlist.name.raw),
                )
            R.id.action_library_off -> treeModel.stopLibrary(menu.playlist)
            R.id.action_new_child -> musicModel.createPlaylist(parent = menu.playlist)
            R.id.action_import -> musicModel.importPlaylist(target = menu.playlist)
            R.id.action_import_folder -> folderModel.requestImport(menu.playlist)
            R.id.action_export -> musicModel.exportPlaylist(menu.playlist)
            R.id.action_delete -> musicModel.deletePlaylist(menu.playlist)
            R.id.action_share -> requireContext().share(menu.playlist)
            else -> error("Unexpected menu item $item")
        }
    }
}

/**
 * [MenuDialogFragment] implementation for a [Song] selection.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class SelectionMenuDialogFragment : MenuDialogFragment<Menu.ForSelection>() {
    override val menuModel: MenuViewModel by activityViewModels()
    override val listModel: ListViewModel by activityViewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val treeModel: PlaylistTreeViewModel by activityViewModels()
    private val args: SelectionMenuDialogFragmentArgs by navArgs()

    override val parcel
        get() = args.parcel

    // A selection of empty playlists has no songs to act on.
    override fun getDisabledItemIds(menu: Menu.ForSelection) =
        if (menu.songs.isEmpty()) {
            setOf(
                R.id.action_play,
                R.id.action_shuffle,
                R.id.action_play_next,
                R.id.action_queue_add,
                R.id.action_playlist_add,
                R.id.action_share,
            )
        } else {
            setOf()
        }

    override fun getHiddenItemIds(menu: Menu.ForSelection) =
        when {
            // The menu for a selection without playlists has neither item.
            menu.playlists.isEmpty() -> setOf()
            // Pin while any selected playlist is not pinned yet.
            menu.playlists.all(treeModel::isPinned) -> setOf(R.id.action_pin)
            else -> setOf(R.id.action_unpin)
        }

    override fun updateMenu(binding: DialogMenuBinding, menu: Menu.ForSelection) {
        val onlyPlaylists = menu.songs.isEmpty() && menu.playlists.isNotEmpty()
        binding.menuCover.bind(
            menu.songs,
            getString(R.string.desc_selection_image),
            if (onlyPlaylists) R.drawable.ic_playlist_24 else R.drawable.ic_song_24,
        )
        binding.menuType.text = getString(R.string.lbl_selection)
        binding.menuName.text =
            if (onlyPlaylists) {
                requireContext().getPlural(R.plurals.fmt_playlist_count, menu.playlists.size)
            } else {
                requireContext().getPlural(R.plurals.fmt_song_count, menu.songs.size)
            }
        binding.menuInfo.text = menu.songs.sumOf { it.durationMs }.formatDurationMs(true)
    }

    override fun onClick(item: MenuItem, menu: Menu.ForSelection) {
        listModel.dropSelection()
        when (item.itemId) {
            R.id.action_play -> playbackModel.play(menu.songs)
            R.id.action_shuffle -> playbackModel.shuffle(menu.songs)
            R.id.action_play_next -> {
                playbackModel.playNext(menu.songs)
                requireContext().showToast(R.string.lng_play_next)
            }
            R.id.action_queue_add -> {
                playbackModel.addToQueue(menu.songs)
                requireContext().showToast(R.string.lng_queue_added)
            }
            R.id.action_playlist_add -> musicModel.addToPlaylist(menu.songs)
            R.id.action_edit_moods ->
                MoodEditDialog.from(menu.songs).show(parentFragmentManager, MoodEditDialog.TAG)
            R.id.action_share -> requireContext().share(menu.songs)
            R.id.action_pin -> treeModel.setPinned(menu.playlists, true)
            R.id.action_unpin -> treeModel.setPinned(menu.playlists, false)
            R.id.action_delete -> musicModel.deletePlaylists(menu.playlists)
            else -> error("Unexpected menu item selected $item")
        }
    }
}
