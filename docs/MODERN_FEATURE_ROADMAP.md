# Nova Player — Modern Feature Roadmap

This roadmap reflects the current repository rather than an aspirational feature list. Improvements must remain additive: preserve the existing library, playback gestures, queue, playlists, subtitle tools, landscape dock, resume checkpoints, and navigation.

## Implemented in the current codebase

- [x] Modern Compose / Material 3 interface, adaptive library grid/list, search, sorting, folders, favorites, history, watched filters and per-video Continue Watching.
- [x] Media3 playback with queue controls, previous/next, playback speed, shuffle/repeat, A–B loop, sleep timer, pinch zoom, touch lock, brightness/volume gestures and immersive landscape controls.
- [x] Subtitle loading and timing adjustment, audio/video track selection where exposed by the media, and Picture-in-Picture support where available.
- [x] Local settings for Midnight/AMOLED theme, orientation, screen-awake behavior, seek interval and gesture toggles; remember the selected library tab and persist gesture changes immediately.
- [x] Playback diagnostics for state, position/duration, active track details and reported video format.
- [x] Debounced seek-preview frame extraction during scrubbing with a time-based fallback.
- [x] Versioned playlist backup/restore with input validation, preference backup/restore, safe value clamping and compatibility with older playlist-only backups.
- [x] Android TV Leanback declaration and D-pad/media-key handling for seek and playback actions.
- [x] JVM tests for seek boundaries/overflow, playback-state labels, orientation/immersive policy, subtitle timing and playlist/preference backup validation.
- [x] GitHub Actions workflow for JVM unit tests, advisory Android lint report, debug APK build and APK artifact upload.

## P0 — Verify and protect existing behavior

- [ ] Confirm the latest CI run completes unit tests, lint reporting and debug APK compilation successfully.
- [ ] Add automated tests for queue reorder/remove/clear, playlist persistence/merge, favorites, watched-state transitions, resume checkpoint rules, sleep timer cancellation and A–B loop validation.
- [ ] Add Android instrumentation/UI smoke tests for first launch, permissions, empty library, opening a document-picker URI, rotation during playback and returning to Continue Watching.
- [ ] Classify playback failures (missing/revoked URI, unsupported format, decoder failure, network timeout) and offer actionable Retry / Choose another file / Back paths.
- [ ] Verify Media3 session/service lifecycle, audio focus, notifications, process recreation and screen-off playback.
- [ ] Review high-severity Android Lint findings and make the check blocking only after the existing baseline is understood and clean.

## P1 — Modern everyday experience

- [ ] Add a lightweight first-run explanation with a prominent **Choose video** action; never block document-picker access.
- [ ] Consider a complete System / Light / Dark theme choice across the library, dialogs and overlays while retaining the existing Midnight/AMOLED options.
- [ ] Add optional haptics and adjustable gesture sensitivity.
- [ ] Improve library navigation with optional pinned/excluded folders and visible filter chips without silently uploading filenames or media.
- [ ] Provide a sanitized, copyable diagnostics report; exclude full local paths, private URI values and unrelated personal metadata by default.
- [ ] Complete an accessibility pass: meaningful TalkBack labels, selected/checked state, 48 dp touch targets where practical, font scaling, contrast and focus order.

## P2 — Optional capabilities, only after testing

- [ ] Add chapter/embedded metadata display where the media exposes it.
- [ ] Consider user-initiated HTTP(S) URLs or LAN shares with explicit opt-in, timeouts and privacy handling; local playback must remain fully usable offline.
- [ ] Improve Android TV layout and focus traversal through device/emulator tests; retain phone-first layout quality.
- [ ] Consider casting only after a supported protocol and real target connection are implemented. Do not display a nonfunctional Cast button.
- [ ] Consider local playback bookmarks and per-video notes.
- [ ] Evaluate thumbnail cache limits and cleanup so seek previews do not grow storage without bound.

## Engineering guardrails

- Preserve saved data and existing public behavior. Any future persistence migration must include a one-time migration, tests and rollback-safe fallback before the old path is removed.
- Keep scanning, thumbnail extraction, metadata inspection and import/export work off the main thread; provide cancellation where practical.
- Handle renamed, moved, duplicate and deleted files gracefully.
- Keep content-URI access within Android's document-picker permission model.
- Do not add an account, analytics SDK or cloud upload by default. Clearly disclose optional network behavior.
- A feature is complete only when its visible controls work, failures are explained, automated tests pass and device testing is recorded.

## Release gate

1. Run `gradle --no-daemon testDebugUnitTest`, `gradle --no-daemon lintDebug` and `gradle --no-daemon assembleDebug`.
2. Review the Lint report; document any remaining advisory findings.
3. Install the exact CI APK on a physical device and test portrait/landscape playback, document-picker access, queue, subtitles, notifications, PiP and saved state.
4. Test TV remote navigation on a TV device or emulator.
5. Test upgrades over the previous signed build before publishing.
6. Publish only a correctly signed release artifact; never commit keystores or signing secrets.

This document distinguishes implemented code from remaining work. A green CI run does not replace physical-device testing.
