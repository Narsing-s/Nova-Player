# Nova Player — Modern Feature Roadmap

This roadmap is additive: preserve the current library, playback gestures, queue, playlists, subtitle tools, landscape dock, resume checkpoints, and existing navigation. Ship improvements behind small, testable changes and keep the debug APK build green after each change.

## Recommended next upgrades

### P0 — Reliability before more features
- [ ] Add regression tests for queue ordering, playlist save/load/merge, favorites, watched state, per-video resume, sleep timer cancellation, and A–B loop validation.
- [ ] Add Compose UI smoke tests for first launch, permission denied/granted, empty library, opening a document-picker URI, rotation during playback, and returning to Continue Watching.
- [ ] Add playback error classification (missing file, unsupported format, decoder failure, network timeout, revoked URI access) with useful Retry / Choose another file / Back actions.
- [ ] Verify Media3 session/service lifecycle, audio focus, notification actions, process recreation, and playback when the screen turns off.
- [ ] Review Android Lint findings and make CI fail on unit-test or APK-build regressions; keep advisory checks clearly labeled until the baseline is clean.

### P1 — Modern everyday experience
- [ ] Add a first-run welcome/permission explanation with a prominent **Choose video** action; do not block users who prefer the document picker.
- [ ] Add a focused Settings page for default fit mode, resume behavior, seek interval, gesture sensitivity, subtitle defaults, haptics, and theme.
- [ ] Offer System / Dark / Light appearance consistently across the library, dialogs, and player overlays.
- [ ] Improve library browsing with pinned folders, optional hide/exclude folders, and clear filter chips; keep scanning local and avoid silently uploading filenames or media.
- [ ] Add export/import of playlists and preferences with a versioned, validated file format. Never export private media itself by default.
- [ ] Add a playback diagnostics panel showing file name, container/codec when available, resolution, frame rate/bitrate when available, selected audio/subtitle track, and a copyable sanitized report.

### P2 — Advanced player capabilities (only after testing)
- [ ] Investigate seek-preview thumbnails for long videos; generate/cache them off the main thread and allow the feature to be disabled to control storage and battery use.
- [ ] Add chapter/embedded metadata display where the media exposes it.
- [ ] Consider network sources (for example, user-selected HTTP(S) URLs or LAN shares) as an explicit opt-in feature with timeout, authentication, and privacy handling; local playback must continue to work without a network.
- [ ] Consider Android TV / D-pad navigation and external keyboard support without compromising phone layouts.
- [ ] Consider casting only after selecting a supported protocol/library and documenting network permissions and compatibility; do not present a cast button until a real target connection works.
- [ ] Consider optional playback bookmarks and per-video notes, stored locally and searchable.

## Engineering guardrails
- Preserve existing public behavior and saved data. If migrating SharedPreferences to DataStore or a database, add a one-time migration, migration tests, and a rollback-safe fallback before removing the old path.
- Keep heavy work (library scanning, thumbnail generation, metadata inspection, import/export) off the main thread and make cancellation explicit.
- Use stable IDs/keys for library items and playlists; handle renamed, moved, duplicate, and deleted files gracefully.
- Use Android's document-picker/content-URI flow and persist URI permissions only when supported and needed.
- Provide meaningful TalkBack labels, selected/checked state, 48 dp touch targets where practical, font scaling, contrast, and landscape/split-screen coverage.
- No feature is complete until its visible controls perform a real operation, failures are explained, automated tests pass, and device testing is recorded.
- Do not add an account, analytics SDK, or cloud upload by default. Clearly disclose any future optional network behavior.

## Suggested release gate
1. Run `gradle testDebugUnitTest` and `gradle assembleDebug`.
2. Review the Android Lint report and fix high-severity findings.
3. Install the exact APK on a physical device and test portrait/landscape playback, document-picker access, queue, subtitles, notification controls, PiP, and saved state.
4. Test an upgrade over the previous signed build before publishing a release.
5. Publish only a correctly signed release artifact; never commit keystores or signing secrets.

This document is a plan, not a claim that unchecked features have already been implemented or physically tested.
