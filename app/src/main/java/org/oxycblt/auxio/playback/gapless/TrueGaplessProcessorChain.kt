/*
 * Copyright (c) 2026 Auxio Project
 * TrueGaplessProcessorChain.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.gapless

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * ExoPlayer's default audio processor chain with a [SilenceTrimmingAudioProcessor] in front. The
 * audio the trimmer drops is reported as skipped, so the playback position jumps over it instead of
 * falling behind the song.
 *
 * @param trimmer The trimmer, run first so it sees the song at its own volume.
 * @param processors The processors to run after the trimmer.
 */
@OptIn(UnstableApi::class)
class TrueGaplessProcessorChain(
    private val trimmer: SilenceTrimmingAudioProcessor,
    vararg processors: AudioProcessor,
) : DefaultAudioSink.AudioProcessorChain {
    private val inner = DefaultAudioSink.DefaultAudioProcessorChain(trimmer, *processors)

    override fun getAudioProcessors(): Array<AudioProcessor> = inner.audioProcessors

    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters) =
        inner.applyPlaybackParameters(playbackParameters)

    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean) =
        inner.applySkipSilenceEnabled(skipSilenceEnabled)

    override fun getMediaDuration(playoutDuration: Long) = inner.getMediaDuration(playoutDuration)

    override fun getSkippedOutputFrameCount() =
        inner.skippedOutputFrameCount + trimmer.droppedFrames
}
