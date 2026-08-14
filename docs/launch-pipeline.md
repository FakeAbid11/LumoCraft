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
  |-- NativeJvmLauncher.start()       in-process JVM, no exec
  |       dlopen(<runtime>/lib/jli/libjli.so)
  |       JLI_Launch on a native thread
  |       stdout/stderr piped into the session log
  |       + env: JAVA_HOME, LD_LIBRARY_PATH, LWJGL paths, XDG dirs
  |
  |-- live log streaming              console SharedFlow + session file
  |
  `-- exit handling
        exit code 0        -> success
        exit code != 0     -> CrashAnalyzer.analyze(code, recentLines)
                               -> typed LaunchFailure shown to the user
```

## Native JVM launcher (noexec)

Android mounts app-writable directories with `noexec`, so executing the
extracted runtime's `bin/java` always fails with `error=13 (Permission
denied)` regardless of file permissions — `chmod` cannot fix a mount
option. Shared libraries are **not** affected by `noexec`, so the JVM is
loaded in-process through JNI instead:

1. `NativeJvmLauncher` resolves `<runtime>/lib/jli/libjli.so`
   (fallbacks: flat `lib/libjli.so` for Microsoft-style builds,
   `lib/<arch>/libjli.so` for legacy JDK 8 layouts) and
   preflights `lib/server/libjvm.so`, `lib/modules` and `lib/jvm.cfg`
   plus a runtime/device architecture match.
2. The native library (app/src/main/cpp, built by CMake) `dlopen`s
   `libjli.so`, resolves `JLI_Launch` (the JDK 9+ launcher entry point)
   and redirects fd 1/2 into a pipe Kotlin created via `Os.pipe()`.
3. `JLI_Launch` runs on a dedicated pthread with the full command line
   `[java, -D..., -cp ..., MainClass, game args]`. JLI derives the JRE
   home from `libjli.so`'s own location (`dladdr`) and loads
   `lib/server/libjvm.so` itself; `JAVA_HOME` and a correctly prefixed
   `LD_LIBRARY_PATH` keep it from trying to re-exec.
4. When the game exits, `JLI_Launch` returns the exit code, the process
   output is restored and the pipe reaches EOF; the pipeline streams
   those lines into the session log and reports the code as before.
5. Cancellation asks the game JVM to exit through its own JNI invocation
   API (`JNI_GetCreatedJavaVMs` + `System.exit(0)`), so shutdown hooks
   run instead of killing the process.

Every step (library path, `dlopen` result, symbol resolution, exit code)
is logged to logcat and the session log; missing libraries, missing
symbols or an architecture mismatch produce a typed, user-friendly
`LaunchException` instead of a crash.

## Threading, timeouts and heartbeats

The session runs on `Dispatchers.Default`, never on the Android main
thread (the original ANR: the pipeline ran on a `Dispatchers.Main
immediate` scope, and the unbounded native handshake — `pthread_join`
inside `exitCode()`, no timeouts anywhere — could stall the UI for
minutes). All file/JSON/network steps use an explicit IO dispatcher and
every native call is bounded or polled:

- `NativeJvmLauncher.start()` (dlopen + handshake) is wrapped in
  `withTimeoutOrNull(NATIVE_START_TIMEOUT_MS)` (30 s) → a stall reports
  `JVM_START_TIMEOUT` instead of an ANR.
- `JvmProcessHandle.stream()` separates JVM startup from JVM lifetime:
  the first 30 s are the bounded startup window. A `JVM_START_HEARTBEAT
  elapsed=Ns` line is logged every second so liveness is visible. If the
  JLI thread is still running after the window with no output at all,
  the JVM is declared wedged and the session stops it
  (`JVM_START_TIMEOUT`, sentinel `K_START_TIMEOUT = -7`).
- Once started, the game runs until exit — unbounded in total, but
  polled in 250 ms cancellable slices (`waitForExit` never joins, so a
  wedged JVM can never block the session from being stopped).
- `STOPPING` is a real state: after `cancel()` the pipeline waits
  `CANCEL_GRACE_MS` (5 s) for the JLI thread to finish; if it does not,
  the JVM is wedged and the user must restart the app.
- `recycleLaunch()` reaps the finished JLI thread and resets the native
  launch state so the next session can start (a no-op while the JVM
  still runs).

Native exit-code sentinels: `kOk = 0`, launch errors `-1..-5`,
`kExitTimeout = -6` (thread still running). Every phase of argument
resolution logs `ARGUMENTS_*` markers with elapsed milliseconds.

## Runtime viability caveat

The bundled Temurin JRE is a glibc build (from api.adoptium.net), but
Android's bionic cannot resolve glibc sonames (`libc.so.6`, `libm.so.6`,
...). The in-process launcher therefore only works with a bionic-linked
JRE (e.g. PojavLauncher's lwjgl-bridge JRE builds) or when a glibc
userspace is bundled into `LD_LIBRARY_PATH`. With the current runtime the
JVM reports `JVM_START_TIMEOUT` or a launcher error at startup; the
in-process architecture is otherwise proven viable on Android.

## Progress and cancellation

`run()` returns `Flow<LaunchProgress>` (a `channelFlow`). Every stage
emits `send()` from wherever the work happens (including worker
dispatchers), so the collector on the main thread never sees a flow
invariant violation. Cancelling the collection cancels the whole launch;
the in-process JVM is then stopped gracefully through
`JvmProcessHandle.cancel()` (see above). Both `cancel()` and
`recycle()` run under `NonCancellable` so they complete even inside a
cancelled coroutine.

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