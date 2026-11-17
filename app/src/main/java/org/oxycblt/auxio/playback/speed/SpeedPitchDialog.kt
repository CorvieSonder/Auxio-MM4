/*
 * Copyright (c) 2026 Auxio Project
 * SpeedPitchDialog.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.speed

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogSpeedPitchBinding
import org.oxycblt.auxio.playback.PlaybackSettings
import org.oxycblt.auxio.ui.ViewBindingMaterialDialogFragment

/**
 * A [ViewBindingMaterialDialogFragment] that allows the playback speed and pitch to be changed.
 * Changes apply as the sliders move, so they can be heard right away.
 */
@AndroidEntryPoint
class SpeedPitchDialog : ViewBindingMaterialDialogFragment<DialogSpeedPitchBinding>() {
    @Inject lateinit var playbackSettings: PlaybackSettings

    override fun onCreateBinding(inflater: LayoutInflater) =
        DialogSpeedPitchBinding.inflate(inflater)

    override fun onConfigDialog(builder: AlertDialog.Builder) {
        builder
            .setTitle(R.string.set_speed_pitch)
            .setPositiveButton(R.string.lbl_ok, null)
            .setNeutralButton(R.string.lbl_reset) { _, _ ->
                playbackSettings.playbackSpeed = 1f
                playbackSettings.playbackPitch = 0
            }
    }

    override fun onBindingCreated(binding: DialogSpeedPitchBinding, savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            // First initialization, we need to supply the sliders with the values from
            // settings. After this, the sliders save their own state.
            binding.speedSlider.value =
                (playbackSettings.playbackSpeed * 100 / SPEED_STEP).roundToInt() * SPEED_STEP
            binding.pitchSlider.value = playbackSettings.playbackPitch.toFloat()
        }

        updateSpeedTicker(binding.speedSlider.value)
        binding.speedSlider.addOnChangeListener { _, value, fromUser ->
            updateSpeedTicker(value)
            if (fromUser) {
                playbackSettings.playbackSpeed = value / 100
            }
        }

        updatePitchTicker(binding.pitchSlider.value)
        binding.pitchSlider.addOnChangeListener { _, value, fromUser ->
            updatePitchTicker(value)
            if (fromUser) {
                playbackSettings.playbackPitch = value.roundToInt()
            }
        }
    }

    private fun updateSpeedTicker(percent: Float) {
        requireBinding().speedTicker.text = getString(R.string.fmt_speed, percent.roundToInt())
    }

    private fun updatePitchTicker(semitones: Float) {
        val pitch = semitones.roundToInt()
        requireBinding().pitchTicker.text =
            if (pitch >= 0) {
                getString(R.string.fmt_pitch_pos, pitch)
            } else {
                getString(R.string.fmt_pitch_neg, abs(pitch))
            }
    }

    companion object {
        const val TAG = "SpeedPitchDialog"
        private const val SPEED_STEP = 5f
    }
}
