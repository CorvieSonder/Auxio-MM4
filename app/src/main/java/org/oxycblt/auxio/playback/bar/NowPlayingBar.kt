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
 * The action bar of the playback panel, whose actions the user can reorder or move into the song
 * menu.
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

    /** The menu items that always stay at the end of the bar. */
    private val FIXED_ITEM_IDS = setOf(R.id.action_song_menu)

    /**
     * Get the IDs of the top-level items of a menu resource.
     *
     * @param context [Context] required to inflate the menu.
     * @param menuRes The menu resource.
     */
    @SuppressLint("RestrictedApi")
    fun itemIds(context: Context, @MenuRes menuRes: Int) =
        MenuBuilder(context)
            .also { SupportMenuInflater(context).inflate(menuRes, it) }
            .children
            .mapTo(mutableSetOf()) { it.itemId }

    /** Inflate the menu resource of the bar. */
    @SuppressLint("RestrictedApi")
    fun inflate(context: Context): Menu =
        MenuBuilder(context).also {
            SupportMenuInflater(context).inflate(R.menu.toolbar_playback, it)
        }

    /**
     * Arrange the configurable items of a bar menu as chosen by the user.
     *
     * @param context [Context] required to look up item names.
     * @param menu The bar menu, as made by [inflate].
     * @param actions The user's choice, from [NowPlayingBarSettings.barActions].
     * @return The configurable menu items in the order to show them, each with whether it is shown
     *   on the bar. Items the user has not arranged yet are put at the end, shown.
     */
    fun arrange(
        context: Context,
        menu: Menu,
        actions: List<Action>,
    ): List<Pair<MenuItem, Boolean>> {
        val items = menu.children.filter { it.itemId !in FIXED_ITEM_IDS }.toList()
        val byName = items.associateBy { context.resources.getResourceEntryName(it.itemId) }
        val arranged = actions.mapNotNull { action ->
            byName[action.name]?.let { it to action.visible }
        }
        val placed = arranged.mapTo(mutableSetOf()) { it.first.itemId }
        return arranged + items.filter { it.itemId !in placed }.map { it to true }
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
     * Get the IDs of the bar items in the order to show them, leaving out the hidden ones.
     *
     * @param context [Context] required to look up item names.
     * @param menu The bar menu, as made by [inflate].
     * @param actions The user's choice, from [NowPlayingBarSettings.barActions].
     */
    fun shownItemIds(context: Context, menu: Menu, actions: List<Action>) =
        arrange(context, menu, actions).filter { it.second }.map { it.first.itemId } +
            FIXED_ITEM_IDS.filter { menu.findItem(it) != null }

    /**
     * Get the bar items that the user moved into the song menu, with sub-menus such as "Go to"
     * flattened into their items.
     *
     * @param context [Context] required to look up item names and titles.
     * @param actions The user's choice, from [NowPlayingBarSettings.barActions].
     * @return The hidden items as (ID, title, parent item) triples, in the order chosen by the
     *   user. The parent item supplies the icon.
     */
    fun hiddenEntries(
        context: Context,
        actions: List<Action>,
    ): List<Triple<Int, CharSequence, MenuItem>> {
        val menu = inflate(context)
        return arrange(context, menu, actions)
            .filter { !it.second }
            .flatMap { (item, _) ->
                val subMenu = item.subMenu
                if (subMenu != null) {
                    subMenu.children
                        .map { child ->
                            Triple(child.itemId, subMenuTitle(context, item, child), item)
                        }
                        .toList()
                } else {
                    listOf(Triple(item.itemId, item.title ?: "", item))
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
 * Passes clicks on bar actions that were moved into the song menu back to the playback panel, which
 * knows how to run them.
 */
@Singleton
class NowPlayingBarRequests @Inject constructor() {
    private val _request = MutableEvent<Int>()

    /** The menu item ID of the bar action to run. */
    val request: Event<Int>
        get() = _request

    /**
     * Ask the playback panel to run a bar action.
     *
     * @param itemId The menu item ID of the action.
     */
    fun run(@IdRes itemId: Int) {
        _request.put(itemId)
    }
}
