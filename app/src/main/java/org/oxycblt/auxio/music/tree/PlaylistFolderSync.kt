/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistFolderSync.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.tree

import android.content.Context
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/**
 * Syncs playlists with a folder exported by MediaMonkey: playlists linked by their MediaMonkey ID
 * are updated in place instead of copied.
 *
 * First a [Plan] is made, so that the user can be asked how to handle playlists changed on both
 * sides and whether to delete playlists that MediaMonkey no longer has. Then the plan is applied.
 */
class PlaylistFolderSync
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val playlistTreeRepository: PlaylistTreeRepository,
    private val playlistSyncRepository: PlaylistSyncRepository,
    private val importer: PlaylistFolderImporter,
) {
    /** How to handle a playlist changed both here and in MediaMonkey since the last sync. */
    enum class ConflictChoice {
        OVERWRITE,
        SKIP,
        MERGE,
    }

    /** One incoming playlist and what will happen to it. */
    class Item(
        val name: String,
        val songs: List<Song>,
        val missing: Int,
        val ids: PlaylistIds,
        /** The playlist here to update, or null to create a new one. */
        val target: Playlist?,
        /** The playlist's state at the last sync, null if never synced. */
        val base: PlaylistState?,
        /** The playlist's current state here. */
        val current: PlaylistState?,
        /** Whether the playlist was deleted here since the last sync; it is not recreated. */
        val deletedHere: Boolean,
        val children: List<Item>,
    ) {
        val incomingPaths = songs.map(PlaylistSyncRepository::pathOf)

        private fun changedFrom(base: PlaylistState, name: String, songs: List<String>) =
            name != base.name || songs != base.songs

        val changedHere: Boolean
            get() =
                base != null && current != null && changedFrom(base, current.name, current.songs)

        val changedThere: Boolean
            get() = base == null || changedFrom(base, name, incomingPaths)

        val isConflict: Boolean
            get() = target != null && base != null && changedHere && changedThere
    }

    /** A sync waiting for the user's answers. */
    class Plan(
        val scan: PlaylistFolderImporter.Scan,
        val parentUid: Music.UID?,
        val items: List<Item>,
        /** Linked playlists here that MediaMonkey no longer has. */
        val missing: List<Playlist>,
    ) {
        val conflicts: List<Item>
            get() = flatten(items).filter { it.isConflict }

        private fun flatten(items: List<Item>): List<Item> = items.flatMap {
            listOf(it) + flatten(it.children)
        }
    }

    /** The outcome of a sync. */
    data class Result(
        val created: Int,
        val updated: Int,
        val conflicts: Int,
        val deleted: Int,
        val missingSongs: Int,
    )

    /**
     * Plan syncing the [selected] items of [scan] into [parent], wrapped in one playlist for the
     * folder if [wrap].
     */
    suspend fun plan(
        scan: PlaylistFolderImporter.Scan,
        selected: List<PlaylistDocument>,
        wrap: Boolean,
        parent: Playlist?,
    ): Plan? {
        return try {
            val links = playlistSyncRepository.links()
            val seenMmIds = mutableSetOf<Long>()
            val planner = Planner(scan, links, seenMmIds)
            val items =
                if (wrap) {
                    listOf(
                        planner.folderItem(
                            scan.folder,
                            scan.own,
                            selected.filter { it != scan.own },
                            parent,
                        )
                    )
                } else {
                    planner.items(selected, parent)
                }
            val library = musicRepository.library
            val missing =
                if (scan.fullExport && library != null) {
                    links.values
                        .filter { it.mmId != null && it.mmId !in seenMmIds }
                        .mapNotNull { library.findPlaylist(it.playlistUid) }
                } else {
                    listOf()
                }
            Plan(scan, parent?.uid, items.filterNotNull(), missing)
        } catch (e: Exception) {
            L.e("Unable to plan the sync of ${scan.treeUri}")
            L.e(e.stackTraceToString())
            null
        }
    }

    private inner class Planner(
        val scan: PlaylistFolderImporter.Scan,
        val links: Map<Music.UID, PlaylistSyncLink>,
        val seenMmIds: MutableSet<Long>,
    ) {
        private val claimed = mutableSetOf<Music.UID>()

        suspend fun items(documents: List<PlaylistDocument>, parent: Playlist?): List<Item> =
            documents.mapNotNull { document ->
                if (document.isFolder) {
                    val children =
                        PlaylistDocuments.listChildren(
                            context.contentResolver,
                            scan.treeUri,
                            document.id,
                        )
                    val own = importer.findOwn(document, children)
                    val rest =
                        children
                            .filter {
                                it != own &&
                                    (it.isFolder || PlaylistDocuments.isPlaylistFile(it.name))
                            }
                            .sortedWith(compareBy({ !it.isFolder }, { it.name.lowercase() }))
                    folderItem(document, own, rest, parent)
                } else {
                    fileItem(document, parent, listOf())
                }
            }

        suspend fun folderItem(
            folder: PlaylistDocument,
            own: PlaylistDocument?,
            rest: List<PlaylistDocument>,
            parent: Playlist?,
        ): Item? {
            if (own != null) {
                return fileItem(own, parent, rest)
            }
            // A folder without its own file: a playlist without songs or IDs.
            val target = findTarget(PlaylistIds(), folder.name, parent)
            return item(folder.name, listOf(), 0, PlaylistIds(), target, rest)
        }

        private suspend fun fileItem(
            file: PlaylistDocument,
            parent: Playlist?,
            rest: List<PlaylistDocument>,
        ): Item? {
            val imported = importer.read(scan.treeUri, file) ?: return null
            val text =
                PlaylistSyncRepository.readText(
                    context.contentResolver,
                    DocumentsContract.buildDocumentUriUsingTree(scan.treeUri, file.id),
                )
            val ids = PlaylistIds.parse(text)
            ids.mmId?.let { seenMmIds.add(it) }
            val name = importer.nameOf(imported, file)
            val (songs, missing) = importer.resolve(imported, EntryInfo.parseAll(text))
            return item(name, songs, missing, ids, findTarget(ids, name, parent), rest)
        }

        private suspend fun item(
            name: String,
            songs: List<Song>,
            missing: Int,
            ids: PlaylistIds,
            found: Found,
            rest: List<PlaylistDocument>,
        ): Item {
            val target = found.playlist
            target?.let { claimed.add(it.uid) }
            val base = target?.let { links[it.uid] }?.let(PlaylistState::of)
            val current = target?.let { playlistSyncRepository.stateOf(it) }
            val children = items(rest, target)
            return Item(
                name,
                songs,
                missing,
                ids,
                target,
                base,
                current,
                found.deletedHere,
                children,
            )
        }

        /**
         * The playlist here that an incoming playlist updates: by its ID here, then by its
         * MediaMonkey ID, then (for playlists never synced) by name among [parent]'s children.
         */
        private suspend fun findTarget(ids: PlaylistIds, name: String, parent: Playlist?): Found {
            val library = musicRepository.library ?: return Found(null, false)
            val byId = ids.auxioId?.let { Music.UID.fromString(it) }?.let(library::findPlaylist)
            if (byId != null && byId.uid !in claimed) return Found(byId, false)
            val link = ids.mmId?.let { mmId -> links.values.find { it.mmId == mmId } }
            if (link != null) {
                val linked = library.findPlaylist(link.playlistUid)
                return if (linked != null && linked.uid !in claimed) Found(linked, false)
                else Found(null, linked == null)
            }
            val byName =
                withContext(Dispatchers.Main) {
                    playlistTreeRepository.childrenOf(parent).find {
                        it.name.raw == name && it.uid !in links && it.uid !in claimed
                    }
                }
            return Found(byName, false)
        }
    }

    private class Found(val playlist: Playlist?, val deletedHere: Boolean)

    /**
     * Apply [plan], handling conflicts by [choice] and deleting its missing playlists if [delete].
     */
    suspend fun apply(plan: Plan, choice: ConflictChoice, delete: Boolean): Result? {
        return try {
            val applier = Applier(choice)
            val parent = plan.parentUid?.let { musicRepository.library?.findPlaylist(it) }
            for (item in plan.items) {
                applier.apply(item, parent)
            }
            var deleted = 0
            if (delete) {
                for (playlist in plan.missing) {
                    val current = musicRepository.library?.findPlaylist(playlist.uid) ?: continue
                    musicRepository.deletePlaylist(current)
                    deleted++
                }
                playlistSyncRepository.forget(plan.missing.map { it.uid })
            }
            playlistSyncRepository.save(applier.links)
            if (applier.report.isNotEmpty()) {
                writeReport(plan, applier.report.toString())
            }
            Result(applier.created, applier.updated, applier.conflicts, deleted, applier.missing)
        } catch (e: Exception) {
            L.e("Unable to sync ${plan.scan.treeUri}")
            L.e(e.stackTraceToString())
            null
        }
    }

    private inner class Applier(val choice: ConflictChoice) {
        var created = 0
        var updated = 0
        var conflicts = 0
        var missing = 0
        val links = mutableListOf<PlaylistSyncLink>()
        val report = StringBuilder()

        suspend fun apply(item: Item, parent: Playlist?) {
            if (item.deletedHere) {
                L.d("${item.name} was deleted here since the last sync, not recreating it")
                return
            }
            missing += item.missing
            val playlist =
                if (item.target == null) create(item, parent) else update(item, item.target, parent)
            if (playlist != null) {
                links.add(
                    playlistSyncRepository.stateOf(playlist).toLink(playlist.uid, item.ids.mmId)
                )
            }
            for (child in item.children) {
                apply(child, playlist ?: parent)
            }
        }

        private suspend fun create(item: Item, parent: Playlist?): Playlist? {
            val playlist = importer.create(item.name, item.songs, parent) ?: return null
            created++
            return playlist
        }

        private suspend fun update(item: Item, target: Playlist, parent: Playlist?): Playlist? {
            val base = item.base
            val current = item.current ?: playlistSyncRepository.stateOf(target)
            var name = item.name
            var paths = item.incomingPaths
            if (item.isConflict) {
                conflicts++
                if (choice != ConflictChoice.OVERWRITE) {
                    report(item, current)
                }
                when (choice) {
                    ConflictChoice.OVERWRITE -> {}
                    ConflictChoice.SKIP -> {
                        name = current.name
                        paths = current.songs
                    }
                    ConflictChoice.MERGE -> {
                        val nameChangedThere = base != null && item.name != base.name
                        name = if (nameChangedThere) item.name else current.name
                        paths = PlaylistMerge.merge(base?.songs ?: listOf(), current.songs, paths)
                    }
                }
            } else if (!item.changedThere || (item.changedHere && base != null)) {
                // Unchanged in MediaMonkey: keep the changes made here.
                name = current.name
                paths = current.songs
            }

            if (name != current.name) {
                musicRepository.renamePlaylist(target, name)
            }
            if (paths != current.songs) {
                val bySong = (target.songs + item.songs).associateBy(PlaylistSyncRepository::pathOf)
                val fresh = musicRepository.library?.findPlaylist(target.uid) ?: return null
                musicRepository.rewritePlaylist(fresh, paths.mapNotNull { bySong[it] })
            }
            val fresh = musicRepository.library?.findPlaylist(target.uid) ?: return null
            // Follow MediaMonkey's nesting, unless only this side moved the playlist.
            if (base == null || base.parentUid != parent?.uid) {
                if (current.parentUid != parent?.uid) {
                    withContext(Dispatchers.Main) {
                        if (playlistTreeRepository.canMove(fresh, parent)) {
                            playlistTreeRepository.move(listOf(fresh), parent)
                        }
                    }
                }
            }
            if (name != current.name || paths != current.songs) {
                updated++
            }
            return fresh
        }

        private fun report(item: Item, current: PlaylistState) {
            val here = current.songs.toSet()
            val there = item.incomingPaths.toSet()
            report.append("== ${current.name}")
            if (item.name != current.name) report.append(" (MediaMonkey: ${item.name})")
            report.append(" ==\n")
            report.append("Only on the phone:\n")
            current.songs.filter { it !in there }.forEach { report.append("  $it\n") }
            report.append("Only in MediaMonkey:\n")
            item.incomingPaths.filter { it !in here }.forEach { report.append("  $it\n") }
            val base = item.base
            if (
                base != null &&
                    PlaylistMerge.reordered(current.songs, base.songs) &&
                    PlaylistMerge.reordered(item.incomingPaths, base.songs)
            ) {
                report.append("Order changed on both sides.\n")
            }
            report.append("\n")
        }
    }

    private fun writeReport(plan: Plan, text: String) {
        val treeUri = plan.scan.treeUri
        val folderUri =
            DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
        val uri =
            DocumentsContract.createDocument(
                context.contentResolver,
                folderUri,
                "text/plain",
                "_conflicts.txt",
            ) ?: return
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
    }
}
