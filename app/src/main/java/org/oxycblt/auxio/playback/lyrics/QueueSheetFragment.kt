/*
 * Copyright (c) 2026 Auxio Project
 * QueueSheetFragment.kt is part of Auxio.
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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.databinding.FragmentQueueSheetBinding
import org.oxycblt.auxio.playback.queue.QueueFragment
import org.oxycblt.auxio.ui.ViewBindingFragment
import org.oxycblt.auxio.util.collectImmediately

/**
 * The content of the queue sheet: a pager with the queue (page 0) and the lyrics (page 1). Swipe
 * between them, or pick one with the Queue | Lyrics handle.
 */
@AndroidEntryPoint
class QueueSheetFragment : ViewBindingFragment<FragmentQueueSheetBinding>() {
    private val lyricsModel: LyricsViewModel by activityViewModels()

    private val pageCallback =
        object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                lyricsModel.selectTab(position)
                updateScrollingChild()
            }
        }

    private val pageViewCallback =
        object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(
                fm: FragmentManager,
                f: Fragment,
                v: View,
                savedInstanceState: Bundle?,
            ) {
                v.post { updateScrollingChild() }
            }
        }

    override fun onCreateBinding(inflater: LayoutInflater) =
        FragmentQueueSheetBinding.inflate(inflater)

    override fun onBindingCreated(binding: FragmentQueueSheetBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)
        childFragmentManager.registerFragmentLifecycleCallbacks(pageViewCallback, false)
        binding.queueSheetPager.apply {
            adapter = PageAdapter(this@QueueSheetFragment)
            offscreenPageLimit = 1
            // The pager's own list must not be taken for the sheet's scrolling child.
            (getChildAt(0) as? RecyclerView)?.isNestedScrollingEnabled = false
            setCurrentItem(lyricsModel.selectedTab.value, false)
            registerOnPageChangeCallback(pageCallback)
        }
        collectImmediately(lyricsModel.selectedTab) { tab ->
            val pager = requireBinding().queueSheetPager
            if (pager.currentItem != tab) {
                pager.setCurrentItem(tab, pager.isShown)
            }
        }
    }

    override fun onDestroyBinding(binding: FragmentQueueSheetBinding) {
        super.onDestroyBinding(binding)
        binding.queueSheetPager.unregisterOnPageChangeCallback(pageCallback)
        childFragmentManager.unregisterFragmentLifecycleCallbacks(pageViewCallback)
    }

    /**
     * The bottom sheet drags and scrolls through one list, the first nested-scrolling view it
     * finds. Enable nested scrolling only on the visible page's lists, then re-layout so the sheet
     * looks for it again.
     */
    private fun updateScrollingChild() {
        val binding = requireBinding()
        val current = binding.queueSheetPager.currentItem
        var changed = false
        for (position in 0 until PAGE_COUNT) {
            // FragmentStateAdapter tags its pages "f" + item id (the position here).
            val page = childFragmentManager.findFragmentByTag("f$position")?.view ?: continue
            page.forEachRecycler { recycler ->
                val enable = position == current
                if (recycler.isNestedScrollingEnabled != enable) {
                    recycler.isNestedScrollingEnabled = enable
                    changed = true
                }
            }
        }
        if (changed) {
            binding.root.requestLayout()
        }
    }

    private fun View.forEachRecycler(block: (RecyclerView) -> Unit) {
        if (this is RecyclerView) {
            block(this)
        } else if (this is ViewGroup) {
            children.forEach { it.forEachRecycler(block) }
        }
    }

    private class PageAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount() = PAGE_COUNT

        override fun createFragment(position: Int): Fragment =
            if (position == LyricsViewModel.TAB_LYRICS) LyricsFragment() else QueueFragment()
    }

    private companion object {
        const val PAGE_COUNT = 2
    }
}
