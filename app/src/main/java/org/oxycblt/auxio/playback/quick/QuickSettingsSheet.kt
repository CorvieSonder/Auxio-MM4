/*
 * Copyright (c) 2026 Auxio Project
 * QuickSettingsSheet.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.quick

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogQuickSettingsBinding
import org.oxycblt.auxio.playback.PlaybackSettings
import org.oxycblt.auxio.playback.formatDurationMs
import org.oxycblt.auxio.playback.sleep.SleepTimer
import org.oxycblt.auxio.playback.sleep.SleepTimerDialog
import org.oxycblt.auxio.playback.speed.bindSpeedPitch
import org.oxycblt.auxio.playback.speed.showSpeedPitch
import org.oxycblt.auxio.ui.ViewBindingBottomSheetDialogFragment
import org.oxycblt.auxio.util.collectImmediately

/**
 * A [ViewBindingBottomSheetDialogFragment] that gathers the playback options changed most often:
 * speed, pitch and the sleep timer.
 */
@AndroidEntryPoint
class QuickSettingsSheet : ViewBindingBottomSheetDialogFragment<DialogQuickSettingsBinding>() {
    @Inject lateinit var playbackSettings: PlaybackSettings
    @Inject lateinit var sleepTimer: SleepTimer

    override fun onCreateBinding(inflater: LayoutInflater) =
        DialogQuickSettingsBinding.inflate(inflater)

    override fun onBindingCreated(
        binding: DialogQuickSettingsBinding,
        savedInstanceState: Bundle?,
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        binding.quickSpeedPitch.bindSpeedPitch(playbackSettings, savedInstanceState == null)
        binding.quickReset.setOnClickListener {
            playbackSettings.playbackSpeed = 1f
            playbackSettings.playbackPitch = 0
            binding.quickSpeedPitch.showSpeedPitch(playbackSettings)
        }

        binding.quickSleep.setOnClickListener {
            // The timer dialog replaces this sheet rather than stacking on top of it.
            SleepTimerDialog().show(parentFragmentManager, SleepTimerDialog.TAG)
            dismiss()
        }

        collectImmediately(sleepTimer.state, ::updateSleepTimer)
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                delay(TICK_MS)
                updateSleepTimer(sleepTimer.state.value)
            }
        }
    }

    private fun updateSleepTimer(state: SleepTimer.State) {
        val binding = requireBinding()
        binding.quickSleepIcon.setImageResource(
            if (state is SleepTimer.State.Off) R.drawable.ic_sleep_24 else R.drawable.ic_sleep_on_24
        )
        binding.quickSleepStatus.text =
            when (state) {
                is SleepTimer.State.Off -> getString(R.string.lbl_sleep_off)
                is SleepTimer.State.Running ->
                    (state.endRealtimeMs - SystemClock.elapsedRealtime())
                        .coerceAtLeast(0)
                        .formatDurationMs(false)
                is SleepTimer.State.FinishingSong -> getString(R.string.lbl_sleep_song_end)
            }
    }

    companion object {
        const val TAG = "QuickSettingsSheet"
        private const val TICK_MS = 1000L
    }
}
