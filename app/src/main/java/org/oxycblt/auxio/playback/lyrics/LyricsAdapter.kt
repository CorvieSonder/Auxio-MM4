/*
 * Copyright (c) 2026 Auxio Project
 * LyricsAdapter.kt is part of Auxio.
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

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.oxycblt.auxio.databinding.ItemLyricsLineBinding
import org.oxycblt.auxio.databinding.ItemLyricsPlainBinding
import org.oxycblt.auxio.util.inflater
import org.oxycblt.musikr.lyrics.SyncedLine

/**
 * Shows synced lyrics as one row per line (the current line highlighted), or plain lyrics as a
 * single selectable block.
 */
class LyricsAdapter(private val listener: Listener) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    /** Called when a synced line is long-pressed. */
    fun interface Listener {
        fun onSeekToLine(line: SyncedLine)
    }

    private var lines = emptyList<SyncedLine>()
    private var plainText: String? = null
    private var textSizeSp = 20f

    /** The highlighted synced line, or -1. */
    var currentIndex = -1
        private set

    @SuppressLint("NotifyDataSetChanged")
    fun showSynced(newLines: List<SyncedLine>) {
        lines = newLines
        plainText = null
        currentIndex = -1
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun showPlain(text: String?) {
        lines = emptyList()
        plainText = text
        currentIndex = -1
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setTextSize(sp: Float) {
        if (sp == textSizeSp) return
        textSizeSp = sp
        notifyDataSetChanged()
    }

    fun setCurrentIndex(index: Int) {
        if (index == currentIndex) return
        val old = currentIndex
        currentIndex = index
        if (old in lines.indices) notifyItemChanged(old)
        if (index in lines.indices) notifyItemChanged(index)
    }

    override fun getItemCount() = if (plainText != null) 1 else lines.size

    override fun getItemViewType(position: Int) = if (plainText != null) TYPE_PLAIN else TYPE_LINE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == TYPE_PLAIN) {
            PlainViewHolder(ItemLyricsPlainBinding.inflate(parent.context.inflater, parent, false))
        } else {
            LineViewHolder(ItemLyricsLineBinding.inflate(parent.context.inflater, parent, false))
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is PlainViewHolder -> holder.bind(plainText ?: "", textSizeSp)
            is LineViewHolder ->
                holder.bind(lines[position], position == currentIndex, textSizeSp) {
                    listener.onSeekToLine(it)
                }
        }
    }

    private class LineViewHolder(private val binding: ItemLyricsLineBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            line: SyncedLine,
            isCurrent: Boolean,
            sizeSp: Float,
            onSeek: (SyncedLine) -> Unit,
        ) {
            binding.lyricsLine.apply {
                // A blank line is an instrumental gap; show a note so it can still be highlighted.
                text = line.text.ifBlank { "♪" }
                setSize(sizeSp)
                setTypeface(null, if (isCurrent) Typeface.BOLD else Typeface.NORMAL)
                alpha = if (isCurrent) 1f else 0.5f
                setOnLongClickListener {
                    onSeek(line)
                    true
                }
            }
        }
    }

    private class PlainViewHolder(private val binding: ItemLyricsPlainBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(text: String, sizeSp: Float) {
            binding.lyricsPlain.apply {
                this.text = text
                setSize(sizeSp)
            }
        }
    }

    private companion object {
        const val TYPE_LINE = 0
        const val TYPE_PLAIN = 1

        fun TextView.setSize(sp: Float) = setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    }
}
