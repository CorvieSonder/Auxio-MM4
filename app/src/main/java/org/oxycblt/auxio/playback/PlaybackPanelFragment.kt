/*
 * Copyright (c) 2021 Auxio Project
 * PlaybackPanelFragment.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.view.updatePadding
import androidx.dynamicanimation.animation.SpringForce
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.FragmentPlaybackPanelBinding
import org.oxycblt.auxio.detail.DetailViewModel
import org.oxycblt.auxio.home.HomeViewModel
import org.oxycblt.auxio.list.ListViewModel
import org.oxycblt.auxio.music.MusicViewModel
import org.oxycblt.auxio.music.PlaylistDecision
import org.oxycblt.auxio.music.delete.DeleteSongsDialog
import org.oxycblt.auxio.music.mood.MoodEditDialog
import org.oxycblt.auxio.music.rating.RatingDialog
import org.oxycblt.auxio.music.rating.RatingRepository
import org.oxycblt.auxio.music.resolve
import org.oxycblt.auxio.music.resolveNames
import org.oxycblt.auxio.music.ringtone.setAsRingtone
import org.oxycblt.auxio.playback.bar.NowPlayingBar
import org.oxycblt.auxio.playback.bar.NowPlayingBarRequests
import org.oxycblt.auxio.playback.bar.NowPlayingBarSettings
import org.oxycblt.auxio.playback.queue.QueueViewModel
import org.oxycblt.auxio.playback.quick.QuickSettingsSheet
import org.oxycblt.auxio.playback.sleep.SleepTimer
import org.oxycblt.auxio.playback.sleep.SleepTimerDialog
import org.oxycblt.auxio.playback.state.RepeatMode
import org.oxycblt.auxio.playback.ui.StyledSeekBar
import org.oxycblt.auxio.playback.ui.stepper.Direction
import org.oxycblt.auxio.playback.ui.stepper.StepperOverlay
import org.oxycblt.auxio.playback.ui.swiper.CarouselTransformer
import org.oxycblt.auxio.playback.ui.swiper.CoverPagerAdapter
import org.oxycblt.auxio.playback.ui.swiper.UserAwarePagerCallback
import org.oxycblt.auxio.ui.ViewBindingFragment
import org.oxycblt.auxio.util.collect
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.auxio.util.dampen
import org.oxycblt.auxio.util.recycler
import org.oxycblt.auxio.util.share
import org.oxycblt.auxio.util.showToast
import org.oxycblt.auxio.util.smoothScrollByPageTo
import org.oxycblt.auxio.util.systemBarInsetsCompat
import org.oxycblt.musikr.MusicParent
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.tag.Name
import timber.log.Timber as L

/**
 * A [ViewBindingFragment] more information about the currently playing song, alongside all
 * available controls.
 *
 * @author Alexander Capehart (OxygenCobalt)
 *
 * TODO: Improve flickering situation on play button
 */
@AndroidEntryPoint
class PlaybackPanelFragment :
    ViewBindingFragment<FragmentPlaybackPanelBinding>(),
    Toolbar.OnMenuItemClickListener,
    StyledSeekBar.Listener,
    StepperOverlay.Listener,
    RatingRepository.Listener,
    NowPlayingBarSettings.Listener {
    private val coverPagerAdapter = CoverPagerAdapter(this)
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()
    private val listModel: ListViewModel by activityViewModels()
    private val queueModel: QueueViewModel by viewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val homeModel: HomeViewModel by activityViewModels()
    @Inject lateinit var sleepTimer: SleepTimer
    @Inject lateinit var ratingRepository: RatingRepository
    @Inject lateinit var barSettings: NowPlayingBarSettings
    @Inject lateinit var barRequests: NowPlayingBarRequests
    private var equalizerLauncher: ActivityResultLauncher<Intent>? = null
    private var userAwarePagerCallback: UserAwarePagerCallback? = null

    override fun onCreateBinding(inflater: LayoutInflater) =
        FragmentPlaybackPanelBinding.inflate(inflater)

    override fun onBindingCreated(
        binding: FragmentPlaybackPanelBinding,
        savedInstanceState: Bundle?,
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        // AudioEffect expects you to use startActivityForResult with the panel intent. There is no
        // contract analogue for this intent, so the generic contract is used instead.
        equalizerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                // Nothing to do
            }

        // --- UI SETUP ---
        binding.root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.systemBarInsetsCompat
            view.updatePadding(bottom = bars.bottom)
            insets
        }

        binding.playbackToolbar.apply {
            setNavigationOnClickListener { playbackModel.openMain() }
            setOnMenuItemClickListener(this@PlaybackPanelFragment)
            // The actions sit in their own row below the title.
            setActionsBelow()
        }
        binding.playbackToolbar.setOverflowIntoMenu {
            // The song menu lists the actions that are hidden or do not fit.
            barRequests.onBar = binding.playbackToolbar.actionItemIds
        }
        applyBarActions()
        barSettings.registerListener(this)

        binding.playbackPager?.apply {
            // intentional LTR override since thepager is a chronological element
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            adapter = coverPagerAdapter
            userAwarePagerCallback =
                UserAwarePagerCallback(this) {
                        // Posting the queue goto command prevents the seekbar pos from desyncing
                        // from the song's duration, which creates a visual flicker in the seekbar.
                        post { queueModel.goto(it) }
                    }
                    .also { it.attach() }
            setPageTransformer(CarouselTransformer())
            recycler().apply {
                // Make it possible to collapse the bottom sheet from the ViewPager's touch area.
                isNestedScrollingEnabled = false
                // Visual effect consistency
                // TODO: Custom overscroll?
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            // Make it easier to collapse the bottom sheet
            dampen()
            offscreenPageLimit = 1
        }

        // Set up fast seek overlay
        binding.playbackSong.apply {
            isSelected = true
            setOnClickListener { navigateToCurrentSong() }
        }
        binding.playbackArtist.apply {
            isSelected = true
            setOnClickListener { navigateToCurrentArtist() }
        }
        binding.playbackAlbum?.apply {
            isSelected = true
            setOnClickListener { navigateToCurrentAlbum() }
        }

        binding.playbackSeekBar?.listener = this

        // Set up actions
        // TODO: Add better playback button accessibility
        binding.playbackRepeat.setOnClickListener { playbackModel.toggleRepeatMode() }
        binding.playbackSkipPrev.setOnClickListener { playbackModel.prev() }
        binding.playbackPlayPause.apply {
            @SuppressLint("RestrictedApi")
            setCornerSpringForce(
                SpringForce().apply {
                    stiffness = 700f
                    dampingRatio = 0.9f
                }
            )
            setOnClickListener { playbackModel.togglePlaying() }
        }
        binding.playbackSkipNext.setOnClickListener { playbackModel.next() }
        binding.playbackShuffle.setOnClickListener { playbackModel.toggleShuffled() }
        binding.playbackMore?.setOnClickListener { openSongMenu() }

        // --- VIEWMODEL SETUP --
        collectImmediately(playbackModel.song, ::updateSong)
        collectImmediately(playbackModel.parent, ::updateParent)
        collectImmediately(playbackModel.positionDs, ::updatePosition)
        collectImmediately(playbackModel.repeatMode, ::updateRepeat)
        collectImmediately(playbackModel.isPlaying, ::updatePlaying)
        collectImmediately(playbackModel.isShuffled, ::updateShuffled)
        collectImmediately(playbackModel.pagerQueue, ::updatePager)
        collectImmediately(sleepTimer.state, ::updateSleepTimer)
        ratingRepository.addListener(this)
        collect(playbackModel.equalizerRequest.flow, ::handleEqualizerRequest)
        collect(barRequests.request.flow, ::handleBarRequest)
    }

    // FIXME: Old code!! Maybe not necessary anymore?
    //    override fun onStart() {
    //        super.onStart()
    //        playbackModel.song.value?.let { requireBinding().playbackCover.bind(it) }
    //        requireBinding().root.viewTreeObserver.addOnGlobalLayoutListener(this)
    //    }

    //    override fun onStop() {
    //        super.onStop()
    //        requireBinding().root.viewTreeObserver.removeOnGlobalLayoutListener(this)
    //    }

    //    override fun onGlobalLayout() {
    //        if (binding == null || lastCoverWidth < 0) {
    //            return
    //        }
    // Hacky workaround for cover radius not being preserved in between sizing changes
    // (i.e split screen or landscape mode)
    // For some reason ConstraintLayout does several passes on 1:1 elements that causes their
    // size to radically change, so we wait until it stabilizes and then force an image
    // reload if needed. Optimistically this is a no-op from coil caching, but when the cover
    // did accidentally load the wrong image (with weird corner radius intended for bigger
    // covers) we can force it to reload.
    // If this breaks, it's fine since we also started a load as we normally did w/state
    // updates, so the cover will not break.
    //        val binding = requireBinding()
    //        val coverWidth = binding.playbackCover.width
    //        if (lastCoverWidth != coverWidth) {
    //            lastCoverWidth = coverWidth
    //        } else {
    //            playbackModel.song.value?.let { binding.playbackCover.bind(it) }
    //            lastCoverWidth = -1
    //        }
    //    }

    override fun onDestroyBinding(binding: FragmentPlaybackPanelBinding) {
        ratingRepository.removeListener(this)
        barSettings.unregisterListener(this)
        equalizerLauncher = null
        binding.playbackRepeat.clearPendingIcon()
        binding.playbackSong.isSelected = false
        binding.playbackArtist.isSelected = false
        binding.playbackAlbum?.isSelected = false
        binding.playbackToolbar.setOnMenuItemClickListener(null)
        userAwarePagerCallback?.release()
        binding.playbackPager?.adapter = null
    }

    override fun onBarActionsChanged() {
        applyBarActions()
    }

    /** Show the bar actions in the order chosen by the user, leaving out the hidden ones. */
    private fun applyBarActions() {
        val context = requireContext()
        val toolbar = requireBinding().playbackToolbar
        val menu = NowPlayingBar.inflate(context)
        val itemIds = NowPlayingBar.shownItemIds(context, menu, barSettings.barActions)
        toolbar.arrangeMenuItems(menu, itemIds)
        // The last actions give way first when they do not all fit.
        toolbar.setOverflowPriority(itemIds.filter { it != NowPlayingBar.MORE_ITEM_ID }.reversed())
        // A new menu starts with the default icons.
        updateSleepTimer(sleepTimer.state.value)
        updateRating(playbackModel.song.value)
    }

    private fun handleBarRequest(itemId: Int?) {
        if (itemId != null) {
            barRequests.request.consume()
            runAction(itemId)
        }
    }

    override fun onMenuItemClick(item: MenuItem) = runAction(item.itemId)

    /**
     * Run a bar action, from the bar or from the song menu it was moved into.
     *
     * @param itemId The menu item ID of the action.
     * @return Whether the action was known.
     */
    private fun runAction(itemId: Int): Boolean {
        when (itemId) {
            R.id.action_quick_settings ->
                QuickSettingsSheet().show(childFragmentManager, QuickSettingsSheet.TAG)
            R.id.action_sleep_timer ->
                SleepTimerDialog().show(childFragmentManager, SleepTimerDialog.TAG)
            R.id.action_playlist_add -> playbackModel.song.value?.let(musicModel::addToPlaylist)
            R.id.action_artist_details -> navigateToCurrentArtist()
            R.id.action_album_details -> navigateToCurrentAlbum()
            R.id.action_genre_details -> navigateToCurrentGenre()
            R.id.action_save_queue ->
                musicModel.createPlaylist(
                    songs = playbackModel.queue,
                    reason = PlaylistDecision.New.Reason.SAVE_QUEUE,
                )
            R.id.action_settings -> homeModel.showSettings()
            R.id.action_rate ->
                playbackModel.song.value?.let {
                    RatingDialog.from(it).show(childFragmentManager, RatingDialog.TAG)
                }
            R.id.action_share -> playbackModel.song.value?.let { requireContext().share(it) }
            // Song menu actions that the user moved onto the bar.
            R.id.action_detail -> playbackModel.song.value?.let(detailModel::showSong)
            R.id.action_open_equalizer -> openEqualizer()
            R.id.action_edit_moods ->
                playbackModel.song.value?.let {
                    MoodEditDialog.from(listOf(it)).show(childFragmentManager, MoodEditDialog.TAG)
                }
            R.id.action_ringtone ->
                playbackModel.song.value?.let { requireContext().setAsRingtone(it) }
            R.id.action_delete_song ->
                playbackModel.song.value?.let {
                    DeleteSongsDialog.from(listOf(it), playbackModel.parent.value as? Playlist)
                        .show(childFragmentManager, DeleteSongsDialog.TAG)
                }
            R.id.action_song_menu -> openSongMenu()
            else -> return false
        }
        return true
    }

    private fun navigateToCurrentGenre() {
        val song = playbackModel.song.value ?: return
        // A song without a genre tag is still given a placeholder genre.
        val genres = song.genres.filter { it.name is Name.Known }
        when (genres.size) {
            0 -> requireContext().showToast(R.string.err_no_genres)
            1 -> detailModel.showGenre(genres.first())
            else -> {
                val context = requireContext()
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.lbl_genres)
                    .setItems(genres.map { it.name.resolve(context) }.toTypedArray()) { _, which ->
                        detailModel.showGenre(genres[which])
                    }
                    .setNegativeButton(R.string.lbl_cancel, null)
                    .show()
            }
        }
    }

    private fun openSongMenu() {
        playbackModel.song.value?.let {
            listModel.openMenu(R.menu.playback_song, it, PlaySong.ByItself)
        }
    }

    private fun handleEqualizerRequest(request: Unit?) {
        if (request != null) {
            playbackModel.equalizerRequest.consume()
            openEqualizer()
        }
    }

    private fun openEqualizer() {
        // Launch the system equalizer app, if possible.
        L.d("Launching equalizer")
        val equalizerIntent =
            Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                // Provide audio session ID so the equalizer can show options for this app
                // in particular.
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, playbackModel.currentAudioSessionId)
                // Signal music type so that the equalizer settings are appropriate for
                // music playback.
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        try {
            requireNotNull(equalizerLauncher) { "Equalizer panel launcher was not available" }
                .launch(equalizerIntent)
        } catch (e: ActivityNotFoundException) {
            requireContext().showToast(R.string.err_no_app)
        }
    }

    override fun onRatingsChanged() {
        updateRating(playbackModel.song.value)
    }

    private fun updateRating(song: Song?) {
        requireBinding()
            .playbackToolbar
            .setMenuItemIcon(
                R.id.action_rate,
                if (song?.let(ratingRepository::ratingOf) != null) {
                    ratingRepository.symbol.icon
                } else {
                    ratingRepository.symbol.outlineIcon
                },
            )
    }

    private fun updateSleepTimer(state: SleepTimer.State) {
        requireBinding()
            .playbackToolbar
            .setMenuItemIcon(
                R.id.action_sleep_timer,
                if (state is SleepTimer.State.Off) {
                    R.drawable.ic_sleep_24
                } else {
                    R.drawable.ic_sleep_on_24
                },
            )
    }

    override fun onSeekConfirmed(positionDs: Long) {
        playbackModel.seekTo(positionDs)
    }

    private fun updateSong(song: Song?) {
        if (song == null) {
            // Nothing to do.
            return
        }

        val binding = requireBinding()
        val context = requireContext()
        L.d("Updating song display: $song")
        updateRating(song)
        binding.playbackSong.text = song.name.resolve(context)
        binding.playbackArtist.text = song.artists.resolveNames(context)
        binding.playbackAlbum?.text = song.album.name.resolve(context)
        binding.playbackSeekBar?.durationDs = song.durationMs.msToDs()
    }

    private fun updateParent(parent: MusicParent?) {
        val binding = requireBinding()
        val context = requireContext()
        binding.playbackToolbar.subtitle =
            parent?.run { name.resolve(context) } ?: context.getString(R.string.lbl_all_songs)
    }

    private fun updatePosition(positionDs: Long) {
        requireBinding().playbackSeekBar?.positionDs = positionDs
    }

    private fun updateRepeat(repeatMode: RepeatMode) {
        val repeatButton = requireBinding().playbackRepeat
        repeatButton.isChecked = repeatMode != RepeatMode.NONE
        repeatButton.setIconResource(repeatMode.icon)
    }

    private fun updatePlaying(isPlaying: Boolean) {
        requireBinding().playbackPlayPause.isChecked = isPlaying
        requireBinding().playbackSeekBar?.setWaveEnabled(isPlaying)
    }

    private fun updateShuffled(isShuffled: Boolean) {
        requireBinding().playbackShuffle.isChecked = isShuffled
    }

    private fun updatePager(queue: PagerQueue) {
        // Right now there's easily 140ms of frame skipping when going next/prev. This is primarily
        // the fault of specifically the nested bottom sheet UI setup, which is intractable to
        // optimize. If I don't do multiple remeasures/relayouts on every slightest state
        // instability
        // I will suddenly encounter insane issues where the sheet fails to measure, appears below
        // the sidebar, flies away, not changing with ui scale, etc, often only on third-party OEM
        // ROMs that randomly mangle  SDK APIs and the SystemUI chrome for no reason.
        //
        // Historically this was not an issue, as I did not animate next/prev. Now I do, and it's
        // highly noticeable. So at least for plain next/prev I have to hack around it, do not
        // execute any transition until the state has fully adjudicated and laid out the UI. It's
        // not effective for swiping but there's nothing I can do there.
        //
        // Eventually one day Claude Fable 6.7 will probably be able to figure out that you need to
        // reflect into System.FoobaCrumbo::beegieConnector(GoolaUtils.PlubBud) and call it
        // specifically with 0x189B31FA alongside disabling the AndroidX Helpo SuperCharge by
        // manually clobbering `BottomSheetM2InternalBoogieCompat::scrimbloManager` to null for it
        // to not actually randomly mangle the sheets and do it in 1 clean layout, but for now I
        // must do this to keep my sanity.
        //
        // Actual snippet here was codex, just cleaned & adapted it / cognitive ownership
        requireBinding().playbackPager.apply {
            if (!isAttachedToWindow) {
                post { updatePagerImpl(queue) }
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isHardwareAccelerated) {
                // New version using post-Q frame hooks
                viewTreeObserver.registerFrameCommitCallback {
                    post { postOnAnimation { updatePagerImpl(queue) } }
                }
                postInvalidateOnAnimation()
            } else {
                // Let current layout happen, then wait for the next to conclude
                postOnAnimation { postOnAnimation { updatePagerImpl(queue) } }
            }
        }
    }

    private fun updatePagerImpl(queue: PagerQueue) {
        // Android insanity means this may be executed after view destruction
        // but only on some devices.
        val binding = binding ?: return

        val command = playbackModel.pagerCommand.consume()
        if (command == null) {
            // This probably shouldn't happen in practice, as QueueViewModel directly
            // attaches to PlaybackStateManager and will basically always initialize
            // with a command as a result.
            //
            // If it does happen we should just make sure the UI state is aligned. Don't
            // want broken UI.
            coverPagerAdapter.update(queue.queue, null)
            binding.playbackPager.setCurrentItem(queue.index, false)
            return
        }

        if (command.update != null) {
            // queue needs to be updated.
            coverPagerAdapter.update(queue.queue, command.update)
        }

        if (command.scroll != null) {
            // we need to scroll, however the smooth scroll only really looks best
            // when we are only doing next/prev due to various factors. better to
            // just not animate on outright gotos or queue updates
            val delta = binding.playbackPager.currentItem - command.scroll
            if (delta == 0) {
                // user scroll, carry on
                return
            }
            if (command.update == null && abs(delta) == 1) {
                binding.playbackPager.smoothScrollByPageTo(command.scroll)
            } else {
                binding.playbackPager.setCurrentItem(command.scroll, false)
            }
        }
    }

    private fun navigateToCurrentSong() {
        playbackModel.song.value?.let(detailModel::showAlbum)
    }

    private fun navigateToCurrentArtist() {
        playbackModel.song.value?.let(detailModel::showArtist)
    }

    private fun navigateToCurrentAlbum() {
        playbackModel.song.value?.let { detailModel.showAlbum(it.album) }
    }

    override fun seek(direction: Direction) {
        when (direction) {
            Direction.FORWARDS -> playbackModel.stepForward()
            Direction.BACKWARDS -> playbackModel.stepBackwards()
        }
    }
}
