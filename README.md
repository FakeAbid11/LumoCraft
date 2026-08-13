# LumoCraft

LumoCraft is an original Android launcher for Minecraft: Java Edition — a
lightweight, from-scratch launcher aimed at low-end devices, supporting local
offline profiles. It is **not** a fork or clone of PojavLauncher.

## Status: Phase 11 — Release Candidate `v0.1.0-rc1`

Phase 11 ships the first release candidate. It adds everything a public
release needs on top of the performance engine: a crash reporter that
persists uncaught exceptions under the launcher log area, a **Diagnostics**
screen (Settings → Diagnostics) that exports session logs and a full
hardware/software snapshot as a shareable ZIP, an **About** section with a
manual update check against the GitHub release channel, a **first-launch
onboarding wizard**, and a **GitHub Actions release workflow** that builds,
signs and publishes release APKs with SHA-256 checksums.

| Area | Current | Later |
|---|---|---|
| UI | Compose Home / Accounts / Versions / Settings screens; full-screen Launch console; Performance dashboard; **Diagnostics screen**; **first-launch onboarding**; **About + update check** | Touch controls, renderer overlay |
| Accounts | Offline accounts: create/select/rename/delete, deterministic pixel avatars | Microsoft sign-in |
| Versions | Manifest browser + filters + search, full vanilla install, install-state tracking, live progress, repair | Fabric/Forge |
| Runtime | Java runtime manager: install/verify/repair/remove, arch detection, RAM sliders, JVM args, cached verification | Runtime selection UI |
| Settings | Theme, Java runtime section, Renderer section (profile, resolution, FPS, VSync, native status), Performance dashboard entry, **Diagnostics entry**, **About section** | Java args, download options, storage picker |
| Native | **NativeRuntimeManager**: per-arch LWJGL extraction (stamps/cache, dedup), verification, JNI env (`java.library.path`, `org.lwjgl.librarypath`), arch-mismatch rejection | Renderer glue (GLFW stub) |
| Performance | **Launch cache + smart verification** (fingerprint-gated, hash-free repeats), **device profiling** (RAM/cores/tier), **smart JVM profiles** (auto or manual, heap-clamped), **launch profiler** (history on disk), **memory pool**, **adaptive + resumable downloads**, **Performance dashboard** | Sodium, shader-aware settings |
| Loader | **Fabric loader**: install/repair/remove via generic `LoaderRepository` abstraction, loader-aware classpath + args | Forge, Quilt |
| Input | **Input framework**: profiles, sensitivity/invert-Y, virtual mouse, control layout editor + preview, controller + hardware keyboard detection | Renderer-bound overlays |
| Reliability | **Crash reporter** (uncaught-exception files), **log/diagnostics export** (redacted ZIP via share sheet or Downloads), session logs + FileProvider | Automatic crash upload |
| Updates | **Manual update check** against GitHub releases (read-only — never auto-downloads) | In-app auto-update |
| Release | **Release workflow**: signed release APK + SHA-256 published to GitHub Releases from tags | Play store, MSIX |

## Tech stack

- Kotlin 2.1.x, Jetpack Compose (Material 3), single activity
- Navigation Compose, feature-first package layout
- Gradle 8.13 + Kotlin DSL + version catalog (`gradle/libs.versions.toml`)
- Android builds run on GitHub Actions (see below); the dev machine is low-end

## Dependencies

Kept minimal and androidx-only (all justified):

| Dependency | Why |
|---|---|
| `androidx.core:core-ktx` | Kotlin extensions for the Android framework |
| `androidx.activity:activity-compose` | `setContent` + edge-to-edge support |
| `androidx.lifecycle:lifecycle-runtime-ktx` | Lifecycle-aware coroutine runtime |
| `androidx.compose.*` (via BOM) | UI toolkit, Material 3, tooling preview |
| `androidx.navigation:navigation-compose` | Screen-to-screen navigation |
| `org.apache.commons:commons-compress` | Streaming tar.gz extraction for Java runtime archives |

No DI framework, no network library, no image loader. HTTP uses the
platform `HttpURLConnection` and JSON uses `org.json` (plus `java.time` for
timestamps).

## Project structure

```
LumoCraft/
├── .github/
│   ├── ISSUE_TEMPLATE/           # bug report + feature request templates
│   ├── PULL_REQUEST_TEMPLATE.md
│   └── workflows/
│       ├── build.yml             # CI: builds and uploads the debug APK
│       └── release.yml           # publishes signed release APK on tags
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/lumocraft/app/
│       │   ├── MainActivity.kt           # single activity, Compose entry
│       │   ├── LumoCraftApp.kt           # root composable: theme + nav scaffold + onboarding gate
│       │   ├── LumoCraftApplication.kt   # manual DI: app-wide repositories
│       │   ├── core/
│       │   │   ├── config/               # AppConfig: endpoints, timeouts, installer version
│       │   │   ├── theme/                # Material 3 theme + brand colors
│       │   │   └── version/              # VersionManager: SemVer parsing/comparison
│       │   ├── domain/
│       │   │   ├── account/              # OfflineAccount, AccountRepository, validator
│       │   │   ├── avatar/               # deterministic pixel avatar generator
│       │   │   ├── version/              # manifest models, InstallProgress/Stage, VersionRepository
│       │   │   ├── runtime/              # RuntimeInfo, RuntimeRepository, JvmConfiguration
│       │   │   ├── launch/               # LaunchContext, LaunchPipeline, LaunchProgress,
│       │   │   │                         #   LaunchFailure/LaunchErrorType, OfflineUuid
│       │   │   ├── native/               # NativeRuntimeManager, NativeStatus/NativeReport,
│       │   │   │                         #   RendererProfile (presets + resolution), InputCompat
│       │   │   ├── loader/               # LoaderType, LoaderMetadata, LoaderInstance,
│       │   │   │                         #   LoaderRepository, LoaderLaunchConfigurator
│       │   │   ├── performance/          # DeviceProfile/Tier, JvmProfile, CacheManager,
│       │   │   │                         #   SmartVerifier, LaunchProfiler, MemoryOptimizer,
│       │   │   │                         #   PerformanceManager
│       │   │   ├── update/               # UpdateRepository, UpdateStatus, ReleaseInfo
│       │   │   └── model/                # ThemeMode
│       │   ├── data/
│       │   │   ├── account/              # SharedPreferences account repository
│       │   │   ├── network/              # HttpClient, Downloader (retry + progress + resume),
│       │   │   │                         #   HashUtils, ThroughputTracker, DownloadScheduler
│       │   │   ├── storage/              # StorageManager: .minecraft layout + metadata
│       │   │   ├── preferences/          # theme, version, renderer, onboarding stores
│       │   │   ├── runtime/              # RuntimeInstaller, ArchiveExtractor, RuntimeVerifier,
│       │   │   │                         #   DefaultRuntimeRepository
│       │   │   ├── version/              # ManifestService, VersionInstaller, LibraryInstaller,
│       │   │   │                         #   AssetInstaller, VerificationService, DownloadTracker
│       │   │   ├── performance/          # AndroidDeviceProfiler, JsonCacheManager,
│       │   │   │                         #   ChecksumCache, SmartVerifierImpl, RuntimeCache,
│       │   │   │                         #   LaunchProfilerImpl, MemoryOptimizerImpl,
│       │   │   │                         #   PerformancePreference, Fingerprints,
│       │   │   │                         #   DefaultPerformanceManager
│       │   │   ├── launch/               # ClasspathBuilder, LaunchArgumentBuilder,
│       │   │   │                         #   ClientJarManager, LaunchValidator, JavaLauncher,
│       │   │   │                         #   CrashAnalyzer, LauncherLogRepository,
│       │   │   │                         #   DefaultLaunchPipeline
│       │   │   ├── loader/               # FabricMetadataService, FabricInstaller,
│       │   │   │                         #   FabricLaunchConfigurator, DefaultLoaderRepository
│       │   │   ├── native/               # NativeLibraryManager, NativeExtractionService
│       │   │   │                         #   (stamps/cache), NativeVerificationService,
│       │   │   │                         #   DefaultNativeRuntimeManager
│       │   │   ├── export/               # CrashLogHandler (uncaught-exception writer),
│       │   │   │                         #   CrashReportExporter (ZIP + Downloads + FileProvider)
│       │   │   └── update/               # GithubUpdateRepository (release-channel checks)
│       │   ├── navigation/               # LumoDestination enum + AppNavHost (+ launch route)
│       │   └── ui/
│       │       ├── components/           # Navigation bar, small shared widgets
│       │       └── home|accounts|settings|versions|launch|performance|diagnostics|onboarding/   # feature screens
│       └── res/                          # strings, platform theme, adaptive icon, FileProvider paths
├── docs/
│   └── testing-checklist.md             # manual test matrix for each release
├── gradle/
│   ├── libs.versions.toml               # version catalog
│   └── wrapper/                         # Gradle 8.13 wrapper
├── gradlew / gradlew.bat
├── settings.gradle.kts
└── build.gradle.kts
```

## Architecture

- **Single activity.** Screens are composables routed through `AppNavHost`.
- **Feature-first packages.** Each feature owns its screen (`ui/<feature>`);
  shared models live in `domain`, persistence in `data`.
- **Stores are swappable.** `AppThemePreference` and `AccountRepository`
  (interface) hide their backends behind small classes.
- **Accounts are the launch identity.** Future stages read the selected
  account through `AccountRepository`.
- **Deterministic avatars.** `AvatarGenerator` derives both the color palette
  and the mirrored pixel pattern from the SHA-256 digest of the username.
- **Versions are a repository.** `VersionRepository` hides manifest fetching,
  installation and install-state tracking behind one interface.
- **Install pipeline.** `VersionInstaller` orchestrates an 8-stage pipeline
  (Preparing → Version JSON → Libraries → Asset Index → Assets → Logging
  Config → Verification → Complete).
- **Parallel downloads.** `LibraryInstaller` and `AssetInstaller` download
  in parallel with adaptive concurrency (device tier + measured
  bandwidth), resume partial files via Range requests, and verify SHA-1 +
  size before renaming into place.
- **Memory-efficient hashing.** `HashUtils.sha1` streams files in small
  buffers, optionally reusing a pooled buffer from `MemoryOptimizer`.
- **Runtime manager.** `RuntimeRepository` exposes runtime detection,
  installation, verification, selection, removal, metadata, RAM config and
  JVM argument generation. Phase 6 will call `getDefaultRuntime()` to obtain
  a fully verified runtime for launch — the repository re-verifies the
  default runtime before returning it. The first installed runtime is
  automatically selected as default.
- **Runtime verification.** `RuntimeVerifier` checks required binaries
  (`bin/java`, `bin/javac`, `bin/keytool`), executable permissions, the
  `release` metadata file version, and a recorded SHA-256 checksum of the
  `release` file. Detailed `RuntimeVerificationReport` lists missing and
  corrupt files so repair can target specific failures.
- **Streaming extraction.** `ArchiveExtractor` streams tar.gz/zip entries
  entry-by-entry, rejects path traversal, preserves executable permissions,
  and never loads archives into RAM.
- **Typed metadata.** `runtime/metadata.json` stores a `runtimes` array with
  id, version, arch, vendor, path, installedAt, isDefault, status, and an
  optional SHA-256 checksum. JVM configuration lives in a separate
  `jvm_config.json` next to it.
- **Launch pipeline.** `LaunchPipeline` is a small UI-free interface
  (StateFlow progress + log stream) implemented by `DefaultLaunchPipeline`:
  environment prep → on-demand client jar download → validation → classpath
  resolution (leaf-first `inheritsFrom` chain) → native extraction →
  JVM/game argument building → `bin/java` process spawn → live log
  streaming → crash analysis. Later phases (Fabric, Forge, renderer glue,
  custom args) swap or extend this implementation without touching the UI.
- **Offline identity.** `OfflineUuid` derives the standard
  `OfflinePlayer:<name>` MD5 UUID (RFC 4122 v3, no dashes), so the same
  username maps to the same UUID everywhere.
- **Natives.** `NativeExtractor` flattens only the device's architecture
  subdirectory (`linux/arm64`, `linux/arm32`, root for x86_64) out of the
  multi-arch LWJGL jars into the version's `natives` directory; LWJGL
  self-extracts the rest on first use.
- **Session logs.** Every launch writes `logs/launcher-<timestamp>.log`; the
  same lines stream to the Launch console through a replaying
  `SharedFlow`. Logs are shared via a FileProvider (`*.logs` authority)
  from the "Open logs" action. Phase 7 adds structured sections for
  native extraction, renderer selection, JNI paths, architecture and
  resolution.
- **Native runtime manager.** `NativeRuntimeManager` is the small,
  UI-free contract for native compatibility: locate native jars
  (classifier libraries across the `inheritsFrom` chain), extract them
  per architecture into `versions/<id>/natives/<arch>/`, verify them and
  expose the JNI environment. Later phases (Fabric, Forge, Sodium,
  OptiFine, custom renderers) add native surfaces without touching this
  interface.
- **Cached extraction.** `NativeExtractionService` writes a stamp
  (`.stamp-<arch>.json`) listing each jar's contributed files and sizes;
  intact stamps skip re-extraction entirely and damaged ones re-extract
  only the affected jars. Multi-arch LWJGL jars are read once per
  architecture; duplicate file names across jars keep the first
  contributor and are counted.
- **Native verification.** `NativeVerificationService` checks every
  recorded file exists with its expected size, that the stamp
  architecture matches the device (arch mismatch → corrupted) and
  reports missing/corrupt lists so the pipeline can reject the launch
  with an actionable message.
- **Renderer profiles.** `RendererProfile` holds renderer type
  (Compatibility/Performance/Experimental presets), resolution scale
  (50/75/100%), FPS limit, VSync and mipmaps; it is persisted via
  `RendererPreference` and injected at launch as `-Dlumocraft.*` JVM
  flags plus scaled `resolution_width/height` game args — consumed by
  the renderer glue in a later phase.
- **Input foundation.** `InputCompat` defines `TouchMapper`,
  `VirtualMouseController`, `KeyboardHandler` and `ControllerHandler`
  plus an `InputEngine` registry; everything stays null until the
  touch-controls phase fills it in.
- **Performance manager.** `PerformanceManager` is the single entry point
  for launcher-side optimization: device profiling (RAM, cores, Android
  version, low-RAM flag → LOW/MEDIUM/HIGH tier), JVM profile selection
  (Auto = device-derived, or a manual Battery Saver / Balanced /
  Performance override, heap-clamped to the device ceiling), the launch
  cache, smart verification, launch history and the memory optimizer.
  Later phases (Fabric, Forge, Sodium, shaders) read the device profile
  without probing hardware again.
- **Launch cache.** `JsonCacheManager` persists one row per version
  (`cache/launch_cache.json`): resolved classpath, launch arguments,
  verified libraries/assets and runtime-validation markers. Rows are keyed
  by a `size:lastModified` fingerprint of the version JSON chain, so
  unchanged versions never rebuild anything; the classpath builder, the
  pipeline and the verifier each merge their own data into the same row.
- **Smart verification.** `SmartVerifier` trusts a fingerprint-matching
  cache row outright for Home-screen readiness checks (no disk scan);
  launch-time checks stat every library/asset by size only, and hashing
  happens solely for files whose size changed or that lack a cached
  checksum (`cache/checksums.json`, keyed by path+size+mtime). The
  installer invalidates rows exactly when a version's files change.
- **Resumable, adaptive downloads.** `HttpClient.downloadResumable`
  resumes partial files via HTTP Range requests (HTTP 200 or 416 restarts
  cleanly); `ThroughputTracker` measures bandwidth over a sliding window
  and `DownloadScheduler` sheds concurrency when per-connection
  throughput falls below 128 KB/s.
- **Launch profiler.** Every launch records validation/classpath/JVM
  start/total timings, cache hits/misses and success into
  `cache/launch_history.json` (capped at 10) for the dashboard.
- **Memory optimizer.** A small pooled byte-buffer store (max 8 buffers,
  256 KB) serves hot hashing/download I/O and is emptied after each
  launch so RAM returns to the game process.
- **Runtime cache.** `RuntimeCache` skips re-verifying an unchanged
  runtime within a 5-minute window (id + path + release checksum), so
  repeated readiness checks and launches avoid rescanning.
- **Semantic versions.** `VersionManager` parses, compares and displays
  SemVer 2.0.0 values (`0.1.0-rc1`), used by the update checker and the
  About screen.
- **Update checks.** `UpdateRepository` is a read-only contract; the
  GitHub-backed implementation fetches the `releases/latest` payload,
  extracts the newest version + APK link and compares with
  `VersionManager`. Checks are manual and never download anything.
- **Crash reporting.** `CrashLogHandler` is registered as the default
  uncaught-exception handler; it appends a timestamped crash file under
  `<launcherRoot>/logs/crashes/` (never throwing itself) and delegates to
  the previous handler, so the process behaves exactly as before.
- **Diagnostics export.** `CrashReportExporter` packages session logs,
  crash files and (optionally) a machine-readable diagnostics JSON
  (app/hardware/launch facts) into a ZIP. Personal data is handled
  defensively: usernames are redacted line-by-line before export. On
  Android 10+ the archive lands in public `Downloads/LumoCraft/` via
  `MediaStore`; on Android 8/9 it is written to app-specific storage and
  shared through the `*.exports` FileProvider authority.
- **Onboarding.** A five-page first-launch wizard is gated in
  `LumoCraftApp`; completion is persisted in `OnboardingPreference` and
  it can be skipped at any time (nothing is forced).
- **Release signing.** `app/build.gradle.kts` reads keystore paths and
  passwords from environment variables; when they are absent (local dev
  or fork builds) the release APK falls back to the debug keystore so
  builds never break.

## Building locally

Prerequisites: JDK 17+ and an Android SDK (Android Studio includes both).
The SDK location goes into `local.properties` (gitignored):

```
sdk.dir=C\:\\path\\to\\Android\\sdk
```

Then:

```
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

For most work, push to GitHub and let CI build — see below.

## CI (GitHub Actions)

### Debug builds

`.github/workflows/build.yml` runs on every push and pull request:

1. Checks out the repository
2. Installs Temurin JDK 17
3. Installs the Android SDK command-line tools (`android-actions/setup-android`)
4. Sets up Gradle (with dependency caching)
5. Runs `./gradlew assembleDebug --no-daemon` — the job **fails** if the
   project does not compile
6. Uploads `app-debug.apk` as a downloadable artifact (`lumocraft-debug-apk`)

### Releases

`.github/workflows/release.yml` publishes signed release APKs. It runs on
pushed tags matching `v*` (e.g. `v0.1.0-rc1`) or manually from the Actions
tab (manual runs derive the tag from `versionName` in `app/build.gradle.kts`):

1. Resolves the release tag
2. Restores the signing keystore from the `ANDROID_KEYSTORE_BASE64` secret
   (plus `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`,
   `ANDROID_STORE_PASSWORD`) — without secrets the APK is built with the
   debug key so the workflow still succeeds
3. Runs `./gradlew assembleRelease --no-daemon`
4. Computes `app-release.apk.sha256`
5. Uploads the APK + checksum as a workflow artifact
6. Creates a GitHub release with the APK + checksum attached; releases
   whose tag contains `-rc`/`-beta`/`-alpha`/`-snapshot`/`-preview` are
   marked as pre-releases

### Downloading the APK from GitHub Actions

1. Open the repository on GitHub and go to the **Actions** tab
2. Select the workflow run for your latest push (it must be green)
3. Scroll to the **Artifacts** section
4. Download **lumocraft-debug-apk** — a zip containing `app-debug.apk`
5. Allow "install from unknown sources" on the device and install

## Roadmap

Completed:
- **Offline accounts** — stable UUID per profile, persisted profile list,
  select/rename/delete, deterministic avatars
- **Version manifests & vanilla installer** — Mojang manifest browser with
  local search + type filters, pull-to-refresh, details sheet, install of
  version JSON + folder structure + persisted install states
- **Libraries & assets installer** — full on-disk preparation: version JSON,
  metadata, official libraries, official asset index + objects, logging
  configuration, 8-stage progress pipeline, verification pass, repair mode
- **Android Java Runtime Manager** — runtime detection, installation
  (Java 17/21), verification, selection, removal, metadata, RAM
  configuration, JVM argument generation, architecture detection
- **Launch pipeline** — Home readiness gating, version picker, client jar
  fetch, classpath + JVM/game args, native extraction, Java process spawn,
  live console, crash analysis, offline UUID, session logs + FileProvider
- **Android Renderer & Native Compatibility** — per-arch LWJGL native
  extraction with stamps and dedup, native verification and arch-mismatch
  rejection, JNI environment injection (`java.library.path`,
  `org.lwjgl.librarypath`), renderer profiles with persisted settings UI,
  resolution scaling, input compatibility foundation
- **Performance Engine & Smart Optimization** — launch cache + smart
  verification (fingerprint-gated, hash-free repeats), device profiling
  with automatic JVM profile selection (manual override supported),
  launch history dashboard, adaptive + resumable downloads, memory pool,
  runtime verification cache, structured performance logging
- **Fabric loader support** — generic `LoaderRepository` abstraction with
  Fabric metadata + installer, repair/removal, loader-aware classpath and
  JVM/game arguments, launch configuration logging
- **Input framework** — profiles with sensitivity/invert-Y, virtual mouse,
  control layout editor + preview, controller and hardware keyboard
  detection, input logging
- **Release Candidate `v0.1.0-rc1`** — crash reporter, Diagnostics screen
  with redacted log/diagnostics export, About + manual update check,
  first-launch onboarding, signed release APK via the release workflow

Next phases, in order:

1. **Renderer glue** — GLFW stub + touchscreen input so the game window
   actually renders on Android
2. **Settings expansion** — game memory slider, download options, storage
   location picker
3. **Storage management** — per-directory size display and cleanup
4. Later phases: Microsoft auth, Forge, mods, resource packs, touch
   controls, shaders