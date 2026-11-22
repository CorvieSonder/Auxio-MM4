/*
 * Copyright (c) 2024 Auxio Project
 * LibraryFactory.kt is part of Auxio.
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
 
package org.oxycblt.musikr.model

import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.Composer
import org.oxycblt.musikr.CustomValue
import org.oxycblt.musikr.Genre
import org.oxycblt.musikr.Grouping
import org.oxycblt.musikr.Mood
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.graph.AlbumVertex
import org.oxycblt.musikr.graph.ArtistVertex
import org.oxycblt.musikr.graph.ComposerVertex
import org.oxycblt.musikr.graph.CustomValueVertex
import org.oxycblt.musikr.graph.GenreVertex
import org.oxycblt.musikr.graph.GroupingVertex
import org.oxycblt.musikr.graph.MoodVertex
import org.oxycblt.musikr.graph.MusicGraph
import org.oxycblt.musikr.graph.PlaylistVertex
import org.oxycblt.musikr.graph.SongVertex
import org.oxycblt.musikr.graph.Vertex
import org.oxycblt.musikr.playlist.db.StoredPlaylists
import org.oxycblt.musikr.playlist.interpret.PlaylistInterpreter

internal interface LibraryFactory {
    fun create(
        graph: MusicGraph,
        storedPlaylists: StoredPlaylists,
        playlistInterpreter: PlaylistInterpreter,
        moodContext: MoodOverrideContext,
    ): LibraryImpl

    companion object {
        fun new(): LibraryFactory = LibraryFactoryImpl()
    }
}

private class LibraryFactoryImpl : LibraryFactory {
    override fun create(
        graph: MusicGraph,
        storedPlaylists: StoredPlaylists,
        playlistInterpreter: PlaylistInterpreter,
        moodContext: MoodOverrideContext,
    ): LibraryImpl {
        val songs =
            graph.songVertex.mapTo(mutableSetOf()) { vertex ->
                SongImpl(SongVertexCore(vertex)).also { vertex.tag = it }
            }
        val albums =
            graph.albumVertex.mapTo(mutableSetOf()) { vertex ->
                AlbumImpl(AlbumVertexCore(vertex)).also { vertex.tag = it }
            }
        val artists =
            graph.artistVertex.mapTo(mutableSetOf()) { vertex ->
                ArtistImpl(ArtistVertexCore(vertex)).also { vertex.tag = it }
            }
        val genres =
            graph.genreVertex.mapTo(mutableSetOf()) { vertex ->
                GenreImpl(GenreVertexCore(vertex)).also { vertex.tag = it }
            }
        val composers =
            graph.composerVertex.mapTo(mutableSetOf()) { vertex ->
                ComposerImpl(ComposerVertexCore(vertex)).also { vertex.tag = it }
            }
        val moods =
            graph.moodVertex.mapTo(mutableSetOf()) { vertex ->
                MoodImpl(MoodVertexCore(vertex)).also { vertex.tag = it }
            }
        val groupings =
            graph.groupingVertex.mapTo(mutableSetOf()) { vertex ->
                GroupingImpl(GroupingVertexCore(vertex)).also { vertex.tag = it }
            }
        val customValues =
            graph.customValueVertex.mapTo(mutableSetOf()) { vertex ->
                CustomValueImpl(CustomValueVertexCore(vertex)).also { vertex.tag = it }
            }
        val playlists =
            graph.playlistVertex.mapTo(mutableSetOf()) { vertex ->
                PlaylistImpl(PlaylistVertexCore(vertex))
            }
        return LibraryImpl(
            songs,
            albums,
            artists,
            genres,
            composers,
            moods,
            groupings,
            customValues,
            playlists,
            storedPlaylists,
            playlistInterpreter,
            moodContext,
        )
    }

    private class SongVertexCore(private val vertex: SongVertex) : SongCore {
        override val preSong = vertex.preSong

        override fun resolveAlbum(): Album = tag(vertex.albumVertex)

        override fun resolveArtists(): List<Artist> = vertex.artistVertices.map { tag(it) }

        override fun resolveGenres(): List<Genre> = vertex.genreVertices.map { tag(it) }

        override fun resolveComposers(): List<Composer> = vertex.composerVertices.map { tag(it) }

        override fun resolveMoods(): List<Mood> = vertex.moodVertices.map { tag(it) }

        override fun resolveGroupings(): List<Grouping> = vertex.groupingVertices.map { tag(it) }

        override fun resolveCustomValues(): List<CustomValue> =
            vertex.customValueVertices.map { tag(it) }
    }

    private class AlbumVertexCore(private val vertex: AlbumVertex) : AlbumCore {
        override val preAlbum = vertex.preAlbum

        override val songs: Set<Song> = vertex.songVertices.mapTo(mutableSetOf()) { tag(it) }

        override fun resolveArtists(): List<Artist> = vertex.artistVertices.map { tag(it) }
    }

    private class ArtistVertexCore(private val vertex: ArtistVertex) : ArtistCore {
        override val preArtist = vertex.preArtist

        override val songs: Set<Song> = vertex.songVertices.mapTo(mutableSetOf()) { tag(it) }

        override val albums: Set<Album> = vertex.albumVertices.mapTo(mutableSetOf()) { tag(it) }

        override fun resolveGenres(): Set<Genre> =
            vertex.genreVertices.mapTo(mutableSetOf()) { tag(it) }
    }

    private class GenreVertexCore(vertex: GenreVertex) : GenreCore {
        override val preGenre = vertex.preGenre

        override val songs: Set<Song> = vertex.songVertices.mapTo(mutableSetOf()) { tag(it) }

        override val artists: Set<Artist> = vertex.artistVertices.mapTo(mutableSetOf()) { tag(it) }
    }

    private class ComposerVertexCore(vertex: ComposerVertex) : ComposerCore {
        override val preComposer = vertex.preComposer

        override val songs: Set<Song> = vertex.songVertices.mapTo(mutableSetOf()) { tag(it) }

        override val artists: Set<Artist> =
            vertex.songVertices.flatMapTo(mutableSetOf()) { song ->
                song.artistVertices.map { tag<Artist>(it) }
            }
    }

    private class MoodVertexCore(vertex: MoodVertex) : MoodCore {
        override val preMood = vertex.preMood

        override val songs: Set<Song> = vertex.songVertices.mapTo(mutableSetOf()) { tag(it) }

        override val artists: Set<Artist> =
            vertex.songVertices.flatMapTo(mutableSetOf()) { song ->
                song.artistVertices.map { tag<Artist>(it) }
            }
    }

    private class GroupingVertexCore(vertex: GroupingVertex) : GroupingCore {
        override val preGrouping = vertex.preGrouping

        override val songs: Set<Song> = vertex.songVertices.mapTo(mutableSetOf()) { tag(it) }

        override val artists: Set<Artist> =
            vertex.songVertices.flatMapTo(mutableSetOf()) { song ->
                song.artistVertices.map { tag<Artist>(it) }
            }
    }

    private class CustomValueVertexCore(vertex: CustomValueVertex) : CustomValueCore {
        override val preCustomValue = vertex.preCustomValue

        override val songs: Set<Song> = vertex.songVertices.mapTo(mutableSetOf()) { tag(it) }

        override val artists: Set<Artist> =
            vertex.songVertices.flatMapTo(mutableSetOf()) { song ->
                song.artistVertices.map { tag<Artist>(it) }
            }
    }

    private class PlaylistVertexCore(vertex: PlaylistVertex) : PlaylistCore {
        override val prePlaylist = vertex.prePlaylist

        override val songs: List<Song> =
            vertex.songVertices.mapNotNull { vertex -> vertex?.let { tag(it) } }
    }

    private companion object {
        private inline fun <reified T : Music> tag(vertex: Vertex): T {
            val tag = vertex.tag
            check(tag is T) { "Dead Vertex Detected: $vertex" }
            return tag
        }
    }
}
