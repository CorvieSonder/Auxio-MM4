/*
 * Copyright (c) 2026 Auxio Project
 * TagFileWriter.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.write

import android.content.Context
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.oxycblt.auxio.R
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.playback.state.PlaybackStateManager
import org.oxycblt.auxio.playback.state.QueueChange
import org.oxycblt.auxio.util.showToast
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.MusicParent
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.metadata.TagEdit
import org.oxycblt.musikr.metadata.TagWriter
import timber.log.Timber as L

/** Writes the edits of every [PendingTagSource] into the audio files. */
interface TagFileWriter {
    /**
     * Write every pending edit, if writing to files is turned on. The song that is playing right
     * now is written once playback moves on.
     */
    fun flush()
}

@Singleton
class TagFileWriterImpl
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val tagWriteSettings: TagWriteSettings,
    private val sources: Lazy<Set<@JvmSuppressWildcards PendingTagSource>>,
    private val musicRepository: MusicRepository,
    private val playbackManager: PlaybackStateManager,
) :
    TagFileWriter,
    TagWriteSettings.Listener,
    MusicRepository.UpdateListener,
    PlaybackStateManager.Listener {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    @Volatile private var heldBack: Music.UID? = null

    init {
        tagWriteSettings.registerListener(this)
        musicRepository.addUpdateListener(this)
        playbackManager.addListener(this)
    }

    override fun flush() {
        if (!tagWriteSettings.writeToFiles) {
            return
        }
        scope.launch { flushNow() }
    }

    private suspend fun flushNow() {
        val library = musicRepository.library ?: return
        val pendingBySource = sources.get().map { it to it.pending() }
        val edits = mutableMapOf<Music.UID, TagEdit>()
        for ((_, pending) in pendingBySource) {
            for (tag in pending) {
                edits[tag.uid] = edits[tag.uid]?.plus(tag.edit) ?: tag.edit
            }
        }
        if (edits.isEmpty()) {
            return
        }

        val playing = withContext(Dispatchers.Main) { playbackManager.currentSong?.uid }
        heldBack = null
        val done = mutableSetOf<Music.UID>()
        var failed = 0
        for ((uid, edit) in edits) {
            if (uid == playing) {
                L.d("Holding back the write of $uid until playback moves on")
                heldBack = uid
                continue
            }
            val song = library.findSong(uid) ?: continue
            when (val result = TagWriter.write(context, song.uri, edit)) {
                TagWriter.Result.Success -> {
                    L.d("Wrote $edit into ${song.path}")
                    done.add(uid)
                }
                TagWriter.Result.Unsupported -> {
                    L.w("Cannot write tags into ${song.path}, skipping")
                    done.add(uid)
                }
                is TagWriter.Result.Failed -> {
                    L.e("Unable to write tags into ${song.path}: ${result.reason}")
                    if (result.originalTouched) {
                        L.e("The original file may be damaged: ${song.path}")
                    }
                    failed++
                }
            }
        }

        for ((source, pending) in pendingBySource) {
            val written = pending.filter { it.uid in done }
            if (written.isNotEmpty()) {
                source.markWritten(written)
            }
        }
        if (failed > 0) {
            withContext(Dispatchers.Main) { context.showToast(R.string.err_write_tags) }
        }
    }

    override fun onWriteToFilesChanged() {
        flush()
    }

    override fun onMusicChanges(changes: MusicRepository.Changes) {
        if (changes.deviceLibrary) {
            flush()
        }
    }

    private fun onSongMaybeChanged() {
        val held = heldBack ?: return
        if (playbackManager.currentSong?.uid != held) {
            flush()
        }
    }

    override fun onIndexMoved(index: Int) = onSongMaybeChanged()

    override fun onQueueChanged(
        queue: List<Song>,
        index: Int,
        change: QueueChange,
    ) = onSongMaybeChanged()

    override fun onNewPlayback(
        parent: MusicParent?,
        queue: List<Song>,
        index: Int,
        isShuffled: Boolean,
    ) = onSongMaybeChanged()

    override fun onSessionEnded() = onSongMaybeChanged()
}
