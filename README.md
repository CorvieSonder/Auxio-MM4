<h1 align="center"><b>Auxio-MM4</b></h1>
<h4 align="center">Auxio: MM4 Edition. A fork designed for interoperability with MediaMonkey 4.</h4>

> [!CAUTION]
> **Unofficial fork.** Auxio-MM4 is a personal, unofficial fork of [Auxio](https://github.com/OxygenCobalt/Auxio) by Alexander Capehart (OxygenCobalt). It is not affiliated with, endorsed by or supported by the Auxio project. Please do not report problems with this fork to Auxio's issue tracker.
>
> **Not affiliated with MediaMonkey.** MediaMonkey is a trademark of Ventis Media Inc. This project is not affiliated with, endorsed by or supported by Ventis Media. It only reads & writes standard tags & playlist files, & its companion scripts use MediaMonkey 4's public scripting interface. No MediaMonkey code or artwork is included.
> 
> **Not affiliated with jetAudio.** jetAudio is a trademark of COWON Systems Inc. This project is not affiliated with, endorsed by or supported by COWON Systems. Several features in this project were inspired by features available in jetAudio & jetAudio Plus, my music app of choice for the last several years. No code & no assets from jetAudio were used to make these features in Auxio-MM4. No reverse-engineering took place, with or without AI.
>
> On a related note, COWON makes killer budget bluetooth headphones with noise cancelling. Highly recommend if you need a pair (no I'm not sponsored either, just a long-time user lol).

## About

Auxio-MM4 is a personal project that may not be useful for anyone else. Like the description says, it's designed for interoperability with MediaMonkey 4. Why 4 & not 5? 4 has more plugins that I use for my huge library, & the themes look better. That's it, that's the reason. Again, something hyper-specific that may not be of use to you. 

*If you are browsing Auxio forks & you like mine but wish it had x feature, go ahead & open an issue or PR. Worst thing I can do is say no, & most of the time, I'll prolly have it added anyway as long as it doesn't bog down the app or majorly change how something works.* **Please** *give a brief look at contributing.md for details on issues & PRs.*

Auxio-MM4 installs next to the regular Auxio (its own app ID, `org.oxycblt.auxio.mm4`) & does not touch Auxio's data.

Auxio-MM4 is based on [Auxio](https://github.com/OxygenCobalt/Auxio), an open-source music player for Android that is light on features but easy to build on. I'll do my best to always have upstream changes pulled into this fork, but unless those changes are urgent security/bugfixes, there will be a delay of at least days, if not weeks. This isn't my only project, & AI usage isn't exactly free. Speaking of:


> [!IMPORTANT]
> **AI DISCLAIMER** I did not write 99% of the code in this project. I'm an incredibly amateur programmer who, at best, can put together some scripts or use visual programming languages to do something slightly complex. I take no credit for the vast majority of work done here. If anything, I should thank the engineers at Anthropic. For all the horrible awful ethical issues that come with AI, Claude Code has been an incredible accessibility tool for me that has vastly expanded the amount of things that I can do within my very limited means. That being said, I take full responsibility for all AI-written code submitted by me, including potential performance or security issues. I'm not going to ignore people or post an AI response, even if I use AI to do most of the work. If I can, I hope to eventually support this repo on my own, without AI, & refactor code as needed.

## Features

### From Auxio

Playback based on Media3 ExoPlayer · Material Design UI · multiple artists, disc numbers, release types, original dates & sort tags · SD-card-aware folders · gapless playback · ReplayGain · external equalizer support · Android Auto · widgets · search · completely private & offline. See [Auxio's README](https://github.com/OxygenCobalt/Auxio#features) for the full list.

### Added in this fork

**Playlists**
- Nested playlists, as in MediaMonkey: any depth, move by drag or menu, pin to the top at each level
  - Library playlists: a top-level playlist can show every song of the playlists inside it, each once, kept up to date (for example one per person sharing a media library); also in MediaMonkey 4 through the script
  - Export & import nested playlists as folders of M3U8 files (replace an earlier export; choose what to import)
- Sort a playlist by any field, remembered per playlist
- Two-way sync with MediaMonkey 4: linked playlists are updated in place (songs, order, name, nesting); changes made on both sides can be overwritten, kept or merged
- Autoplaylists: Recently Added, Recently Played, Most Played, Rated & Now Playing

**Tags**
- **Rating**s from POPM frames (MediaMonkey, MusicBee, Windows Media Player (only MM tested)), with a choice of source & scale; a rating label in lists, a rating button in Now Playing, filter by rating; shown as stars, hearts or music notes
- **Composer & Mood**: tabs, detail pages, search sections & sort (tabs hidden by default)
- **Grouping** (TIT1) tab - in my library, this groups together things like Minecraft music, or 80s music
- MediaMonkey custom fields (**Custom 1–5 & Occasion**): name them, & show them in song details, search, sort & their own tabs (off by default)
- Edit moods & ratings in the app, & optionally write them into the files
- Send Mood & Ratings edits back to MediaMonkey, & restore changes to the phone's db if migrating or accidentally overwritten.

**Playback**
- Playback speed & pitch
- Sleep timer with fade-out or "finish the current song"
- Crossfade between songs (0–12 s), gapless within albums
- True Gapless: trims silence at the start & end of each song (off by default; replaces crossfade while on)
- Resume long songs: songs of 15 minutes or longer continue where they were left off (off by default, currently untested)
- Now Playing top bar with quick buttons (rating, speed, sleep timer); choose which buttons show & in what order, including any song menu action; the rest go into the ⋮ menu
- Improvements to landscape mode: a full-height cover with the Now Playing buttons, title & play controls laid over it; tap the cover to hide or show them
- Lyrics page next to the queue: synced lyrics (SYLT) follow playback, with a timing offset; plain lyrics (USLT, Vorbis, MP4) & sidecar `.lrc` files are supported but currently untested

**Other**
- Select all / invert selection
- Delete songs from a playlist or from the device
- Use a song as the ringtone
- Search bar in Settings
- Back up & restore settings & app data (playlists, moods, ratings, listened status) as a zip file; music folders are picked again after a restore
- Listened status: export which songs were played to the end, & when, as a CSV file

> [!TIP]
> For faster startup with large libraries, set **Settings → Library → Music sources → Load from** to **System**. Loading from folders you pick ("File picker") is slower, especially on restart. Buuuuut: System mode can miss files that Android hasn't indexed, & it doesn't read `cover.jpg` files.

### Performance improvements

Measured on a phone with about 8,000 songs (release build, System mode).

- The library appears at startup straight from the cache, & changes are checked in the background
- Startup skips that check when nothing changed on the phone (System mode, Android 11+): a restart went from 5–6 s to about 3 s
- Faster cache reading: storage volumes are looked up once per load instead of once per song
- Faster sorting setup for non-Latin names: one shared transliterator instead of one per name
- Fixed a small memory leak in the tag reader
- Leaving settings is faster: home tabs are built when first opened, & the Now Playing bar sets up its buttons in one go
- Names that repeat across songs are built once per load: the tag step at startup takes about half as long
- Loading progress is reported at most 10 times a second instead of for every file: a full rescan is about 5 s faster
- Song IDs reuse one hash object per thread instead of creating one per ID
- Smooth fast scrolling in large lists: the scrollbar jumps to its row, & cover loading uses a small thread pool (from upstream pull request [#1437](https://github.com/OxygenCobalt/Auxio/pull/1437))
- The lock screen, Bluetooth & Android Auto get the 100 songs around the current one instead of the whole queue, so shuffling the whole library stays fast (from upstream pull request [#1437](https://github.com/OxygenCobalt/Auxio/pull/1437); the Tonewise fork does the same)
- ~~Now Multicore!~~ No measurable improvements :<

Planned:

- Faster "Refresh music" (its file check takes about 7 s)
- Faster song ID hashing

### Planned

- Phone & PC sync improvements as they come up
- Custom theme (we're going skeumorphic babyyyyy but holy moly is it gonna take a while because I want to do this without AI)

## MediaMonkey 4 scripts

The PC side of playlist sync is a VBScript for MediaMonkey 4 (`PlaylistFolders.vbs`): copy it into MediaMonkey's `Scripts\Auto` folder & restart MediaMonkey. It adds right-click items to the playlist tree: export as playlist folder, import playlist folder here, & apply phone changes. Also the ability to make, stop & refresh library playlists. *(Link to the scripts repository to be added when it is published.)*

## Permissions

- Storage (`READ_MEDIA_AUDIO`, `READ_EXTERNAL_STORAGE`) to read & play your music files
- Services (`FOREGROUND_SERVICE`, `WAKE_LOCK`) to keep the music playing in the background
- Notifications (`POST_NOTIFICATION`) for playback & music loading
- Modify system settings (`WRITE_SETTINGS`), only when you use a song as the ringtone

Writing tags into files & deleting songs ask Android for permission each time; nothing leaves the device.

## Building

Build from source on Linux (the custom Media3 build runs shell scripts, so Windows is not supported). Signed releases are available if you don't want to do this!

1. Install JDK 21, the Android SDK, NDK 28.2.13676358, `cmake` & `ninja`.
2. Clone with submodules: `git clone --recurse-submodules <this repository>`
3. Build: `./gradlew assembleDebug`
4. Install over USB (with USB debugging on): `adb install -r app/build/outputs/apk/debug/app-debug.apk`

## Credits & license

Auxio-MM4 is built on [Auxio](https://github.com/OxygenCobalt/Auxio) by Alexander Capehart (OxygenCobalt) & its contributors. If you like Auxio, consider [supporting its developer](https://github.com/sponsors/OxygenCobalt).

Performance work ported or adapted from: upstream pull requests [#1488](https://github.com/OxygenCobalt/Auxio/pull/1488) (shared transliterator) & [#1499](https://github.com/OxygenCobalt/Auxio/pull/1499) (library from the cache at startup), [#1437](https://github.com/OxygenCobalt/Auxio/pull/1437) (fast scrolling, cover thread pool & queue window), & the tag reader leak fix from [HrshD1eux/Music_Player](https://github.com/HrshD1eux/Music_Player).

[![GNU GPLv3 Image](https://www.gnu.org/graphics/gplv3-127x51.png)](http://www.gnu.org/licenses/gpl-3.0.en.html)

Like Auxio, this fork is Free Software under the [GNU General Public License](https://www.gnu.org/licenses/gpl.html), version 3 or (at your option) any later version. Upstream copyright notices are kept in every file.
