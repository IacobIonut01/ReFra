package com.dot.gallery.editor.ui

/**
 * Where preview frames are produced. [Auto] tries the platform
 * runtime-shader backend (Android AGSL API 33+, Skia RuntimeEffect on
 * desktop — the actual raster device is Skiko's choice) and falls back to
 * the CPU pipeline when unavailable or failed. [Cpu] always renders the
 * preview through the reference CPU renderer; exports are unaffected —
 * saving a copy is always the CPU strip pipeline either way.
 */
enum class PhotoRenderPreference { Auto, Cpu }
