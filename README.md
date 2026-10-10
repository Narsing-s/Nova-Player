# Nova Player

A modern Android video player built from the ground up with Kotlin, Jetpack Compose, Material 3 and AndroidX Media3.

[**Repository**](https://github.com/Narsing-s/Nova-Player) · [**Builds & APK artifacts**](https://github.com/Narsing-s/Nova-Player/actions) · [**Releases**](https://github.com/Narsing-s/Nova-Player/releases)

> **Development status:** early preview. The latest Android CI build passed and produced a debug APK artifact. The app still needs installation and playback testing on physical Android devices; it is not yet a complete indexed video-library app.

## Features implemented

- **Modern dark UI** with Compose and Material 3.
- **Indexed device video library** using MediaStore, with permission-gated scanning, search, recent/name sorting, duration and file-size metadata.
- **Local video picker** using Android's system document picker as a fallback.
- **Playback resume** stores the last media URI and position locally and offers a Resume recent action.
- **Recent playback history** stores up to eight recently played items locally; the home screen shows the latest five.
- **Favorites library** lets you save and remove videos with a heart control; favorite entries persist locally on the device.
- **External subtitles** for .srt and .vtt files.
- **MX Player-inspired controls**: dedicated 10-second rewind/forward buttons, horizontal swipe seeking, and vertical brightness/volume gestures.
- **Playback speed** cycling from 0.5× to 2×.
- **Screen-fit modes** cycling through Fit, Fill and Zoom.
- **Video quality/track selection** when the local file exposes selectable video tracks; a file's original resolution cannot be increased by the player.
- **Audio-track selection** using Media3's track selection dialog when the media exposes selectable tracks.
- **Picture-in-Picture (PiP)** action on Android 8.0+ devices, with manifest support enabled.
- **Subtitle track controls** in the Media3 player UI where supported by the media.
- **GitHub Actions** workflow to build a debug APK and upload it as an artifact.

## Roadmap

- [x] Verify Android CI debug build and APK artifact upload
- [ ] Install and test playback on physical Android devices
- [x] Indexed MediaStore video library with search and recent/name sorting
- [ ] Folder navigation
- [x] Video thumbnails
- [x] Basic recent playback history (up to eight entries)
- [x] Local favourites
- [ ] Playlists
- [x] Basic brightness/volume/seek swipe gestures
- [ ] Orientation-aware immersive full-screen playback
- [x] Basic audio-track selection
- [ ] Subtitle styling / timing controls
- [ ] Background playback via MediaSession service
- [ ] Better playback error reporting and network retry handling
- [ ] Automated tests and accessibility checks
- [x] Signed release APK workflow and public GitHub Releases (requires owner-configured signing secrets)
- [ ] Verify signed release on physical devices

## Tech stack

- Kotlin, Android SDK 35, JDK 17
- Jetpack Compose, Material 3
- AndroidX Media3 ExoPlayer and PlayerView
- SharedPreferences for the initial resume-state implementation
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

A previous app-code revision built successfully and uploaded `nova-player-debug`. The latest offline-library and thumbnail changes passed CI; the newest rewind/forward and video-track quality controls are awaiting a fresh CI run. Check the [Actions page](https://github.com/Narsing-s/Nova-Player/actions), open a successful run, and download its `nova-player-debug` artifact. Debug APKs are for testing, not public release distribution.

## Offline-only library

Nova Player is designed to work with videos already stored on your Android device. On launch, it requests video-library permission and scans the device media index; videos from available device folders appear in the Videos tab, with search, sorting, duration and file-size details. If permission is denied or a video is not indexed, use **Browse files** to select it with Android’s system picker. No online-stream tab or streaming URL input is provided.

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
