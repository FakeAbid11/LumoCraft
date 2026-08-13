# LumoCraft — Release Testing Checklist

Manual test matrix for release candidates (`v0.1.0-rc1` and later).
Run on at least two devices when possible: one low-end (2–3 GB RAM,
arm64) and one mid-range. Mark every item **PASS** / **FAIL** and note
the device, Android version and app version at the top of each run.

| Device / Emulator | Android version | LumoCraft version | Date |
|---|---|---|---|
|  |  |  |  |

---

## 1. First launch & onboarding

- [ ] Fresh install shows the 5-page onboarding wizard (Welcome → Install → Profile → Performance → Ready).
- [ ] "Next" walks through all pages; page dots highlight the current page.
- [ ] "Skip" and "Get started" both dismiss the wizard.
- [ ] After finishing, the Home screen appears and the wizard is never shown again (restart app to confirm).
- [ ] Reinstalling (clearing app data) shows the wizard again.

## 2. Accounts

- [ ] Empty state prompts to create an account.
- [ ] Create account: the first account becomes selected; avatar is deterministic (same username → same avatar).
- [ ] Rename account updates the list and avatar.
- [ ] Select another account switches selection.
- [ ] Delete account: cannot delete the last account; deleting a non-last account auto-selects another.
- [ ] Duplicate usernames (case-insensitive) are rejected.
- [ ] Accounts persist across app restarts.

## 3. Versions

- [ ] Version list loads from the Mojang manifest with pull-to-refresh.
- [ ] Search and filters (Release / Snapshot / Old) work.
- [ ] Version details sheet shows metadata and an Install action.
- [ ] Installing a version shows live progress through all stages (version JSON → libraries → assets → logging config → verification → complete).
- [ ] Installed versions show "Installed"; repair works and re-downloads missing files.
- [ ] Version install-state persists across restarts.
- [ ] Interrupted install can be resumed/repaired.

## 4. Java runtime

- [ ] Runtime manager detects installed runtimes.
- [ ] Installing a runtime (Java 17/21) streams progress and verifies (`bin/java`, `bin/javac`, `bin/keytool`, `release` checksum).
- [ ] First installed runtime becomes the default.
- [ ] Repair fixes a corrupted runtime; removal works.
- [ ] RAM configuration (recommended max / sliders) is clamped to the device ceiling.
- [ ] JVM arguments reflect the selected configuration.

## 5. Launch pipeline

- [ ] Home screen readiness gates on having an account, an installed version and a verified runtime.
- [ ] Version picker on Home selects the launch version and persists it.
- [ ] Play opens the Launch screen with a live console.
- [ ] Client jar is fetched on demand; launch proceeds only after validation.
- [ ] Classpath resolution follows the `inheritsFrom` chain (leaf-first).
- [ ] JVM/game argument builder produces correct args (including `-Dlumocraft.*` renderer flags and scaled resolution).
- [ ] Launch finishes with a clear success/crash message; crash analysis classifies common failures.
- [ ] Session log is written under `logs/launcher-<timestamp>.log`.
- [ ] Second launch of an unchanged version is faster (launch cache + smart verification).

## 6. Native compatibility

- [ ] Native libraries extract per-architecture into `versions/<id>/natives/<arch>/`.
- [ ] Re-launching skips extraction (stamp/cache hit) unless files changed.
- [ ] Arch mismatch (e.g. arm64 runtime on x86 device) is rejected with an actionable message.
- [ ] JNI environment exposes `java.library.path` and `org.lwjgl.librarypath`.
- [ ] Renderer profile (Compatibility/Performance/Experimental), resolution scale, FPS limit, VSync and mipmaps persist and are applied.

## 7. Performance

- [ ] Performance dashboard shows the detected device profile (RAM/cores/tier).
- [ ] JVM profile shows Auto or the manual override (Battery Saver / Balanced / Performance).
- [ ] Launch history records validation/classpath/JVM/total timings.
- [ ] Cache stats update after launches; "Clear cache" drops entries.
- [ ] Reset performance settings restores the recommendation.
- [ ] Download concurrency adapts to bandwidth (observable in installs on slow networks).

## 8. Loaders (Fabric)

- [ ] Loader manager (Versions → Loaders) shows installed loaders for a version.
- [ ] Installing Fabric downloads metadata + libraries and reports progress.
- [ ] Repair re-downloads missing loader files.
- [ ] Removing a loader restores vanilla launch config.
- [ ] Launch with an active loader applies the loader's main class, libraries and args (verified in session log).

## 9. Input

- [ ] Input settings: profile create/duplicate/delete, sensitivity, invert-Y, cursor speed, button opacity.
- [ ] Control layout editor: add/remove/move/resize controls; preview shows the layout.
- [ ] Controller and hardware keyboard detection is logged.
- [ ] Settings persist across restarts.

## 10. Settings, theme & diagnostics

- [ ] Theme (System/Light/Dark) applies immediately and persists.
- [ ] Settings → Diagnostics opens the Diagnostics screen.
- [ ] Diagnostics shows app version, device/hardware facts, launch state (runtime, version, loader, native arch) and log file count.
- [ ] "Export logs" produces a ZIP; "Export diagnostics" adds `diagnostics.json`.
  - Android 10+: ZIP lands in `Downloads/LumoCraft/`.
  - Android 8/9: ZIP is shared through the share sheet.
- [ ] Exported logs have account usernames redacted to `[REDACTED]`.
- [ ] "Clear logs" empties the session logs; "Clear cache" empties the launch cache.
- [ ] Crash capture: force a crash (e.g. an exception in a dev build) and confirm a `crash-<timestamp>.txt` appears under `logs/crashes/` and is included in a later export.

## 11. Update check & About

- [ ] Settings → About shows the installed version.
- [ ] "Check for updates" reports "up to date" when installed version ≥ latest release.
- [ ] With a newer release published, it shows the version and an "Open release page" action.
- [ ] Offline/no-network check fails gracefully ("Update check failed") without blocking anything.
- [ ] The app never auto-downloads updates.

## 12. Release APK

- [ ] `./gradlew assembleRelease` succeeds locally (debug-key fallback) and via the `release.yml` workflow with a tag.
- [ ] The workflow attaches `app-release.apk` + `app-release.apk.sha256` and marks `-rc` tags as pre-releases.
- [ ] `sha256sum -c app-release.apk.sha256` passes on the downloaded APK.
- [ ] Fresh install of the release APK: onboarding → account → install version → install runtime → launch.

---

## Sign-off

| Reviewer | Result (PASS / FAIL) | Notes |
|---|---|---|
|  |  |  |
