# JW Pocket

Watch the jw.org video library on your **phone** and your **Android TV**, built for playback first: it keeps playing with the screen locked, shrinks into a floating window, has a mini player like a music app, and keeps every download in one place.

The successor of JW TV, rebuilt from scratch with a clean structure and a shared playback engine.

## What's new compared to JW TV

**Phone**
- **Keeps playing** with the screen locked or in other apps (as audio), with lock-screen, notification, headset and Bluetooth controls. Can be turned off in Settings.
- **Picture-in-picture**: leave the app during a video and it shrinks to a floating window (automatic on Android 12+, or with the button in the player). The Bible and audio float too, from any screen: sound bars, the title and the verse being read. Play/pause and next in the window; closing it (×) pauses, expanding it returns to where you were.
- **Mini player** above the tab bar: browse while the video keeps playing; tap it to open the full player, swipe the full player down to shrink it again. After the app is closed, the last thing played waits there, paused where it was left (until you close it with ×).
- **Bible tab**: the Bible read aloud (New World Translation), chapter by chapter. Continue where you stopped, chapters heard per book, download a whole book. A book shows every chapter as a tile: heard (✓), started (progress bar), downloaded, playing; hold a chapter to play it from the start, mark it heard or not, download or remove it. The player follows the reading verse by verse with the verse text on screen (from jw.org's Bible EPUB, downloaded once), tap a verse to jump to it, step a verse or a chapter back and forward. A small link under the title plays the book's introduction video, then the chapter goes on where it was. The Bible has its own language in Settings (the video language unless changed).
- **Audio**: Browse › Audio has the music collections as albums (Play all, Shuffle, Download all, find a song by number), audio dramas and dramatic Bible readings, played with sound bars over the cover. Search finds them too.
- **Full player**: Up next list with autoplay (the parts of a series and numbered episodes always follow in order), subtitles in any language, quality, playback speed, sleep timer, share, download and favorite in one row. The app stays upright; the full-screen button turns the video sideways (double-tap a side there to skip 10 s).
- **Another language for one video**: the Language pill plays the video or song in any language it's published in, from the same moment, without changing the library language. The two languages you pick most (for subtitles or a video) stay at the top of every language list.
- **Library tab**: Downloads (progress, size, free space, pause/resume/retry/delete, Wi-Fi only), Favorites, History and Clips.
- **Share a moment**: Share › Share a moment opens a full-screen clip editor (the video pauses): set the start and end with the handles on the trim bar, or tap the first and last subtitle line; tap the video to preview. The phone makes a short MP4 (subtitles drawn in, if wanted). Clips are kept in Library › Clips and are for personal sharing only, not for social media or anywhere public.
- **Search** across the whole library, by title and description.
- **Downloaded videos are marked everywhere** with a green check; "Ready offline" row on Home and an offline banner when there's no internet.
- **New-video notifications** for the monthly JW Broadcasting program and Governing Body updates, each with its own switch in Settings; tapping one opens the video.
- **Android Auto**: browse Continue watching, the Bible, Music, Downloads, Favorites, Latest, audio dramas and every category in the car and listen (sound only).
- **Saves data when only listening**: with the screen off or the app in the background (and in the car), videos stream in the smallest quality, on mobile data by default; the **Listen** button in the player does it anytime and shows moving sound bars with the subtitles instead of the picture. The picture isn't decoded meanwhile, which saves battery.

**TV**
- New **Downloads**, **Favorites**, **History** and **Search** pages in the sidebar; downloaded videos marked on every card.
- Same remote-friendly player: OK shows the controls, left/right skip (hold to go faster), CC opens the subtitle list.
- The remote's **search/mic button** opens Search and listens.

**Both**
- **In your language**: the app speaks English, Czech, Slovak, German, Spanish, French, Italian, Portuguese, Polish, Dutch, Hungarian, Romanian, Russian and Ukrainian, and starts in the device's language. App, video and subtitle languages are set separately in Settings; changing one offers to change the ones below it too.
- **Subtitles in any language** a video is published in, remembered for the next videos; saved with downloads so they work offline.
- **Voice search** with the microphone button on the search field, in the app's language.
- **Downloads run in the background** with a progress notification, resume after a dropped connection, and play through the same player as streams.
- **Faster**: a category is now one request instead of one per row, the last copy shows instantly (and offline) while a fresh one loads, and release builds are about 3 MB.
- **Updates itself** from this repository's releases.

## Project layout

| Module | What's in it |
|---|---|
| `core` | API client, response cache, database (progress, favorites), settings, repositories and the screen logic both apps share. No UI. |
| `media` | The playback engine: a Media3 session service that owns the player, the connection screens use to control it, and downloads. |
| `brand` | The launcher icon (a phone in a suit's breast pocket) and the launch animation: the phone pops out of the pocket and flies at you until its screen becomes the app. |
| `app-phone` | Phone screens (Jetpack Compose, Material 3). |
| `app-tv` | TV screens (Jetpack Compose for TV). |

Build: `./gradlew :app-phone:assembleRelease :app-tv:assembleRelease`. Tests: `./gradlew :core:testDebugUnitTest`.

## Releasing

Publish a GitHub release with a higher tag, e.g. `gh release create v1.0.1 --generate-notes`. The Release workflow tests, builds and attaches `jw-pocket-phone.apk` and `jw-pocket-tv.apk`; installed apps then offer the update, listing the notes of every release since the installed version. Put the signing keystore (base64) in the `RELEASE_KEYSTORE_B64` secret and keep it forever.

## Disclaimer

A personal, non-commercial project. Not affiliated with, endorsed by, or connected to Jehovah's Witnesses. All media belongs to its owners and comes from the public jw.org video service.

## License

Apache License 2.0.
