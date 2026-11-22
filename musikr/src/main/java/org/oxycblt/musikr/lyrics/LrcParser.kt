/*
 * Copyright (c) 2026 Auxio Project
 * LrcParser.kt is part of Auxio.
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
 
package org.oxycblt.musikr.lyrics

/** Parses LRC text ("[mm:ss.xx]line"), as found in .lrc files and some plain lyrics tags. */
internal object LrcParser {
    private val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val OFFSET = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    // Enhanced LRC adds per-word timestamps such as <00:12.34>; they are dropped.
    private val WORD_TIMESTAMP = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

    /**
     * Parse LRC text.
     *
     * @return The timed lines sorted by time, or null if the text has no timed lines.
     */
    fun parse(text: String): List<SyncedLine>? {
        // A positive offset makes the lyrics appear sooner.
        val offsetMs = OFFSET.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val lines = mutableListOf<SyncedLine>()
        for (rawLine in text.lineSequence()) {
            var rest = rawLine.trim()
            val times = mutableListOf<Long>()
            while (true) {
                val match = TIMESTAMP.matchAt(rest, 0) ?: break
                times.add(match.toMs())
                rest = rest.substring(match.range.last + 1)
            }
            if (times.isEmpty()) continue
            val lineText = rest.replace(WORD_TIMESTAMP, "").trim()
            times.forEach { lines.add(SyncedLine((it - offsetMs).coerceAtLeast(0), lineText)) }
        }
        return lines.sortedBy { it.timeMs }.takeIf { it.isNotEmpty() }
    }

    private fun MatchResult.toMs(): Long {
        val minutes = groupValues[1].toLong()
        val seconds = groupValues[2].toLong()
        val fraction = groupValues[3]
        val fractionMs =
            when (fraction.length) {
                0 -> 0L
                1 -> fraction.toLong() * 100
                2 -> fraction.toLong() * 10
                else -> fraction.toLong()
            }
        return (minutes * 60 + seconds) * 1000 + fractionMs
    }
}
