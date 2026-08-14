# Java runtime

`RuntimeRepository` manages the embedded Java runtimes:

- `install(runtimeId)` — download a Temurin archive for the detected
  architecture, extract it, verify it, and (for the first runtime) make it
  the default
- `verify(runtimeId)` — full verification on demand
- `repair(runtimeId)` — re-download and re-extract only what is missing or
  corrupt (targeted by the verification report)
- `remove(runtimeId)` — delete the runtime directory and promote another
  installed runtime to default if the removed one was default
- `setDefault(runtimeId)` — switch the default runtime

## Installation flow

```
RuntimeRepository.install(id)          (channelFlow -> RuntimeProgress)
   |
   |-- detect architecture (Build.SUPPORTED_ABIS)
   |-- build Temurin URL for (version, arch)
   |-- RuntimeInstaller.install()
   |       download archive (resumable, sha256 verified)
   |       ArchiveExtractor streams tar.gz/zip entry-by-entry
   |         (path-traversal rejected, exec bits preserved)
   |       merge into runtimes/<id>/ (safe rename at the end)
   |-- RuntimeVerifier.verify()
   |-- write metadata + mark default when needed
   `-- re-read runtimes from disk (state flow refresh)
```

## Verification

`RuntimeVerifier` checks:

- `bin/java`, `bin/javac`, `bin/keytool` exist and are executable
- `release` metadata file exists and declares `JAVA_VERSION="<major>"`
- SHA-256 checksum of the `release` file matches the one recorded at
  install time (mismatch reported in `checksumDetail`)
- `lib/modules` exists
- `lib/server/libjvm.so` exists
- `jmods/` is non-empty
- root layout consistency (`bin/` and `lib/` directories at the runtime
  root)

`RuntimeVerificationReport.ok` requires every check to pass; it also lists
`missingFiles` and `corruptFiles` so `repair` can be targeted.

`RuntimeCache` skips re-verifying an unchanged runtime within a 5-minute
window (id + path + release checksum), so readiness checks and launches
are cheap.

## JVM configuration

`jvm_config.json` next to the runtime metadata stores the RAM settings and
JVM arguments. `JvmConfiguration` clamps heap values to the device ceiling
(device profiling in the performance engine) and generates the `-Xmx`,
`-Xms` and `-XX:` flags used by the launch pipeline.

## Layout

```
<launcherRoot>/runtimes/
  java17/                      bin/ java javac keytool ...
                               lib/ modules, server/libjvm.so, ...
                               jmods/ ...
                               release
  java21/                      ...
  metadata.json                { "runtimes": [ { id, version, arch,
                                 vendor, path, installedAt, isDefault,
                                 status, sha256 } ] }
  jvm_config.json              { heap settings, JVM args }
```

See docs/storage-layout.md for the full tree.