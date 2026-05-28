## What's new in 4.3.0

### New Features

- **Story Cards** — A new horizontal carousel above the timeline surfaces highlights from your recent photos, albums, categories, locations, and favorites. Tap any card to open an immersive full-screen story viewer with auto-advancing slides and progress indicators. Fully configurable: toggle card types, reorder them, and adjust viewer timing in Settings > Timeline & albums > Story cards.
- **Redesigned Video Controls** — Video control buttons (subtitle, speed, volume, rotate) have been consolidated into a single "more options" button that expands into a blurred animated popup with scale+fade transitions. Uses row layout in landscape, column in portrait.
- **Video Auto-Contrast** — Auto-contrast and adaptive surfaceContainer colors now extend to the video options popup, matching the viewer's luminance-based theme.

### Performance

- **Faster Cold Startup** — Cold startup optimized from ~900 ms to ~350 ms through lazy Keystore SecretKey caching, deferred database passphrase validation, async media distribution loading, and eager permission checks.

### Improvements

- Improved shared element transitions: single stable registration per media item, switched from sharedBounds to sharedElement, Long-based keys for more accurate photo-to-viewer animations
- Loading shimmer now shows during timeline startup instead of a blank screen

### Bug Fixes

- Fixed move/copy failing for albums outside Pictures/DCIM when app has MANAGE_MEDIA permission (#875)
- Fixed forced screen orientation not resetting when leaving video player (#880)
- Fixed status bar icons not syncing with followTheme logic in media viewer
- Fixed ClassCastException when viewing media from lock screen after unlock (StandaloneActivity) (#877)
- Fixed grid realignment glitch on back navigation caused by animated sticky header offset
- Fixed empty-media/loading overlapping above-grid content; hidden collection card when albums list is empty

