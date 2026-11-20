/*
 * Copyright (c) 2026 Auxio Project
 * MoodEditDialog.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.mood

import android.os.Bundle
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogMoodEditBinding
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.music.MusicViewModel
import org.oxycblt.auxio.music.write.TagWriteSettings
import org.oxycblt.auxio.ui.ViewBindingMaterialDialogFragment
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Song

/**
 * A [ViewBindingMaterialDialogFragment] that adds and removes moods on one or more [Song]s. The
 * moods are kept in the app, the audio files are not changed. Use [from] to create an instance.
 */
@AndroidEntryPoint
class MoodEditDialog : ViewBindingMaterialDialogFragment<DialogMoodEditBinding>() {
    @Inject lateinit var musicRepository: MusicRepository
    @Inject lateinit var moodRepository: MoodRepository
    @Inject lateinit var tagWriteSettings: TagWriteSettings
    private val musicModel: MusicViewModel by activityViewModels()

    /** The chosen state of each mood shown, in the order shown. */
    private val moods = mutableListOf<MoodChoice>()

    private val songs: List<Song> by lazy {
        val library = musicRepository.library
        requireArguments().getStringArray(ARG_SONG_UIDS).orEmpty().mapNotNull { uid ->
            Music.UID.fromString(uid)?.let { library?.findSong(it) }
        }
    }

    override fun onCreateBinding(inflater: LayoutInflater) = DialogMoodEditBinding.inflate(inflater)

    override fun onConfigDialog(builder: AlertDialog.Builder) {
        val songs = songs
        builder
            .setTitle(
                if (songs.size > 1) getString(R.string.fmt_edit_moods_songs, songs.size)
                else getString(R.string.lbl_edit_moods)
            )
            .setPositiveButton(R.string.lbl_ok) { _, _ -> apply() }
            .setNegativeButton(R.string.lbl_cancel, null)
        if (songs.any { moodRepository.isEdited(it) }) {
            builder.setNeutralButton(R.string.lbl_moods_reset) { _, _ ->
                musicModel.editMoods(songs.associateWith { null })
            }
        }
    }

    override fun onBindingCreated(binding: DialogMoodEditBinding, savedInstanceState: Bundle?) {
        val songs = songs
        if (songs.isEmpty()) {
            // The songs are gone, there is nothing left to edit.
            dismiss()
            return
        }
        binding.moodWriteNote.isVisible = tagWriteSettings.writeToFiles

        val counts = mutableMapOf<String, Int>()
        val names = mutableMapOf<String, String>()
        for (mood in musicRepository.library?.moods.orEmpty()) {
            val name = mood.name.raw
            counts[name.lowercase()] = mood.songs.size
            names[name.lowercase()] = name
        }
        val songMoods = songs.map { song ->
            song.moods.mapTo(mutableSetOf()) { it.name.raw.lowercase() }
        }

        moods.clear()
        for ((key, name) in names.entries.sortedBy { it.key }) {
            val have = songMoods.count { key in it }
            val initial =
                when (have) {
                    0 -> State.NONE
                    songs.size -> State.ALL
                    else -> State.SOME
                }
            moods.add(MoodChoice(name, counts[key] ?: 0, initial, initial))
        }

        savedInstanceState?.let { restore(it) }
        moods.forEach { addChip(it) }

        binding.moodAdd.setOnClickListener { addTyped() }
        binding.moodNew.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTyped()
                true
            } else {
                false
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArray(KEY_NEW, moods.filter { it.isNew }.map { it.name }.toTypedArray())
        outState.putStringArray(KEY_NAMES, moods.map { it.name }.toTypedArray())
        outState.putIntArray(KEY_STATES, moods.map { it.state.ordinal }.toIntArray())
    }

    private fun restore(state: Bundle) {
        for (name in state.getStringArray(KEY_NEW).orEmpty()) {
            moods.add(MoodChoice(name, 0, State.NONE, State.ALL, isNew = true))
        }
        val names = state.getStringArray(KEY_NAMES).orEmpty()
        val states = state.getIntArray(KEY_STATES) ?: IntArray(0)
        names.zip(states.toList()).forEach { (name, ordinal) ->
            moods.find { it.name == name }?.state = State.entries[ordinal]
        }
    }

    private fun addTyped() {
        val binding = requireBinding()
        val name = binding.moodNew.text?.toString()?.trim()?.replace("\n", " ").orEmpty()
        if (name.isEmpty()) return
        binding.moodNew.text = null
        // Reuse the existing spelling of a mood that only differs in letter case.
        val existing = moods.find { it.name.equals(name, ignoreCase = true) }
        if (existing != null) {
            existing.state = State.ALL
            binding.moodChips.findViewWithTag<Chip>(existing)?.let { bindChip(it, existing) }
            return
        }
        val choice = MoodChoice(name, 0, State.NONE, State.ALL, isNew = true)
        moods.add(choice)
        addChip(choice)
    }

    private fun addChip(choice: MoodChoice) {
        val binding = requireBinding()
        val chip = layoutInflater.inflate(R.layout.item_mood_chip, binding.moodChips, false) as Chip
        chip.tag = choice
        chip.text =
            if (choice.isNew) getString(R.string.fmt_mood_chip_new, choice.name)
            else getString(R.string.fmt_mood_chip, choice.name, choice.count)
        bindChip(chip, choice)
        chip.setOnClickListener {
            choice.state = choice.next()
            bindChip(chip, choice)
        }
        binding.moodChips.addView(chip)
    }

    private fun bindChip(chip: Chip, choice: MoodChoice) {
        chip.isChecked = choice.state != State.NONE
        chip.setCheckedIconResource(
            if (choice.state == State.SOME) R.drawable.ic_mood_partial_20
            else R.drawable.ic_check_20
        )
    }

    private fun apply() {
        val add = moods.filter { it.state == State.ALL && it.state != it.initial }
        val remove = moods.filter { it.state == State.NONE && it.state != it.initial }
        if (add.isEmpty() && remove.isEmpty()) return
        val removeKeys = remove.mapTo(mutableSetOf()) { it.name.lowercase() }
        val changes = mutableMapOf<Song, List<String>?>()
        for (song in songs) {
            val current = song.moods.map { it.name.raw }
            val result = current.filterTo(mutableListOf()) { it.lowercase() !in removeKeys }
            for (choice in add) {
                if (result.none { it.equals(choice.name, ignoreCase = true) }) {
                    result.add(choice.name)
                }
            }
            if (result != current) {
                changes[song] = result
            }
        }
        musicModel.editMoods(changes)
    }

    private enum class State {
        NONE,
        SOME,
        ALL,
    }

    /**
     * A mood shown in the dialog.
     *
     * @param initial The state before any edits. [State.SOME] means only some songs have it.
     * @param state The chosen state.
     */
    private class MoodChoice(
        val name: String,
        val count: Int,
        val initial: State,
        var state: State,
        val isNew: Boolean = false,
    ) {
        /** Tapping cycles through add to all, remove from all, and leaving it as it was. */
        fun next() =
            when (state) {
                State.SOME -> State.ALL
                State.ALL -> State.NONE
                State.NONE -> if (initial == State.SOME) State.SOME else State.ALL
            }
    }

    companion object {
        const val TAG = "MoodEditDialog"
        private const val ARG_SONG_UIDS = "song_uids"
        private const val KEY_NEW = "new_moods"
        private const val KEY_NAMES = "mood_names"
        private const val KEY_STATES = "mood_states"

        /**
         * Create a new instance.
         *
         * @param songs The [Song]s to edit.
         * @return A new instance.
         */
        fun from(songs: List<Song>) =
            MoodEditDialog().apply {
                arguments =
                    bundleOf(ARG_SONG_UIDS to songs.map { it.uid.toString() }.toTypedArray())
            }
    }
}
