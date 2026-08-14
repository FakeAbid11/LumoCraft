# Storage layout

All game data lives under `<launcherRoot>` = `getExternalFilesDir(null)/.minecraft`
(named `.lumocraft` internally where versions of the layout changed).

```
<launcherRoot>/
├── versions/
│   └── <id>/
│       ├── <id>.json                  version JSON (Mojang)
│       ├── <id>-client.txt            logging config
│       ├── <id>.jar                   client jar (downloaded on demand)
│       ├── natives/
│       │   └── <arch>/                extracted LWJGL natives
│       │       .stamp-<arch>.json     extraction stamp
│       └── metadata.json              InstalledVersionMetadata:
│                                     version, installedAt, source,
│                                     installerVersion, state
│                                     (PENDING/INSTALLED/FAILED/CORRUPTED)
├── libraries/
│   └── <group>/<artifact>/<version>/  Mojang layout, one file per library
│       *.jar, *.pom, *-natives-<os>.jar
├── assets/
│   ├── indexes/
│   │   └── <id>.json                  asset index
│   └── objects/
│       └── <sha1-prefix>/
│           └── <sha1>                 asset objects (SHA-1 named)
├── runtimes/
│   ├── java17/  java21/               extracted Temurin runtimes
│   │   ├── bin/ lib/ jmods/ release
│   ├── metadata.json                  runtime list (id, arch, isDefault,
│   │                                 status, sha256, ...)
│   └── jvm_config.json                RAM + JVM argument settings
├── loaders/
│   └── <type>/<instanceId>/           Fabric (and later) instances:
│       ├── <type>.json                loader metadata
│       ├── libraries/                 loader libraries
│       └── metadata.json              LoaderMetadata
├── logs/
│   ├── launcher-<timestamp>.log       session logs
│   └── crashes/
│       └── crash-<timestamp>.txt      uncaught-exception reports
├── cache/
│   ├── launch_cache.json              per-version launch rows keyed by
│   │                                 fingerprint (classpath, args,
│   │                                 verified markers)
│   ├── checksums.json                 path+size+mtime -> sha1 cache
│   └── launch_history.json            last 10 launches (dashboard)
└── settings/
    └── ...                            launcher preferences (JSON files)
```

## Metadata semantics

- `InstalledVersionMetadata.state` is re-read from disk after every
  pipeline (install, repair, cancel) — the UI never trusts memory.
- `installerVersion` guards against layout changes: a higher
  `installerVersion` in the app than in a metadata file marks the version
  as `CORRUPTED` (needs repair).

## Caches and invalidation

- `cache/launch_cache.json` rows are keyed by a `size:lastModified`
  fingerprint of the version JSON chain; unchanged versions reuse the row.
- `cache/checksums.json` entries are keyed by `path+size+mtime`; files are
  only re-hashed when size or mtime changed.
- The installer, loader and runtime flows invalidate cache rows exactly
  when the corresponding files change (`onFilesChanged` hooks).

## Sharing

- Session logs and crash files are shared through the `*.logs` FileProvider
  authority (Settings > Diagnostics > Open logs).
- The diagnostics export ZIP (session logs + crash files + diagnostics
  JSON) lands in public `Downloads/LumoCraft/` on Android 10+ via
  MediaStore, or is shared through the `*.exports` authority on Android 8/9.