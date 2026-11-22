/*
 * Copyright (c) 2026 Auxio Project
 * NowPlayingBar.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.bar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.IdRes
import androidx.annotation.MenuRes
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.view.children
import javax.inject.Inject
import javax.inject.Singleton
import org.oxycblt.auxio.R
import org.oxycblt.auxio.util.Event
import org.oxycblt.auxio.util.MutableEvent

/**
 * The action bar of the playback panel. The user picks which actions it shows, from its own menu
 * and the song menu, and in what order. Actions that are not shown, or that do not fit, are listed
 * in the song menu.
 */
object NowPlayingBar {
    /**
     * An action of the bar as chosen by the user.
     *
     * @param name The resource entry name of the menu item, such as "action_sleep_timer". Names are
     *   stored instead of IDs, as IDs change between builds.
     * @param visible Whether the action is shown on the bar, or only in the song menu.
     */
    data class Action(val name: String, val visible: Boolean) {
        companion object {
            /** Read a list of [Action]s from a code made by [toCode]. */
            fun fromCode(code: String?) =
                code
                    ?.split(',')
                    ?.filter { it.isNotBlank() }
                    ?.map {
                        if (it.startsWith('-')) Action(it.substring(1), false) else Action(it, true)
                    } ?: listOf()

            /** Write a list of [Action]s into a code that [fromCode] can read. */
            fun toCode(actions: List<Action>) =
                actions.joinToString(",") { if (it.visible) it.name else "-${it.name}" }
        }
    }

    /**
     * An entry of the song menu that comes from the bar.
     *
     * @param itemId The menu item ID.
     * @param title The title to show.
     * @param icon The icon to show.
     * @param fromBar Whether the item comes from the bar menu, and so must be run by the playback
     *   panel, instead of from the song menu.
     */
    data class Entry(
        @IdRes val itemId: Int,
        val title: CharSequence,
        val icon: Drawable?,
        val fromBar: Boolean,
    )

    /**
     * The menu item that always stays at the end of the bar and opens the song menu. Not a const,
     * as a const copy of a resource ID can go stale in incremental builds.
     */
    val MORE_ITEM_ID: Int
        get() = R.id.action_song_menu

    /**
     * Inflate every action that can go on the bar: the bar menu, followed by the song menu items
     * that the bar menu does not have. All of them ask for an action button.
     */
    @SuppressLint("RestrictedApi")
    fun inflate(context: Context): Menu {
        val menu = MenuBuilder(context)
        val inflater = SupportMenuInflater(context)
        inflater.inflate(R.menu.toolbar_playback, menu)
        val songMenu = MenuBuilder(context).also { inflater.inflate(R.menu.playback_song, it) }
        for (item in songMenu.children) {
            if (menu.findItem(item.itemId) == null) {
                menu.add(item.groupId, item.itemId, Menu.NONE, item.title).icon = item.icon
            }
        }
        for (item in menu.children) {
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        return menu
    }

    /**
     * Get the IDs of the top-level items of a menu resource.
     *
     * @param context [Context] required to inflate the menu.
     * @param menuRes The menu resource.
     */
    @SuppressLint("RestrictedApi")
    fun itemIds(context: Context, @MenuRes menuRes: Int): Set<Int> =
        // Item IDs never change while the app runs, so each menu only has to be inflated once.
        itemIdCache.getOrPut(menuRes) {
            MenuBuilder(context)
                .also { SupportMenuInflater(context).inflate(menuRes, it) }
                .children
                .mapTo(mutableSetOf()) { it.itemId }
        }

    private val itemIdCache = mutableMapOf<Int, Set<Int>>()

    /**
     * Arrange the configurable actions as chosen by the user.
     *
     * @param context [Context] required to look up item names.
     * @param menu The actions, as made by [inflate].
     * @param actions The user's choice, from [NowPlayingBarSettings.barActions].
     * @return The configurable menu items in the order to show them, each with whether it is shown
     *   on the bar. Items the user has not arranged yet are put at the end: shown if they come from
     *   the bar menu, hidden if they come from the song menu.
     */
    fun arrange(
        context: Context,
        menu: Menu,
        actions: List<Action>,
    ): List<Pair<MenuItem, Boolean>> {
        val items = menu.children.filter { it.itemId != MORE_ITEM_ID }.toList()
        val byName = items.associateBy { context.resources.getResourceEntryName(it.itemId) }
        val arranged = actions.mapNotNull { action ->
            byName[action.name]?.let { it to action.visible }
        }
        val placed = arranged.mapTo(mutableSetOf()) { it.first.itemId }
        val barIds = itemIds(context, R.menu.toolbar_playback)
        return arranged + items.filter { it.itemId !in placed }.map { it to (it.itemId in barIds) }
    }

    /**
     * Turn an arrangement made by [arrange] back into [Action]s to store.
     *
     * @param context [Context] required to look up item names.
     * @param arranged The arranged items, each with whether it is shown on the bar.
     */
    fun toActions(context: Context, arranged: List<Pair<MenuItem, Boolean>>) =
        arranged.map { (item, visible) ->
            Action(context.resources.getResourceEntryName(item.itemId), visible)
        }

    /**
     * Get the IDs of the actions to put on the bar, in order, followed by [MORE_ITEM_ID].
     *
     * @param context [Context] required to look up item names.
     * @param menu The actions, as made by [inflate].
     * @param actions The user's choice, from [NowPlayingBarSettings.barActions].
     */
    fun shownItemIds(context: Context, menu: Menu, actions: List<Action>) =
        arrange(context, menu, actions).filter { it.second }.map { it.first.itemId } +
            listOf(MORE_ITEM_ID)

    /**
     * Get the song menu entries: every action that is not on the bar right now, in the order chosen
     * by the user, with sub-menus such as "Go to" flattened into their items.
     *
     * @param context [Context] required to look up item names and titles.
     * @param actions The user's choice, from [NowPlayingBarSettings.barActions].
     * @param onBar The IDs of the actions that have a button on the bar right now.
     */
    fun songMenuEntries(context: Context, actions: List<Action>, onBar: Set<Int>): List<Entry> {
        val barIds = itemIds(context, R.menu.toolbar_playback)
        return arrange(context, inflate(context), actions)
            .filter { (item, _) -> item.itemId !in onBar }
            .flatMap { (item, _) ->
                val fromBar = item.itemId in barIds
                val subMenu = item.subMenu
                if (subMenu != null) {
                    subMenu.children
                        .map { child ->
                            Entry(
                                child.itemId,
                                subMenuTitle(context, item, child),
                                item.icon,
                                fromBar,
                            )
                        }
                        .toList()
                } else {
                    listOf(Entry(item.itemId, item.title ?: "", item.icon, fromBar))
                }
            }
    }

    private fun subMenuTitle(context: Context, parent: MenuItem, child: MenuItem): CharSequence =
        when (child.itemId) {
            R.id.action_artist_details -> context.getString(R.string.lbl_artist_details)
            R.id.action_album_details -> context.getString(R.string.lbl_album_details)
            R.id.action_genre_details -> context.getString(R.string.lbl_genre_details)
            else -> "${parent.title}: ${child.title}"
        }
}

/**
 * Connects the playback panel bar with the song menu: tells the song menu which actions are on the
 * bar right now, and passes clicks on bar actions in the song menu back to the playback panel,
 * which knows how to run them.
 */
@Singleton
class NowPlayingBarRequests @Inject constructor() {
    private val _request = MutableEvent<Int>()

    /** The menu item ID of the bar action to run. */
    val request: Event<Int>
        get() = _request

    /** The IDs of the actions that have a button on the bar right now. */
    @Volatile var onBar = setOf<Int>()

    /**
     * Ask the playback panel to run a bar action.
     *
     * @param itemId The menu item ID of the action.
     */
    fun run(@IdRes itemId: Int) {
        _request.put(itemId)
    }
}
