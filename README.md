# Nova Player

A modern Android video player built from the ground up with Kotlin, Jetpack Compose, Material 3 and AndroidX Media3.

[**Repository**](https://github.com/Narsing-s/Nova-Player) · [**Builds & APK artifacts**](https://github.com/Narsing-s/Nova-Player/actions) · [**Releases**](https://github.com/Narsing-s/Nova-Player/releases)

> **Development status:** active preview. The app includes offline video browsing, playback controls, a session queue and locally saved playlists. Android CI builds the debug APK; physical-device playback testing is still required before release.

## Features implemented

- **Modern dark UI** with Compose and Material 3, using a midnight-blue canvas, cyan accents and violet highlights.
- **Original Nova Player identity**: custom orbit-and-play launcher icon with adaptive resources on Android 8.0+ and vector fallbacks on older devices.
- **Indexed device video library** using MediaStore, with permission-gated scanning, search, recent/name sorting, duration and file-size metadata.
- **Switchable library layouts**: browse a thumbnail-rich two-column grid or a compact list, with the layout choice remembered locally; grid cards expose quick favorite and queue actions.
- **Quick duration filters**: instantly narrow the local video library to all videos, clips under 10 minutes, videos from 10–60 minutes, or feature-length videos over an hour; the selected filter is remembered on-device.
- **Folder browser** groups indexed videos by device folder and lets you open a folder to filter the Videos library. On Android 10+, it uses MediaStore relative paths; older Android versions show a general device-storage group.
- **Local video picker** using Android's system document picker as a fallback.
- **Playback resume** stores the last media URI and position locally and offers a prominent **Continue Watching** card on the Videos tab, showing the saved resume point when available.
- **Recent playback history** stores up to eight recently played items locally; the home screen shows the latest five.
- **Favorites library** lets you save and remove videos with a heart control; favorite entries persist locally on the device.
- **External subtitles** for .srt and .vtt files.
- **MX Player-inspired controls**: dedicated 10-second rewind/forward buttons, horizontal swipe seeking, and vertical brightness/volume gestures.
- **Custom gesture controls**: independently enable or disable horizontal seek, left-side brightness and right-side volume swipes; choices are saved locally.
- **Subtitle timing sync**: shift external `.srt` and `.vtt` cues up to 5 seconds earlier or later in 250 ms steps; adjusted subtitle copies are generated in the app cache, keeping the original file untouched.
- **Background playback and media notification**: a dedicated Media3 session service owns the player beyond the Activity lifecycle, allowing playback to continue when the app is minimized, with Android media-session notification/lock-screen controls where supported by the device.
- **Advanced playback modes**: shuffle, repeat-all and repeat-one, with active-state styling.
- **Persistent play queue**: add videos from the library, play the queue from any item, move entries up/down, remove individual items or clear the queue. Queue order is saved locally and restored when the app is reopened.
- **Saved playlists**: save a named playlist from the current queue, restore it after restarting the app, play it, merge it into the queue, or delete it. Playlist metadata is stored locally and works offline.
- **Immersive viewing toggle**: hide Android system bars while watching, with an in-player control to exit immersive mode.
- **Subtitle appearance**: adjust subtitle text size and toggle a dark subtitle background for readability; your preference is remembered locally.
- **Remembered playback preferences**: speed, shuffle, repeat mode and subtitle appearance settings persist locally between app launches.
- **Playback speed** cycling from 0.5× to 2×.
- **Offline sleep timer**: automatically pause playback after 15, 30, 45, 60 or 90 minutes, with the ability to cancel or replace an active timer.
- **Screen-fit modes** cycling through Fit, Fill and Zoom.
- **Playback recovery actions** surface Media3 playback errors with Retry playback and Back to library actions instead of leaving the user guessing.
- **Video quality/track selection** when the local file exposes selectable video tracks; a file's original resolution cannot be increased by the player.
- **Audio-track selection** using Media3's track selection dialog when the media exposes selectable tracks.
- **Picture-in-Picture (PiP)** action on Android 8.0+ devices, with manifest support enabled.
- **Subtitle track controls** in the Media3 player UI where supported by the media.
- **GitHub Actions** workflow to build a debug APK and upload it as an artifact.

## Roadmap

- [ ] Verify the latest Android CI debug build and APK artifact upload
- [ ] Install and test playback on physical Android devices
- [ ] Verify background playback, notification actions and lock-screen controls on Android 13–15 devices
- [x] Indexed MediaStore video library with search and recent/name sorting
- [x] Basic folder navigation for indexed MediaStore videos
- [x] Video thumbnails
- [x] Original Nova Player launcher icon with adaptive and legacy Android resources
- [x] Basic recent playback history (up to eight entries)
- [x] Local favourites
- [x] Session play queue with add, play-from-item, reorder, remove and clear actions
- [x] Named playlists persisted locally with play, add-to-queue and delete actions
- [x] Restore the last session queue automatically after app restart
- [x] Basic brightness/volume/seek swipe gestures
- [x] Immersive system-bar toggle for playback
- [x] Subtitle text-size and background styling controls
- [ ] Orientation-aware layout polish for immersive playback
- [x] Basic audio-track selection
- [x] Basic subtitle appearance controls (text size and background)
- [x] Subtitle timing offset controls
- [x] Background playback via MediaSession service (CI build verified; physical-device notification testing still required)
- [x] Playback error reporting with retry and return-to-library actions
- [x] Continue Watching card with saved resume position
- [ ] More recovery guidance for unsupported or damaged local files
- [ ] Automated tests and accessibility checks
- [x] Signed release APK workflow and public GitHub Releases (requires owner-configured signing secrets)
- [ ] Verify signed release on physical devices

## Nova Player visual identity

The launcher uses an original midnight-blue tile with a cyan orbit, violet ring and crisp play mark. Its vector-based assets live in `app/src/main/res/`; Android 8.0+ uses adaptive launcher resources, while older Android versions use the vector fallback. The icon is part of the app source and is built into the APK—no downloaded artwork or runtime network request is required.

## Tech stack

- Kotlin, Android SDK 35, JDK 17
- Jetpack Compose, Material 3
- AndroidX Media3 ExoPlayer and PlayerView
- SharedPreferences for resume state, favorites, history, persistent queue, saved playlists and playback preferences
- GitHub Actions

## Build locally

Requirements: Android Studio with Android SDK 35 and JDK 17. The CI currently uses Gradle 8.11.1.

Open the project in Android Studio and sync Gradle. If Gradle is installed separately, run:

```bash
gradle assembleDebug
```

The debug APK should be generated at:

```
app/build/outputs/apk/debug/app-debug.apk
```

Check the [Actions page](https://github.com/Narsing-s/Nova-Player/actions) for the newest workflow result. After a successful run, open it and download the `nova-player-debug` artifact. The debug APK is for testing, not public release distribution.

## Offline-only library

Nova Player is designed to work with videos already stored on your Android device. On launch, it requests video-library permission and scans the device media index; videos from available device folders appear in the Videos tab, with search, sorting, duration and file-size details. The Folders tab groups indexed videos by folder and filters the library when a folder is opened. If permission is denied or a video is not indexed, use **Browse files** to select it with Android’s system picker. No online-stream tab or streaming URL input is provided.

## Privacy

- Local files are selected through Android's document picker; the app does not request broad storage access for this flow.
- The most recent media URI, title and resume position are stored locally in app preferences.
- No analytics or account backend is configured in this preview.
- Publish a full privacy policy before a public release.

## Publish a signed APK

The `.github/workflows/release.yml` workflow builds and publishes a signed APK when a `v*` tag is pushed, or when manually run. It intentionally stops if signing secrets are missing.

1. Create a persistent Android release keystore and store a secure backup outside this repository.
2. In **Repository → Settings → Secrets and variables → Actions**, add:
   - `ANDROID_KEYSTORE_BASE64`: base64-encoded keystore file contents
   - `ANDROID_KEYSTORE_PASSWORD`: keystore password
   - `ANDROID_KEY_ALIAS`: key alias
   - `ANDROID_KEY_PASSWORD`: key password
3. To encode a keystore locally, use `base64 -w 0 your-release.jks` on Linux or `[Convert]::ToBase64String([IO.File]::ReadAllBytes("your-release.jks"))` in PowerShell. Keep the output private.
4. Push a version tag such as `v0.1.0` to trigger a signed build and GitHub Release:
   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```
5. Check the release workflow and install-test the APK before sharing it.

Never commit a keystore or signing passwords. Losing the signing key can prevent future in-place updates. A GitHub Release is not published until the signing secrets are configured and the release workflow succeeds.

## License

Original project code is intended to use Apache-2.0. Third-party dependencies retain their respective licenses.
