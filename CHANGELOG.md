# Changelog

Auxio-MM4's own changes. For upstream Auxio's history, see [Auxio's changelog](https://github.com/OxygenCobalt/Auxio/blob/dev/CHANGELOG.md).

## 4.1.5-mm4.1

First version of the fork, based on Auxio 4.1.5. See the [README](README.md#added-in-this-fork) for details of each feature.

### Added
- Nested playlists, library playlists, per-playlist sort, folder export & import, two-way sync with MediaMonkey 4, autoplaylists
- Ratings (POPM, with a choice of source & scale), Composer, Mood & Grouping tabs, MediaMonkey custom fields, editing moods & ratings & writing them into files
- Playback speed & pitch, sleep timer, crossfade, true gapless, resume for long songs, Now Playing bar with configurable buttons, landscape Now Playing, lyrics page
- Select all / invert selection, delete songs, set as ringtone, search in Settings, settings backup & restore, listened status export

### Faster
- The library appears at startup straight from the cache, & the background check is skipped when nothing changed (System mode, Android 11+)
- Faster cache reading, name sorting & tag interpretation; faster exit from Settings
