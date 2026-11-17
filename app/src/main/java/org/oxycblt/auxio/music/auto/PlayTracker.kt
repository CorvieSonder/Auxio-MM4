/*
 * Copyright (c) 2026 Auxio Project
 * PlayTracker.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.auto

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.oxycblt.auxio.playback.state.PlaybackStateManager
import org.oxycblt.auxio.playback.state.Progression
import org.oxycblt.musikr.MusicParent
import org.oxycblt.musikr.Song

/**
 * Watches playback and records a song in the [PlayHistoryRepository] once it has been playing for
 * [PLAYED_AFTER_MS]. A song is recorded at most once each time it starts.
 */
@Singleton
class PlayTracker
@Inject
constructor(
    private val playbackManager: PlaybackStateManager,
    private val playHistoryRepository: PlayHistoryRepository,
) : PlaybackStateManager.Listener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pending: Job? = null
    private var recorded = false

    init {
        playbackManager.addListener(this)
    }

    override fun onIndexMoved(index: Int) {
        recorded = false
        schedule()
    }

    override fun onNewPlayback(
        parent: MusicParent?,
        queue: List<Song>,
        index: Int,
        isShuffled: Boolean,
    ) {
        recorded = false
        schedule()
    }

    override fun onProgressionChanged(progression: Progression) {
        schedule()
    }

    override fun onSessionEnded() {
        pending?.cancel()
        pending = null
    }

    private fun schedule() {
        pending?.cancel()
        pending = null
        val song = playbackManager.currentSong
        val progression = playbackManager.progression
        if (recorded || song == null || !progression.isPlaying) {
            return
        }
        val remainingMs = PLAYED_AFTER_MS - progression.calculateElapsedPositionMs()
        if (remainingMs <= 0) {
            record(song)
            return
        }
        pending = scope.launch {
            delay(remainingMs)
            // Check again rather than trust the delay, as playback may have stalled.
            schedule()
        }
    }

    private fun record(song: Song) {
        recorded = true
        playHistoryRepository.recordPlay(song)
    }

    private companion object {
        /** How far into a song playback has to be for the song to count as played. */
        const val PLAYED_AFTER_MS = 7000L
    }
}
