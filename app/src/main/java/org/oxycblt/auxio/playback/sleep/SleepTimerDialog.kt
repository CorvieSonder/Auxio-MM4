/*
 * Copyright (c) 2026 Auxio Project
 * SleepTimerDialog.kt is part of Auxio.
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

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogSleepTimerBinding
import org.oxycblt.auxio.playback.formatDurationMs
import org.oxycblt.auxio.ui.ViewBindingMaterialDialogFragment
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.auxio.util.getPlural

/**
 * A [ViewBindingMaterialDialogFragment] that starts the [SleepTimer], or shows the time left and
 * allows it to be extended or stopped when it is already running.
 */
@AndroidEntryPoint
class SleepTimerDialog : ViewBindingMaterialDialogFragment<DialogSleepTimerBinding>() {
    @Inject lateinit var sleepTimer: SleepTimer
    @Inject lateinit var sleepTimerSettings: SleepTimerSettings
    // The dialog is built for the state the timer was in when it was opened.
    private var openedRunning = false

    override fun onCreateBinding(inflater: LayoutInflater) =
        DialogSleepTimerBinding.inflate(inflater)

    override fun onConfigDialog(builder: AlertDialog.Builder) {
        openedRunning = sleepTimer.state.value !is SleepTimer.State.Off
        builder.setTitle(R.string.lbl_sleep_timer)
        if (openedRunning) {
            builder.setPositiveButton(R.string.lbl_ok, null).setNegativeButton(
                R.string.lbl_sleep_stop
            ) { _, _ ->
                sleepTimer.cancel()
            }
            if (sleepTimer.state.value is SleepTimer.State.Running) {
                builder.setNeutralButton(R.string.lbl_sleep_extend) { _, _ ->
                    sleepTimer.extend(EXTEND_MS)
                }
            }
        } else {
            builder
                .setPositiveButton(R.string.lbl_sleep_start) { _, _ ->
                    val binding = requireBinding()
                    val mins = DURATIONS_MINS[binding.sleepSlider.value.roundToInt()]
                    val finishSong = binding.sleepFinishSong.isChecked
                    val fade = binding.sleepFade.isChecked
                    sleepTimerSettings.durationMins = mins
                    sleepTimerSettings.finishSong = finishSong
                    sleepTimerSettings.fade = fade
                    sleepTimer.start(mins * 60_000L, finishSong, fade)
                }
                .setNegativeButton(R.string.lbl_cancel, null)
        }
    }

    override fun onBindingCreated(binding: DialogSleepTimerBinding, savedInstanceState: Bundle?) {
        binding.sleepStatus.isVisible = openedRunning
        binding.sleepSetup.isVisible = !openedRunning

        if (openedRunning) {
            collectImmediately(sleepTimer.state, ::updateState)
            viewLifecycleOwner.lifecycleScope.launch {
                while (true) {
                    delay(TICK_MS)
                    updateState(sleepTimer.state.value)
                }
            }
            return
        }

        binding.sleepSlider.valueTo = (DURATIONS_MINS.size - 1).toFloat()
        if (savedInstanceState == null) {
            val index = DURATIONS_MINS.indexOf(sleepTimerSettings.durationMins)
            binding.sleepSlider.value = (if (index >= 0) index else DEFAULT_INDEX).toFloat()
            binding.sleepFinishSong.isChecked = sleepTimerSettings.finishSong
            binding.sleepFade.isChecked = sleepTimerSettings.fade
        }

        updateTicker(binding.sleepSlider.value)
        binding.sleepSlider.addOnChangeListener { _, value, _ -> updateTicker(value) }
        // The mode names say minutes, not a slider position.
        binding.sleepSlider.setLabelFormatter { formatMins(DURATIONS_MINS[it.roundToInt()]) }

        // Waiting for the song to end and fading out before it do not go together.
        binding.sleepFade.isEnabled = !binding.sleepFinishSong.isChecked
        binding.sleepFinishSong.setOnCheckedChangeListener { _, isChecked ->
            binding.sleepFade.isEnabled = !isChecked
        }
    }

    private fun updateState(state: SleepTimer.State) {
        val binding = requireBinding()
        when (state) {
            is SleepTimer.State.Running -> {
                val remainingMs =
                    (state.endRealtimeMs - SystemClock.elapsedRealtime()).coerceAtLeast(0)
                binding.sleepStatus.text =
                    getString(
                        if (state.finishSong) {
                            R.string.fmt_sleep_remaining_song
                        } else {
                            R.string.fmt_sleep_remaining
                        },
                        remainingMs.formatDurationMs(false),
                    )
            }
            is SleepTimer.State.FinishingSong ->
                binding.sleepStatus.text = getString(R.string.lng_sleep_finishing)
            // Nothing left to show once the timer is done.
            is SleepTimer.State.Off -> dismiss()
        }
    }

    private fun updateTicker(index: Float) {
        requireBinding().sleepTicker.text = formatMins(DURATIONS_MINS[index.roundToInt()])
    }

    private fun formatMins(mins: Int) = requireContext().getPlural(R.plurals.fmt_minutes, mins)

    companion object {
        const val TAG = "SleepTimerDialog"
        private val DURATIONS_MINS = listOf(1, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180)
        private const val DEFAULT_INDEX = 5
        private const val EXTEND_MS = 5 * 60_000L
        private const val TICK_MS = 1000L
    }
}
