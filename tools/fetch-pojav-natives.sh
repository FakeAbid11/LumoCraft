#!/usr/bin/env bash
#
# fetch-pojav-natives.sh — vendor PojavLauncher's rendering natives + patched
# LWJGL jars into this project WITHOUT committing the binaries to git.
#
# LumoCraft renders the desktop Minecraft JVM on Android by loading Pojav's
# patched LWJGL (whose GLFW/OpenGL calls are intercepted by libpojavexec and
# translated to GLES via gl4es), instead of Mojang's desktop x86_64 LWJGL.
# Those binaries are GPL-3.0 (see ../THIRD_PARTY.md); this script fetches them
# from a pinned PojavLauncher release so the exact bytes are reproducible.
#
# Output (git-ignored — see app/src/main/jniLibs/.gitignore):
#   app/src/main/jniLibs/<abi>/*.so          native rendering/bridge libs
#   app/src/main/assets/lwjgl/*.jar          patched LWJGL classes jars
#
# Usage:
#   tools/fetch-pojav-natives.sh              # fetch + verify + install
#   POJAV_SKIP_VERIFY=1 tools/fetch-pojav-natives.sh   # first run: print hash
#
set -euo pipefail

# ---- Pinned upstream refs -------------------------------------------------
# PojavLauncher release providing the prebuilt per-ABI natives + LWJGL jars.
POJAV_TAG="gladiolus"
POJAV_APK_URL="https://github.com/PojavLauncherTeam/PojavLauncher/releases/download/${POJAV_TAG}/PojavLauncher.apk"
# SHA-256 of the pinned APK. Leave empty and run once with POJAV_SKIP_VERIFY=1
# to print the hash, then paste it here to lock the pin.
POJAV_APK_SHA256=""

# ABIs LumoCraft ships. Keep in sync with app/build.gradle.kts ndk abiFilters.
ABIS=("arm64-v8a" "armeabi-v7a" "x86_64")

# ---- Paths ----------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
JNILIBS_DIR="${ROOT_DIR}/app/src/main/jniLibs"
ASSETS_LWJGL_DIR="${ROOT_DIR}/app/src/main/assets/lwjgl"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT

echo "==> Fetching PojavLauncher (${POJAV_TAG})"
APK="${WORK_DIR}/pojav.apk"
curl -fL --retry 3 -o "${APK}" "${POJAV_APK_URL}"

# ---- Integrity ------------------------------------------------------------
ACTUAL_SHA="$(sha256sum "${APK}" | awk '{print $1}')"
if [[ "${POJAV_SKIP_VERIFY:-0}" == "1" || -z "${POJAV_APK_SHA256}" ]]; then
  echo "==> APK SHA-256: ${ACTUAL_SHA}"
  echo "    (set POJAV_APK_SHA256 in this script to lock the pin)"
else
  if [[ "${ACTUAL_SHA}" != "${POJAV_APK_SHA256}" ]]; then
    echo "!! SHA-256 mismatch for pinned APK" >&2
    echo "   expected ${POJAV_APK_SHA256}" >&2
    echo "   actual   ${ACTUAL_SHA}" >&2
    exit 1
  fi
  echo "==> APK SHA-256 verified"
fi

# ---- Extract --------------------------------------------------------------
echo "==> Unpacking APK"
unzip -q "${APK}" -d "${WORK_DIR}/apk"

echo "==> Installing native libraries into jniLibs/"
for abi in "${ABIS[@]}"; do
  src="${WORK_DIR}/apk/lib/${abi}"
  dst="${JNILIBS_DIR}/${abi}"
  if [[ ! -d "${src}" ]]; then
    echo "!! ${abi}: no lib/ directory in APK (skipping)" >&2
    continue
  fi
  mkdir -p "${dst}"
  # Copy every rendering/runtime .so Pojav ships for this ABI. The launch
  # env (LaunchArgumentBuilder) points -Dorg.lwjgl.librarypath here.
  find "${src}" -maxdepth 1 -name '*.so' -exec cp -f {} "${dst}/" \;
  echo "   ${abi}: $(ls -1 "${dst}"/*.so 2>/dev/null | wc -l) libraries"
done

echo "==> Installing patched LWJGL jars into assets/lwjgl/"
mkdir -p "${ASSETS_LWJGL_DIR}"
# Pojav bundles its patched LWJGL classes under assets/components/lwjgl3 or
# similar; copy every lwjgl*.jar found in the APK assets.
find "${WORK_DIR}/apk/assets" -name 'lwjgl*.jar' -exec cp -f {} "${ASSETS_LWJGL_DIR}/" \; 2>/dev/null || true
echo "   $(ls -1 "${ASSETS_LWJGL_DIR}"/*.jar 2>/dev/null | wc -l) jars"

echo "==> Done. Vendored natives are git-ignored; rebuild the app to bundle them."
