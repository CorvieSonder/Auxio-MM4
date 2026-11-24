/*
 * Copyright (c) 2024 Auxio Project
 * EvaluateStep.kt is part of Auxio.
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
 
package org.oxycblt.musikr.pipeline

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.oxycblt.musikr.BuildConfig
import org.oxycblt.musikr.Config
import org.oxycblt.musikr.Interpretation
import org.oxycblt.musikr.MutableLibrary
import org.oxycblt.musikr.graph.MusicGraph
import org.oxycblt.musikr.model.LibraryFactory
import org.oxycblt.musikr.model.MoodOverrideContext
import org.oxycblt.musikr.model.withMoodOverride
import org.oxycblt.musikr.playlist.db.StoredPlaylists
import org.oxycblt.musikr.playlist.interpret.PlaylistInterpreter
import org.oxycblt.musikr.tag.interpret.PreSong
import org.oxycblt.musikr.tag.interpret.TagInterpreter
import org.oxycblt.musikr.util.Perf

internal interface EvaluateStep {
    suspend fun evaluate(extractedMusic: Channel<Extracted>): MutableLibrary

    companion object {
        fun new(context: Context, config: Config, interpretation: Interpretation): EvaluateStep =
            EvaluateStepImpl(
                context,
                interpretation,
                TagInterpreter.new(interpretation),
                PlaylistInterpreter.new(interpretation),
                config.storage.storedPlaylists,
                LibraryFactory.new(),
                interpretation.parallel,
            )
    }
}

private class EvaluateStepImpl(
    private val context: Context,
    private val interpretation: Interpretation,
    private val tagInterpreter: TagInterpreter,
    private val playlistInterpreter: PlaylistInterpreter,
    private val storedPlaylists: StoredPlaylists,
    private val libraryFactory: LibraryFactory,
    private val parallel: Boolean,
) : EvaluateStep {
    override suspend fun evaluate(extractedMusic: Channel<Extracted>): MutableLibrary {
        val builder = MusicGraph.builder()
        val preSongs = mutableListOf<PreSong>()
        val loopStart = Perf.now()
        if (parallel) {
            evaluateParallel(extractedMusic) { extracted, preSong ->
                add(builder, preSongs, extracted, preSong)
            }
        } else {
            for (extracted in extractedMusic) {
                val preSong =
                    if (extracted is RawSong) tagInterpreter.interpret(extracted) else null
                add(builder, preSongs, extracted, preSong)
            }
        }
        Perf.since("evaluate loop (waits on extract)", loopStart)
        val graph = Perf.time("graph build") { builder.build() }

        // Render graph to Graphviz in debug mode
        if (BuildConfig.DEBUG) {
            val fileName = "music_graph_debug.dot"
            Perf.time("graphviz render (debug only)") {
                graph.renderToGraphviz(context, fileName)
            }
        }

        val moodContext = MoodOverrideContext(preSongs, interpretation, libraryFactory)
        return Perf.time("library create") {
            libraryFactory.create(graph, storedPlaylists, playlistInterpreter, moodContext)
        }
    }

    private fun add(
        builder: MusicGraph.Builder,
        preSongs: MutableList<PreSong>,
        extracted: Extracted,
        preSong: PreSong?,
    ) {
        when (extracted) {
            is RawSong -> {
                val song = requireNotNull(preSong)
                preSongs.add(song)
                builder.add(song.withMoodOverride(interpretation.moodOverrides, interpretation))
            }
            is RawPlaylist -> builder.add(playlistInterpreter.interpret(extracted.file))
            is NotAudio -> {}
            is InvalidSong -> {}
        }
    }

    /**
     * Interpret the tags of several songs at once on every CPU core, but hand the results to
     * [onEach] one at a time, on the calling coroutine and in the order they were extracted, so
     * that the resulting library is exactly the same as when interpreting one song at a time.
     */
    private suspend fun evaluateParallel(
        extractedMusic: Channel<Extracted>,
        onEach: (Extracted, PreSong?) -> Unit,
    ) = coroutineScope {
        // Pending results in extraction order. Bounded so that only a limited number of songs is
        // being interpreted (and held in memory) ahead of the consumer at any time.
        val ordered = Channel<Pair<Extracted, Deferred<PreSong?>>>(IN_FLIGHT)
        launch {
            try {
                for (extracted in extractedMusic) {
                    val preSong =
                        if (extracted is RawSong) {
                            async(Dispatchers.Default) { tagInterpreter.interpret(extracted) }
                        } else {
                            NO_SONG
                        }
                    ordered.send(extracted to preSong)
                }
            } finally {
                ordered.close()
            }
        }
        for ((extracted, preSong) in ordered) {
            onEach(extracted, preSong.await())
        }
    }

    private companion object {
        const val IN_FLIGHT = 256

        /**
         * An already-completed result for items that are not songs. Note that
         * `CompletableDeferred(null)` would instead create an incomplete deferred with no parent
         * job, which would never complete.
         */
        val NO_SONG: Deferred<PreSong?> = CompletableDeferred<PreSong?>().apply { complete(null) }
    }
}
