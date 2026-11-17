/*
 * Copyright (c) 2026 Auxio Project
 * SleepTimer.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.sleep

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.oxycblt.auxio.playback.state.PlaybackStateManager
import org.oxycblt.auxio.playback.state.Progression
import timber.log.Timber as L

/**
 * A timer that pauses playback once it runs out, optionally fading the volume out beforehand or
 * waiting for the current song to finish.
 */
interface SleepTimer {
    /** The current [State] of the timer. */
    val state: StateFlow<State>

    /** The volume multiplier the player should apply. 1 unless the timer is fading out. */
    val volume: Float

    /** Whether the player should pause at the end of the current song. */
    val pauseAtSongEnd: Boolean

    /**
     * Start the timer, replacing any timer currently running.
     *
     * @param durationMs How long to wait until playback is paused, in milliseconds.
     * @param finishSong Whether to let the song playing at that moment finish before pausing.
     * @param fade Whether to fade the volume out before pausing. Has no effect with [finishSong].
     */
    fun start(durationMs: Long, finishSong: Boolean, fade: Boolean)

    /**
     * Give a running timer more time. Does nothing if the timer is not running.
     *
     * @param durationMs The amount of time to add, in milliseconds.
     */
    fun extend(durationMs: Long)

    /** Stop the timer without pausing playback. */
    fun cancel()

    /**
     * Set the [Listener] that applies the timer to the player.
     *
     * @param listener The [Listener], or null to remove the current one.
     */
    fun setListener(listener: Listener?)

    /** The state of a [SleepTimer]. */
    sealed interface State {
        /** No timer is running. */
        data object Off : State

        /**
         * The timer is running.
         *
         * @param endRealtimeMs When the timer runs out, as a [SystemClock.elapsedRealtime] value.
         * @param finishSong Whether the song playing at that moment will finish before pausing.
         */
        data class Running(val endRealtimeMs: Long, val finishSong: Boolean) : State

        /** The timer has run out, and playback will pause when the current song ends. */
        data object FinishingSong : State
    }

    /** Applies the timer to the player. */
    interface Listener {
        /** Called when [volume] or [pauseAtSongEnd] have changed. */
        fun onSleepTimerChanged()
    }
}

@Singleton
class SleepTimerImpl @Inject constructor(private val playbackManager: PlaybackStateManager) :
    SleepTimer, PlaybackStateManager.Listener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var listener: SleepTimer.Listener? = null
    private var fade = false
    private var pausing = false

    override val state: StateFlow<SleepTimer.State>
        field = MutableStateFlow<SleepTimer.State>(SleepTimer.State.Off)

    override var volume = 1f
        private set

    override val pauseAtSongEnd: Boolean
        get() = state.value is SleepTimer.State.FinishingSong

    init {
        playbackManager.addListener(this)
    }

    override fun setListener(listener: SleepTimer.Listener?) {
        this.listener = listener
        listener?.onSleepTimerChanged()
    }

    override fun start(durationMs: Long, finishSong: Boolean, fade: Boolean) {
        L.d("Starting sleep timer [duration=$durationMs finishSong=$finishSong fade=$fade]")
        this.fade = fade && !finishSong
        run(SystemClock.elapsedRealtime() + durationMs, finishSong)
    }

    override fun extend(durationMs: Long) {
        val current = state.value as? SleepTimer.State.Running ?: return
        L.d("Extending sleep timer by $durationMs")
        run(current.endRealtimeMs + durationMs, current.finishSong)
    }

    override fun cancel() {
        L.d("Cancelling sleep timer")
        job?.cancel()
        pausing = false
        job = null
        finish()
    }

    override fun onProgressionChanged(progression: Progression) {
        if (progression.isPlaying || pausing) {
            return
        }
        when (state.value) {
            is SleepTimer.State.FinishingSong -> {
                // Either the song has ended and the player paused itself, or the user paused
                // while waiting for that. Both leave nothing more to do.
                L.d("Playback paused after the timer ran out, timer is done")
                finish()
            }
            is SleepTimer.State.Running -> {
                // Playback was paused by the user, so there is nothing left to pause later.
                L.d("Playback paused while the timer was running, stopping it")
                cancel()
            }
            is SleepTimer.State.Off -> {}
        }
    }

    private fun run(endRealtimeMs: Long, finishSong: Boolean) {
        job?.cancel()
        pausing = false
        // A previous fade may have been interrupted.
        applyVolume(1f)
        state.value = SleepTimer.State.Running(endRealtimeMs, finishSong)
        listener?.onSleepTimerChanged()
        job = scope.launch {
            val fadeMs = if (fade) FADE_MS else 0
            waitUntil(endRealtimeMs - fadeMs)
            if (finishSong) {
                if (playbackManager.progression.isPlaying) {
                    L.d("Sleep timer ran out, waiting for the song to end")
                    state.value = SleepTimer.State.FinishingSong
                    listener?.onSleepTimerChanged()
                } else {
                    finish()
                }
                return@launch
            }
            while (fadeMs > 0) {
                val remainingMs = endRealtimeMs - SystemClock.elapsedRealtime()
                if (remainingMs <= 0) break
                applyVolume((remainingMs.toFloat() / fadeMs).coerceIn(0f, 1f))
                delay(FADE_STEP_MS)
            }
            L.d("Sleep timer ran out, pausing playback")
            applyVolume(0f)
            // The timer's own pause must not be taken for the user pausing, which would bring
            // the volume back before the player has stopped.
            pausing = true
            playbackManager.playing(false)
            // Let the player stop before the volume comes back.
            delay(RESTORE_DELAY_MS)
            pausing = false
            finish()
        }
    }

    private suspend fun waitUntil(realtimeMs: Long) {
        while (true) {
            val remainingMs = realtimeMs - SystemClock.elapsedRealtime()
            if (remainingMs <= 0) return
            // Short waits keep the timer close to real time even if the device dozes.
            delay(remainingMs.coerceAtMost(MAX_WAIT_MS))
        }
    }

    private fun finish() {
        volume = 1f
        state.value = SleepTimer.State.Off
        listener?.onSleepTimerChanged()
    }

    private fun applyVolume(volume: Float) {
        if (this.volume != volume) {
            this.volume = volume
            listener?.onSleepTimerChanged()
        }
    }

    private companion object {
        const val FADE_MS = 10_000L
        const val FADE_STEP_MS = 100L
        const val RESTORE_DELAY_MS = 500L
        const val MAX_WAIT_MS = 1000L
    }
}
