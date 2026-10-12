# Nova Player — Modernization & Release Readiness

This checklist complements the implemented app features and separates code/CI coverage from physical-device verification. Preserve existing playback and saved data while modernizing.

## Implemented capabilities (code present; device verification still required)

- [x] Compose / Material 3 library with search, sorting, grid/list, folders, favorites, history, watched filters and per-video resume checkpoints.
- [x] Playback controls, speed, queue, shuffle/repeat, A–B loop, sleep timer, touch lock, seek/brightness/volume gestures and landscape quick-control dock.
- [x] External subtitles, subtitle timing and appearance controls, and available audio/video track selection.
- [x] Midnight/AMOLED theme settings, orientation preference, keep-screen-awake and configurable gesture toggles.
- [x] Playback diagnostics and debounced seek-preview thumbnails with a fallback when frame extraction is unavailable.
- [x] Validated playlist plus player-preference backup/restore, with old playlist-only backup compatibility.
- [x] Leanback launcher declaration and Android TV remote key handling.
- [x] JVM tests for seek safety, orientation/immersive policy, subtitle timing and backup validation.
- [x] CI workflow for unit tests, advisory lint, debug APK build and artifact upload.

## P0 — Physical-device playback and regression checks

- [ ] Confirm the latest [Android CI run](https://github.com/Narsing-s/Nova-Player/actions) passes unit tests and debug APK compilation.
- [ ] Test portrait ↔ landscape rotation during playback, including repeated quick rotations; verify the video uses the available screen area and controls remain reachable.
- [ ] Exercise every visible player action: play/pause, previous/next, seek, speed, fit modes, zoom reset, touch lock/unlock, brightness, volume, favorite, A–B loop, shuffle, repeat, timer/cancel, subtitle load/sync, track selection, PiP and exit.
- [ ] Verify empty-library and permission-denied states, plus opening a video through Android's document picker.
- [ ] Test missing/moved files, revoked URI access, corrupt media, unsupported codecs, unavailable network streams and playback error recovery.
- [ ] Confirm resume position, queue, playlists, favorites, history and settings persist after app backgrounding and process recreation.
- [ ] Verify background playback, media notification controls and audio focus across screen-off and interruption scenarios.

## P1 — TV, accessibility and layout

- [ ] Test D-pad focus order, seek, play/pause and focus recovery on Android TV emulator or physical TV hardware.
- [ ] Validate small phones, large phones, tablets, display cutouts and gesture-navigation insets.
- [ ] Check icon-only controls for meaningful TalkBack labels and all interactive elements for selected/checked state.
- [ ] Verify TalkBack order, font scaling, contrast and touch targets; confirm announcements do not become excessively chatty.
- [ ] Confirm controls that cannot operate for the selected media are disabled or explain why.

## P2 — Data safety and release readiness

- [ ] Export playlists and preferences to a JSON file, import it on a clean install and verify settings plus playlist contents.
- [ ] Test malformed, oversized, unsupported-version and settings-only backups; confirm invalid values are clamped and imports fail safely.
- [ ] Confirm old playlist-only backups still import and unrelated playlists are preserved during merge.
- [ ] Verify seek-preview fallback and storage behavior when frame extraction fails; evaluate cache cleanup before expanding thumbnail generation.
- [ ] Review Android Lint report and third-party dependency licenses.
- [ ] Verify package/version metadata, APK signing, release notes and checksums.
- [ ] Test an upgrade over the previous signed release without losing saved data.
- [ ] Publish a privacy notice describing local media access, local preferences and optional network features before public production distribution.
- [ ] Never commit signing keystores or passwords; only publish a production release when release signing secrets are correctly configured.

## Verification boundary

Checked items above mean the capability is represented in source/tests, not that every device scenario has passed. Record actual device results before marking physical checks complete. CI compilation alone is not a release certification.

See [Modern Feature Roadmap](MODERN_FEATURE_ROADMAP.md) for planned additions and engineering guardrails.
