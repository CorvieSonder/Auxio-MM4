/*
 * Copyright (c) 2026 Auxio Project
 * SilenceTrimmingAudioSink.kt is part of Auxio.
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink

/**
 * An [AudioSink] that tells a [SilenceTrimmingAudioProcessor] where one song ends and the next
 * begins. ExoPlayer does not flush audio processors between songs that play gaplessly, but the
 * renderers set a new output stream offset right after the last audio of the old song and before
 * the first audio of the new one.
 */
@OptIn(UnstableApi::class)
class SilenceTrimmingAudioSink(
    sink: AudioSink,
    private val trimmer: SilenceTrimmingAudioProcessor,
) : ForwardingAudioSink(sink) {
    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
        trimmer.markSongBoundary()
        super.setOutputStreamOffsetUs(outputStreamOffsetUs)
    }
}
