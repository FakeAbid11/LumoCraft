# LumoCraft

LumoCraft is an original Android launcher for Minecraft: Java Edition — a
lightweight, from-scratch launcher aimed at low-end devices, supporting local
offline profiles. It is **not** a fork or clone of PojavLauncher.

## Status: Phase 6 — Launch Pipeline

Phase 6 connects every existing system: the Home screen now validates launch
readiness (account, runtime, version, libraries, assets), downloads the
client jar on demand, builds the JVM + game arguments for vanilla Minecraft
1.20.1, extracts native libraries, spawns `bin/java`, streams the session log
to a live console and analyzes crashes.

| Area | Current | Later |
|---|---|---|
| UI | Compose Home / Accounts / Versions / Settings screens (bottom navigation); full-screen Launch console (stages, live log, cancel/retry/open logs) | Touch controls, renderer overlay |
| Accounts | Offline accounts: create/select/rename/delete, deterministic pixel avatars, SharedPreferences persistence | Microsoft sign-in |
| Versions | Manifest browser (All/Release/Snapshot/Old Beta/Old Alpha filters + search), full vanilla install (version JSON, libraries, asset index, asset objects, logging config), install-state tracking, live progress, repair mode | Fabric/Forge |
| Runtime | Java runtime manager: install (Java 17/21), verify, repair, remove, architecture detection, RAM sliders, JVM args, metadata | Runtime selection UI |
| Settings | Theme (system/light/dark, persisted), Java runtime section | Java args, download options, storage picker |
| Launching | Launch pipeline: validation, client jar fetch, classpath + JVM/game args, native extraction, process spawn, log streaming, crash analysis, offline UUID | Renderer glue (GLFW stub), Microsoft auth |

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
├── .github/workflows/build.yml   # CI: builds and uploads the debug APK
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/lumocraft/app/
│       │   ├── MainActivity.kt           # single activity, Compose entry
│       │   ├── LumoCraftApp.kt           # root composable: theme + nav scaffold
│       │   ├── LumoCraftApplication.kt   # manual DI: app-wide repositories
│       │   ├── core/
│       │   │   ├── config/               # AppConfig: endpoints, timeouts, installer version
│       │   │   └── theme/                # Material 3 theme + brand colors
│       │   ├── domain/
│       │   │   ├── account/              # OfflineAccount, AccountRepository, validator
│       │   │   ├── avatar/               # deterministic pixel avatar generator
│       │   │   ├── version/              # manifest models, InstallProgress/Stage, VersionRepository
│       │   │   ├── runtime/              # RuntimeInfo, RuntimeRepository, JvmConfiguration
│       │   │   ├── launch/               # LaunchContext, LaunchPipeline, LaunchProgress,
│       │   │   │                         #   LaunchFailure/LaunchErrorType, OfflineUuid
│       │   │   └── model/                # ThemeMode
│       │   ├── data/
│       │   │   ├── account/              # SharedPreferences account repository
│       │   │   ├── network/              # HttpClient, Downloader (retry + progress), HashUtils
│       │   │   ├── storage/              # StorageManager: .minecraft layout + metadata
│       │   │   ├── preferences/          # theme + version preference stores
│       │   │   ├── runtime/              # RuntimeInstaller, ArchiveExtractor, RuntimeVerifier,
│       │   │   │                         #   DefaultRuntimeRepository
│       │   │   ├── version/              # ManifestService, VersionInstaller, LibraryInstaller,
│       │   │   │                         #   AssetInstaller, VerificationService, DownloadTracker
│       │   │   └── launch/               # ClasspathBuilder, LaunchArgumentBuilder,
│       │   │                             #   NativeExtractor, ClientJarManager, LaunchValidator,
│       │   │                             #   JavaLauncher, CrashAnalyzer, LauncherLogRepository,
│       │   │                             #   DefaultLaunchPipeline
│       │   ├── navigation/               # LumoDestination enum + AppNavHost (+ launch route)
│       │   └── ui/
│       │       ├── components/           # Navigation bar, small shared widgets
│       │       └── home|accounts|settings|versions|launch/   # feature screens
│       └── res/                          # strings, platform theme, adaptive icon
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
- **Parallel downloads.** `LibraryInstaller` and `AssetInstaller` download in
  parallel with a fixed concurrency limit, verify SHA-1 + size before
  renaming into place.
- **Memory-efficient hashing.** `HashUtils.sha1` streams files in 16 KB
  buffers.
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
  from the "Open logs" action.

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

`.github/workflows/build.yml` runs on every push and pull request:

1. Checks out the repository
2. Installs Temurin JDK 17
3. Installs the Android SDK command-line tools (`android-actions/setup-android`)
4. Sets up Gradle (with dependency caching)
5. Runs `./gradlew assembleDebug --no-daemon` — the job **fails** if the
   project does not compile
6. Uploads `app-debug.apk` as a downloadable artifact (`lumocraft-debug-apk`)

### Pushing to GitHub

The repository already contains a local `main` branch (no commits yet —
create the initial commit first):

```
git add .
git commit -m "Phase 5: Android Java Runtime Manager"
git remote add origin https://github.com/<your-username>/LumoCraft.git
git push -u origin main
```

(The remote URL must be a repository you created on GitHub. A private repo is
fine for development.)

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

Next phases, in order:

1. **Renderer glue** — GLFW stub + touchscreen input so the game window
   actually renders on Android
2. **Settings expansion** — game memory slider, download options, storage
   location picker
3. **Storage management** — per-directory size display and cleanup
4. Later phases: Microsoft auth, Fabric/Forge, mods, resource packs, touch
   controls, shaders