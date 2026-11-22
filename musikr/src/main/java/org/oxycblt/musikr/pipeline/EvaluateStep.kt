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
import kotlinx.coroutines.channels.Channel
import org.oxycblt.musikr.BuildConfig
import org.oxycblt.musikr.Config
import org.oxycblt.musikr.Interpretation
import org.oxycblt.musikr.MutableLibrary
import org.oxycblt.musikr.graph.MusicGraph
import org.oxycblt.musikr.model.LibraryFactory
import org.oxycblt.musikr.playlist.db.StoredPlaylists
import org.oxycblt.musikr.playlist.interpret.PlaylistInterpreter
import org.oxycblt.musikr.tag.interpret.TagInterpreter
import org.oxycblt.musikr.util.Perf

internal interface EvaluateStep {
    suspend fun evaluate(extractedMusic: Channel<Extracted>): MutableLibrary

    companion object {
        fun new(context: Context, config: Config, interpretation: Interpretation): EvaluateStep =
            EvaluateStepImpl(
                context,
                TagInterpreter.new(interpretation),
                PlaylistInterpreter.new(interpretation),
                config.storage.storedPlaylists,
                LibraryFactory.new(),
            )
    }
}

private class EvaluateStepImpl(
    private val context: Context,
    private val tagInterpreter: TagInterpreter,
    private val playlistInterpreter: PlaylistInterpreter,
    private val storedPlaylists: StoredPlaylists,
    private val libraryFactory: LibraryFactory,
) : EvaluateStep {
    override suspend fun evaluate(extractedMusic: Channel<Extracted>): MutableLibrary {
        val builder = MusicGraph.builder()
        val loopStart = Perf.now()
        var interpretMs = 0L
        for (extracted in extractedMusic) {
            when (extracted) {
                is RawSong -> {
                    val interpretStart = Perf.now()
                    val preSong = tagInterpreter.interpret(extracted)
                    interpretMs += Perf.now() - interpretStart
                    builder.add(preSong)
                }
                is RawPlaylist -> builder.add(playlistInterpreter.interpret(extracted.file))
                is NotAudio -> {}
                is InvalidSong -> {}
            }
        }
        Perf.since("evaluate loop (waits on extract)", loopStart)
        Perf.log("tag interpret (sum): ${interpretMs}ms")
        val graph = Perf.time("graph build") { builder.build() }

        // Render graph to Graphviz in debug mode
        if (BuildConfig.DEBUG) {
            val fileName = "music_graph_debug.dot"
            Perf.time("graphviz render (debug only)") {
                graph.renderToGraphviz(context, fileName)
            }
        }

        return Perf.time("library create") {
            libraryFactory.create(graph, storedPlaylists, playlistInterpreter)
        }
    }
}
