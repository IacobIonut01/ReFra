#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ] || [ ! -f "$1" ]; then
    echo "Usage: $0 path/to/app.apk|app.aab" >&2
    exit 2
fi

ARTIFACT="$1"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ] && [ -f "$REPO_ROOT/local.properties" ]; then
    SDK="$(grep -E '^sdk\.dir=' "$REPO_ROOT/local.properties" | head -n1 | cut -d'=' -f2-)"
fi
if [ -z "$SDK" ] || [ ! -d "$SDK" ]; then
    echo "ERROR: Android SDK not found. Set ANDROID_HOME or sdk.dir in local.properties." >&2
    exit 1
fi
NDK_VERSION="$(sed -n 's/^refra\.ndkVersion=//p' "$REPO_ROOT/gradle.properties" | head -n1)"
READELF="$(ls -d "$SDK/ndk/$NDK_VERSION"/toolchains/llvm/prebuilt/*/bin/llvm-readelf 2>/dev/null | head -n1)"
ZIPALIGN="$SDK/build-tools/37.0.0/zipalign"
if [ ! -x "$READELF" ]; then
    echo "ERROR: llvm-readelf was not found in the configured Android NDK." >&2
    exit 1
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
case "$ARTIFACT" in
    *.apk)
        if [ ! -x "$ZIPALIGN" ]; then
            echo "ERROR: zipalign 37.0.0 was not found in the configured Android SDK." >&2
            exit 1
        fi
        "$ZIPALIGN" -c -P 16 4 "$ARTIFACT"
        unzip -qq "$ARTIFACT" 'lib/*/*.so' -d "$WORK"
        LIB_ROOT="$WORK/lib"
        ;;
    *.aab)
        unzip -qq "$ARTIFACT" 'base/lib/*/*.so' -d "$WORK"
        LIB_ROOT="$WORK/base/lib"
        ;;
    *)
        echo "ERROR: Expected an APK or Android App Bundle." >&2
        exit 2
        ;;
esac

shopt -s nullglob
LIBRARIES=("$LIB_ROOT"/arm64-v8a/*.so "$LIB_ROOT"/x86_64/*.so)
if [ "${#LIBRARIES[@]}" -eq 0 ]; then
    echo "ERROR: The artifact contains no arm64-v8a or x86_64 native libraries." >&2
    exit 1
fi

FAILED=0
for LIBRARY in "${LIBRARIES[@]}"; do
    LOAD_COUNT=0
    LIBRARY_FAILED=0
    while read -r ALIGN; do
        LOAD_COUNT=$((LOAD_COUNT + 1))
        if (( ALIGN < 0x4000 )); then
            echo "UNALIGNED: ${LIBRARY#"$WORK"/} ($ALIGN)" >&2
            FAILED=1
            LIBRARY_FAILED=1
        fi
    done < <("$READELF" -lW "$LIBRARY" | awk '$1 == "LOAD" {print $NF}')
    if [ "$LOAD_COUNT" -eq 0 ]; then
        echo "INVALID ELF: ${LIBRARY#"$WORK"/}" >&2
        FAILED=1
    elif [ "$LIBRARY_FAILED" -eq 0 ]; then
        echo "ALIGNED: ${LIBRARY#"$WORK"/}"
    fi
done

if [ "$FAILED" -ne 0 ]; then
    exit 1
fi
