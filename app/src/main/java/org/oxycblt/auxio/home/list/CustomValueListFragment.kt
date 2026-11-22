/*
 * Copyright (c) 2026 Auxio Project
 * CustomValueListFragment.kt is part of Auxio.
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
 
package org.oxycblt.auxio.home.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.FragmentHomeListBinding
import org.oxycblt.auxio.detail.DetailViewModel
import org.oxycblt.auxio.home.HomeViewModel
import org.oxycblt.auxio.list.ListFragment
import org.oxycblt.auxio.list.ListViewModel
import org.oxycblt.auxio.list.SelectableListListener
import org.oxycblt.auxio.list.adapter.SelectionIndicatorAdapter
import org.oxycblt.auxio.list.recycler.CustomValueViewHolder
import org.oxycblt.auxio.list.recycler.FastScrollRecyclerView
import org.oxycblt.auxio.list.sort.Sort
import org.oxycblt.auxio.music.IndexingState
import org.oxycblt.auxio.music.MusicViewModel
import org.oxycblt.auxio.music.tags.CustomTag
import org.oxycblt.auxio.playback.PlaybackViewModel
import org.oxycblt.auxio.playback.formatDurationMsPopup
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.musikr.CustomValue
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.MusicParent
import org.oxycblt.musikr.Song

/** A [ListFragment] that shows a list of [CustomValue]s. */
@AndroidEntryPoint
class CustomValueListFragment :
    ListFragment<CustomValue, FragmentHomeListBinding>(),
    FastScrollRecyclerView.PopupProvider,
    FastScrollRecyclerView.Listener {
    private val homeModel: HomeViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()
    override val listModel: ListViewModel by activityViewModels()
    override val musicModel: MusicViewModel by activityViewModels()
    override val playbackModel: PlaybackViewModel by activityViewModels()
    private val customValueAdapter = CustomValueAdapter(this)
    private val slot: Int
        get() = requireArguments().getInt(ARG_SLOT)

    override fun onCreateBinding(inflater: LayoutInflater) =
        FragmentHomeListBinding.inflate(inflater)

    override fun onBindingCreated(binding: FragmentHomeListBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        binding.homeRecycler.apply {
            id = RECYCLER_IDS[slot - 1]
            adapter = customValueAdapter
            popupProvider = this@CustomValueListFragment
            listener = this@CustomValueListFragment
        }

        binding.homeNoMusicPlaceholder.apply {
            setImageResource(R.drawable.ic_custom_value_48)
            contentDescription = CustomTag.nameOf(context, slot)
        }
        binding.homeNoMusicMsg.text = getString(R.string.lng_empty_custom_values)

        binding.homeNoMusicAction.setOnClickListener { homeModel.startChooseMusicLocations() }

        collectImmediately(homeModel.customValueList(slot), ::updateCustomValues)
        collectImmediately(homeModel.empty, musicModel.indexingState, ::updateNoMusicIndicator)
        collectImmediately(listModel.selected, ::updateSelection)
        collectImmediately(
            playbackModel.song,
            playbackModel.parent,
            playbackModel.isPlaying,
            ::updatePlayback,
        )
    }

    override fun onDestroyBinding(binding: FragmentHomeListBinding) {
        super.onDestroyBinding(binding)
        binding.homeRecycler.apply {
            adapter = null
            popupProvider = null
            listener = null
        }
    }

    override fun getPopupData(pos: Int): FastScrollRecyclerView.PopupProvider.PopupData? {
        val customValue = homeModel.customValueList(slot).value.getOrNull(pos) ?: return null
        // Change how we display the popup depending on the current sort mode.
        return when (homeModel.customValueSort.mode) {
            // By Name -> Use Name
            is Sort.Mode.ByName ->
                FastScrollRecyclerView.PopupProvider.PopupData(customValue.name.thumb() ?: "?")

            // Duration -> Use compact bucket duration
            is Sort.Mode.ByDuration ->
                FastScrollRecyclerView.PopupProvider.PopupData(
                    customValue.durationMs.formatDurationMsPopup()
                )

            // Count -> Use song count
            is Sort.Mode.ByCount ->
                FastScrollRecyclerView.PopupProvider.PopupData(customValue.songs.size.toString())

            // Unsupported sort, error gracefully
            else -> null
        }
    }

    override fun onFastScrollingChanged(isFastScrolling: Boolean) {
        homeModel.setFastScrolling(isFastScrolling)
    }

    override fun onRealClick(item: CustomValue) {
        detailModel.showCustomValue(item)
    }

    override fun onOpenMenu(item: CustomValue) {
        listModel.openMenu(R.menu.parent, item)
    }

    private fun updateCustomValues(customValues: List<CustomValue>) {
        customValueAdapter.update(customValues, homeModel.customValueInstructions(slot).consume())
    }

    private fun updateNoMusicIndicator(empty: Boolean, indexingState: IndexingState?) {
        val binding = requireBinding()
        binding.homeRecycler.isInvisible = empty
        binding.homeNoMusic.isInvisible = !empty
        binding.homeNoMusicAction.isVisible =
            indexingState == null || (empty && indexingState is IndexingState.Completed)
    }

    private fun updateSelection(selection: List<Music>) {
        customValueAdapter.setSelected(selection.filterIsInstanceTo(mutableSetOf()))
    }

    private fun updatePlayback(song: Song?, parent: MusicParent?, isPlaying: Boolean) {
        // Only highlight the custom value if it is currently playing, and if the currently
        // playing song is also contained within.
        val customValue =
            (parent as? CustomValue)?.takeIf { song?.run { customValues.contains(it) } ?: false }
        customValueAdapter.setPlaying(customValue, isPlaying)
    }

    /**
     * A [SelectionIndicatorAdapter] that shows a list of [CustomValue]s using
     * [CustomValueViewHolder].
     *
     * @param listener An [SelectableListListener] to bind interactions to.
     */
    private class CustomValueAdapter(private val listener: SelectableListListener<CustomValue>) :
        SelectionIndicatorAdapter<CustomValue, CustomValueViewHolder>(
            CustomValueViewHolder.DIFF_CALLBACK
        ) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            CustomValueViewHolder.from(parent)

        override fun onBindViewHolder(holder: CustomValueViewHolder, position: Int) {
            holder.bind(getItem(position), listener)
        }
    }

    companion object {
        private const val ARG_SLOT = "slot"

        /** The recycler id of each slot's tab, so that their scroll states are kept apart. */
        val RECYCLER_IDS =
            listOf(
                R.id.home_custom_1_recycler,
                R.id.home_custom_2_recycler,
                R.id.home_custom_3_recycler,
                R.id.home_custom_4_recycler,
                R.id.home_custom_5_recycler,
                R.id.home_custom_6_recycler,
            )

        /** Create the list of the values of the custom tag in [slot]. */
        fun new(slot: Int) =
            CustomValueListFragment().apply { arguments = bundleOf(ARG_SLOT to slot) }
    }
}
