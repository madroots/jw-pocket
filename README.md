# JW Pocket

Watch the jw.org video library on your **phone** and your **Android TV**, built for playback first: it keeps playing with the screen locked, shrinks into a floating window, has a mini player like a music app, and keeps every download in one place.

The successor of JW TV, rebuilt from scratch with a clean structure and a shared playback engine.

## What's new compared to JW TV

**Phone**
- **Keeps playing** with the screen locked or in other apps (as audio), with lock-screen, notification, headset and Bluetooth controls. Can be turned off in Settings.
- **Picture-in-picture**: leave the app during a video and it shrinks to a floating window (automatic on Android 12+, or with the button in the player).
- **Mini player** above the tab bar: browse while the video keeps playing; tap it to open the full player, swipe the full player down to shrink it again.
- **Full player**: Up next list with autoplay, subtitles in any language, quality, playback speed, sleep timer, share, download and favorite in one row. Turn the phone sideways for full screen.
- **Library tab**: Downloads (progress, size, free space, pause/resume/retry/delete, Wi-Fi only), Favorites and History.
- **Search** across the whole library, by title and description.
- **Downloaded videos are marked everywhere** with a green check; "Ready offline" row on Home and an offline banner when there's no internet.
- **New-video notifications** for the monthly JW Broadcasting program and Governing Body updates, each with its own switch in Settings; tapping one opens the video.
- **Android Auto**: browse Continue watching, Downloads, Favorites, Latest and every category in the car and listen (sound only).
- **Saves data when only listening**: with the screen off or the app in the background (and in the car), videos stream in the smallest quality, on mobile data by default; the **Listen** button in the player does it anytime.

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

Publish a GitHub release with a higher tag, e.g. `gh release create v1.0.1 --generate-notes`. The Release workflow tests, builds and attaches `jw-pocket-phone.apk` and `jw-pocket-tv.apk`; installed apps then offer the update. Put the signing keystore (base64) in the `RELEASE_KEYSTORE_B64` secret and keep it forever.

## Disclaimer

A personal, non-commercial project. Not affiliated with, endorsed by, or connected to Jehovah's Witnesses. All media belongs to its owners and comes from the public jw.org video service.

## License

Apache License 2.0.
