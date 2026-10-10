# Nova Player

A modern Android video player built from the ground up with Kotlin, Jetpack Compose and AndroidX Media3.

> Current milestone: initial functional playback foundation. Advanced library indexing, persistent history, subtitle management, gestures, Picture-in-Picture and signed releases are planned follow-up milestones and are not yet claimed as complete.

## Current features

- Dark, modern Compose interface
- Open a local video using Android's system document picker
- Play direct HTTP/HTTPS media URLs
- Media3 / ExoPlayer playback controls
- HLS and DASH playback dependencies included
- GitHub Actions debug build artifact

## Planned roadmap

- [ ] Local video library with MediaStore and folder browsing
- [ ] Thumbnails, search, sort and favourites
- [ ] Playback history and resume position using Room
- [ ] Double-tap seeking, brightness and volume gestures
- [ ] External subtitles and subtitle styling
- [ ] Audio-track and subtitle-track selection
- [ ] Picture-in-Picture and background playback
- [ ] Robust network errors, retries and stream validation
- [ ] Signed release APK and GitHub Releases downloads

## Tech stack

- Kotlin
- Jetpack Compose and Material 3
- AndroidX Media3 ExoPlayer
- Room and DataStore (for upcoming persistence features)
- GitHub Actions

## Requirements

- Android Studio with Android SDK 35 installed
- JDK 17
- Gradle 8.11.1 or a compatible Gradle 8.x release
- Android device or emulator with API 24+

## Build locally

Open this repository in Android Studio and allow Gradle sync to finish. Or install a compatible Gradle distribution and run:

```bash
gradle assembleDebug
```

The APK is generated at:

```
app/build/outputs/apk/debug/app-debug.apk
```

The first CI workflow builds a debug APK and uploads it as a GitHub Actions artifact. Debug builds are for testing, not public release distribution.

## Streaming notes

Use direct media URLs you own or are authorized to access. Playback depends on the URL, server response, codecs, DRM and device decoder support. The app does not provide a movie catalogue or bypass DRM.

## Privacy

Local videos are selected through Android's document picker. The app does not need broad storage access for that flow. URLs entered for streaming are sent to the selected media host when playback starts. A fuller privacy policy should be published before a public release.

## Release signing

Do not commit signing keys or passwords. Configure a persistent owner-controlled release keystore and GitHub Actions secrets before creating a signed release workflow. Back up the keystore securely.

## License

Apache-2.0 for original project code. Third-party dependencies retain their own licenses.
