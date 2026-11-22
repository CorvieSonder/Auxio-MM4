<h1 align="center"><b>Auxio-MM4</b></h1>
<h4 align="center">Auxio: MM4 Edition. A fork designed for interoperability with MediaMonkey 4.</h4>

> [!CAUTION]
> **Unofficial fork.** Auxio-MM4 is a personal, unofficial fork of [Auxio](https://github.com/OxygenCobalt/Auxio) by Alexander Capehart (OxygenCobalt). It is not affiliated with, endorsed by or supported by the Auxio project. Please do not report problems with this fork to Auxio's issue tracker.
>
> **Not affiliated with MediaMonkey.** MediaMonkey is a trademark of Ventis Media Inc. This project is not affiliated with, endorsed by or supported by Ventis Media. It only reads and writes standard tags and playlist files, and its companion scripts use MediaMonkey 4's public scripting interface. No MediaMonkey code or artwork is included.

## About

Auxio-MM4 is a personal project that may not be useful for anyone else. Like the description says, it's designed for interoperability with MediaMonkey 4. Why 4 & not 5? 4 has more plugins that I use for my huge library, & the themes look better. That's it, that's the reason. Again, something hyper-specific that may not be of use to you. If you are browsing Auxio forks & you like mine but wish it had x feature, go ahead & open an issue. Worst thing I can do is say no, & most of the time, I'll prolly have it added anyway as long as it doesn't bog down the app or majorly change how something works.

Auxio-MM4 installs next to the regular Auxio (its own app ID, `org.oxycblt.auxio.fork`) and does not touch Auxio's data.

Auxio-MM4 is based on [Auxio](https://github.com/OxygenCobalt/Auxio), an open-source music player for Android that is light on features but easy to build on. I'll do my best to always have upstream changes pulled into this fork, but unless those changes are urgent security/bugfixes, there will be a delay of at least days, if not weeks. This isn't my only project, & AI usage isn't exactly free. Speaking of:


> [!IMPORTANT]
> **AI DISCLAIMER** I did not write 99% of the code in this project. I'm an incredibly amateur programmer who, at best, can put together some scripts or use visual programming languages to do something slightly complex. I take no credit for the vast majority of work done here. If anything, I should thank the engineers at Anthropic. For all the horrible awful ethical issues that come with AI, Claude Code has been an incredible accessibility tool for me that has vastly expanded the amount of things that I can do within my very limited means. That being said, I take full responsibility for all AI-written code submitted by me, including potential performance or security issues. I'm not going to ignore people or post an AI response, even if I use AI to do most of the work. If I can, I hope to eventually support this repo on my own, without AI, & refactor code as needed.

## Features

### From Auxio

Playback based on Media3 ExoPlayer · Material Design UI · multiple artists, disc numbers, release types, original dates and sort tags · SD-card-aware folders · gapless playback · ReplayGain · external equalizer support · Android Auto · widgets · search · completely private and offline. See [Auxio's README](https://github.com/OxygenCobalt/Auxio#features) for the full list.

### Added in this fork

**Playlists**
- Nested playlists, as in MediaMonkey: any depth, move by drag or menu, pin to the top at each level
- Sort a playlist by any field, remembered per playlist
- Export and import nested playlists as folders of M3U8 files (replace an earlier export; choose what to import)
- Two-way sync with MediaMonkey 4: linked playlists are updated in place (songs, order, name, nesting); changes made on both sides can be overwritten, kept or merged
- Autoplaylists: Recently Added, Recently Played, Most Played, Rated and Now Playing
- Library playlists: a top-level playlist can show every song of the playlists inside it, each once, kept up to date (for example one per person sharing a media library); also in MediaMonkey 4 through the script (PC side untested)

**Tags**
- Ratings from POPM frames (MediaMonkey, MusicBee, Windows Media Player (only MM tested)), with a choice of source and scale; a rating label in lists, a rating button in Now Playing, filter by rating
- Composer and Mood: tabs, detail pages, search sections and sort (tabs hidden by default)
- Grouping (TIT1) tab - in my library, this groups together things like Minecraft music, or 80s music
- Edit moods and ratings in the app, and optionally write them into the files
- Send Mood & Ratings edits back to MediaMonkey, & restore changes to the phone's db if migrating or accidentally overwritten.

**Playback**
- Playback speed and pitch
- Sleep timer with fade-out or "finish the current song"
- Crossfade between songs (0–12 s), gapless within albums
- True gapless: trims silence at the start and end of each song (off by default; replaces crossfade while on)
- Resume long songs: songs of 15 minutes or longer continue where they were left off (off by default; untested)
- Now Playing top bar with quick buttons (rating, speed, sleep timer)
- Lyrics page next to the queue: synced lyrics (SYLT) follow playback, with a timing offset; plain lyrics (USLT, Vorbis, MP4) and sidecar `.lrc` files are supported but untested

**Other**
- Select all / invert selection
- Delete songs from a playlist or from the device
- Use a song as the ringtone
- Search bar in Settings
- Listened status: export which songs were played to the end, and when, as a CSV file

### Planned

- Phone and PC sync improvements as they come up
- MediaMonkey-style custom tags (`Songs-DB_Custom1` to `Custom5`, Occasion)
- Custom theme (we're going skeumorphic babyyyyy)
- Configurable Now Playing buttons and rating symbol
- Faster loading for large libraries (10,000+ songs)

## MediaMonkey 4 scripts

The PC side of playlist sync is a VBScript for MediaMonkey 4 (`PlaylistFolders.vbs`): copy it into MediaMonkey's `Scripts\Auto` folder and restart MediaMonkey. It adds right-click items to the playlist tree: export as playlist folder, import playlist folder here, and apply phone changes, plus make, stop and refresh library playlists. *(Link to the scripts repository to be added when it is published.)*

## Permissions

- Storage (`READ_MEDIA_AUDIO`, `READ_EXTERNAL_STORAGE`) to read and play your music files
- Services (`FOREGROUND_SERVICE`, `WAKE_LOCK`) to keep the music playing in the background
- Notifications (`POST_NOTIFICATION`) for playback and music loading
- Modify system settings (`WRITE_SETTINGS`), only when you use a song as the ringtone

Writing tags into files and deleting songs ask Android for permission each time; nothing leaves the device.

## Building

There are no releases yet; build from source on Linux (the custom Media3 build runs shell scripts, so Windows is not supported). Builds will be added when the app is considered feature complete (as in, my list of planned features is mostly empty).

1. Install JDK 21, the Android SDK, NDK 28.2.13676358, `cmake` and `ninja`.
2. Clone with submodules: `git clone --recurse-submodules <this repository>`
3. Build: `./gradlew assembleDebug`
4. Install over USB (with USB debugging on): `adb install -r app/build/outputs/apk/debug/app-debug.apk`

## Credits and license

Auxio-MM4 is built on [Auxio](https://github.com/OxygenCobalt/Auxio) by Alexander Capehart (OxygenCobalt) and its contributors. If you like Auxio, consider [supporting its developer](https://github.com/sponsors/OxygenCobalt).

[![GNU GPLv3 Image](https://www.gnu.org/graphics/gplv3-127x51.png)](http://www.gnu.org/licenses/gpl-3.0.en.html)

Like Auxio, this fork is Free Software under the [GNU General Public License](https://www.gnu.org/licenses/gpl.html), version 3 or (at your option) any later version. Upstream copyright notices are kept in every file.
