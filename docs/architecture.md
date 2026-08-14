# Architecture

LumoCraft is a single-activity Android app. Screens are Compose composables
routed through `AppNavHost`; all state behind them flows through small
repository interfaces in `domain/`, with concrete implementations in
`data/`. There is no DI framework: `LumoCraftApplication` wires everything
by hand.

## Layers

```
ui/          Compose screens + ViewModels (no logic beyond state mapping)
navigation/  Destination enum + NavHost
domain/      Public contracts: repositories, models, pure rules
data/        Implementations: storage, network, installers, verifiers
core/        Shared helpers (config, theme, versioning)
```

Rules of the codebase:

- The UI only ever sees `domain` interfaces (`VersionRepository`,
  `RuntimeRepository`, `LoaderRepository`, `LaunchPipeline`,
  `AccountRepository`, ...). Data-layer classes are not referenced from
  screens.
- Repositories are the single entry point for their feature: they combine
  network, disk and installers behind one contract.
- Install pipelines return a cold `Flow` of progress events. The pipeline
  implementations use `channelFlow` so progress can be emitted from worker
  dispatchers (see docs/launch-pipeline.md).
- Nothing is forced on the user: every screen can be skipped, every state
  is re-read from disk when a pipeline finishes.

## Flow: installing a version

```
VersionViewModel
   |
   |  repository.install(version)     (cold Flow<InstallProgress>)
   v
DefaultVersionRepository.pipeline     (channelFlow)
   |
   |  installer.install(version, onProgress)
   v
VersionInstaller.runPipeline          (withContext(Dispatchers.IO))
   |
   |-- downloadVersionJson      -> versions/<id>/<id>.json
   |-- installLibraries         -> libraries/<path>        (parallel, resumable)
   |-- installAssetIndex        -> assets/indexes/<id>.json
   |-- installAssets            -> assets/objects/<sha1>   (parallel, resumable)
   |-- installLoggingConfig     -> versions/<id>/<id>-client.txt
   |-- verify                   -> VerificationService (files + sizes)
   `-- writeMetadata            -> versions/<id>/metadata.json
```

Every `onProgress` call is a `send()` on the channel; the collector (a
ViewModel on `Dispatchers.Main`) receives it safely from any thread.
A `finally` block re-reads install states from disk so the UI always shows
`PENDING / FAILED / CORRUPTED / INSTALLED` truthfully, even after a
cancellation or a crash mid-install.

## Flow: launching the game

```
HomeScreen -> LaunchViewModel -> DefaultLaunchPipeline.run()
   |
   |-- validate runtime + version files (LaunchValidator)
   |-- on-demand client jar download (ClientJarManager)
   |-- classpath resolution (leaf-first inheritsFrom chain)
   |-- native extraction (NativeRuntimeManager)
   |-- JVM + game argument building (LaunchArgumentBuilder)
   |-- spawn bin/java process (JavaLauncher)
   |-- stream stdout/stderr to the console + session log
   `-- on non-zero exit: CrashAnalyzer maps the log tail to a typed failure
```

Details: docs/launch-pipeline.md

## Concurrency and dispatchers

- UI and ViewModels: `Dispatchers.Main`
- Installers and file I/O: `Dispatchers.IO`
- Downloads: `Downloader` with a `DownloadScheduler` that adapts
  concurrency to measured throughput; partial files resume via HTTP Range
- Progress crossing the dispatcher boundary: only through `channelFlow`
  channels or `MutableStateFlow` — never through `emit` from a
  `withContext` block (that violates the flow invariant).

## Persistence

See docs/storage-layout.md for the on-disk layout.