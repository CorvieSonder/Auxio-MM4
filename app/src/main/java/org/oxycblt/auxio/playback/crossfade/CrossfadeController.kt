/*
 * Copyright (c) 2026 Auxio Project
 * CrossfadeController.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.crossfade

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
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
 * Crossfades between songs that end on their own.
 *
 * Shortly before the end of a song, a [TailPlayer] is prepared at the point where the fade will
 * start. Once the main player reaches that point, the tail player takes over the end of the song
 * while the main player moves on to the next song, and the volumes of both are ramped so that the
 * old song fades out as the new one fades in. The main player stays the only source of truth for
 * the queue and the playback state.
 *
 * Any user action (pausing, seeking, skipping) cancels a running fade.
 *
 * @param player The main player.
 * @param advance Moves the main player to the next song and lets the rest of the app know.
 */
@OptIn(UnstableApi::class)
class CrossfadeController(
    private val context: Context,
    private val player: ExoPlayer,
    private val mediaSourceFactory: MediaSource.Factory,
    private val settings: CrossfadeSettings,
    private val currentGain: () -> Float,
    private val advance: () -> Unit,
) {
    /** Called with the song whose end was played out by a crossfade. */
    var onSongFinished: ((Song) -> Unit)? = null

    /**
     * The volume that playback should have outside of a fade, for example while a sleep timer fades
     * out. Fades are applied on top of it.
     */
    var baseVolume = 1f
        set(value) {
            field = value
            applyVolumes()
        }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watchJob: Job? = null
    private var fadeJob: Job? = null
    private var tail: TailPlayer? = null
    private var mainFade = 1f
    private var tailFade = 0f
    private var advancing = false
    // The song that will not be crossfaded again, for example because the fade could not be
    // prepared in time.
    private var handledItem: MediaItem? = null

    /** Start or stop watching for the end of the song. Call when the playback state changes. */
    fun update() {
        watchJob?.cancel()
        watchJob = null
        if (fadeJob != null) {
            return
        }
        if (!player.isPlaying) {
            releaseTail()
            return
        }
        watchJob = scope.launch { watch() }
    }

    /**
     * Stop any fade that is prepared or running, and play the main player at full volume. Call on
     * any user action that changes playback.
     */
    fun cancel() {
        if (advancing) {
            // This is our own move to the next song.
            return
        }
        if (fadeJob != null) {
            L.d("Crossfade cancelled")
        }
        watchJob?.cancel()
        watchJob = null
        fadeJob?.cancel()
        fadeJob = null
        releaseTail()
        mainFade = 1f
        applyVolumes()
    }

    fun release() {
        cancel()
        scope.cancel()
    }

    private suspend fun watch() {
        while (scope.isActive) {
            val fadeMs = settings.crossfadeMs
            val item = player.currentMediaItem
            val duration = player.duration
            if (fadeMs <= 0) {
                // Crossfade is off. Checked again on the next playback change.
                releaseTail()
                return
            }
            if (item == null || item === handledItem || duration == C.TIME_UNSET) {
                releaseTail()
                delay(MAX_POLL_MS)
                continue
            }
            val speed = player.playbackParameters.speed
            val startAt = duration - (fadeMs * speed).toLong()
            val untilStartMs = ((startAt - player.currentPosition) / speed).toLong()
            when {
                untilStartMs > PREPARE_LEAD_MS -> {
                    releaseTail()
                    delay((untilStartMs - PREPARE_LEAD_MS).coerceIn(MIN_POLL_MS, MAX_POLL_MS))
                }
                untilStartMs > 0 -> {
                    if (tail == null) {
                        if (!canCrossfade(fadeMs, duration)) {
                            delay(MAX_POLL_MS)
                            continue
                        }
                        prepareTail(item, startAt)
                    }
                    delay(MIN_POLL_MS)
                }
                else -> {
                    start(item, fadeMs)
                    return
                }
            }
        }
    }

    private fun canCrossfade(fadeMs: Long, durationMs: Long): Boolean {
        if (
            player.repeatMode == Player.REPEAT_MODE_ONE ||
                player.pauseAtEndOfMediaItems ||
                !player.hasNextMediaItem() ||
                durationMs < fadeMs * 2
        ) {
            return false
        }
        val current = player.currentMediaItem?.song ?: return false
        val next = player.getMediaItemAt(player.nextMediaItemIndex).song ?: return false
        if (next.durationMs < fadeMs * 2) {
            return false
        }
        return !(settings.skipWithinAlbum && isNextAlbumTrack(current, next))
    }

    private fun isNextAlbumTrack(current: Song, next: Song): Boolean {
        if (current.album != next.album) {
            return false
        }
        val track = current.track ?: return false
        val nextTrack = next.track ?: return false
        val disc = current.disc?.number ?: 1
        val nextDisc = next.disc?.number ?: 1
        return (nextDisc == disc && nextTrack == track + 1) ||
            (nextDisc == disc + 1 && nextTrack == 1)
    }

    private fun prepareTail(item: MediaItem, startAt: Long) {
        val song = item.song ?: return
        L.d("Preparing crossfade for $song at $startAt ms")
        tail =
            TailPlayer(
                context,
                mediaSourceFactory,
                song,
                startAt,
                currentGain(),
                player.audioSessionId,
                player.playbackParameters,
            )
    }

    private fun start(item: MediaItem, fadeMs: Long) {
        handledItem = item
        val tail = tail
        if (tail == null || !tail.isReady) {
            // Too late to start the end of the song in step, so play it gaplessly instead.
            L.d("Crossfade not ready, playing gaplessly")
            releaseTail()
            return
        }
        L.d("Starting crossfade")
        mainFade = 0f
        tailFade = 1f
        applyVolumes()
        tail.play()
        advancing = true
        advance()
        advancing = false
        item.song?.let { onSongFinished?.invoke(it) }
        fadeJob = scope.launch {
            val startTime = SystemClock.elapsedRealtime()
            while (true) {
                val t =
                    ((SystemClock.elapsedRealtime() - startTime).toFloat() / fadeMs).coerceAtMost(
                        1f
                    )
                // Equal-power curve, so the overall loudness stays even during the fade.
                mainFade = sin(t * PI / 2).toFloat()
                tailFade = cos(t * PI / 2).toFloat()
                applyVolumes()
                if (t >= 1f) {
                    break
                }
                delay(FADE_STEP_MS)
            }
            fadeJob = null
            releaseTail()
            mainFade = 1f
            applyVolumes()
            update()
        }
    }

    private fun releaseTail() {
        tail?.release()
        tail = null
        tailFade = 0f
    }

    private fun applyVolumes() {
        player.volume = baseVolume * mainFade
        tail?.volume = baseVolume * tailFade
    }

    private val MediaItem.song: Song?
        get() = localConfiguration?.tag as? Song

    private companion object {
        /** How long before the fade the end of the song starts buffering. */
        const val PREPARE_LEAD_MS = 2000L
        const val MIN_POLL_MS = 10L
        const val MAX_POLL_MS = 1000L
        const val FADE_STEP_MS = 30L
    }
}
