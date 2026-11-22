/*
 * Copyright (c) 2026 Auxio Project
 * FixedGainAudioProcessor.kt is part of Auxio.
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

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Scales 16-bit PCM audio by a gain that does not change, so that the end of a song keeps its
 * ReplayGain adjustment while it fades out in a [TailPlayer].
 *
 * @param gain The linear volume factor to apply.
 */
@UnstableApi
class FixedGainAudioProcessor(private val gain: Float) : BaseAudioProcessor() {
    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat
    ): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            // No adjustment needed means no processing needed.
            return if (gain == 1f) AudioProcessor.AudioFormat.NOT_SET else inputAudioFormat
        }
        throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val pos = inputBuffer.position()
        val limit = inputBuffer.limit()
        val buffer = replaceOutputBuffer(limit - pos)
        for (i in pos until limit step 2) {
            val sample = inputBuffer.getLeShort(i) * gain
            val clamped = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer.put(clamped.toByte())
            buffer.put(clamped.shr(8).toByte())
        }
        inputBuffer.position(limit)
        buffer.flip()
    }

    private fun ByteBuffer.getLeShort(at: Int) =
        get(at + 1).toInt().shl(8).or(get(at).toInt().and(0xFF)).toShort()
}
