# Nova Player — Modernization & Release Readiness

This checklist complements the existing app features. It intentionally separates implemented capabilities from items that still need verification; a successful build alone does not prove device behavior.

## Priority 0 — Protect the experience already implemented

- [ ] Run the app on a physical Android 14/15 device in portrait and landscape.
- [ ] Verify landscape playback uses the full available video surface (not a tiny portrait-sized view), and that system bars and the quick-control dock can be restored predictably.
- [ ] Exercise every visible player action: play/pause, previous/next, seek, speed, fit modes, zoom reset, touch lock/unlock, brightness, volume, favorite, A–B loop, shuffle, repeat, timer/cancel, subtitle load/sync, audio/video track selection, PiP and exit.
- [ ] Confirm unsupported actions are disabled or explain why they are unavailable; avoid buttons that appear active but do nothing.
- [ ] Confirm cancellation paths exist for timers, dialogs, overlays and queued operations. Do not leave unexplained blank space or duplicate controls.
- [ ] Test deleted/moved files, permission denial, empty library, corrupt media, unavailable network streams and rapid orientation changes.

## Priority 1 — Reliability and data safety

- [ ] Add automated unit tests for resume checkpoint rules, watched-state transitions, queue reorder/remove/clear, playlist persistence and A–B loop validation.
- [ ] Add UI/instrumentation smoke tests for opening a local video, rotating, returning to the library and reopening Continue Watching.
- [ ] Ensure preference/database writes are resilient to malformed or older saved data; recover safely instead of crashing.
- [ ] Verify a file's content URI permission remains usable when selected from Android's document picker, and handle revoked access gracefully.
- [ ] Verify Media3 player/service lifecycle and audio focus across screen-off, incoming calls, app backgrounding and process recreation.
- [ ] Add clear error states for unsupported codecs, broken files and stream timeouts, with Retry and Back actions.

## Priority 2 — Modern UX polish

- [ ] Accessibility pass: meaningful labels for icon-only controls, TalkBack order, scalable text, adequate contrast and minimum touch targets.
- [ ] Add a first-run permission explanation and useful empty-library onboarding with a clear “Choose video” action.
- [ ] Respect system dark/light mode or provide a complete in-app theme option consistently across library, dialogs and player overlays.
- [ ] Add a settings screen section for gesture sensitivity, default fit mode, default subtitle style, resume behavior and playback controls.
- [ ] Provide haptic feedback only where appropriate and allow it to be disabled.
- [ ] Validate responsive layouts on small phones, large phones and tablets; avoid controls overlapping cutouts or gesture navigation.
- [ ] Keep the player uncluttered: primary playback controls first, advanced actions in a clearly labeled expandable panel, with an obvious close/cancel route.

## Priority 3 — Release quality

- [ ] Add Android Lint and unit-test steps to CI once the current project baseline passes them.
- [ ] Test install, upgrade and uninstall/reinstall behavior; confirm local data expectations are documented.
- [ ] Produce a signed release only when keystore secrets are configured; never publish a debug APK as a production release.
- [ ] Verify APK signing, package/version metadata, release notes, checksums and a stable GitHub Releases download.
- [ ] Publish a privacy notice describing local media access, local preferences and any optional network features.
- [ ] Review dependency updates and third-party licenses before each public release.

## Current known verification boundary

The repository README describes the current feature set and provides a physical-device checklist. Items above are recommendations/checks, not claims that the features are already implemented or that device testing has passed. Prioritize fixing any broken existing operation before adding new features.
