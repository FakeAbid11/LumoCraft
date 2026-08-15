# Third-party components & licenses

LumoCraft is distributed under the **GNU General Public License v3.0** (see
[`LICENSE`](LICENSE)). It incorporates and/or depends on the following
third-party components. This choice of license is driven by the PojavLauncher
rendering stack, which is GPLv3.

## Bundled / integrated at runtime

| Component | Purpose | Upstream | License | Pinned ref |
|-----------|---------|----------|---------|-----------|
| PojavLauncher (patched LWJGL, GLFW stub, `pojavexec` bridge) | OpenGL/GLFW → Android surface bridge that lets the desktop Minecraft JVM render on Android | https://github.com/PojavLauncherTeam/PojavLauncher | GPL-3.0 | release tag `gladiolus` (see `tools/fetch-pojav-natives.sh`) |
| gl4es | OpenGL 1.x/2.x → OpenGL ES 2.0 translation | https://github.com/ptitSeb/gl4es | MIT | bundled in the pinned PojavLauncher `gladiolus` build |
| Android/Bionic OpenJDK 21 (ZalithLauncher build) | Java 21 runtime for Minecraft 1.20.5+ (Bionic-linked) | https://github.com/Vera-Firefly/ZalithLauncher | GPL-3.0 | commit `606aa07` (see `core/config/AppConfig.kt`) |
| Android/Bionic OpenJDK (multiarch) | The Java runtime the game runs on (Bionic-linked so it can be `dlopen`'d on Android) | https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch | GPL-2.0 with Classpath Exception | `jre17-ec28559` (see `core/config/AppConfig.kt`) |

The exact PojavLauncher / gl4es release tags used to build the vendored
`app/src/main/jniLibs/**/*.so` binaries are recorded in
`tools/fetch-pojav-natives.sh`. Those binaries are fetched at setup time and
kept out of git (see `app/src/main/jniLibs/.gitignore`).

## Build/library dependencies

Standard AndroidX / Jetpack Compose / Kotlin libraries (Apache-2.0), Apache
Commons Compress (Apache-2.0), and XZ for Java (public domain) as declared in
`gradle/libs.versions.toml`.

## Minecraft

Minecraft is a trademark of Mojang AB. LumoCraft is an unofficial, third-party
launcher and is not affiliated with, endorsed by, or associated with Mojang AB
or Microsoft. Users must own a valid copy of the game; LumoCraft does not
distribute Minecraft game files.
