/*
 * Copyright (c) 2026 Auxio Project
 * RatingRepository.kt is part of Auxio.
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

import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.oxycblt.auxio.music.write.TagFileWriter
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/**
 * Manages song [Rating]s. A rating comes from the audio file's tags, unless one was given in the
 * app, which then takes priority. Ratings given in the app are kept in memory so that they can be
 * read synchronously, and are persisted in the background. Audio files are never changed.
 */
interface RatingRepository {
    /** Whether ratings should be shown in song lists. */
    val showInLists: Boolean

    /** The lowest [Rating] of songs shown in search results, or null to show all songs. */
    var searchMinRating: Rating?

    /** The lowest [Rating] of songs shown on detail pages, or null to show all songs. */
    var detailMinRating: Rating?

    /**
     * Keep only the songs rated at least [minRating].
     *
     * @param songs The songs to filter.
     * @param minRating The lowest [Rating] to keep, or null to keep every song.
     * @return The filtered songs, in the same order.
     */
    fun filterSongs(songs: Collection<Song>, minRating: Rating?): List<Song>

    /**
     * Get the current [Rating] of a [Song].
     *
     * @param song The [Song] to get the [Rating] of.
     * @return The [Rating] given in the app, or the one from the audio file if there is none, or
     *   null if the song is not rated.
     */
    fun ratingOf(song: Song): Rating?

    /**
     * Get the [Rating] in the audio file of a [Song].
     *
     * @param song The [Song] to get the [Rating] of.
     * @return The [Rating] from the audio file, or null if it has none.
     */
    fun fileRatingOf(song: Song): Rating?

    /**
     * Get whether a [Song] has a rating that was given in the app.
     *
     * @param song The [Song] to check.
     * @return true if the [Song] was rated or explicitly left unrated in the app.
     */
    fun isRatedInApp(song: Song): Boolean

    /**
     * Rate a [Song] in the app.
     *
     * @param song The [Song] to rate.
     * @param rating The new [Rating], or null to leave the song unrated regardless of its audio
     *   file.
     */
    fun rate(song: Song, rating: Rating?)

    /**
     * Remove the rating given to a [Song] in the app, so that the audio file's is used again.
     *
     * @param song The [Song] to remove the rating of.
     */
    fun resetRating(song: Song)

    /**
     * Add a [Listener] to this instance.
     *
     * @param listener The [Listener] to add.
     */
    fun addListener(listener: Listener)

    /**
     * Remove a [Listener] from this instance.
     *
     * @param listener The [Listener] to remove.
     */
    fun removeListener(listener: Listener)

    /** A listener for changes to song [Rating]s. */
    interface Listener {
        /** Called when the [Rating] of one or more songs may have changed. */
        fun onRatingsChanged()
    }
}

@Singleton
class RatingRepositoryImpl
@Inject
constructor(
    private val ratingDao: RatingDao,
    private val ratingSettings: RatingSettings,
    private val tagFileWriter: Lazy<TagFileWriter>,
) : RatingRepository, RatingSettings.Listener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listeners = mutableListOf<RatingRepository.Listener>()
    @Volatile private var appRatings = mapOf<Music.UID, Int>()
    @Volatile private var sourceKeys = sourceKeys()
    @Volatile
    override var showInLists = ratingSettings.showInLists
        private set

    init {
        Ratings.repository = this
        ratingSettings.registerListener(this)
        scope.launch {
            val loaded =
                try {
                    ratingDao.getRatings().associate { it.songUid to it.halfStars }
                } catch (e: Exception) {
                    L.e("Unable to load ratings: $e")
                    return@launch
                }
            withContext(Dispatchers.Main) {
                // Keep anything that was rated while the load was still running.
                appRatings = loaded + appRatings
                dispatchChange()
            }
        }
    }

    override fun ratingOf(song: Song): Rating? {
        val appRating = appRatings[song.uid]
        return if (appRating != null) Rating.fromHalfStars(appRating) else fileRatingOf(song)
    }

    override fun fileRatingOf(song: Song): Rating? {
        val extras = song.extras
        if (extras.isEmpty()) {
            return null
        }
        for (key in sourceKeys) {
            extras[key].popularimeterRating()?.let {
                return it
            }
        }
        // Not rated by a known program, try any other.
        for ((key, values) in extras) {
            if (key.startsWith(POPULARIMETER_PREFIX)) {
                values.popularimeterRating()?.let {
                    return it
                }
            }
        }
        // Text ratings, such as "4.5/5", are only used when no popularimeter rating exists.
        for (key in TEXT_KEYS) {
            extras[key]?.firstOrNull()?.let(Rating::fromText)?.let {
                return it
            }
        }
        return extras[FRACTION_KEY]?.firstOrNull()?.toFloatOrNull()?.let(Rating::fromFraction)
    }

    override fun isRatedInApp(song: Song) = appRatings.containsKey(song.uid)

    override var searchMinRating: Rating?
        get() = Rating.fromHalfStars(ratingSettings.searchMinRating)
        set(value) {
            ratingSettings.searchMinRating = value?.halfStars ?: 0
        }

    override var detailMinRating: Rating?
        get() = Rating.fromHalfStars(ratingSettings.detailMinRating)
        set(value) {
            ratingSettings.detailMinRating = value?.halfStars ?: 0
        }

    override fun filterSongs(songs: Collection<Song>, minRating: Rating?): List<Song> {
        if (minRating == null) {
            return songs as? List<Song> ?: songs.toList()
        }
        return songs.filter { song -> ratingOf(song)?.let { it >= minRating } == true }
    }

    override fun rate(song: Song, rating: Rating?) {
        val halfStars = rating?.halfStars ?: 0
        L.d("Rating ${song.uid} with $halfStars half stars")
        appRatings = appRatings + (song.uid to halfStars)
        scope.launch {
            try {
                ratingDao.insertRating(
                    RatedSong(song.uid, halfStars, System.currentTimeMillis(), fileWritten = false)
                )
                tagFileWriter.get().flush()
            } catch (e: Exception) {
                L.e("Unable to save rating: $e")
            }
        }
        dispatchChange()
    }

    override fun resetRating(song: Song) {
        if (!appRatings.containsKey(song.uid)) {
            return
        }
        L.d("Resetting rating of ${song.uid}")
        appRatings = appRatings - song.uid
        scope.launch {
            try {
                ratingDao.deleteRating(song.uid)
            } catch (e: Exception) {
                L.e("Unable to delete rating: $e")
            }
        }
        dispatchChange()
    }

    override fun onRatingSettingsChanged() {
        sourceKeys = sourceKeys()
        showInLists = ratingSettings.showInLists
        dispatchChange()
    }

    @Synchronized
    override fun addListener(listener: RatingRepository.Listener) {
        listeners.add(listener)
    }

    @Synchronized
    override fun removeListener(listener: RatingRepository.Listener) {
        listeners.remove(listener)
    }

    private fun dispatchChange() {
        val current = synchronized(this) { listeners.toList() }
        for (listener in current) {
            listener.onRatingsChanged()
        }
    }

    private fun sourceKeys(): List<String> {
        val preferred = ratingSettings.preferredSource
        return (listOf(preferred) + (RatingSource.entries - preferred)).map { it.key }
    }

    private fun List<String>?.popularimeterRating() =
        this?.firstOrNull()?.toIntOrNull()?.let(Rating::fromPopularimeter)

    private companion object {
        const val POPULARIMETER_PREFIX = "POPM:"
        const val FRACTION_KEY = "FMPS_RATING"
        val TEXT_KEYS = listOf("TXXX:DISCOGS_RATING", "TXXX:ALBUM RATING")
    }
}
