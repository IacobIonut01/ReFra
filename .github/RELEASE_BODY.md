## What's new in 5.2.1

ReFra 5.2.1 lets Free Up Space run itself on a schedule per cloud account, adds visual search and file renaming to the media viewer, brings monochrome launcher icons, a single lock for every vault, and a large round of fixes across the viewer, cloud providers, categories and locations.

### New Features

- **Scheduled Free Up Space** — Free Up Space can now run automatically on a daily, 3-day or weekly schedule while charging on unmetered networks. Settings are scoped per cloud account, new providers default to a 90-day cutoff, and runs post a notification with the removed count — or a review prompt when silent deletion isn't permitted (#1234)
- **Visual search** — Send the viewed image — or the current video frame — to an external provider like Google Lens straight from the media viewer. Placement is configurable, vault items are opt-out, and formats providers can't decode (RAW, TIFF, JXL…) can be converted first (#1155)
- **Rename in the viewer** — A rename action joins the viewer's quick bottom bar, opening a prefilled bottom sheet, and the edit action swaps to a sliders icon so the two read distinctly (#1082, #966, #952)
- **Monochrome launcher icons** — Two new black-on-white logo options — ReFra Monochrome and Gallery Mono — join the logo picker in settings and setup, with matching splash screens (#1147)
- **Exclude videos from categories** — A categories toggle drops video embeddings before matching and offers to purge existing video entries (#948)
- **One lock for all vaults** — An optional setting lets the satisfied vault-selector gate stand in for per-vault authentication instead of prompting on every entry (#1220)
- **Dark viewer background** — A toggle forces the viewer's dark backdrop even under light theme (#1035)
- **Hide the locations section** — The Library locations carousel can be turned off from Timeline & Albums settings (#1181)
- **Auto-open keyboard on search** — The search field grabs focus once the transition settles, with a Navigation settings switch to turn it off (#1202)

### Improvements

- **Top scrim everywhere** — A persistent scrim sits behind the floating search bar on the Timeline, Albums, Media and Library screens so its actions stay readable over any content (#1182)
- **Honest ignored-album patterns** — The ignored-albums option is now correctly presented as full path matching, with verified examples, a monospace field and a live "albums matched" count

### Bug Fixes

- **Resilient SMB/NFS sessions** — LAN sessions rebuild on transport changes (Wi-Fi ↔ mobile/VPN) and after dead-link reads instead of zombie-ing until the app is killed (#1236, #1237)
- **Vault picker respects the gate** — SelectVaultSheet no longer reveals vault names without satisfying the selector lock, and fails closed when the gate can't be satisfied (#1220)
- **Stable media viewer** — No more dismissal while source data is still loading, and in-viewer navigation exits the overlay correctly — "Show in app" now deep-links into the map centred on that photo (#1069)
- **Smoother viewer gestures** — The grid repositions minimally instead of jumping a full screen on dismiss, the shared-element surrogate stays hidden at rest, and double-tap-drag zoom works again (#960, #1226)
- **Rotate applies to the right file** — The pending-rotation chip resolves the media it was created for, not whichever page the pager settled on (#962)
- **Sharp thumbnails again** — The MediaStore fast path is capped at the platform's real thumbnail size, so wide tiles and large cells decode properly instead of upscaling (#1119)
- **Screen stays awake in the viewer** — keepScreenOn is scoped to the playing page's own surface instead of racing across pager pages (#1067)
- **Cleaner locations** — Media with no usable coordinates no longer pools into an "Unknown Location" group, and the screen hints when media-location permission is missing (#1235)
- **Editing fixes** — Text annotations stay inside the markup canvas, and the crop overlay clamps to image bounds before the gesture-end transform (#979, #956)
- **Honest categories** — Vaulted media no longer inflates category counts, and the scan status, progress and stop action stay in sync with the actual run (#1106, #1224)
- **Daily grouping restored** — Timelines group by day again when the date format follows the system (#1231)
- **People fixes** — Merging "same person" no longer crashes on duplicate face assertions, user-deleted AI models stay uninstalled across restarts, and cached people survive while the face model is still downloading (#1228, #1229)
- **Album move completes cleanly** — No more bogus "source cannot be transferred" error after a successful move, including moves to storage roots (#1222)
- **Reliable splash icon** — The selected launcher icon shows on every launch, including restarts, notification taps and deep links (#1158)
- **Pull-refresh stays visible** — The refresh indicator renders below the floating search bar instead of behind it (#1047)
- **Duplicate actions removed** — The viewer no longer shows two "Develop RAW"/edit actions doing the same thing, and the editor picker no longer crashes when two apps share a label (#1225)
