/*
 * Copyright (c) 2026 Auxio Project
 * AuxioToolbar.kt is part of Auxio.
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
 
package org.oxycblt.auxio.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.annotation.MenuRes
import androidx.appcompat.R as AR
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.children
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import com.google.android.material.R as MR
import com.google.android.material.color.MaterialColors
import kotlin.math.roundToInt
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.ViewToolbarBinding
import org.oxycblt.auxio.util.isRtl
import org.oxycblt.auxio.util.unlikelyToBeNull

/**
 * Minimal MaterialToolbar replacement that implements everything with MaterialButtons compared to
 * the main androidx/MDC toolbar which is more or less unusable.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
class AuxioToolbar
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = AR.attr.toolbarStyle,
) : FrameLayout(context, attrs, defStyleAttr) {
    private var inflatingLayout: Boolean = true
    private val binding: ViewToolbarBinding =
        ViewToolbarBinding.inflate(LayoutInflater.from(context), this, true)
    private var menuItemClickListener: Toolbar.OnMenuItemClickListener? = null
    private var overflowClickListener: OnClickListener? = null
    private var actionRow: LinearLayout? = null
    private var actionRowShown = true
    private var overflowPriority = listOf<Int>()
    private val actionButtons = mutableMapOf<Int, RippleFixMaterialButton>()
    private var overflowIntoMenu = false
    private var onActionsRebuilt: (() -> Unit)? = null
    @SuppressLint("RestrictedApi") private var menuBuilder = MenuBuilder(context)

    init {
        inflatingLayout = false

        clipChildren = false
        clipToPadding = false

        binding.toolbarRoot.apply {
            clipChildren = false
            clipToPadding = false
        }
        binding.toolbarContentFrame.apply {
            clipChildren = false
            clipToPadding = false
        }
        binding.toolbarActionGroup.apply {
            clipChildren = false
            clipToPadding = false
            spacing = 0
        }

        configureIconButton(binding.toolbarNavigationButton)

        val toolbarAttrs =
            context.obtainStyledAttributes(attrs, AR.styleable.Toolbar, defStyleAttr, 0)
        val materialToolbarAttrs =
            context.obtainStyledAttributes(attrs, MR.styleable.MaterialToolbar, defStyleAttr, 0)

        val titleCentered =
            materialToolbarAttrs.getBoolean(MR.styleable.MaterialToolbar_titleCentered, false)
        binding.toolbarTitle.apply {
            text = toolbarAttrs.getText(AR.styleable.Toolbar_title)
            isVisible = !text.isNullOrEmpty()
            gravity =
                if (titleCentered) android.view.Gravity.CENTER_HORIZONTAL
                else android.view.Gravity.START
            textAlignment =
                if (titleCentered) {
                    TEXT_ALIGNMENT_CENTER
                } else {
                    TEXT_ALIGNMENT_VIEW_START
                }
            setTextAppearance(
                toolbarAttrs.getResourceId(
                    AR.styleable.Toolbar_titleTextAppearance,
                    MR.attr.textAppearanceDisplaySmallEmphasized,
                )
            )
        }

        val subtitleCentered =
            materialToolbarAttrs.getBoolean(MR.styleable.MaterialToolbar_subtitleCentered, false)
        binding.toolbarSubtitle.apply {
            text = toolbarAttrs.getText(AR.styleable.Toolbar_subtitle)
            isVisible = !text.isNullOrEmpty()
            gravity =
                if (subtitleCentered) android.view.Gravity.CENTER_HORIZONTAL
                else android.view.Gravity.START
            textAlignment =
                if (subtitleCentered) {
                    TEXT_ALIGNMENT_CENTER
                } else {
                    TEXT_ALIGNMENT_VIEW_START
                }
            setTextAppearance(
                toolbarAttrs.getResourceId(
                    AR.styleable.Toolbar_subtitleTextAppearance,
                    MR.attr.textAppearanceTitleLarge,
                )
            )
        }
        binding.toolbarNavigationButton.apply {
            icon = toolbarAttrs.getDrawable(AR.styleable.Toolbar_navigationIcon)
            contentDescription =
                toolbarAttrs.getText(AR.styleable.Toolbar_navigationContentDescription)
            isVisible = icon != null
            TooltipCompat.setTooltipText(this, contentDescription)
            if (isRtl) {
                rotation = 180f
            }
        }

        val menuResId = toolbarAttrs.getResourceId(AR.styleable.Toolbar_menu, 0)

        toolbarAttrs.recycle()
        materialToolbarAttrs.recycle()

        if (menuResId != 0) {
            inflateMenu(menuResId)
        }
    }

    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
        if (inflatingLayout) {
            super.addView(child, index, params)
        } else {
            // hardcode layoutparams because injectable children logic is insane and never
            binding.toolbarTitleContainer.addView(
                child,
                LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
            )
        }
    }

    var title: CharSequence?
        get() = binding.toolbarTitle.text
        set(value) {
            binding.toolbarTitle.apply {
                text = value
                isVisible = !text.isNullOrEmpty()
            }
        }

    var subtitle: CharSequence?
        get() = binding.toolbarSubtitle.text
        set(value) {
            binding.toolbarSubtitle.apply {
                text = value
                isVisible = !text.isNullOrEmpty()
            }
        }

    val menu: Menu
        get() = menuBuilder

    @SuppressLint("RestrictedApi")
    fun inflateMenu(@MenuRes resId: Int) {
        val builder = MenuBuilder(context)
        SupportMenuInflater(context).inflate(resId, builder)
        menuBuilder = builder
        rebuildActionButtons()
    }

    fun setOnMenuItemClickListener(listener: Toolbar.OnMenuItemClickListener?) {
        menuItemClickListener = listener
    }

    fun getMenuButton(itemId: Int): RippleFixMaterialButton? = actionButtons[itemId]

    /** The IDs of the menu items that have an action button right now. */
    val actionItemIds: Set<Int>
        get() = actionButtons.keys.toSet()

    /**
     * Leave out the overflow button, for toolbars that list the actions that do not fit in a menu
     * of their own, such as one opened by one of their actions.
     *
     * @param onRebuilt Called whenever the action buttons change, so that [actionItemIds] can be
     *   read again.
     */
    fun setOverflowIntoMenu(onRebuilt: () -> Unit) {
        overflowIntoMenu = true
        onActionsRebuilt = onRebuilt
        rebuildActionButtons()
    }

    /**
     * Show or hide a menu item, along with its action button.
     *
     * @param itemId The ID of the menu item to change.
     * @param visible Whether the menu item should be shown.
     */
    @SuppressLint("RestrictedApi")
    fun setMenuItemVisible(itemId: Int, visible: Boolean) {
        val item = menuBuilder.findItem(itemId) ?: return
        if (item.isVisible != visible) {
            item.isVisible = visible
            rebuildActionButtons()
        }
    }

    /**
     * Change the icon of a menu item, along with its action button.
     *
     * @param itemId The ID of the menu item to change.
     * @param iconRes The drawable resource of the new icon.
     */
    @SuppressLint("RestrictedApi")
    fun setMenuItemIcon(itemId: Int, @DrawableRes iconRes: Int) {
        menuBuilder.findItem(itemId)?.setIcon(iconRes)
        getMenuButton(itemId)?.setIconResource(iconRes)
    }

    /**
     * Replace the menu with some of the items of another menu, in the given order, along with their
     * action buttons.
     *
     * @param source The menu to take the items from.
     * @param itemIds The IDs of the top-level items of [source] to use, in the order to show them.
     */
    @SuppressLint("RestrictedApi")
    fun arrangeMenuItems(source: Menu, itemIds: List<Int>) {
        val old = source
        val builder = MenuBuilder(context)
        itemIds.forEachIndexed { order, itemId ->
            val item = old.findItem(itemId) as? MenuItemImpl ?: return@forEachIndexed
            val subMenu = item.subMenu
            val copy =
                if (subMenu != null) {
                    val newSubMenu = builder.addSubMenu(item.groupId, itemId, order, item.title)
                    for (child in subMenu.children) {
                        newSubMenu
                            .add(child.groupId, child.itemId, child.order, child.title)
                            .setIcon(child.icon)
                    }
                    newSubMenu.item
                } else {
                    builder.add(item.groupId, itemId, order, item.title)
                }
            copy.icon = item.icon
            copy.isEnabled = item.isEnabled
            copy.isVisible = item.isVisible
            copy.setShowAsAction(
                when {
                    item.requiresActionButton() -> MenuItem.SHOW_AS_ACTION_ALWAYS
                    item.requestsActionButton() -> MenuItem.SHOW_AS_ACTION_IF_ROOM
                    else -> MenuItem.SHOW_AS_ACTION_NEVER
                }
            )
        }
        menuBuilder = builder
        rebuildActionButtons()
    }

    /**
     * Move the action buttons out of the title row into a row of their own below it, for toolbars
     * with more actions than fit beside the title. Menu items in different menu groups are set
     * apart by dividers, the first group at the start of the row and the last one at its end.
     * Actions that do not fit go into the overflow menu, in the order given by
     * [setOverflowPriority].
     */
    fun setActionsBelow() {
        if (actionRow != null) {
            return
        }
        binding.toolbarRoot.removeView(binding.toolbarActionGroup)
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
            }
        val column =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                clipChildren = false
                clipToPadding = false
            }
        removeView(binding.toolbarRoot)
        column.addView(
            binding.toolbarRoot,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        column.addView(
            row,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        // Children added from outside go to the title container, so add this one like the
        // inflated layout was.
        inflatingLayout = true
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        inflatingLayout = false
        actionRow = row
        rebuildActionButtons()
    }

    /**
     * Set which actions give way first when the row made by [setActionsBelow] is too narrow.
     *
     * @param itemIds The IDs of the menu items to move into the overflow menu, in the order they
     *   should be moved. Items that are not listed always keep their button.
     */
    fun setOverflowPriority(itemIds: List<Int>) {
        overflowPriority = itemIds
        rebuildActionButtons()
    }

    /**
     * Show or hide the row made by [setActionsBelow], so that it takes no space while its toolbar
     * is not in use.
     *
     * @param visible Whether the row should be shown.
     */
    fun setActionRowVisible(visible: Boolean) {
        actionRowShown = visible
        actionRow?.let { it.isVisible = visible && it.isNotEmpty() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (actionRow != null && w != oldw) {
            // How many actions fit depends on the width.
            post { rebuildActionButtons() }
        }
    }

    @SuppressLint("RestrictedApi")
    fun setMenuItemEnabled(itemId: Int, enabled: Boolean) {
        menuBuilder.findItem(itemId)?.isEnabled = enabled
        getMenuButton(itemId)?.isEnabled = enabled
    }

    val titleContainer: View
        get() = binding.toolbarTitleContainer

    fun setNavigationOnClickListener(listener: OnClickListener) {
        binding.toolbarNavigationButton.setOnClickListener(listener)
    }

    /**
     * Override the overflow button's click behavior. When set, the overflow button will call
     * [block] instead of showing the default popup menu.
     */
    fun setOnOverflowMenuClick(block: OnClickListener) {
        overflowClickListener = block
    }

    private fun configureIconButton(button: RippleFixMaterialButton) {
        button.minimumWidth = resources.getDimensionPixelSize(R.dimen.size_touchable_small)
        button.minimumHeight = resources.getDimensionPixelSize(R.dimen.size_touchable_small)
        button.iconSize = resources.getDimensionPixelSize(R.dimen.size_icon_small)
    }

    @SuppressLint("RestrictedApi")
    private fun rebuildActionButtons() {
        val row = actionRow
        val container: ViewGroup = row ?: binding.toolbarActionGroup
        actionButtons.clear()
        container.removeAllViews()

        val actionItems = mutableListOf<MenuItemImpl>()
        val overflowItems = mutableListOf<MenuItemImpl>()

        for (i in 0 until menuBuilder.size()) {
            val item = menuBuilder.getItem(i) as MenuItemImpl
            if (!item.isVisible) continue
            if (item.requiresActionButton() || item.requestsActionButton()) {
                actionItems.add(item)
            } else {
                overflowItems.add(item)
            }
        }

        if (row != null) {
            fitActionItems(actionItems, overflowItems)
        }

        var lastItem: MenuItemImpl? = null
        for (item in actionItems) {
            if (row != null && lastItem != null && lastItem.groupId != item.groupId) {
                row.addView(createDivider())
            }
            lastItem = item
            val btn = createActionButton(item)
            container.addView(btn)
            actionButtons[item.itemId] = btn
        }

        if (overflowItems.isNotEmpty() && !overflowIntoMenu) {
            container.addView(createOverflowButton(overflowItems))
        }

        container.isVisible = container.isNotEmpty() && (row == null || actionRowShown)
        onActionsRebuilt?.invoke()
    }

    /** Move actions into the overflow menu until the rest fits into the width of this toolbar. */
    @SuppressLint("RestrictedApi")
    private fun fitActionItems(
        actionItems: MutableList<MenuItemImpl>,
        overflowItems: MutableList<MenuItemImpl>,
    ) {
        val available = width - paddingLeft - paddingRight
        if (available <= 0) {
            // Not laid out yet, onSizeChanged will come back to this.
            return
        }
        val buttonSize = resources.getDimensionPixelSize(R.dimen.size_touchable_small)
        val dividerSize = resources.getDimensionPixelSize(R.dimen.spacing_medium)
        fun needed(): Int {
            val buttons =
                actionItems.size + if (overflowItems.isNotEmpty() && !overflowIntoMenu) 1 else 0
            val dividers = (actionItems.map { it.groupId }.distinct().size - 1).coerceAtLeast(0)
            return buttons * buttonSize + dividers * dividerSize
        }
        for (itemId in overflowPriority) {
            if (needed() <= available) {
                break
            }
            val item = actionItems.find { it.itemId == itemId } ?: continue
            actionItems.remove(item)
            overflowItems.add(item)
        }
    }

    /** Create a divider that takes up the free space between two groups of actions. */
    private fun createDivider() =
        FrameLayout(context).apply {
            minimumWidth = resources.getDimensionPixelSize(R.dimen.spacing_medium)
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            val line =
                View(context).apply {
                    setBackgroundColor(
                        MaterialColors.getColor(this@AuxioToolbar, MR.attr.colorOutlineVariant)
                    )
                }
            // Not the overridden addView of the toolbar, as this is a plain FrameLayout.
            addView(
                line,
                LayoutParams(
                    resources.displayMetrics.density.roundToInt().coerceAtLeast(1),
                    resources.getDimensionPixelSize(R.dimen.size_icon_small),
                    Gravity.CENTER,
                ),
            )
        }

    @SuppressLint("RestrictedApi")
    private fun createActionButton(item: MenuItemImpl) =
        RippleFixMaterialButton(
                createActionButtonContext(item.itemId),
                null,
                MR.attr.materialIconButtonStyle,
            )
            .apply {
                configureIconButton(this)
                id = item.itemId
                icon = item.icon
                contentDescription = item.title
                isEnabled = item.isEnabled
                TooltipCompat.setTooltipText(this, item.title)
                setOnClickListener { view ->
                    if (item.hasSubMenu()) {
                        val subMenu = unlikelyToBeNull(item.subMenu)
                        showPopupMenu(view, subMenu.children.toList())
                    } else {
                        menuItemClickListener?.onMenuItemClick(item)
                    }
                }
            }

    @SuppressLint("RestrictedApi")
    private fun createOverflowButton(overflowItems: List<MenuItemImpl>) =
        RippleFixMaterialButton(context, null, MR.attr.materialIconButtonStyle).apply {
            configureIconButton(this)
            setIconResource(R.drawable.ic_more_vert_24)
            contentDescription = context.getString(R.string.lbl_more)
            TooltipCompat.setTooltipText(this, contentDescription)
            setOnClickListener { view ->
                val customListener = overflowClickListener
                if (customListener != null) {
                    customListener.onClick(view)
                } else {
                    showPopupMenu(view, overflowItems)
                }
            }
        }

    @SuppressLint("RestrictedApi")
    private fun showPopupMenu(anchor: View, items: List<MenuItem>) {
        val popup = PopupMenu(context, anchor)
        val originalItems = mutableMapOf<Int, MenuItem>()
        for (item in items) {
            popup.menu.add(item.groupId, item.itemId, item.order, item.title).apply {
                icon = item.icon
                isEnabled = item.isEnabled
                isCheckable = item.isCheckable
                isChecked = item.isChecked
            }
            originalItems[item.itemId] = item
        }
        popup.setOnMenuItemClickListener { clickedItem ->
            val originalItem =
                originalItems[clickedItem.itemId] ?: return@setOnMenuItemClickListener false
            menuItemClickListener?.onMenuItemClick(originalItem) ?: false
        }
        popup.show()
    }

    private fun createActionButtonContext(itemId: Int): Context {
        val themeOverlayRes =
            when (itemId) {
                R.id.action_play -> R.style.ThemeOverlay_Auxio_IconButton_Style_Small_Secondary
                R.id.action_shuffle -> R.style.ThemeOverlay_Auxio_IconButton_Style_Small_Primary
                else -> 0
            }

        return if (themeOverlayRes != 0) ContextThemeWrapper(context, themeOverlayRes) else context
    }
}
