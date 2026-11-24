# Changelog

Auxio-MM4's own changes. For upstream Auxio's history, see [Auxio's changelog](https://github.com/OxygenCobalt/Auxio/blob/dev/CHANGELOG.md).

## 4.1.5-mm4.2

Loading speed. Still based on Auxio 4.1.5. The first launch after updating does one full rescan, because the stored library data changed format.

### Faster
- The library from the last session shows straight away at startup while the real scan runs behind it (upstream PR [#1499](https://github.com/OxygenCobalt/Auxio/pull/1499))
- Faster loading of an already scanned library: names are no longer transliterated one by one, & the file walk runs alongside the rest of the loading (upstream PR [#1488](https://github.com/OxygenCobalt/Auxio/pull/1488))
- System mode (Android 11+): the full check at startup is skipped when the system's music database hasn't changed (restart ~18 s → ~4 s on ~8,000 songs)
- Names are reused within a load
- Loading progress updates at most every 100 ms (full rescan ~34 s → ~28 s)
- Faster song ID creation
- Smooth fast-scroll dragging on large libraries; covers load on a limited number of threads (upstream PR [#1437](https://github.com/OxygenCobalt/Auxio/pull/1437))
- Lock screen, Bluetooth & Android Auto get the 100 songs around the current one instead of the whole queue (upstream PR [#1437](https://github.com/OxygenCobalt/Auxio/pull/1437); the Tonewise fork does the same)

### Changed
- The Folder & System loading options say in bold which is slower but more reliable & which is faster but less reliable
- Bug report links, issue templates & the About screen point to Auxio-MM4
- Removed upstream's sponsor button, `fastlane/` & version history

## 4.1.5-mm4.1

First version of the fork, based on Auxio 4.1.5. See the [README](README.md#added-in-this-fork) for details of each feature.

### Added
- Nested playlists, library playlists, per-playlist sort, folder export & import, two-way sync with MediaMonkey 4, autoplaylists
- Ratings (POPM, with a choice of source & scale), Composer, Mood & Grouping tabs, MediaMonkey custom fields, editing moods & ratings & writing them into files
- Playback speed & pitch, sleep timer, crossfade, true gapless, resume for long songs, Now Playing bar with configurable buttons, landscape Now Playing, lyrics page
- Select all / invert selection, delete songs, set as ringtone, search in Settings, settings backup & restore, listened status export

### Faster
- Faster exit from Settings
