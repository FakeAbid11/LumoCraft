# Troubleshooting

## Install fails with a progress error

The app never silently crashes during install: every install/repair
pipeline ends in an error progress event with a message. Common causes:

- **No network / Mojang unreachable** — the pipeline reports the download
  failure; retry later (downloads resume partial files via Range requests).
- **"Flow invariant is violated"** — a bug: progress was emitted from a
  worker dispatcher outside a channel. Since 0.1.0 all pipelines use
  `channelFlow`, so this should not happen; if it does, collect the
  session log (below) and report it.
- **Verification failed** — a downloaded file did not match its SHA-1.
  Use **Repair** on the version; it re-downloads only the affected files.

## A version shows as CORRUPTED / FAILED

- Metadata state is re-read from disk after every pipeline. `FAILED`
  means the last install did not complete; `CORRUPTED` means files or
  metadata do not match expectations (e.g. a newer `installerVersion`).
- Fix: open the version and choose **Repair**.

## Launch fails

The Launch screen shows a typed failure; the mapping is in
docs/launch-pipeline.md:

- **MAIN_CLASS_MISSING** — client jar missing/corrupt; repair the version.
- **JVM_INITIALIZATION_FAILURE** — heap/flag problem; check RAM settings
  in Settings > Java runtime (values are clamped to the device).
- **NATIVE_LIBRARY_MISSING** — LWJGL natives missing/corrupt or the
  wrong architecture; repair the version (re-extracts natives) or
  reinstall the runtime.

## Runtime verification fails

- `RuntimeVerifier` checks binaries, the `release` metadata, a checksum,
  `lib/modules`, `lib/server/libjvm.so` and `jmods/`.
- Fix: **Repair** the runtime (targeted re-download of the broken parts)
  or remove and reinstall it.

## Getting diagnostics

Settings > **Diagnostics**:

- **Open logs** — shares the current session log (FileProvider).
- **Export diagnostics** — a ZIP of session logs, crash files and a
  machine-readable diagnostics JSON (app, hardware, launch facts),
  with usernames and paths redacted. On Android 10+ it lands in
  `Downloads/LumoCraft/`; otherwise it is shared from the app.

Crash files are also written under
`<launcherRoot>/logs/crashes/crash-<timestamp>.txt` by the uncaught-
exception handler without interfering with the app.

## Versioning looks wrong

- `versionName`/`versionCode` are derived (docs/ci.md). Check the About
  screen and the GitHub Actions `verify` job output:
  `printVersionName` must be SemVer; `printVersionCode` a positive int.
- Manual overrides: `LUMOCRAFT_VERSION_NAME` / `LUMOCRAFT_VERSION_CODE`
  environment variables, then git tags, then build metadata.

## CI fails in the verify job

- `testDebugUnitTest` failures: run the tests locally with
  `./gradlew testDebugUnitTest` (needs a JDK 17+ and an Android SDK).
- `lintDebug` failures: fix the reported lint issue.
- Version checks: `printVersionName` must match
  `^v?[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$`.