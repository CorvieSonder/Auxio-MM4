/*
 * Copyright (c) 2026 Auxio Project
 * LyricsViewModel.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.lyrics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.oxycblt.auxio.playback.state.PlaybackStateManager
import org.oxycblt.auxio.playback.state.Progression
import org.oxycblt.auxio.playback.state.QueueChange
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.MusicParent
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.lyrics.Lyrics
import org.oxycblt.musikr.lyrics.LyricsReader

/**
 * Holds the lyrics of the playing song and which page of the queue sheet (queue or lyrics) is
 * selected. Lyrics are read on demand and only the current and next song's are kept in memory.
 */
@HiltViewModel
class LyricsViewModel
@Inject
constructor(
    private val playbackManager: PlaybackStateManager,
    @ApplicationContext context: Context,
) : ViewModel(), PlaybackStateManager.Listener {
    private val reader = LyricsReader.from(context)
    private val cache = mutableMapOf<Music.UID, Lyrics>()
    private var loadJob: Job? = null
    private var prefetchJob: Job? = null

    private val _selectedTab = MutableStateFlow(TAB_QUEUE)
    /** The selected page of the queue sheet, [TAB_QUEUE] or [TAB_LYRICS]. */
    val selectedTab: StateFlow<Int> = _selectedTab

    private val _song = MutableStateFlow<Song?>(null)

    private val _lyrics = MutableStateFlow<Lyrics?>(null)
    /** The playing song's lyrics. Null while they load or when nothing is playing. */
    val lyrics: StateFlow<Lyrics?> = _lyrics

    private val _showPlain = MutableStateFlow(false)
    /** Whether to show plain lyrics even though synced ones exist. Reset for each song. */
    val showPlain: StateFlow<Boolean> = _showPlain

    private val _plainIndex = MutableStateFlow(0)
    /** Which plain lyrics block (translation) to show. Reset for each song. */
    val plainIndex: StateFlow<Int> = _plainIndex

    private val _isPlaying = MutableStateFlow(false)
    /** Whether playback is ongoing. */
    val isPlaying: StateFlow<Boolean> = _isPlaying

    init {
        playbackManager.addListener(this)
        update()
    }

    override fun onCleared() {
        playbackManager.removeListener(this)
    }

    override fun onIndexMoved(index: Int) = update()

    override fun onQueueChanged(queue: List<Song>, index: Int, change: QueueChange) = update()

    override fun onQueueReordered(queue: List<Song>, index: Int, isShuffled: Boolean) = update()

    override fun onNewPlayback(
        parent: MusicParent?,
        queue: List<Song>,
        index: Int,
        isShuffled: Boolean,
    ) = update()

    override fun onProgressionChanged(progression: Progression) {
        _isPlaying.value = progression.isPlaying
    }

    override fun onSessionEnded() = update()

    /** Select a page of the queue sheet. */
    fun selectTab(tab: Int) {
        _selectedTab.value = tab
    }

    /** Switch between synced and plain lyrics. */
    fun toggleMode() {
        _showPlain.value = !_showPlain.value
    }

    /** Show the plain lyrics block at [index]. */
    fun selectPlain(index: Int) {
        _plainIndex.value = index
    }

    /** The current playback position, in milliseconds. */
    fun positionMs() = playbackManager.progression.calculateElapsedPositionMs()

    /** Seek to [positionMs] in the playing song. */
    fun seekTo(positionMs: Long) = playbackManager.seekTo(positionMs)

    private fun update() {
        _isPlaying.value = playbackManager.progression.isPlaying
        val song = playbackManager.currentSong
        if (song != _song.value) {
            _song.value = song
            _showPlain.value = false
            _plainIndex.value = 0
            load(song)
        }
        prefetchNext()
    }

    private fun load(song: Song?) {
        loadJob?.cancel()
        if (song == null) {
            _lyrics.value = null
            return
        }
        val cached = cache[song.uid]
        if (cached != null) {
            _lyrics.value = cached
            return
        }
        _lyrics.value = null
        loadJob = viewModelScope.launch {
            val lyrics = reader.read(song)
            remember(song, lyrics)
            _lyrics.value = lyrics
        }
    }

    private fun prefetchNext() {
        val next = playbackManager.queue.getOrNull(playbackManager.index + 1) ?: return
        if (cache.containsKey(next.uid)) return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch { remember(next, reader.read(next)) }
    }

    /** Keep only the current and next song's lyrics, plus the one just read. */
    private fun remember(song: Song, lyrics: Lyrics) {
        val keep =
            setOfNotNull(
                song.uid,
                playbackManager.currentSong?.uid,
                playbackManager.queue.getOrNull(playbackManager.index + 1)?.uid,
            )
        cache.keys.retainAll(keep)
        cache[song.uid] = lyrics
    }

    companion object {
        const val TAB_QUEUE = 0
        const val TAB_LYRICS = 1
    }
}
