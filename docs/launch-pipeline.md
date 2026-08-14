# Launch pipeline

`LaunchPipeline` is a UI-free contract: one `run()` call that drives a
full game session. `DefaultLaunchPipeline` implements it.

```
DefaultLaunchPipeline.run()
  |
  |-- prepare()                     environment, session log, progress flow
  |
  |-- LaunchValidator.validate()
  |       runtime present + verified?     -> RuntimeVerifier report
  |       version JSON present?           -> versionJsonOk
  |       client jar present?             -> clientJarOk
  |       libraries/assets verified?      -> SmartVerifier cache
  |       actionable failure message      -> validationFailureMessage
  |
  |-- ClientJarManager.ensure()     downloads client-<id>.jar on demand
  |
  |-- ClasspathBuilder.build()
  |       leaf-first walk of the inheritsFrom chain
  |       + loader (Fabric) libraries + client jar + natives dir
  |       (cached per version fingerprint)
  |
  |-- NativeRuntimeManager            extracts device-arch LWJGL natives
  |       (see docs/native-runtime.md)
  |
  |-- LaunchArgumentBuilder           JVM args (heap, LWJGL paths, flags)
  |       + game args (--version, --username, --uuid, --accessToken,
  |         --assetIndex, --assetsDir, --gameDir, --width/height)
  |       + loader (Fabric) args
  |
  |-- JavaLauncher.spawn()            bin/java with the built command
  |       + env: JAVA_HOME, LD_LIBRARY_PATH, LWJGL paths, XDG dirs
  |
  |-- live log streaming              console SharedFlow + session file
  |
  `-- exit handling
        exit code 0        -> success
        exit code != 0     -> CrashAnalyzer.analyze(code, recentLines)
                               -> typed LaunchFailure shown to the user
```

## Progress and cancellation

`run()` returns `Flow<LaunchProgress>` (a `channelFlow`). Every stage
emits `send()` from wherever the work happens (including worker
dispatchers), so the collector on the main thread never sees a flow
invariant violation. Cancelling the collection cancels the whole launch
(the child process is stopped with the coroutine's job).

## Validation report

`LaunchValidationReport` aggregates:

- `runtimeOk` / `runtimeDetail` — runtime exists, binaries executable,
  `release` metadata major version matches, checksum matches, modules and
  `lib/server/libjvm.so` present (from `RuntimeVerifier`)
- `versionJsonOk` — the version JSON parses
- `clientJarOk`, `librariesOk`, `assetsOk`, `nativeLibrariesOk`
- `failureMessage` — a single actionable sentence for the user

`ok` is true only when every check passes.

## Crash analysis

`CrashAnalyzer` matches the tail of the session log (case-insensitive) to
typed failures, most-specific first:

| Pattern | LaunchErrorType |
|---|---|
| could not find/load main class, ClassNotFoundException, no main manifest | MAIN_CLASS_MISSING |
| initialization of VM, invalid heap size, unrecognized option, unable to allocate, OOM, metaspace | JVM_INITIALIZATION_FAILURE |
| UnsatisfiedLinkError, liblwjgl, GLFW, failed to create window, X11 issues, no suitable device | NATIVE_LIBRARY_MISSING |
| anything else | GAME_CRASHED |

The last 40 lines of the log are included as `detail`; the full session
log stays on disk (docs/storage-layout.md).

## Offline identity

`OfflineUuid` computes `UUID.nameUUIDFromBytes("OfflinePlayer:<name>")`
(the classic MD5, RFC 4122 v3 form), so the same username always maps to
the same UUID and world data follows the username.