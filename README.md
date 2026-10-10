# Nova Player

A modern Android video player built from the ground up with Kotlin, Jetpack Compose, Material 3 and AndroidX Media3.

[**Repository**](https://github.com/Narsing-s/Nova-Player) · [**Builds & APK artifacts**](https://github.com/Narsing-s/Nova-Player/actions) · [**Releases**](https://github.com/Narsing-s/Nova-Player/releases)

> **Development status:** early preview. The current code provides basic local-file and HTTPS playback, resume-last playback, external SRT/VTT subtitle loading and seek controls. It is not yet a complete indexed video-library app, and the first CI build still needs to be verified before treating an APK as tested.

## Features implemented

- **Modern dark UI** with Compose and Material 3.
- **Local video picker** using Android's system document picker instead of broad storage permissions.
- **Direct HTTPS streaming** with Media3 / ExoPlayer.
- **HLS and DASH playback support** through Media3 modules (subject to URL, codec, server and device compatibility).
- **Playback resume** stores the last media URI and position locally and offers a Resume recent action.
- **External subtitles** for .srt and .vtt files.
- **Seeking** with 10-second buttons and a horizontal swipe gesture over the video surface.
- **Picture-in-Picture action** on Android 8.0+ devices, with manifest support enabled.
- **Subtitle track controls** in the Media3 player UI where supported by the media.
- **GitHub Actions** workflow to build a debug APK and upload it as an artifact.

## Roadmap

- [ ] Verify CI build and install on physical Android devices
- [ ] Indexed library using MediaStore, folders, thumbnails and search
- [ ] Multiple-item playback history, favourites and playlists
- [ ] Gesture tuning for brightness/volume, orientation and immersive full-screen
- [ ] Audio-track selection and subtitle styling / timing controls
- [ ] Background playback via MediaSession service
- [ ] Better playback error reporting and network retry handling
- [ ] Automated tests and accessibility checks
- [x] Signed release APK workflow and public GitHub Releases (requires owner-configured signing secrets)
- [ ] Verify signed release on physical devices

## Tech stack

- Kotlin, Android SDK 35, JDK 17
- Jetpack Compose, Material 3
- AndroidX Media3 ExoPlayer, HLS, DASH and PlayerView
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

The GitHub Actions workflow uploads a debug APK artifact when the build succeeds. Open [Actions](https://github.com/Narsing-s/Nova-Player/actions), select the latest successful run and download the nova-player-debug artifact. Debug APKs are for testing, not public release distribution.

## Streaming and compatibility

Enter a direct **HTTPS media URL** such as a supported MP4, HLS (.m3u8) or DASH (.mpd) resource. Web pages, playlist pages requiring browser login, unsupported codecs, DRM-protected streams and hosts requiring custom authentication may not play. Plain HTTP streaming is intentionally disabled by Android network security configuration.

Only play media you own or are authorized to access. Nova Player does not include a content catalogue or bypass DRM.

## Privacy

- Local files are selected through Android's document picker; the app does not request broad storage access for this flow.
- The most recent media URI, title and resume position are stored locally in app preferences.
- URLs entered for streaming are sent to the media host when playback starts.
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
