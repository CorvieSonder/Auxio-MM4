/*
 * Copyright (c) 2026 Auxio Project
 * TailPlayer.kt is part of Auxio.
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
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.exoplayer.BaseRenderer
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.MediaSource
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/**
 * A short-lived player that plays the end of a song while the main player has already moved on to
 * the next one, so the two can overlap. It does not handle audio focus, as the main player does
 * that for both.
 *
 * @param song The song whose end to play.
 * @param positionMs Where in the song to start.
 * @param gain The ReplayGain adjustment of the song, as a linear volume factor.
 * @param audioSessionId The main player's audio session, shared so that audio effects apply to
 *   both.
 * @param playbackParameters The main player's speed and pitch.
 */
@OptIn(UnstableApi::class)
class TailPlayer(
    context: Context,
    mediaSourceFactory: MediaSource.Factory,
    song: Song,
    positionMs: Long,
    gain: Float,
    audioSessionId: Int,
    playbackParameters: PlaybackParameters,
) : Player.Listener {
    private val player: ExoPlayer

    init {
        val processor = FixedGainAudioProcessor(gain)
        val renderers = RenderersFactory { handler, _, audioListener, _, _ ->
            arrayOf<BaseRenderer>(
                FfmpegAudioRenderer(handler, audioListener, processor),
                MediaCodecAudioRenderer(
                    context,
                    MediaCodecSelector.DEFAULT,
                    handler,
                    audioListener,
                    DefaultAudioSink.Builder(context)
                        .setAudioProcessors(arrayOf(processor))
                        .build(),
                ),
            )
        }
        player =
            ExoPlayer.Builder(context, renderers)
                .setMediaSourceFactory(mediaSourceFactory)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    false,
                )
                .build()
        player.addListener(this)
        player.setAudioSessionId(audioSessionId)
        player.playbackParameters = playbackParameters
        player.volume = 0f
        player.setMediaItem(MediaItem.Builder().setUri(song.uri).build(), positionMs)
        player.prepare()
    }

    /** Whether the player has buffered and can start right away. */
    val isReady: Boolean
        get() = player.playbackState == Player.STATE_READY

    /** The volume of this player, from 0 to 1. */
    var volume: Float
        get() = player.volume
        set(value) {
            player.volume = value
        }

    fun play() {
        player.play()
    }

    fun release() {
        player.removeListener(this)
        player.release()
    }

    override fun onPlayerError(error: PlaybackException) {
        L.e("Crossfade tail failed: ${error.stackTraceToString()}")
        player.stop()
    }
}
