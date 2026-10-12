# ✦ Nova Player

<p align="center">
  <strong>A modern, offline-first Android video player.</strong><br/>
  Built for movies, reels, lectures, and everything in between.
</p>

<p align="center">
  <a href="https://github.com/Narsing-s/Nova-Player/actions/workflows/android-ci.yml"><img alt="Android CI" src="https://img.shields.io/github/actions/workflow/status/Narsing-s/Nova-Player/android-ci.yml?branch=main&style=for-the-badge&logo=android&logoColor=white&label=Android%20CI"/></a>
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Android-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white"/>
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white"/>
  <img alt="Media3" src="https://img.shields.io/badge/Playback-AndroidX%20Media3-00BFA5?style=for-the-badge&logo=android&logoColor=white"/>
</p>

<p align="center">
  <a href="https://github.com/Narsing-s/Nova-Player/actions">⬇ Download test APK</a> ·
  <a href="https://github.com/Narsing-s/Nova-Player/releases">Releases</a> ·
  <a href="https://github.com/Narsing-s/Nova-Player/issues">Report an issue</a>
</p>

> **Project status:** Active preview. CI runs JVM unit tests, an advisory Android lint scan, and a debug APK build. Open [Android CI](https://github.com/Narsing-s/Nova-Player/actions/workflows/android-ci.yml) to check the latest status and download the APK artifact from a completed successful run. Real-device playback, notification, PiP, accessibility, and release-signing checks are still required before a production release.

---

## ✨ Why Nova Player?

Nova Player combines a cinematic dark interface with practical, power-user playback controls. It is designed for **local videos first**—no account, cloud library, or streaming service is required.

| 🎬 Cinema-style playback | 🎛️ Fine-grained control | 📚 Your personal library |
|---|---|---|
| Landscape immersive mode, adaptive fit, zoom | Swipe gestures, seek presets, speed and A–B loop | Search, folders, favorites, history and playlists |

## 🚀 Feature highlights

### 🌐 Network playback
- Open a direct **HTTPS** video or stream URL from the Videos screen without leaving the library.
- Uses AndroidX Media3 playback and keeps normal playback controls, history and resume checkpoints available where the stream supports them.
- HTTPS-only by design; insecure HTTP links are rejected and the app does not enable cleartext traffic globally.

### 🎥 A better viewing experience
- **Adaptive Auto / Fit / Fill / Zoom:** landscape movies can crop-to-fill in landscape mode; portrait reels remain fully visible in Auto mode.
- **Immersive landscape playback:** expands the video edge-to-edge, hides library chrome and system bars, and keeps an on-video way back.
- **Pinch-to-zoom:** magnify up to 2.5× with a quick reset.
- **Orientation choices:** Auto, Landscape and Portrait, with the preference remembered locally.
- **Playback speed:** quick presets from 0.25× to 3×.
- **Keep screen awake** while watching, or allow normal screen timeout.
- **Personal settings:** choose the midnight or AMOLED-black canvas, set the default orientation, tune the dedicated-button and remote seek interval, and enable or disable individual gestures. Preferences are stored locally. Nova also remembers the last library tab you used, and gesture toggles are saved immediately. Bottom navigation labels stay on a single line with compact spacing, and the tab strip can scroll on narrow screens.

### 🕹️ MX Player-inspired controls
- Single-tap seeking: tap the left half of the video to rewind 10 seconds or the right half to skip forward 10 seconds.
- Dedicated rewind/forward buttons and D-pad controls use the configurable 5, 10, 15 or 30-second skip interval; Previous / Next controls navigate queued media.
- Horizontal swipe seeking with a live time preview.
- Left-side brightness and right-side volume gestures, each independently configurable.
- Touch lock with a visible unlock action, now also reachable directly from the landscape quick-control dock.
- Shuffle, repeat-all, repeat-one, and A–B scene looping.
- **Landscape navigation tabs:** Display & tracks and Playback & more use compact icon-over-label tabs, matching the main-screen navigation style while keeping only one action row visible at a time. The selected tab and collapsed/expanded dock state are saved locally.
- **Landscape quick Favorite:** add or remove the current video from Favorites while staying in playback; the heart state reflects the saved favorite.
- **Landscape A–B loop shortcut:** tap **Set A** at the section start, **Set B** after that point to define the loop, then **Clear A–B** to reset both markers. Point B must be later than point A; invalid taps show a status message.
- Sleep timer for 15, 30, 45, 60 or 90 minutes, with cancel/replace support.

### 💬 Subtitles and tracks
- Load external **.srt** and **.vtt** subtitles.
- Shift subtitle timing by up to ±5 seconds in 250 ms steps without modifying the original subtitle file.
- Compact, Standard and Large subtitle presets, text-size adjustment and optional dark background.
- Choose audio and subtitle tracks when the media exposes selectable tracks.
- Select available video tracks where supported. A player cannot increase the source file's original resolution.

### 🗂️ Your offline video library
- Scan videos through Android MediaStore with a background refresh indicator.
- Browse the full matching library in a responsive thumbnail grid (adapts to phone/tablet width) or compact list, with consistent 16:9 previews; neither view artificially caps results at 200 videos.
- Search by video title/filename or containing folder; clear the search with one tap, and sort by newest, recently played, title, duration or file size.
- Helpful empty states distinguish an empty device library from filters with no matches, with a one-tap clear-search-and-filters action.
- **Per-video Continue Watching:** each partially watched video keeps its own saved resume position, progress bar and watched percentage in both grid and list views—not just the most recently played video. Opening a video resumes from its own saved checkpoint; finishing it clears only that video’s checkpoint.
- **Long-video timecodes:** durations and playback timestamps use `HH:MM:SS` for videos of an hour or longer, while shorter clips keep the compact `MM:SS` format.
- Live library result counts show how many videos match the current search and filters, with a subtle indicator for the active filter state.
- Filter by clip length and watched status.
- Automatically mark a video watched when playback finishes; manual watched/unwatched controls remain available.
- Browse folders, inspect video details, and share through Android's native share sheet.
- Use Android's document picker when a video is not listed in the library.
- See recent playback history, favorites and **Continue Watching** with saved resume position.

### 📋 Queue and playlists
- Add videos from the library or player overlay.
- Play from any queue position, reorder entries, remove individual items or clear the queue.
- Save named playlists locally and play, merge or delete them later.
- **Playlist backup and restore:** export saved playlists to a versioned JSON file using Android's document picker, or import a backup later. Imports validate the format, ignore malformed entries, and update same-named playlists while preserving unrelated playlists. Only playlist metadata and content URIs are exported—not the video files themselves.
- Queue order persists across app restarts. Opening a standalone file or reapplying subtitle timing should not silently clear the saved Up Next list.
- Background playback is implemented with a Media3 session service and media notification support; verify device-specific behavior during testing.

### 🛟 Useful extras
- **Playback diagnostics:** inspect playback state, current position and duration, active video/audio track codecs, reported resolution, frame rate, and the last player status to help narrow down unsupported or damaged media.
- **Shareable troubleshooting report:** copy a sanitized playback diagnostics report to the clipboard in one tap; it deliberately excludes media URIs and local file paths.
- Retry playback or return to the library when Media3 reports an error.
- **Android TV and remote playback:** the app declares a Leanback launcher category and does not require a touchscreen. When the player surface has focus, D-pad left/right seeks by the configured interval, while center/Enter and media play-pause toggle playback. Physical TV focus order and layout still need device validation.
- Accessibility polish: library status updates are exposed as polite screen-reader announcements, navigation tabs expose selected state, and the library scan indicator announces when scanning is active.
- Picture-in-Picture action on supported Android devices.
- Local preferences for playback speed, repeat/shuffle, subtitle appearance, seek interval and display mode.
- Original Nova Player launcher identity with adaptive and legacy vector resources.

---

## 📲 Get the latest test APK

1. Open [Android CI workflow runs](https://github.com/Narsing-s/Nova-Player/actions/workflows/android-ci.yml) and select the newest run with a green **Success** status.
2. Scroll to **Artifacts**.
3. Download **`nova-player-debug`** and extract the ZIP.
4. Transfer the APK to your Android device and install it. If Android asks, allow installs from the app you used to open the APK.

**Note:** this is a debug build for testing, not a signed production release. Artifact links can expire. For public distribution, use the [Releases page](https://github.com/Narsing-s/Nova-Player/releases) after a signed release is successfully published.

## 🧰 Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Playback:** AndroidX Media3 ExoPlayer and PlayerView
- **Platform:** Android SDK 35, JDK 17
- **Local persistence:** SharedPreferences for resume state, favorites, history, queue, playlists and playback preferences
- **Automation:** GitHub Actions runs JVM unit tests, an advisory Android lint scan, builds a debug APK and uploads the APK plus lint report artifacts

## 🧪 Automated quality checks

The JVM test suite includes regression coverage for:
- Playback time formatting for short clips, long videos and negative inputs.
- SRT and WebVTT subtitle timing shifts, including zero-offset preservation and hour-boundary formatting.
- Negative subtitle timestamps clamped to zero, including an extreme negative offset that must not overflow.
- Playlist backup JSON round-trips, malformed entries, and unsupported backup format/version handling.
- Gesture seek boundaries, unknown-duration seeking, integer-overflow protection, and readable playback-state labels.
- Orientation-mode mapping and immersive-playback policy for portrait, landscape, auto, selected-media, and full-screen combinations.

The Android CI workflow runs these tests before building the debug APK. Android Lint is uploaded as a report and remains advisory for now. A green CI run confirms the configured automated checks passed; it does not replace physical-device playback, TalkBack, PiP, notification, or signed-upgrade testing.

## 🛠️ Build locally

**Requirements:** Android Studio, Android SDK 35 and JDK 17. The CI workflow currently uses Gradle 8.11.1.

1. Clone the repository:
   ```bash
   git clone https://github.com/Narsing-s/Nova-Player.git
   cd Nova-Player
   ```
2. Open the project in Android Studio and sync Gradle.
3. Run checks and build the debug APK:
   ```bash
   gradle testDebugUnitTest
   gradle lintDebug
   gradle assembleDebug
   ```
4. Find the APK at:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

Alternatively, download the latest artifact from [GitHub Actions](https://github.com/Narsing-s/Nova-Player/actions).

## 🗺️ Roadmap

- [x] Modern Compose / Material 3 interface and original launcher identity
- [x] MediaStore library, thumbnails, search, sort and folder navigation
- [x] Grid/list layouts, favorites, recent history and watched filters
- [x] Per-video resume checkpoints and Continue Watching progress in grid and list
- [x] Persistent queue and named local playlists
- [x] Import/export playlist backups through Android's document picker with versioned JSON validation
- [x] Include validated player preferences in backups and restore theme, orientation, playback speed, seek interval, subtitles and gesture options; older playlist-only backups remain supported
- [x] Local settings for AMOLED canvas, orientation, screen-awake behavior and gesture toggles
- [x] Playback diagnostics for state, position, active track codecs and reported video format
- [x] Unit tests for safe seek boundaries and playback-state diagnostics
- [x] Android TV Leanback launcher declaration and optional touchscreen feature
- [x] Swipe gestures, touch lock, seek presets and pinch-to-zoom
- [x] Landscape immersive mode and adaptive screen fitting
- [x] External subtitle loading, timing sync and appearance controls
- [x] Playback speed, shuffle/repeat, A–B loop and sleep timer
- [x] CI unit-test step, advisory lint report, debug build and APK artifact upload
- [ ] Physical-device playback testing across common video formats
- [ ] Verify background playback, notification controls and PiP across Android versions
- [ ] Add device-level regression tests for rotation, immersive landscape controls, background playback and PiP
- [x] Add debounced video-frame thumbnails during drag scrubbing when the media provider can extract a frame
- [ ] Validate remote-control focus order, TV-sized layouts and playback controls on physical Android TV devices
- [ ] Expand automated test coverage, complete accessibility review and improve damaged-file recovery guidance
- [ ] Verify a signed release APK on physical devices

## 🔐 Privacy and offline design

- Nova Player is designed to browse videos available on the Android device.
- Files selected through the document picker are handled through Android's content URI flow.
- Resume position, recent history, favorites, watched state, queue, playlists and playback preferences are stored locally in app preferences.
- No account backend or analytics service is configured in this preview.
- A complete privacy policy should be published before a public production release.

## 📦 Publish a signed release

The `.github/workflows/release.yml` workflow runs JVM unit tests and an advisory Android lint scan before packaging. It can build and publish a signed APK when a `v*` tag is pushed or the workflow is manually run. If signing secrets are missing, it builds and uploads a debug APK instead with a warning; it does **not** publish an unsigned APK as a signed release. Configure all four secrets below to publish a signed release.

1. Create a persistent Android release keystore and keep a secure backup outside this repository.
2. In **Repository → Settings → Secrets and variables → Actions**, add:
   - `ANDROID_KEYSTORE_BASE64` — base64-encoded keystore contents
   - `ANDROID_KEYSTORE_PASSWORD` — keystore password
   - `ANDROID_KEY_ALIAS` — key alias
   - `ANDROID_KEY_PASSWORD` — key password
3. Encode the keystore locally:
   - Linux: `base64 -w 0 your-release.jks`
   - Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("your-release.jks"))`
4. Push a version tag:
   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```
5. Check the release workflow, install the APK and test it before sharing.

**Never commit your keystore or signing passwords.** Losing the signing key can prevent future in-place app updates. A public GitHub Release is available only after the signing secrets are configured and the release workflow succeeds.

## 🎨 Visual identity

Nova Player uses a midnight-blue foundation with cyan and violet accents and an original orbit-and-play launcher icon. Vector assets are included in `app/src/main/res/`; no downloaded artwork or runtime network request is needed for the app icon.

## 📄 License

Original project code is intended to use Apache-2.0. Third-party dependencies retain their respective licenses.

---

## ✅ Release-readiness verification

A successful CI build confirms that the Android project compiles; it does **not** replace testing on a physical Android device. Use this checklist before calling a build production-ready.

### Landscape playback quick controls

When a video is playing in landscape, advanced actions live in a dedicated **Playback Tools footer below the video** instead of covering the picture. The footer uses two compact selectable groups: **Display & tracks** (speed, Auto/Fit/Fill/Zoom, subtitles and audio tracks) and **Playback & more** (Favorite, A–B loop, shuffle, repeat, sleep timer, Picture-in-Picture, touch lock and immersive mode). Only one horizontally scrollable action row is shown at a time to reduce landscape clutter and preserve video space. A clear **PLAYBACK TOOLS** label and **Collapse / Show tools** action keep the footer discoverable while giving the video more unobstructed space. Favorite toggles the current video's saved favorite state. For A–B looping, tap **Set A** at the start, **Set B** after A to activate the range, then **Clear A–B** to reset both points; B must be later than A. Shuffle and repeat settings are saved locally. The footer is hidden when player controls are locked or immersive full-screen is active, so the video can use the full available height; exit immersive mode from the on-video control.

### Core playback
- [ ] Open a video from the library and from Android's document picker.
- [ ] Confirm play, pause, seek bar, previous/next, queue order, shuffle and all repeat modes.
- [ ] Rotate between portrait and landscape and confirm the video surface expands to the available screen instead of remaining thumbnail-sized.
- [ ] Confirm Auto / Fit / Fill / Zoom behavior with both landscape movies and portrait clips.
- [ ] Test single-tap 10-second seeking on both video halves, configurable button/remote seeking, horizontal seek gestures, brightness and volume gestures.
- [ ] Lock controls, verify touches are blocked, then use the visible unlock action.
- [ ] Verify speed presets, keep-screen-awake, sleep timer and timer cancellation.
- [ ] Confirm playback position is restored after leaving and reopening a video.

### Library and saved data
- [ ] Grant and deny media permission; verify the document picker still provides a way to open a file.
- [ ] Search, sort, switch grid/list views, and filter watched/unwatched videos.
- [ ] Add/remove favorites; add, reorder, remove and clear queue items.
- [ ] Create, reopen, play, merge and delete playlists.
- [ ] Verify history, Continue Watching, watched state and resume position persist after force-closing the app.
- [ ] Try a file that has been moved or deleted and confirm the app recovers without crashing.

### Subtitles, tracks and Android integration
- [ ] Load valid SRT and VTT subtitles; test subtitle size, background and timing offset.
- [ ] Open audio/video with multiple tracks and confirm track selection is available where supported.
- [ ] Test background playback and media notification controls.
- [ ] Test Picture-in-Picture on a supported Android device.
- [ ] Test HTTP/HTTPS streams, unsupported formats, corrupt files and network loss.
- [ ] Verify no control is visually enabled when its operation cannot be performed, and confirm each operation reports success or a useful error.

### Build and distribution
- [ ] Check the latest [Android CI run](https://github.com/Narsing-s/Nova-Player/actions), download its APK artifact and review the advisory lint report.
- [ ] Install and test on at least one physical Android device in both orientations.
- [ ] Publish only a release APK signed with the project's private release key. Never commit a keystore or signing passwords.
- [ ] Verify the installed release APK upgrades the previous release without losing local preferences and saved lists.

**Current verification boundary:** use the [latest Android CI run](https://github.com/Narsing-s/Nova-Player/actions/workflows/android-ci.yml) as the source of truth for automated build status. A successful CI run does not prove physical-device behavior; the checklist above is not complete until each item has actually been tested. Media format support and Picture-in-Picture behavior can vary by Android version and device.


## 🧭 Modernization and release-readiness

The next improvements should protect existing playback controls first, then improve reliability and accessibility before adding more UI. See the [Modernization & Release Readiness checklist](docs/PRODUCTION_READINESS.md) for prioritized physical-device tests, data-safety checks, accessibility polish, CI improvements and signed-release requirements. Also use the [Accessibility & UX Quality Gate](docs/ACCESSIBILITY_UX_CHECKLIST.md) to check TalkBack labels, touch targets, contrast, rotation, error recovery, and responsive layouts without changing existing behavior.

**Important:** CI compilation is not the same as real-device validation. Confirm landscape playback sizing, every visible control, background/notification behavior, PiP, document-picker access and saved playback state on physical devices before calling a build production-ready.


### Single-tap video seeking

- Tap the **right half** of the video to skip forward exactly 10 seconds.
- Tap the **left half** of the video to rewind exactly 10 seconds.
- Seeking is clamped to the beginning and end of the video; a locked player remains protected.
- The existing configurable seek buttons and double-tap-independent controls remain available.
