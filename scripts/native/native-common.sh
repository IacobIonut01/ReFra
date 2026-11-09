#!/usr/bin/env bash

native_sha256() {
    # GNU coreutils prefixes the output with a backslash when the file name itself contains
    # one (Windows paths passed in through the environment), so drop that escape marker.
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}' | sed 's/^\\//'
    else
        shasum -a 256 "$1" | awk '{print $1}'
    fi
}

native_normalize_archives() {
    local strip_bin
    strip_bin="$(ls -d "$NDK_DIR"/toolchains/llvm/prebuilt/*/bin/llvm-strip 2>/dev/null | head -n1)"
    if [ -z "$strip_bin" ] || [ ! -x "$strip_bin" ]; then
        echo "ERROR: NDK llvm-strip not found under $NDK_DIR" >&2
        exit 1
    fi
    "$strip_bin" -S "$@"
}

native_prepare_source() {
    local env_name="$1"
    local key="$2"
    local destination="$3"
    local url="$4"
    local expected_sha="$5"
    local sentinel="$6"
    # Optional tar --exclude pattern. zxing-cpp ships writer-only symlinks that Windows tar
    # cannot create; a reader-only build never needs them.
    local exclude="${7:-}"
    local -a tar_extra=()
    if [ -n "$exclude" ]; then
        tar_extra=(--exclude="$exclude")
    fi
    local source_dir
    source_dir="$(printenv "$env_name" 2>/dev/null || true)"
    if [ -z "$source_dir" ] && [ -n "${NATIVE_SOURCES_DIR:-}" ] && [ -d "$NATIVE_SOURCES_DIR/$key" ]; then
        source_dir="$NATIVE_SOURCES_DIR/$key"
    fi

    rm -rf "$destination"
    mkdir -p "$destination"
    if [ -n "$source_dir" ]; then
        if [ ! -d "$source_dir" ]; then
            echo "ERROR: $env_name does not point to a directory: $source_dir" >&2
            exit 1
        fi
        cp -R "$source_dir"/. "$destination"/
        rm -rf "$destination/.git"
    else
        local archive download_dir download actual_sha
        if [ -n "${NATIVE_SOURCE_ARCHIVES_DIR:-}" ]; then
            archive="$NATIVE_SOURCE_ARCHIVES_DIR/${url##*/}"
        else
            download_dir="${NATIVE_DOWNLOAD_CACHE:-$WORK/downloads}"
            archive="$download_dir/$key-$expected_sha.tar.gz"
            mkdir -p "$download_dir"
        fi
        if [ ! -f "$archive" ] || [ "$(native_sha256 "$archive")" != "$expected_sha" ]; then
            case "${NATIVE_OFFLINE:-0}" in
                1|true|TRUE|yes|YES)
                    echo "ERROR: Missing or invalid offline source archive for $key: $archive" >&2
                    exit 1
                    ;;
            esac
            if [ -n "${NATIVE_SOURCE_ARCHIVES_DIR:-}" ]; then
                echo "ERROR: Invalid vendored source archive for $key: $archive" >&2
                exit 1
            fi
            download="$(mktemp "$download_dir/.$key-$expected_sha.XXXXXX")"
            echo "==> Downloading $key"
            if ! curl -fsSL "$url" -o "$download"; then
                rm -f "$download"
                exit 1
            fi
            actual_sha="$(native_sha256 "$download")"
            if [ "$actual_sha" != "$expected_sha" ]; then
                rm -f "$download"
                echo "ERROR: SHA-256 mismatch for $key: expected $expected_sha, got $actual_sha" >&2
                exit 1
            fi
            mv -f "$download" "$archive"
        fi
        actual_sha="$(native_sha256 "$archive")"
        if [ "$actual_sha" != "$expected_sha" ]; then
            echo "ERROR: SHA-256 mismatch for $key: expected $expected_sha, got $actual_sha" >&2
            exit 1
        fi
        # GNU tar reads a Windows drive path ("D:/...") as a remote host spec; keep it local.
        # bsdtar (macOS) neither needs nor accepts --force-local.
        if tar --version 2>/dev/null | grep -q "GNU tar"; then
            tar --force-local ${tar_extra[@]+"${tar_extra[@]}"} -xzf "$archive" --strip-components=1 -C "$destination"
        else
            tar ${tar_extra[@]+"${tar_extra[@]}"} -xzf "$archive" --strip-components=1 -C "$destination"
        fi
    fi

    if [ ! -e "$destination/$sentinel" ]; then
        echo "ERROR: Invalid $key source; missing $sentinel in $destination" >&2
        exit 1
    fi
}

# Reads a local.properties entry and prints it in a shell-friendly form.
# Android Studio writes java.util.Properties escapes into that file (for example
# "sdk.dir=D\:\\Android"), so backslash escapes are removed and Windows separators are
# normalised to '/' for the bash tooling.
native_read_property() {
    local file="$1"
    local key="$2"
    local line value out="" char i=0
    [ -f "$file" ] || return 1
    line="$(grep -E "^${key//./\\.}[[:space:]]*=" "$file" | head -n1)" || return 1
    [ -n "$line" ] || return 1
    value="${line#*=}"
    while [ "$i" -lt "${#value}" ]; do
        char="${value:$i:1}"
        if [ "$char" = '\' ] && [ "$((i + 1))" -lt "${#value}" ]; then
            i=$((i + 1))
            char="${value:$i:1}"
        fi
        out="$out$char"
        i=$((i + 1))
    done
    printf '%s' "$out" | tr '\\' '/'
}

native_set_reproducible_env() {
    export LC_ALL=C
    export TZ=UTC
    umask 022
    if [ -z "${SOURCE_DATE_EPOCH:-}" ]; then
        SOURCE_DATE_EPOCH="$(git -C "$REPO_ROOT" log -1 --format=%ct 2>/dev/null || printf '1')"
        export SOURCE_DATE_EPOCH
    fi
    local work_real ndk_real native_path_root
    work_real="$(cd "$WORK" && pwd -P)"
    ndk_real="$(cd "$NDK_DIR" && pwd -P)"
    NATIVE_REPRO_FLAGS="-ffile-prefix-map=$WORK=/usr/src/refra-native -ffile-prefix-map=$work_real=/usr/src/refra-native -ffile-prefix-map=$NDK_DIR=/opt/android-ndk -ffile-prefix-map=$ndk_real=/opt/android-ndk"
    # Windows drive paths (C:/...) contain a ':' that the NDK launcher reads as a path-list
    # separator, corrupting -ffile-prefix-map. Reproducible remapping is a Unix-only guarantee,
    # so drop the flags rather than passing values the compiler cannot parse.
    for native_path_root in "$WORK" "$NDK_DIR"; do
        case "$native_path_root" in
            [A-Za-z]:*) NATIVE_REPRO_FLAGS="" ;;
        esac
    done
    export NATIVE_REPRO_FLAGS
}
