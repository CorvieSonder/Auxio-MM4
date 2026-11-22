/*
 * Copyright (c) 2026 Auxio Project
 * SilenceTrimmingAudioProcessor.kt is part of Auxio.
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
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Trims the silence at the start and end of songs, so that songs follow each other without a gap.
 * Silence inside a song (a pause, or the gap before a hidden track) is kept.
 *
 * Silence is audio quieter than [THRESHOLD] that lasts at least [MIN_SILENCE_MS]; shorter silence
 * is kept. At the start of a song, silence is dropped until the first sound. Later in the song,
 * silence is held back: if sound follows, the held silence is played after all; if the song ends
 * first, it is dropped. Silence longer than [MAX_HOLD_S] is played as it is, so playback never
 * stalls.
 *
 * Only handles 16-bit PCM, which is what ExoPlayer gives audio processors. The song boundaries come
 * from a [SilenceTrimmingAudioSink], and the dropped audio must be reported as skipped through a
 * [TrueGaplessProcessorChain] so that the playback position stays right.
 *
 * @param settings Whether trimming is on. Read for every buffer, so it can change during playback.
 */
@OptIn(UnstableApi::class)
class SilenceTrimmingAudioProcessor(private val settings: TrueGaplessSettings) :
    BaseAudioProcessor() {
    /** The number of audio frames dropped since the last flush. */
    var droppedFrames = 0L
        private set

    private enum class State {
        /** At the start of a song: silence is held until it is long enough to drop. */
        LEADING,
        /** At the start of a song, in a silence that is long enough: silence is dropped. */
        DROPPING,
        /** In the song: sound passes through, silence is held in case the song ends in it. */
        PLAYING,
        /** In a silence too long to hold: everything passes through until sound comes back. */
        PASSING,
    }

    private var state = State.LEADING
    private var bytesPerFrame = 0
    private var minSilenceBytes = 0
    private var maxHoldBytes = 0
    private var hold = ByteArray(0)
    private var holdSize = 0
    private var input = ByteArray(0)
    private var output = ByteArray(0)
    private var outputSize = 0
    private var boundaryPending = false
    private var queuedSinceFlush = false

    /** Called when the audio of the previous song has all been queued and a new song begins. */
    fun markSongBoundary() {
        // A boundary right after a flush only means a song was loaded. The flush already decided
        // whether playback starts at the beginning of a song.
        if (queuedSinceFlush) {
            boundaryPending = true
        }
    }

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat
    ): AudioProcessor.AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            inputAudioFormat
        } else {
            // Other formats pass through untouched.
            AudioProcessor.AudioFormat.NOT_SET
        }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        queuedSinceFlush = true
        outputSize = 0
        if (boundaryPending) {
            boundaryPending = false
            endSong()
            state = State.LEADING
        }
        if (input.size < size) input = ByteArray(size)
        inputBuffer.get(input, 0, size)

        if (!settings.enabled) {
            emitHold()
            state = State.PLAYING
            append(input, 0, size)
        } else {
            var at = 0
            while (at + bytesPerFrame <= size) {
                processFrame(at, isSilent(at))
                at += bytesPerFrame
            }
        }
        flushOutput()
    }

    override fun onQueueEndOfStream() {
        // The output of the last buffer may not have been read yet, so nothing new can be output
        // here. Any held silence is at the very end of the stream, so it is dropped even if short.
        dropHold()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        bytesPerFrame = inputAudioFormat.bytesPerFrame
        minSilenceBytes = (inputAudioFormat.sampleRate * MIN_SILENCE_MS / 1000) * bytesPerFrame
        maxHoldBytes = inputAudioFormat.sampleRate * MAX_HOLD_S * bytesPerFrame
        // Only trim leading silence when playback starts at the beginning of a song, not after a
        // seek into the middle of one.
        state =
            if (streamMetadata.positionOffsetUs <= SONG_START_US) State.LEADING else State.PLAYING
        holdSize = 0
        droppedFrames = 0
        boundaryPending = false
        queuedSinceFlush = false
    }

    override fun onReset() {
        hold = ByteArray(0)
        input = ByteArray(0)
        output = ByteArray(0)
        holdSize = 0
        outputSize = 0
    }

    private fun processFrame(at: Int, silent: Boolean) {
        when (state) {
            State.LEADING ->
                if (silent) {
                    holdFrame(at)
                    if (holdSize >= minSilenceBytes) {
                        dropHold()
                        state = State.DROPPING
                    }
                } else {
                    emitHold()
                    append(input, at, bytesPerFrame)
                    state = State.PLAYING
                }
            State.DROPPING ->
                if (silent) {
                    droppedFrames++
                } else {
                    append(input, at, bytesPerFrame)
                    state = State.PLAYING
                }
            State.PLAYING ->
                if (silent) {
                    holdFrame(at)
                    if (holdSize >= maxHoldBytes) {
                        emitHold()
                        state = State.PASSING
                    }
                } else {
                    emitHold()
                    append(input, at, bytesPerFrame)
                }
            State.PASSING -> {
                append(input, at, bytesPerFrame)
                if (!silent) state = State.PLAYING
            }
        }
    }

    /** The song ended: drop the held silence if it is long enough, otherwise play it. */
    private fun endSong() {
        if (holdSize >= minSilenceBytes) dropHold() else emitHold()
    }

    private fun isSilent(at: Int): Boolean {
        for (i in at until at + bytesPerFrame step 2) {
            // 16-bit little-endian sample.
            val sample = (input[i].toInt() and 0xFF) or (input[i + 1].toInt() shl 8)
            if (abs(sample.toShort().toInt()) > THRESHOLD) return false
        }
        return true
    }

    private fun holdFrame(at: Int) {
        if (hold.size < holdSize + bytesPerFrame) {
            hold = hold.copyOf(maxOf(holdSize + bytesPerFrame, hold.size * 2, 64 * 1024))
        }
        System.arraycopy(input, at, hold, holdSize, bytesPerFrame)
        holdSize += bytesPerFrame
    }

    private fun emitHold() {
        append(hold, 0, holdSize)
        holdSize = 0
    }

    private fun dropHold() {
        droppedFrames += holdSize / bytesPerFrame
        holdSize = 0
    }

    private fun append(from: ByteArray, at: Int, length: Int) {
        if (length == 0) return
        if (output.size < outputSize + length) {
            output = output.copyOf(maxOf(outputSize + length, output.size * 2))
        }
        System.arraycopy(from, at, output, outputSize, length)
        outputSize += length
    }

    private fun flushOutput() {
        if (outputSize == 0) return
        replaceOutputBuffer(outputSize).put(output, 0, outputSize).flip()
        outputSize = 0
    }

    private companion object {
        /** Samples at or below this level count as silence: about -50 dBFS for 16-bit audio. */
        const val THRESHOLD = 104
        /** Silence shorter than this is kept. */
        const val MIN_SILENCE_MS = 500
        /** Silence longer than this is played as it is, to bound the memory used for holding it. */
        const val MAX_HOLD_S = 30
        /** Playback starting this close to the beginning of a song counts as starting at it. */
        const val SONG_START_US = 50_000L
    }
}
