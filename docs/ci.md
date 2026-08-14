# CI (GitHub Actions)

Builds are cloud-only: the project never requires Android Studio or local
Gradle builds. Every push and pull request is verified on
`ubuntu-latest`; release tags publish signed APKs.

## build.yml — every push / PR

Two parallel jobs:

1. **build** — `./gradlew assembleDebug` and uploads
   `app-debug.apk` as the `lumocraft-debug-apk` artifact.
2. **verify** — the quality gate:
   - checks required config files exist (`libs.versions.toml`,
     gradle wrapper properties, manifest, baseline profile)
   - validates automated versioning: `printVersionName` must be SemVer,
     `printVersionCode` must be a positive integer
   - `./gradlew testDebugUnitTest` (68 unit tests: versioning, offline
     UUIDs, crash analysis, runtime verification, install pipelines,
     storage, accounts, launch arguments — JVM-pure and Robolectric)
   - `./gradlew lintDebug`

A red `verify` job blocks the merge.

## release.yml — tags `v*` or manual dispatch

1. **Resolve the release tag** — from the pushed tag, or (manual runs)
   from `./gradlew -q printVersionName` (the version name is computed at
   build time, so workflows never parse `build.gradle.kts`). Non-semver
   tags are refused.
2. **Restore the signing keystore** from `ANDROID_KEYSTORE_BASE64` +
   `ANDROID_KEY_ALIAS` / `ANDROID_KEY_PASSWORD` /
   `ANDROID_STORE_PASSWORD`. Without secrets the APK is built with the
   debug key so the workflow still succeeds.
3. **Build** `assembleRelease` (fails if the APK is missing/empty).
4. **Verify integrity**:
   - `unzip -t` on the APK (zip structure intact)
   - `apksigner verify --verbose` (from `$ANDROID_HOME/build-tools`)
   - `sha256sum -c` against the published checksum
5. **Generate release notes** from `git log` since the previous tag
   (with install instructions).
6. **Publish** the GitHub release with APK + `app-release.apk.sha256`
   attached; tags containing `-rc`/`-beta`/`-alpha`/`-snapshot`/
   `-preview` are marked as pre-releases.

## Versioning

- `versionName`: `LUMOCRAFT_VERSION_NAME` env > `GITHUB_REF_NAME` (tag,
  `v` stripped) > `git describe --tags --abbrev=0` > `0.1.0-rc1`
- `versionCode`: `LUMOCRAFT_VERSION_CODE` env > count of git tags whose
  semver is not newer than the current one > `GITHUB_RUN_NUMBER` > 1
- `./gradlew -q printVersionName` / `printVersionCode` print the resolved
  values for other tooling (release workflow, verify job).

## Downloading a build

1. Open the repository on GitHub, go to **Actions**.
2. Open the workflow run (must be green).
3. Download the artifact (**lumocraft-debug-apk** or
   **lumocraft-release-apk**).
4. Allow "install from unknown sources" on the device and install.

See also docs/troubleshooting.md for install failures.