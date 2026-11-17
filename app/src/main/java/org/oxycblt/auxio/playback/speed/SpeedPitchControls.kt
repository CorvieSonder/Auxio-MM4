/*
 * Copyright (c) 2026 Auxio Project
 * SpeedPitchControls.kt is part of Auxio.
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

import kotlin.math.abs
import kotlin.math.roundToInt
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogSpeedPitchBinding
import org.oxycblt.auxio.playback.PlaybackSettings
import org.oxycblt.auxio.util.context

private const val SPEED_STEP = 5f

/**
 * Connect the speed and pitch sliders to [PlaybackSettings]. Changes apply as the sliders move, so
 * they can be heard right away.
 *
 * @param playbackSettings The [PlaybackSettings] to read from and write to.
 * @param initialize Whether to move the sliders to the current settings. False when the sliders
 *   restore their own state.
 */
fun DialogSpeedPitchBinding.bindSpeedPitch(
    playbackSettings: PlaybackSettings,
    initialize: Boolean,
) {
    if (initialize) {
        showSpeedPitch(playbackSettings)
    }

    updateSpeedTicker(speedSlider.value)
    speedSlider.addOnChangeListener { _, value, fromUser ->
        updateSpeedTicker(value)
        if (fromUser) {
            playbackSettings.playbackSpeed = value / 100
        }
    }

    updatePitchTicker(pitchSlider.value)
    pitchSlider.addOnChangeListener { _, value, fromUser ->
        updatePitchTicker(value)
        if (fromUser) {
            playbackSettings.playbackPitch = value.roundToInt()
        }
    }
}

/**
 * Move the speed and pitch sliders to the current settings.
 *
 * @param playbackSettings The [PlaybackSettings] to read from.
 */
fun DialogSpeedPitchBinding.showSpeedPitch(playbackSettings: PlaybackSettings) {
    speedSlider.value =
        (playbackSettings.playbackSpeed * 100 / SPEED_STEP).roundToInt() * SPEED_STEP
    pitchSlider.value = playbackSettings.playbackPitch.toFloat()
}

private fun DialogSpeedPitchBinding.updateSpeedTicker(percent: Float) {
    speedTicker.text = context.getString(R.string.fmt_speed, percent.roundToInt())
}

private fun DialogSpeedPitchBinding.updatePitchTicker(semitones: Float) {
    val pitch = semitones.roundToInt()
    pitchTicker.text =
        if (pitch >= 0) {
            context.getString(R.string.fmt_pitch_pos, pitch)
        } else {
            context.getString(R.string.fmt_pitch_neg, abs(pitch))
        }
}
