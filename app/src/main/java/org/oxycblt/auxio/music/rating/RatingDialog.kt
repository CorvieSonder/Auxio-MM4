/*
 * Copyright (c) 2026 Auxio Project
 * RatingDialog.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.rating

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.roundToInt
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogRatingBinding
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.music.resolve
import org.oxycblt.auxio.music.write.TagWriteSettings
import org.oxycblt.auxio.ui.ViewBindingMaterialDialogFragment
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Song

/**
 * A [ViewBindingMaterialDialogFragment] that allows a [Song] to be rated. The rating is only kept
 * in the app, the audio file is not changed. Use [from] to create an instance.
 */
@AndroidEntryPoint
class RatingDialog : ViewBindingMaterialDialogFragment<DialogRatingBinding>() {
    @Inject lateinit var ratingRepository: RatingRepository
    @Inject lateinit var musicRepository: MusicRepository
    @Inject lateinit var tagWriteSettings: TagWriteSettings

    private val song: Song?
        get() =
            requireArguments().getString(ARG_SONG_UID)?.let(Music.UID::fromString)?.let {
                musicRepository.library?.findSong(it)
            }

    override fun onCreateBinding(inflater: LayoutInflater) = DialogRatingBinding.inflate(inflater)

    override fun onConfigDialog(builder: AlertDialog.Builder) {
        builder
            .setTitle(R.string.lbl_rating)
            .setPositiveButton(R.string.lbl_ok) { _, _ ->
                val song = song ?: return@setPositiveButton
                val halfStars = (requireBinding().ratingBar.rating * 2).roundToInt()
                ratingRepository.rate(song, Rating.fromHalfStars(halfStars))
            }
            .setNegativeButton(R.string.lbl_cancel, null)
        val song = song
        if (song != null && ratingRepository.isRatedInApp(song)) {
            builder.setNeutralButton(R.string.lbl_rating_reset) { _, _ ->
                ratingRepository.resetRating(song)
            }
        }
    }

    override fun onBindingCreated(binding: DialogRatingBinding, savedInstanceState: Bundle?) {
        val song = song
        if (song == null) {
            // The song is gone, there is nothing left to rate.
            dismiss()
            return
        }
        val context = requireContext()
        binding.ratingSong.text = song.name.resolve(context)
        if (savedInstanceState == null) {
            binding.ratingBar.rating = ratingRepository.ratingOf(song)?.stars ?: 0f
        }
        updateValue(binding.ratingBar.rating)
        binding.ratingBar.setOnRatingBarChangeListener { _, rating, _ -> updateValue(rating) }

        val fileRating = ratingRepository.fileRatingOf(song)
        binding.ratingFile.isVisible = fileRating != null
        if (fileRating != null) {
            binding.ratingFile.text =
                getString(R.string.fmt_rating_file, fileRating.format(context))
        }
        binding.ratingWriteNote.isVisible = tagWriteSettings.writeToFiles
    }

    private fun updateValue(stars: Float) {
        val rating = Rating.fromHalfStars((stars * 2).roundToInt())
        requireBinding().ratingValue.text =
            rating?.format(requireContext()) ?: getString(R.string.lbl_rating_none)
    }

    companion object {
        const val TAG = "RatingDialog"
        private const val ARG_SONG_UID = "song_uid"

        /**
         * Create a new instance.
         *
         * @param song The [Song] to rate.
         * @return A new instance.
         */
        fun from(song: Song) =
            RatingDialog().apply { arguments = bundleOf(ARG_SONG_UID to song.uid.toString()) }
    }
}
