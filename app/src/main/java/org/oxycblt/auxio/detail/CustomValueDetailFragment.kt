/*
 * Copyright (c) 2026 Auxio Project
 * CustomValueDetailFragment.kt is part of Auxio.
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

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.FragmentDetailBinding
import org.oxycblt.auxio.detail.list.GenreDetailListAdapter
import org.oxycblt.auxio.list.Item
import org.oxycblt.auxio.list.ListFragment
import org.oxycblt.auxio.list.menu.Menu
import org.oxycblt.auxio.music.PlaylistDecision
import org.oxycblt.auxio.music.PlaylistMessage
import org.oxycblt.auxio.music.resolve
import org.oxycblt.auxio.music.tags.CustomTag
import org.oxycblt.auxio.playback.PlaybackDecision
import org.oxycblt.auxio.util.collect
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.auxio.util.getPlural
import org.oxycblt.auxio.util.navigateSafe
import org.oxycblt.auxio.util.showToast
import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.CustomValue
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.MusicParent
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/** A [ListFragment] that shows information for a particular [CustomValue]. */
@AndroidEntryPoint
class CustomValueDetailFragment : DetailFragment<CustomValue, Music>() {
    // Information about what custom value to display is initially within the navigation arguments
    // as a UID, as that is the only safe way to parcel an custom value.
    private val args: CustomValueDetailFragmentArgs by navArgs()
    private val customValueListAdapter = GenreDetailListAdapter(this)

    override fun getDetailListAdapter() = customValueListAdapter

    override fun getToolbarParent() = detailModel.currentCustomValue.value

    override fun onBindingCreated(binding: FragmentDetailBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        // --- VIEWMODEL SETUP ---
        // DetailViewModel handles most initialization from the navigation argument.
        detailModel.setCustomValue(args.customValueUid)
        collectImmediately(detailModel.currentCustomValue, ::updateCustomValue)
        collectImmediately(detailModel.customValueSongList, ::updateList)
        collect(detailModel.toShow.flow, ::handleShow)
        collect(listModel.menu.flow, ::handleMenu)
        collectImmediately(listModel.selected, ::updateSelection)
        collect(musicModel.playlistDecision.flow, ::handlePlaylistDecision)
        collect(musicModel.playlistMessage.flow, ::handlePlaylistMessage)
        collectImmediately(
            playbackModel.song,
            playbackModel.parent,
            playbackModel.isPlaying,
            ::updatePlayback,
        )
        collect(playbackModel.playbackDecision.flow, ::handlePlaybackDecision)
    }

    override fun onDestroyBinding(binding: FragmentDetailBinding) {
        super.onDestroyBinding(binding)
        // Avoid possible race conditions that could cause a bad replace instruction to be consumed
        // during list initialization and crash the app. Could happen if the user is fast enough.
        detailModel.customValueSongInstructions.consume()
    }

    override fun onPlayParent(parent: CustomValue) {
        playbackModel.play(parent)
    }

    override fun onShuffleParent(parent: CustomValue) {
        playbackModel.shuffle(parent)
    }

    override fun onRealClick(item: Music) {
        when (item) {
            is Artist -> detailModel.showArtist(item)
            is Song -> playbackModel.play(item, detailModel.playInCustomValueWith)
            else -> error("Unexpected datatype: ${item::class.simpleName}")
        }
    }

    override fun onOpenParentMenu() {
        val customValue = detailModel.currentCustomValue.value ?: return
        listModel.openMenu(R.menu.detail_parent, customValue)
    }

    override fun onOpenMenu(item: Music) {
        when (item) {
            is Artist -> listModel.openMenu(R.menu.parent, item)
            is Song -> listModel.openMenu(R.menu.song, item, detailModel.playInCustomValueWith)
            else -> error("Unexpected datatype: ${item::class.simpleName}")
        }
    }

    override fun onOpenSortMenu() {
        findNavController().navigateSafe(CustomValueDetailFragmentDirections.sort())
    }

    private fun updateCustomValue(customValue: CustomValue?) {
        if (customValue == null) {
            L.d("No customValue to show, navigating away")
            findNavController().navigateUp()
            return
        }
        val binding = requireBinding()
        val context = requireContext()
        val name = customValue.name.resolve(context)
        binding.detailNormalToolbar.title = name
        binding.detailCover.bind(customValue)
        binding.detailType.text = CustomTag.nameOf(context, customValue.slot)
        binding.detailName.text = customValue.name.resolve(context)
        // Nothing about a custom value is applicable to the sub-head text.
        binding.detailSubhead.isVisible = false
        // The song and artist count of the custom value maps to the info text.
        binding.detailInfo.text =
            context.getString(
                R.string.fmt_two,
                context.getPlural(R.plurals.fmt_artist_count, customValue.artists.size),
                context.getPlural(R.plurals.fmt_song_count, customValue.songs.size),
            )
        binding.detailPlayButton?.setOnClickListener { playbackModel.play(customValue) }
        binding.detailShuffleButton?.setOnClickListener { playbackModel.shuffle(customValue) }
        setToolbarPlaybackButtonsEnabled(true)
        updatePlayback(
            playbackModel.song.value,
            playbackModel.parent.value,
            playbackModel.isPlaying.value,
        )
    }

    private fun updateList(list: List<Item>) {
        customValueListAdapter.update(list, detailModel.customValueSongInstructions.consume())
    }

    private fun handleShow(show: Show?) {
        when (show) {
            is Show.SongDetails -> {
                L.d("Navigating to ${show.song}")
                findNavController()
                    .navigateSafe(CustomValueDetailFragmentDirections.showSong(show.song.uid))
            }

            // Songs should be scrolled to if the album matches, or a new detail
            // fragment should be launched otherwise.
            is Show.SongAlbumDetails -> {
                L.d("Navigating to the album of ${show.song}")
                findNavController()
                    .navigateSafe(
                        CustomValueDetailFragmentDirections.showAlbum(show.song.album.uid)
                    )
            }

            // If the album matches, no need to do anything. Otherwise launch a new
            // detail fragment.
            is Show.AlbumDetails -> {
                L.d("Navigating to ${show.album}")
                findNavController()
                    .navigateSafe(CustomValueDetailFragmentDirections.showAlbum(show.album.uid))
            }

            // Always launch a new ArtistDetailFragment.
            is Show.ArtistDetails -> {
                L.d("Navigating to ${show.artist}")
                findNavController()
                    .navigateSafe(CustomValueDetailFragmentDirections.showArtist(show.artist.uid))
            }
            is Show.SongArtistDecision -> {
                L.d("Navigating to artist choices for ${show.song}")
                findNavController()
                    .navigateSafe(
                        CustomValueDetailFragmentDirections.showArtistChoices(show.song.uid)
                    )
            }
            is Show.AlbumArtistDecision -> {
                L.d("Navigating to artist choices for ${show.album}")
                findNavController()
                    .navigateSafe(
                        CustomValueDetailFragmentDirections.showArtistChoices(show.album.uid)
                    )
            }
            is Show.CustomValueDetails -> {
                L.d("Navigated to this customValue")
                detailModel.toShow.consume()
            }
            is Show.GroupingDetails -> {
                L.d("Navigating to ${show.grouping}")
                findNavController()
                    .navigateSafe(
                        CustomValueDetailFragmentDirections.showGrouping(show.grouping.uid)
                    )
            }
            is Show.MoodDetails -> {
                L.d("Navigating to ${show.mood}")
                findNavController()
                    .navigateSafe(CustomValueDetailFragmentDirections.showMood(show.mood.uid))
            }
            is Show.ComposerDetails -> {
                L.d("Navigating to ${show.composer}")
                findNavController()
                    .navigateSafe(
                        CustomValueDetailFragmentDirections.showComposer(show.composer.uid)
                    )
            }
            is Show.GenreDetails -> {
                L.d("Navigating to ${show.genre}")
                findNavController()
                    .navigateSafe(CustomValueDetailFragmentDirections.showGenre(show.genre.uid))
            }
            is Show.PlaylistDetails -> {
                error("Unexpected show command $show")
            }
            null -> {}
        }
    }

    private fun handleMenu(menu: Menu?) {
        if (menu == null) return
        val directions =
            when (menu) {
                is Menu.ForSong -> CustomValueDetailFragmentDirections.openSongMenu(menu.parcel)
                is Menu.ForArtist -> CustomValueDetailFragmentDirections.openArtistMenu(menu.parcel)
                is Menu.ForCustomValue ->
                    CustomValueDetailFragmentDirections.openCustomValueMenu(menu.parcel)
                is Menu.ForSelection ->
                    CustomValueDetailFragmentDirections.openSelectionMenu(menu.parcel)
                is Menu.ForAlbum,
                is Menu.ForGenre,
                is Menu.ForComposer,
                is Menu.ForMood,
                is Menu.ForGrouping,
                is Menu.ForPlaylist -> error("Unexpected menu $menu")
            }
        findNavController().navigateSafe(directions)
    }

    private fun updateSelection(selected: List<Music>) {
        customValueListAdapter.setSelected(selected.toSet())

        val binding = requireBinding()
        if (selected.isNotEmpty()) {
            binding.detailSelectionToolbar.title = getString(R.string.fmt_selected, selected.size)
            binding.detailToolbar.setVisible(R.id.detail_selection_toolbar)
        } else {
            binding.detailToolbar.setVisible(R.id.detail_normal_toolbar)
        }
    }

    private fun handlePlaylistDecision(decision: PlaylistDecision?) {
        if (decision == null) return
        val directions =
            when (decision) {
                is PlaylistDecision.Add -> {
                    L.d("Adding ${decision.songs.size} songs to a playlist")
                    CustomValueDetailFragmentDirections.addToPlaylist(
                        decision.songs.map { it.uid }.toTypedArray()
                    )
                }
                is PlaylistDecision.New,
                is PlaylistDecision.Import,
                is PlaylistDecision.Rename,
                is PlaylistDecision.Export,
                is PlaylistDecision.Delete -> error("Unexpected playlist decision $decision")
            }
        findNavController().navigateSafe(directions)
    }

    private fun handlePlaylistMessage(message: PlaylistMessage?) {
        if (message == null) return
        requireContext().showToast(message.stringRes)
        musicModel.playlistMessage.consume()
    }

    private fun updatePlayback(song: Song?, parent: MusicParent?, isPlaying: Boolean) {
        val currentCustomValue = detailModel.currentCustomValue.value ?: return
        val playingItem =
            when (parent) {
                // Always highlight a playing artist if it's from this custom value, and if the
                // currently
                // playing song is contained within.
                is Artist -> parent.takeIf { song?.run { artists.contains(it) } ?: false }
                // If the parent is the artist itself, use the currently playing song.
                currentCustomValue -> song
                // Nothing is playing from this artist.
                else -> null
            }
        customValueListAdapter.setPlaying(playingItem, isPlaying)
    }

    private fun handlePlaybackDecision(decision: PlaybackDecision?) {
        if (decision == null) return
        val directions =
            when (decision) {
                is PlaybackDecision.PlayFromArtist -> {
                    L.d("Launching play from artist dialog for $decision")
                    CustomValueDetailFragmentDirections.playFromArtist(decision.song.uid)
                }
                is PlaybackDecision.PlayFromGenre -> {
                    L.d("Launching play from genre dialog for $decision")
                    CustomValueDetailFragmentDirections.playFromGenre(decision.song.uid)
                }
            }
        findNavController().navigateSafe(directions)
    }
}
