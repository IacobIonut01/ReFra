## What's new in 5.2.2

ReFra 5.2.2 makes the Library fully yours with editable sections and proper people management, dresses up home-screen photo widgets with black & white and icon styles, plays HDR video for real on compatible displays, and lets you choose whether deletes hit the device, the cloud, or both — plus a hidden developer menu with anonymized session logs and a large round of fixes across vault, cloud providers, widgets and the editor.

### New Features

- **Editable Library sections** — Long-press any Library section header or item to enter edit mode and toggle the sections you want; empty sections hide themselves, and matching switches live under Settings → Timeline & Albums (#1262)
- **Reachable hidden people & person deletion** — The People section keeps an "N hidden" entry so hidden people stay openable, and a person can now be unhidden or deleted for good — their faces are suppressed so re-scanning never brings them back (#1262)
- **Widget display styles** — Photo widgets get a second configuration step with three looks: colour, black & white, or an icon/emoji stand-in with suggestions and a live home-screen preview (#1269)
- **Widgets open the tapped photo** — Tapping a media widget now opens that exact photo in the media viewer instead of just launching the app (#1268)
- **HDR video playback** — Dolby Vision, HDR10 and HLG tracks are detected and played in HDR on compatible displays, gated by a new "HDR video playback" setting; unsupported Dolby Vision profiles are reported honestly in the details sheet (#1274)
- **Delete from device, cloud, or both** — When a selection has cloud copies, the delete dialog lets you choose where it applies, with a "set as my default" option and a "Deleting backed-up items" setting in General (#1241)
- **Sync when app opens** — An optional one-time sync/download pass on every cold start, throttled and wired to your per-account sync policies (#1241)
- **Immich album management** — Albums open fully populated (including shared albums and Immich v3 servers), and owned albums can be renamed, shared, deleted or have members removed straight from the app
- **Developer menu & session logs** — Long-press the version footer in Settings to unlock a diagnostics menu: anonymized session logs with filters, breadcrumbs, crash capture and report/export actions

### Improvements

- **Cloud media sorted by capture date** — WebDAV, Nextcloud, ownCloud, SMB and NFS media now order by the embedded capture time instead of the server's last-modified date (#1277)
- **Persistent thumbnails for heavy formats** — JXL, PSD, JP2, TIFF, RAW and deep-bit-depth HEIF thumbnails are stored on disk instead of being regenerated on every launch (#1276)
- **Frosted surfaces on haze 2.0** — All blur surfaces migrated to haze 2.0 through a single gateway, fixing fully transparent bars on empty grids
- **Viewer honors "Animate media items"** — With animations off, viewers open and dismiss instantly; the exit overlay no longer swallows taps, so tap → back → tap reopens immediately

### Bug Fixes

- **Vault temp files no longer balloon** — All readers share one refcounted decrypted temp per vault file instead of copying it per consumer, and a failed vault add can no longer delete the original (#1282)
- **Trash works on mixed selections** — Local + cloud selections can be trashed again; providers without a bin are marked in the dialog and deleted permanently on confirm instead of hiding the action (#1279)
- **Preset crops commit reliably** — Crop ratios commit against the settled cropper state, so Save Copy no longer stays disabled after picking a preset (#1278)
- **Video details recovered on Android 12** — A wedged isolated parser is bounded and skipped, duration/dimensions are backfilled in-process, and previously failed rows re-collect (#1267)
- **Redacted GPS handled honestly** — Media served with zeroed coordinates shows no bogus "0.000, 0.000" location, and affected rows re-parse once media-location access is granted (#1235)
- **MediaStore request APIs always used** — Trash, delete and favorite route through the system request flow for supported media, fixing silent failures on GrapheneOS/Android 17 (#1263)
- **Immich downloads land in the right folders** — Downloads go to account/album folders instead of mirroring the server's internal store tree, identical local files are linked rather than re-downloaded, and a "Remove downloaded copies" repair action cleans up affected installs (#1270)
- **Immich v3 album support** — Albums on Immich v3 servers load their members via search/metadata since the API no longer embeds them
- **Album covers survive deletions** — Deleting the photo used as an album cover falls back to the newest media instead of rendering a grey thumbnail (#1247)
- **People edits stick** — Merging a person commits before the screen closes, and rescans carry names over via face matching instead of recreating unnamed people
- **Overflowing delete labels shortened** — Permanent-delete actions read "Delete" so the selection bar no longer pushes siblings off screen (#1264)
- **Shortcut customization always reachable** — A dashed "Customize shortcuts" entry appears when every Library shortcut tile is hidden (#1260)
- **Deep-bit-depth AVIF/HEIF decode** — 12-bit AVIF/HEIF files decode through the bundled software decoder instead of rendering corrupted on every surface (#1244)
