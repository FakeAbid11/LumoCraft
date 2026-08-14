# Native runtime (LWJGL)

Minecraft's libraries ship multi-architecture native code in classifier
jars (`natives-linux`, `natives-macos`, ...). LumoCraft extracts only the
device's architecture into the version's `natives` directory and injects
the JNI environment at launch.

## Architecture mapping

| Device ABI (Build.SUPPORTED_ABIS) | Extraction subdirectory |
|---|---|
| arm64-v8a | `linux/arm64` (fallback `linux-arm64`) |
| armeabi-v7a | `linux/arm32` (fallback `linux-arm32`) |
| x86_64 | root of the jar (`x86_64`, fallback `linux`) |
| anything else | rejected as unsupported |

`NativeLibraryManager` only accepts libraries whose classifier contains
`natives` (so e.g. `-sources` or `-javadoc` jars are never treated as
native code).

## Extraction flow

```
NativeRuntimeManager.ensure(nativeLibraries)
   |
   |-- NativeLibraryManager.select     classifiers filtered to *natives*
   |
   |-- NativeExtractionService.extract
   |       for each jar (inheritsFrom chain, leaf-first):
   |         open jar once per architecture
   |         flatten only the matching subdirectory (see table above)
   |         write files to versions/<id>/natives/<arch>/
   |         record every contributed file + size in a stamp file
   |         duplicate file names keep the first contributor
   |       write .stamp-<arch>.json
   |
   |-- NativeVerificationService.verify
   |       stamp architecture matches device?       else corrupted
   |       every recorded file exists with size?    else corrupted
   |       source jars present for every stamp entry? else missing
   `-- report: ok | missing list | corrupt list
```

## Stamps and caching

- An intact `.stamp-<arch>.json` skips re-extraction entirely.
- A damaged stamp re-extracts **only** the affected jars.
- Stamps are written atomically after extraction completes.

## JNI environment at launch

`NativeRuntimeManager` exposes:

- `java.library.path` — the extracted natives directory
- `org.lwjgl.librarypath` — same, for LWJGL's own loader
- `-Dorg.lwjgl.glfw.libname`, `-Dorg.lwjgl.opengl.libname` as needed

LWJGL self-extracts any remaining natives (its own bundles) on first use.

## Failure mode

If native verification fails, the launch pipeline rejects the launch with
`NATIVE_LIBRARY_MISSING` and an actionable message (see
docs/launch-pipeline.md). Repairing the version re-runs the extraction.