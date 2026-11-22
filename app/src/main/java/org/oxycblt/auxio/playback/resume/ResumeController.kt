/*
 * Copyright (c) 2026 Auxio Project
 * ResumeController.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.resume

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/**
 * Remembers where long songs were left off and resumes them from there when they are played again.
 *
 * Only songs of at least [MIN_DURATION_MS] are handled. A position is saved once a song is more
 * than [MIN_POSITION_MS] in, and forgotten once it gets within [END_MARGIN_MS] of the end.
 *
 * @param player The [Player] to follow. Must be used from its application thread.
 * @param resumeRepository Where positions are kept.
 * @param resumeSettings Whether resuming is enabled.
 */
class ResumeController(
    private val player: Player,
    private val resumeRepository: ResumeRepository,
    private val resumeSettings: ResumeSettings,
) : Player.Listener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickJob: Job? = null
    private var ignoringStarts = false

    fun attach() {
        player.addListener(this)
    }

    fun release() {
        saveCurrent()
        player.removeListener(this)
        scope.cancel()
    }

    /** Save the position of the current song. Call before replacing the queue. */
    fun saveCurrent() {
        save(player.currentMediaItem?.song, player.currentPosition)
    }

    /**
     * Don't resume songs that start during the current command. Used when restoring a saved
     * playback state, which already carries its own position.
     */
    fun ignoreStarts() {
        ignoringStarts = true
        // Runs once the current command has finished.
        scope.launch { ignoringStarts = false }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        // The old song was left: by skipping, picking another song, or ending on its own.
        if (
            oldPosition.mediaItemIndex != newPosition.mediaItemIndex ||
                reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION ||
                reason == Player.DISCONTINUITY_REASON_REMOVE
        ) {
            save(oldPosition.mediaItem?.song, oldPosition.positionMs)
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (ignoringStarts || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
            return
        }
        val song = mediaItem?.song ?: return
        // Wait until the command that started this song has finished, as it can still move the
        // position back to the start afterwards.
        scope.launch { resume(song) }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        tickJob?.cancel()
        if (isPlaying) {
            // Save regularly, so that the position survives the app being closed.
            tickJob = scope.launch {
                while (isActive) {
                    delay(SAVE_INTERVAL_MS.milliseconds)
                    saveCurrent()
                }
            }
        } else {
            saveCurrent()
        }
    }

    private fun resume(song: Song) {
        if (!resumeSettings.resumeLongSongs || player.currentMediaItem?.song != song) {
            return
        }
        val positionMs = resumeRepository.get(song.uid) ?: return
        if (player.currentPosition >= MIN_POSITION_MS || !isResumable(song, positionMs)) {
            return
        }
        L.d("Resuming $song at $positionMs ms")
        player.seekTo(positionMs)
    }

    private fun save(song: Song?, positionMs: Long) {
        if (song == null || !resumeSettings.resumeLongSongs || song.durationMs < MIN_DURATION_MS) {
            return
        }
        if (positionMs >= song.durationMs - END_MARGIN_MS) {
            resumeRepository.clear(song.uid)
        } else if (positionMs > MIN_POSITION_MS) {
            resumeRepository.save(song.uid, positionMs)
        }
    }

    private fun isResumable(song: Song, positionMs: Long) =
        song.durationMs >= MIN_DURATION_MS &&
            positionMs > MIN_POSITION_MS &&
            positionMs < song.durationMs - END_MARGIN_MS

    private val MediaItem.song: Song?
        get() = localConfiguration?.tag as? Song

    private companion object {
        const val MIN_DURATION_MS = 15 * 60 * 1000L
        const val MIN_POSITION_MS = 7 * 1000L
        const val END_MARGIN_MS = 20 * 1000L
        const val SAVE_INTERVAL_MS = 10 * 1000L
    }
}
