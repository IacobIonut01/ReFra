# PanoramaViewer

A lightweight Jetpack Compose panorama and photosphere viewer for Android, powered by OpenGL ES 2.0.

## Features

- **Sphere projection** — full 360° × 180° equirectangular photospheres
- **Cylinder projection** — wide panoramas with automatic arc and height from aspect ratio
- **Progressive loading** — low-res base texture displayed instantly, high-res detail decoded on demand via `BitmapRegionDecoder`
- **Blurred background** — tiny texture stretched fullscreen behind the geometry for a polished look
- **Touch gestures** — drag to rotate, pinch to zoom, inertia/fling
- **Gyroscope input** — optional device-motion rotation for photospheres
- **Tap detection** — single-tap callback for toggling surrounding UI
- **Camera state** — real-time yaw/pitch/fov callbacks for compass overlays
- **Pager-safe** — gesture disambiguation allows coexistence with `HorizontalPager`
- **Memory efficient** — never loads the full-resolution image into memory

## Quick Start

```kotlin
PanoramaViewer(
    imageUri       = mediaUri,
    projectionType = ProjectionType.CYLINDER,
    gyroscopeEnabled = false,
    onTap          = { toggleUi() },
    onCameraChanged = { state -> updateCompass(state) },
    modifier       = Modifier.fillMaxSize()
)
```

## Architecture

```
PanoramaViewer (Compose entry-point)
  └─ PanoramaGLSurfaceView (Android View — touch, sensors, detail orchestration)
       ├─ PanoramaRenderer (GL thread — geometry, shaders, textures)
       ├─ RegionLoader (background thread — BitmapRegionDecoder)
       └─ SphereGeometry (mesh generation)
```

## Public API

| Symbol | Description |
|---|---|
| `PanoramaViewer` | Composable entry-point |
| `ProjectionType` | `SPHERE` or `CYLINDER` enum |
| `CameraState` | Data class with yaw, pitch, fov, arcDegrees, projectionType |
| `PanoramaImageLoader` | Interface for custom image loading (encrypted, network, etc.) |
| `PanoramaLog` | Toggleable debug logger (`PanoramaLog.enabled = true`) |

All other classes are `internal` and not part of the public API.

## Custom Image Loader

By default, `PanoramaViewer` loads images from a content URI using Android's
`BitmapRegionDecoder`. To load images from other sources — encrypted vault files,
network streams, custom archives — implement the `PanoramaImageLoader` interface
and pass it via the `imageLoader` parameter.

### Interface

```kotlin
interface PanoramaImageLoader : Closeable {
    val imageWidth: Int
    val imageHeight: Int

    fun initialize(): Boolean
    fun loadBase(maxDimension: Int): Bitmap?
    fun loadRegion(left: Int, top: Int, right: Int, bottom: Int, maxDimension: Int): Bitmap?
    fun close()
}
```

### Lifecycle

All methods are called on a dedicated background thread. The library manages the
lifecycle automatically:

1. **`initialize()`** — Called once. Read dimensions, set `imageWidth`/`imageHeight`,
   and prepare the decoder. Return `false` if region decoding is not possible
   (the library will try `loadBase` as a full-image fallback).
2. **`loadBase(maxDimension)`** — Called to obtain a low-res texture for the full
   geometry. Downsample so neither dimension exceeds `maxDimension`.
3. **`loadRegion(...)`** — Called repeatedly as the user pans/zooms. Decode only
   the requested rectangle at high resolution.
4. **`close()`** — Called when the viewer is disposed. Release all resources.

### Example: Encrypted Vault Loader

```kotlin
class EncryptedPanoramaImageLoader(
    private val keychainHolder: KeychainHolder,
    private val encryptedFile: File
) : PanoramaImageLoader {

    private var decoder: BitmapRegionDecoder? = null
    private var decryptedBytes: ByteArray? = null

    override var imageWidth: Int = 0; private set
    override var imageHeight: Int = 0; private set

    override fun initialize(): Boolean {
        // Decrypt once, then use the raw bytes for all decoding
        val media = with(keychainHolder) {
            encryptedFile.decryptKotlin<EncryptedMedia>()
        }
        decryptedBytes = media.bytes

        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(media.bytes, 0, media.bytes.size, opts)
        imageWidth = opts.outWidth
        imageHeight = opts.outHeight

        decoder = BitmapRegionDecoder.newInstance(media.bytes, 0, media.bytes.size)
        return decoder != null
    }

    override fun loadBase(maxDimension: Int): Bitmap? {
        var sampleSize = 1
        while (imageWidth / sampleSize > maxDimension || imageHeight / sampleSize > maxDimension)
            sampleSize *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return decoder?.decodeRegion(Rect(0, 0, imageWidth, imageHeight), opts)
    }

    override fun loadRegion(left: Int, top: Int, right: Int, bottom: Int, maxDimension: Int): Bitmap? {
        val w = right - left; val h = bottom - top
        var sampleSize = 1
        while (w / sampleSize > maxDimension || h / sampleSize > maxDimension)
            sampleSize *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return decoder?.decodeRegion(Rect(left, top, right, bottom), opts)
    }

    override fun close() {
        decoder?.recycle(); decoder = null; decryptedBytes = null
    }
}
```

### Using a Custom Loader in Compose

```kotlin
val loader = remember(media.id) {
    EncryptedPanoramaImageLoader(keychainHolder, encryptedFile)
}
PanoramaViewer(
    imageUri       = Uri.EMPTY,          // ignored when imageLoader is set
    projectionType = ProjectionType.SPHERE,
    imageLoader    = loader,
    onTap          = { toggleUi() },
    modifier       = Modifier.fillMaxSize()
)
```

## Image Loading Strategy

1. **Background texture** (≈32 px) — loaded first, stretched fullscreen with GL bilinear filtering for a natural blur effect behind the geometry.
2. **Base texture** (≈1024 px) — decoded and mapped onto the full geometry surface.
3. **Detail texture** (up to 4096 px) — only the visible viewport region is decoded at high resolution and overlaid on the base. Refreshed as the user pans or zooms.

## Configuration

Tuning constants are defined in the companion objects of `PanoramaRenderer` and `PanoramaGLSurfaceView`:

| Constant | Default | Description |
|---|---|---|
| `DEFAULT_FOV` | 95° | Initial field of view |
| `MIN_FOV` / `MAX_FOV` | 30° / 110° | Zoom limits |
| `TOUCH_SENSITIVITY` | 0.13 | Drag-to-rotate speed multiplier |
| `INERTIA_FRICTION` | 0.92 | Fling decay per frame |
| `DETAIL_RELOAD_THRESHOLD` | 3° | Camera movement before detail refresh |
| `BASE_TEXTURE_SIZE` | 1024 px | Max dimension for the base texture |
| `DETAIL_TEXTURE_SIZE` | 4096 px | Max dimension for the detail texture |
| `BG_TEXTURE_SIZE` | 32 px | Background blur texture size |

## Requirements

- Android API 30+
- OpenGL ES 2.0
- Jetpack Compose

## License

```
Copyright 2026 IacobIacob01
SPDX-License-Identifier: Apache-2.0
```
