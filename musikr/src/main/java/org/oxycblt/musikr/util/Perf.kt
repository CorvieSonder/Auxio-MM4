/*
 * Copyright (c) 2026 Auxio Project
 * Perf.kt is part of Auxio.
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
 
package org.oxycblt.musikr.util

import android.os.SystemClock
import android.util.Log

/**
 * Minimal timing log for measuring library loading. All lines go to the "AuxioPerf" logcat tag so
 * they can be collected with `adb logcat -s AuxioPerf`.
 */
object Perf {
    const val TAG = "AuxioPerf"

    /** Milliseconds since boot, for computing durations across threads. */
    fun now() = SystemClock.elapsedRealtime()

    /** Log a message under the perf tag. */
    fun log(message: String) {
        Log.i(TAG, message)
    }

    /** Log the time [label] took since [start] (a value from [now]). */
    fun since(label: String, start: Long) {
        log("$label: ${now() - start}ms")
    }

    /** Run [block] and log how long it took under [label]. */
    inline fun <T> time(label: String, block: () -> T): T {
        val start = now()
        val result = block()
        since(label, start)
        return result
    }
}
