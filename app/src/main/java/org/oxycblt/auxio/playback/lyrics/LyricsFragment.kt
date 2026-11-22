/*
 * Copyright (c) 2026 Auxio Project
 * LyricsFragment.kt is part of Auxio.
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
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.FragmentLyricsBinding
import org.oxycblt.auxio.ui.ViewBindingFragment
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.musikr.lyrics.Lyrics
import org.oxycblt.musikr.lyrics.PlainLyrics
import org.oxycblt.musikr.lyrics.SyncedLine

/**
 * The lyrics page of the queue sheet. Synced lyrics follow playback; plain lyrics scroll freely and
 * can be selected and copied.
 */
@AndroidEntryPoint
class LyricsFragment : ViewBindingFragment<FragmentLyricsBinding>(), LyricsAdapter.Listener {
    private val lyricsModel: LyricsViewModel by activityViewModels()
    @Inject lateinit var lyricsSettings: LyricsSettings
    private val lyricsAdapter = LyricsAdapter(this)
    private var syncedLines: List<SyncedLine>? = null
    private var plainBlocks = emptyList<PlainLyrics>()
    // Auto-scroll pauses for a while after the user scrolls by hand.
    private var autoScrollPausedUntil = 0L
    private var keepingScreenOn = false
    private var timingOffsetMs = 0

    override fun onCreateBinding(inflater: LayoutInflater) = FragmentLyricsBinding.inflate(inflater)

    override fun onBindingCreated(binding: FragmentLyricsBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        binding.lyricsRecycler.apply {
            adapter = lyricsAdapter
            layoutManager = LinearLayoutManager(context)
            // The change animation resets item alpha, which left old lines looking highlighted.
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
            addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                            autoScrollPausedUntil =
                                SystemClock.uptimeMillis() + AUTO_SCROLL_PAUSE_MS
                        }
                    }
                }
            )
        }
        binding.lyricsMode.setOnClickListener { lyricsModel.toggleMode() }
        binding.lyricsVersion.setOnClickListener { showVersionMenu() }

        collectImmediately(
            lyricsModel.lyrics,
            lyricsModel.showPlain,
            lyricsModel.plainIndex,
            ::updateLyrics,
        )

        // Follow playback only while this page is the selected one.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    delay(tick())
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lyricsAdapter.setTextSize(lyricsSettings.textSizeSp)
        timingOffsetMs = lyricsSettings.timingOffsetMs
    }

    override fun onPause() {
        super.onPause()
        setKeepScreenOn(false)
    }

    override fun onDestroyBinding(binding: FragmentLyricsBinding) {
        super.onDestroyBinding(binding)
        binding.lyricsRecycler.adapter = null
        setKeepScreenOn(false)
    }

    override fun onSeekToLine(line: SyncedLine) {
        lyricsModel.seekTo(line.timeMs)
        autoScrollPausedUntil = 0
    }

    private fun updateLyrics(lyrics: Lyrics?, showPlain: Boolean, plainIndex: Int) {
        val binding = requireBinding()
        if (lyrics == null) {
            // Still loading, or nothing is playing.
            syncedLines = null
            plainBlocks = emptyList()
            lyricsAdapter.showPlain(null)
            binding.lyricsEmpty.isVisible = false
            binding.lyricsControls.isVisible = false
            return
        }

        val synced = lyrics.synced
        plainBlocks = lyrics.plain
        val useSynced = synced != null && !(showPlain && plainBlocks.isNotEmpty())
        val plain = plainBlocks.getOrNull(plainIndex) ?: plainBlocks.firstOrNull()

        binding.lyricsEmpty.isVisible = lyrics.isEmpty
        binding.lyricsMode.apply {
            isVisible = synced != null && plainBlocks.isNotEmpty()
            // The button names the mode being shown; tap it to switch.
            setText(if (useSynced) R.string.lbl_lyrics_synced else R.string.lbl_lyrics_plain)
        }
        binding.lyricsVersion.apply {
            isVisible = !useSynced && plainBlocks.size > 1
            text = plain?.let { label(it, plainBlocks.indexOf(it)) }
        }
        binding.lyricsControls.isVisible =
            binding.lyricsMode.isVisible || binding.lyricsVersion.isVisible

        if (useSynced) {
            syncedLines = synced
            lyricsAdapter.showSynced(requireNotNull(synced))
            autoScrollPausedUntil = 0
        } else {
            syncedLines = null
            lyricsAdapter.showPlain(plain?.text)
        }
        binding.lyricsRecycler.scrollToPosition(0)
    }

    /**
     * Update the highlighted line and the screen-on flag.
     *
     * @return How long to wait until the next update, in milliseconds.
     */
    private fun tick(): Long {
        val binding = requireBinding()
        // The queue sheet hides its content while collapsed.
        val visible = binding.root.isShown
        val playing = lyricsModel.isPlaying.value
        setKeepScreenOn(visible && playing && lyricsSettings.keepScreenOn)

        val lines = syncedLines
        if (!visible || lines == null) return IDLE_TICK_MS

        // A negative offset shows lines sooner (for lyrics that lag behind the song).
        val positionMs = lyricsModel.positionMs() - timingOffsetMs
        val index = lines.indexOfLastStartedBy(positionMs)
        if (index != lyricsAdapter.currentIndex) {
            lyricsAdapter.setCurrentIndex(index)
            if (index >= 0 && SystemClock.uptimeMillis() >= autoScrollPausedUntil) {
                scrollToCenter(index)
            }
        }
        return if (playing) PLAYING_TICK_MS else PAUSED_TICK_MS
    }

    private fun scrollToCenter(index: Int) {
        val recycler = requireBinding().lyricsRecycler
        val scroller =
            object : LinearSmoothScroller(recycler.context) {
                override fun calculateDtToFit(
                    viewStart: Int,
                    viewEnd: Int,
                    boxStart: Int,
                    boxEnd: Int,
                    snapPreference: Int,
                ) = (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)
            }
        scroller.targetPosition = index
        recycler.layoutManager?.startSmoothScroll(scroller)
    }

    private fun showVersionMenu() {
        val blocks = plainBlocks
        PopupMenu(requireContext(), requireBinding().lyricsVersion).apply {
            blocks.forEachIndexed { i, block -> menu.add(0, i, i, label(block, i)) }
            setOnMenuItemClickListener {
                lyricsModel.selectPlain(it.itemId)
                true
            }
            show()
        }
    }

    /** A short name for a plain lyrics block: its language and description, if any. */
    private fun label(block: PlainLyrics, index: Int): String {
        val language =
            block.language.takeIf { it.isNotBlank() && !it.equals("XXX", true) }?.uppercase()
        val parts = listOfNotNull(language, block.description.takeIf { it.isNotBlank() })
        return parts.joinToString(" · ").ifEmpty {
            "${getString(R.string.lbl_lyrics_unknown_language)} ${index + 1}"
        }
    }

    private fun setKeepScreenOn(keepOn: Boolean) {
        if (keepOn == keepingScreenOn) return
        keepingScreenOn = keepOn
        val window = activity?.window ?: return
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private companion object {
        const val AUTO_SCROLL_PAUSE_MS = 4000L
        const val PLAYING_TICK_MS = 100L
        const val PAUSED_TICK_MS = 300L
        const val IDLE_TICK_MS = 500L

        /** The index of the last line starting at or before [positionMs], or -1. */
        fun List<SyncedLine>.indexOfLastStartedBy(positionMs: Long): Int {
            var low = 0
            var high = size - 1
            var found = -1
            while (low <= high) {
                val mid = (low + high) ushr 1
                if (this[mid].timeMs <= positionMs) {
                    found = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            return found
        }
    }
}
