# Nova Player — Accessibility and UX Quality Gate

This checklist is additive: it does not change player behavior or remove existing controls. Use it to validate the current interface before introducing more UI.

## Accessibility basics

- [ ] Every icon-only action has a meaningful accessibility label (for example, “Pause”, “Open subtitles”, “Close quick controls”, and “Unlock controls”).
- [ ] TalkBack announces buttons by action and current state where applicable; avoid announcing decorative icons twice.
- [ ] Verify the library scan progress is announced as “Scanning library” while a scan is active, and that the selected navigation tab is exposed as selected.
- [ ] Interactive targets are at least 48 dp where practical, including compact landscape controls.
- [ ] Text and icons have sufficient contrast against the midnight panels and video overlay; do not rely on color alone for active/inactive state.
- [ ] Text remains readable with Android font scaling enabled; dialogs and quick-control rows can scroll instead of clipping.
- [ ] Keyboard/D-pad focus, Android Back, and system gesture navigation remain predictable.

## Playback and interruption resilience

- [ ] Rotate repeatedly between portrait and landscape during playback; confirm the video stays full-size and position/state are retained.
- [ ] Open/close the landscape quick-control dock and touch lock; confirm no invisible overlay intercepts taps after closing or unlocking.
- [ ] Trigger a phone call, notification shade, screen lock, app switch, and return; confirm playback and saved state follow the intended behavior.
- [ ] Test media notification actions and Picture-in-Picture on supported Android versions.
- [ ] Try a deleted file, unreadable file, unsupported format, denied media permission, and a document-picker URI; the app should recover with a clear message rather than a stuck spinner or crash.
- [ ] Test subtitle files with zero offset and positive/negative offsets, including timestamps crossing midnight/hour boundaries; the original file must remain unchanged.
- [ ] Verify queue, playlists, favorites, watched status, history, and per-video resume checkpoints after force-stop and relaunch.

## Responsive layout

- [ ] Check a narrow phone, a larger phone, landscape, split-screen/resizable window, and a tablet/emulator where available.
- [ ] Confirm the library grid/list adapts without hiding search, filters, sort, or navigation actions.
- [ ] Confirm long filenames, empty states, and large font sizes do not overlap or push essential controls off-screen.
- [ ] Keep playback actions grouped by purpose; do not add duplicate controls or unexplained blank spacers.

## CI and release gate

- [ ] Review the HTML/XML Android Lint report from the latest Actions run and resolve high-severity findings.
- [ ] Keep JVM unit tests and debug APK assembly green.
- [ ] Install the produced APK on a physical Android device and exercise core playback before release.
- [ ] Test a signed release upgrade over the prior signed release without losing local state.
- [ ] Verify release signing secrets and keystore backups without committing any secret material.

## Reporting results

For each check, record device model, Android version, APK commit SHA, pass/fail, and a short reproduction note for failures. CI success proves the project passed configured automated steps; it does not prove physical-device playback, accessibility, or release readiness.
