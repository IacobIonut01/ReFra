# Visual Search (Google Lens & providers) — Plan (Issue #1155)

> Status: **planning only — not yet implemented.**

## Goal

One-tap "search this image" action in the media viewer that hands the current
image — or the current video frame — to a visual-search provider (Google Lens or
any other installed app that accepts image shares), exactly like OEM stock
galleries do.

- Issue: https://github.com/IacobIonut01/ReFra/issues/1155
- Intent contract: `ACTION_SEND` + `EXTRA_STREAM` + `image/*` to
  `com.google.ar.lens` (Lens has registered this share target since v1.1). Other
  apps (Google app, Bing, Yandex, Pinterest, ChatGPT, Gemini…) expose the same
  generic share target; there is **no** dedicated visual-search intent, so
  "provider" == an installed app that accepts an image share.

## Requirements (from issue + user direction)

1. **Opt-out setting**, default ON. Effective visibility also requires ≥1
   installed provider — "enabled by default if Google Lens or other providers
   are available".
2. **Provider switching in settings**: a picker listing available providers —
   curated known visual-search apps first (friendly names/branding), then any
   other installed app accepting `ACTION_SEND image/*`.
3. **Configurable placement**, either:
   - **Top**: inside the back button's pill, to the right of the back arrow
     (the pill becomes `[back | search]`), or
   - **Bottom**: inside the quick-actions pill (`MediaViewQuickBottomBar`).
   Default placement: **top** (per user).
4. **Icon**: Google Lens selected → the Lens **monochrome** glyph, tinted by the
   viewer's content color. Any other provider → generic AI-search icon
   (`Icons.Outlined.ImageSearch`, material-icons-extended is already a dep).
5. **Media scope**: images (all sources) + **video current frame**. Frame
   extraction is async + cancellable with progress UI (some sources can't be
   instant — cloud downloads, vault decrypt, seekable-frame decode).
6. Long-press: nothing extra — standard tooltip/content-description only;
   provider switching happens in settings.
7. **Vault media**: included, but **configurable** — a "secure vault" switch in
   the visual-search settings (default ON; shares through the same
   decrypt-to-temp path as Share). When OFF, encrypted/vault media hides the
   button entirely.
8. **Exotic formats** (RAW, TIFF, PSD, JP2, JXL, SVG, DNG… — anything a Lens-
   style target plausibly can't decode): sent **as-is by default**, with a
   configurable policy in settings — `ask` (default) / `always` / `never` —
   plus a preferred output format (`jpeg` default / `png` / `webp`). In `ask`
   mode, the first share of an exotic image pops a bottom sheet with these
   same options (convert+format / send original / remember choice).

## Current state (what exists today)

- `MediaViewScreen` (`mediaview/MediaViewScreen.kt`, ~2.8k lines): top chrome =
  `MediaViewAppBar`; bottom chrome = `MediaViewQuickBottomBar` hosted inside the
  info `BottomSheet` at `imageOnlyDetent`.
- `MediaViewAppBar` (`mediaview/components/MediaViewAppBar.kt`): back button is a
  solo circular pill at `Alignment.CenterStart` (`clip(CircleShape)` +
  `backgroundModifier` + `hazeEffectScaled`); a `castButton` slot already
  demonstrates injecting an extra `IconButton` into the **end** pill. A
  `tertiaryContainer` "rotate in progress" chip below the bar shows how a busy
  pill with spinner + label is built.
- `MediaViewQuickBottomBar`: `Row` of `MediaViewButton`s (48 dp circle, tooltip,
  `imageVector` + tint via `ColorFilter.tint`) gated by
  `viewerActionCapabilities(...)`.
- `MediaActionCapabilityPolicy` (`mediaview/MediaActionCapabilityPolicy.kt`):
  immutable, platform-free capability matrix, JVM-tested
  (`MediaActionCapabilityPolicyTest`). `share = exportAllowed = !cloudLocked`.
- Share path: `Context.shareMedia` → `resolveShareableUri(media)`
  (`util/ImageUtils.kt`) — MediaStore content URI passthrough, FileProvider for
  files, **downloads cloud originals to a cache file**, encrypted media via
  `createDecryptedTempFile` (`shareEncryptedMedia`).
- **Video frame extraction already exists** (`frameextract/` package):
  - `FrameSourceMaterializer` — local/document/cloud/vault → local file, with
    `onProgress: (Int?)` callback, `ensureActive()` cancellation, session-file
    cleanup (`FrameSourceCleanup` sweeps >24 h). LOCAL seekable videos are
    `DIRECT` (no copy).
  - `FrameDecoderSession` — `prepare()` → `resolveInitial(preferredTimeUs,
    initialPositionMs)` → `decodeFullResolution(identity)` → `Bitmap` (rotation-
    and HDR-normalized).
  - `FrameSourceSpec.from(media, metadata, currentVault, …, initialPositionMs)`.
- Current playback position plumbing already exists: `VideoFramePickerControllerRef`
  (`position: MutableLongState?`, `player: ExoPlayer?`) published per-frame by the
  current page's video overlay (`SideEffect` at ~line 2009), consumed by
  `openFramePicker` (~line 1036). The Lens action reuses this ref.
- Settings: `SettingsMediaViewerScreen` uses a `detailKey` pattern —
  `SwitchPreferenceDetailScreen` (switch + preview + `options` radio list +
  `customContent` slot) and `ChooserPreferenceDetailScreen`. Provider-style
  chooser precedent: `getEditImageCapableApps()` + `DETAIL_EDITOR`.
- Settings storage: `Settings.Misc` `rememberPreference(key, default)` over
  `activeDataStore` (encrypted DataStore).
- Icons: `com.dot.gallery.ui.core.Icons` object + extension `ImageVector`s in
  `ui/core/icons/*.kt` (960×960 viewport, single fill). Brand/provider icons
  also exist as `res/drawable/ic_provider_*.xml`.
- Manifest `<queries>` currently covers `ACTION_EDIT` `image/*` + `video/*`
  (needed for `queryIntentActivities` visibility on API 30+).
- `HelpSearchIndex` indexes viewer settings as `Toggle(R.string.x, Screen
  .SettingsMediaViewerScreen(), HelpCategory.VIEWER_SETTINGS)` entries.
- `toastError()` helper for viewer error toasts; `MediaViewEvent` SharedFlow
  routes one-shot events from `MediaViewViewModel` to the screen.

## Architecture

### New/changed pieces

```
core/Settings.kt                      +3 keys + remember fns
AndroidManifest.xml                   +ACTION_SEND image/* query
ui/core/icons/GoogleLens.kt           new monochrome Lens ImageVector
mediaview/VisualSearchProviders.kt    new: catalog + discovery + resolution
mediaview/VisualSearchLauncher.kt     new: shareable-URI prep + intent fire
mediaview/MediaActionCapabilityPolicy.kt  +visualSearch capability
mediaview/MediaViewViewModel.kt       +VisualSearchUiState + job + launch/cancel
mediaview/components/MediaViewAppBar.kt   start pill → Row [back | search]
                                        + preparing/cancel chip param
mediaview/components/MediaViewQuickBottomBar.kt  +VisualSearchButton (bottom mode)
mediaview/components/actionbuttons/VisualSearchButton.kt  new
mediaview/components/VisualSearchConvertSheet.kt  new: ask-mode bottom sheet
mediaview/MediaViewScreen.kt          wiring: prefs, resolve, slots, events,
                                        convert-sheet host
settings/…/SettingsMediaViewerScreen.kt   +DETAIL_VISUAL_SEARCH, list row,
                                          provider+position pickers, preview
res/values/strings.xml                new strings (base locale only)
help/data/HelpSearchIndex.kt          +index entry
app/src/test/…/MediaActionCapabilityPolicyTest.kt   extend
app/src/test/…/VisualSearchProvidersTest.kt         new
```

### Provider model — `VisualSearchProviders.kt`

```kotlin
/** One installed app that can receive an image share. */
data class VisualSearchTarget(
    val packageName: String,
    val activityName: String,   // ResolveInfo.activityInfo.name → setClassName
    val label: String,
    val known: KnownProvider?,
)

enum class KnownProvider(val packageName: String) {
    GOOGLE_LENS("com.google.ar.lens"),
    GOOGLE_APP("com.google.android.googlequicksearchbox"),
    BING("com.microsoft.bing"),
    YANDEX("ru.yandex.searchplugin"),          // verify on device
    PINTEREST("com.pinterest"),
}
```

- `discoverTargets(context): List<VisualSearchTarget>` — mirrors
  `getEditImageCapableApps`: `queryIntentActivities(Intent(ACTION_SEND)
  .setType("image/*"), 0)`, drop own package, `distinctBy packageName`+activity,
  label via `loadLabel(pm)`. Sort: `GOOGLE_LENS` first, then other known
  providers in catalog order, then the rest alphabetical by label.
- `resolveTarget(targets, storedPackage): VisualSearchTarget?` — pure/JVM-
  testable: stored pkg if still installed → else auto order (Lens → first
  known → first overall) → `null` when empty. A provider uninstalled after
  selection therefore degrades gracefully instead of breaking the button.
- Button icon rule: `target.known == GOOGLE_LENS` → `Icons.GoogleLens`, else
  `Icons.Outlined.ImageSearch`.
- Provider rows in settings show the real app icon
  (`rememberDrawablePainter(resolveInfo.loadIcon(pm))` — accompanist already
  used in `SettingsMediaViewerScreen`).

### Settings keys (`Settings.Misc`)

```kotlin
private val VISUAL_SEARCH_ENABLED = booleanPreferencesKey("visual_search_enabled")
@Composable fun rememberVisualSearchEnabled() =
    rememberPreference(key = VISUAL_SEARCH_ENABLED, defaultValue = true)

const val VISUAL_SEARCH_PROVIDER_AUTO = ""    // empty = auto-resolve
private val VISUAL_SEARCH_PROVIDER = stringPreferencesKey("visual_search_provider")
@Composable fun rememberVisualSearchProvider() =
    rememberPreference(key = VISUAL_SEARCH_PROVIDER, defaultValue = VISUAL_SEARCH_PROVIDER_AUTO)

const val VISUAL_SEARCH_POSITION_TOP = "top"
const val VISUAL_SEARCH_POSITION_BOTTOM = "bottom"
private val VISUAL_SEARCH_POSITION = stringPreferencesKey("visual_search_position")
@Composable fun rememberVisualSearchPosition() =
    rememberPreference(key = VISUAL_SEARCH_POSITION, defaultValue = VISUAL_SEARCH_POSITION_TOP)

/** Whether encrypted/vault media may be sent to a provider. */
private val VISUAL_SEARCH_ALLOW_VAULT = booleanPreferencesKey("visual_search_allow_vault")
@Composable fun rememberVisualSearchAllowVault() =
    rememberPreference(key = VISUAL_SEARCH_ALLOW_VAULT, defaultValue = true)

const val VISUAL_SEARCH_CONVERT_ASK = "ask"      // default — bottom sheet on exotic
const val VISUAL_SEARCH_CONVERT_ALWAYS = "always"
const val VISUAL_SEARCH_CONVERT_NEVER = "never"
private val VISUAL_SEARCH_CONVERT = stringPreferencesKey("visual_search_convert")
@Composable fun rememberVisualSearchConvertMode() =
    rememberPreference(key = VISUAL_SEARCH_CONVERT, defaultValue = VISUAL_SEARCH_CONVERT_ASK)

const val VISUAL_SEARCH_FORMAT_JPEG = "jpeg"     // default
const val VISUAL_SEARCH_FORMAT_PNG = "png"
const val VISUAL_SEARCH_FORMAT_WEBP = "webp"
private val VISUAL_SEARCH_CONVERT_FORMAT = stringPreferencesKey("visual_search_convert_format")
@Composable fun rememberVisualSearchConvertFormat() =
    rememberPreference(key = VISUAL_SEARCH_CONVERT_FORMAT, defaultValue = VISUAL_SEARCH_FORMAT_JPEG)
```

The "enabled by default when providers exist" semantics = enabled default
`true` **∧** `resolveTarget(...) != null` gates the button. A device with no
compatible app simply never shows it; the settings row reports "no compatible
app installed".

### Capability — `MediaActionCapabilityPolicy`

Add `visualSearch: Boolean` to `MediaActionCapabilities` and resolve it as:

```kotlin
visualSearch = exportAllowed && (input.isImage || input.isVideo)
```

Rationale: sharing to a provider is the same data-leaving-device action as
Share (`exportAllowed` covers cloud read-only). Vault/encrypted media is
**included by default** — the send path goes through the same
decrypt-to-temp-file flow as `shareEncryptedMedia` — but is user-configurable
(`rememberVisualSearchAllowVault`, default true); the screen-level gate adds
`&& (allowVault || !media.isEncrypted)` so the button disappears entirely on
vault items when the user opts out. `isTrashed` isn't in the policy — the
trashed branch of the bottom bar and the screen-level gate suppress it.

Add `isVideo` to `MediaActionPolicyInput` (currently absent).

### Send pipeline — `VisualSearchLauncher.kt` (`presentation/util/` or `mediaview/`)

```kotlin
suspend fun prepareVisualSearchImage(
    media: Media,
    metadata: MediaMetadata?,
    currentVault: Vault?,
    positionMs: Long?,
    onProgress: (Int?) -> Unit,
): Uri
```

- **Plain image, provider-safe format** → `resolveShareableUri(media)` (content
  passthrough / FileProvider / cloud download). Reuses the established share
  path.
- **Plain image, exotic format** → conversion policy decides (see
  *Exotic-format conversion* below): `never` → send as-is via
  `resolveShareableUri`; `always` → decode → `ImageReencoder` → temp JPEG/PNG/
  WebP; `ask` → the screen shows the conversion bottom sheet first and only
  reaches the launcher after the user picks.
- **Encrypted image** → `createDecryptedTempFile` → `FileProvider` URI (same as
  `shareEncryptedMedia`); exotic-format check applies **after** decrypt (a vault
  RAW still gets converted when policy says so).
- **Video** (incl. cloud/vault) →
  1. `FrameSourceMaterializer.materialize(FrameSourceSpec.from(media, metadata,
     currentVault, initialPositionMs = positionMs), onProgress)` — DIRECT for
     seekable local files; downloads cloud originals with progress; decrypts
     vault with progress.
  2. `FrameDecoderSession(context, prepared.sourceUri, prepared.localFile)` →
     `prepare()` → `resolveInitial(preferredTimeUs = -1,
     initialPositionMs = positionMs)` → `decodeFullResolution(identity)`.
  3. Encode JPEG (q95) into `cacheDir/visual_search/<uuid>.jpg`,
     `FileProvider.getUriForFile(...)`.
  4. `prepared.deleteIfOwned()` + `session.closeSafely()` in `finally`;
     leftover files are swept by `FrameSourceCleanup` anyway.
- **Intent**:

```kotlin
Intent(Intent.ACTION_SEND).apply {
    setClassName(target.packageName, target.activityName)
    type = if (media.isVideo) "image/jpeg" else media.mimeType
    putExtra(Intent.EXTRA_STREAM, uri)
    clipData = ClipData.newUri(contentResolver, media.label, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
```

`setClassName` (not `setPackage`) pins the exact resolved activity inside
multi-target packages. `clipData` is required — a bare `EXTRA_STREAM` without
ClipData loses the read grant on some targets.

### Exotic-format conversion

Provider-safe MIME set (no conversion ever offered):

```kotlin
val VISUAL_SEARCH_SAFE_MIMES = setOf(
    "image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp",
    "image/heic", "image/heif", "image/avif",   // system-decoded; Lens handles them
)
val Media.needsVisualSearchConvert: Boolean
    get() = isImage && mimeType.lowercase() !in VISUAL_SEARCH_SAFE_MIMES
```

(HEIC/AVIF count as safe — Android decodes them natively and Lens/Google ingest
them routinely. Everything else — RAW family, TIFF, PSD, JP2, JXL, SVG, XCF —
is exotic. Conservative, user-overridable via `never`.)

**Policy resolution** at tap time (screen, before calling the VM):

- `media.needsVisualSearchConvert == false` → proceed directly.
- mode `never` → proceed with original.
- mode `always` → proceed with `convertTo = configured format`.
- mode `ask` (default) → show `VisualSearchConvertSheet` bottom sheet:
  - Title: "Unsupported format?" + body: "%s may not be readable by %s.
    Convert a temporary copy?" — nothing is written back; conversion produces a
    cache-dir temp file only.
  - Format radios: JPEG / PNG / WebP (`visual_search_convert_format` default
    JPEG; PNG for graphics/transparency, WebP smaller).
  - Action rows: **Convert and share**, **Share original**.
  - "Remember my choice" switch → Convert-and-share persists
    `always`+format; Share-original persists `never`. Without it, `ask`
    stays — the sheet reappears next time.
  - Hosted in `MediaViewScreen` via the existing `AppBottomSheetState` +
    sheet pattern (same as the other viewer sheets), e.g. new
    `components/VisualSearchConvertSheet.kt`.

**Transcode path** (inside `prepareVisualSearchImage`, stage `CONVERTING`,
indeterminate progress — decode of a 45 MP DNG isn't instant):

1. Resolve a readable source: `resolveShareableUri` for plain images —
   decrypted temp file for vault items (materialize first so the decoder sees
   a normal stream).
2. Decode full-resolution `Bitmap` via the repo's decoder stack — same approach
   as `RotateMediaWorker.decodeFromStream` (format sniff → JXL/HEIF/specialized
   decoders → `BitmapFactory` fallback). If a mime has no JVM decoder here,
   fall back to Glide `asBitmap().submit().get()` on IO — the Glide decoder
   registry already handles RAW/TIFF/PSD/JP2 for display.
3. `ImageReencoder.encodeToBytes(bitmap, format.mapToWriteFormat(),
   ReencodeConfig(lossyQuality = 95))` → write to
   `cacheDir/visual_search/<uuid>.<ext>` → `FileProvider` URI.
4. Intent `type` = the output mime, not the source mime.

### ViewModel — `MediaViewViewModel`

Mirror `rotationState` / `MediaViewEvent` patterns:

```kotlin
sealed interface VisualSearchUiState {
    data object Idle : VisualSearchUiState
    data class Preparing(
        val mediaId: Long,
        val stage: Stage,              // DOWNLOADING | DECRYPTING | EXTRACTING | CONVERTING
        val progress: Int?,            // null = indeterminate
    ) : VisualSearchUiState
}

private var visualSearchJob: Job?
private val _visualSearchState = MutableStateFlow<VisualSearchUiState>(Idle)
val visualSearchState: StateFlow<VisualSearchUiState>

fun launchVisualSearch(media, target, positionMs, convertTo: ImageWriteFormat? = null) {
    visualSearchJob?.cancel()
    _visualSearchState.value = Preparing(media.id, initialStage(media, convertTo))
    visualSearchJob = viewModelScope.launch {
        runCatching { prepareVisualSearchImage(...) { p -> updateProgress(p) } }
            .onSuccess { uri ->
                _visualSearchState.value = Idle
                _uiEvents.emit(MediaViewEvent.LaunchVisualSearch(uri, target))
            }
            .onFailure { e ->
                if (e is CancellationException) throw e
                _visualSearchState.value = Idle
                _uiEvents.emit(MediaViewEvent.VisualSearchFailed(e.message))
            }
    }
}
fun cancelVisualSearch() { visualSearchJob?.cancel(); _visualSearchState.value = Idle }
```

`MediaViewEvent.LaunchVisualSearch(uri, target)` → screen performs
`startActivity` (has the resolved activity + context); `VisualSearchFailed` →
`toastError`. Inject `FrameSourceMaterializer` into the VM (it's `@Singleton`).

### UI — top pill (`MediaViewAppBar`)

Restructure the CenterStart control into a single stadium pill:

```kotlin
Row(
    modifier = Modifier
        .align(Alignment.CenterStart)
        .padding(horizontal = 8.dp)
        .clip(CircleShape)
        .then(backgroundModifier)
        .hazeEffectScaled(state = LocalHazeState.current,
            style = HazeMaterials.ultraThin(containerColor = surfaceContainer)),
    verticalAlignment = Alignment.CenterVertically,
) {
    IconButton(onClick = onGoBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, …) }
    visualSearchButton?.invoke()   // new slot, rendered inside the same pill
}
```

New param: `visualSearchButton: (@Composable () -> Unit)? = null` — mirrors the
existing `castButton` slot convention. When null the pill is just the back
button (identical to today). When present the pill widens to `[back │ search]`;
a 1 dp vertical hairline divider between the two can be added during
implementation if the pair reads cramped.

Also add a preparing chip under the bar (same extras column as the rotation
chip, same `tertiaryContainer` pill, `topExtrasAlpha` fade):

```kotlin
visualSearchPreparing: VisualSearchUiState.Preparing? = null,
onCancelVisualSearch: () -> Unit = {},
```

Rendered as `AnimatedVisibility(visible = visualSearchPreparing != null)` → Row
pill: `CircularProgressIndicator(18.dp)` (or `LinearProgressIndicator` when
`progress != null`) + stage label ("Downloading…" / "Extracting frame…") +
small cancel `IconButton` (`Icons.Outlined.Close`) → `onCancelVisualSearch`.

### UI — bottom pill (`MediaViewQuickBottomBar` + new `VisualSearchButton`)

New `actionbuttons/VisualSearchButton.kt` modeled on `ShareButton`:

```kotlin
@Composable
fun <T : Media> VisualSearchButton(
    media: T,
    enabled: Boolean,
    followTheme: Boolean,
    icon: ImageVector,                    // Icons.GoogleLens or ImageSearch
    title: String,                        // "Search with Google Lens" / "Visual search"
    onItemClick: (T) -> Unit,
) = MediaViewButton(currentMedia = media, imageVector = icon, title = title,
    followTheme = followTheme, enabled = enabled, onItemClick = onItemClick)
```

Placement in the row: **immediately after Share** (share-family grouping,
matches OEM ordering share/edit/lens/delete).

`MediaViewQuickBottomBar` gains `visualSearchIcon: ImageVector?`,
`visualSearchTitle: String`, `onVisualSearch: (T) -> Unit` params (null icon =
don't render), gated on `capabilities.visualSearch && position == BOTTOM`.

### UI — screen wiring (`MediaViewScreen`)

- `rememberVisualSearchEnabled/Provider/Position()` prefs.
- `produceState`/`remember` resolved target: `discoverTargets(context)` is a PM
  query — run once per viewer entry keyed on the stored package; a mid-session
  install isn't picked up until re-entry (acceptable).
- Gate: `enabled && target != null && currentCapabilities?.visualSearch == true
  && !isTrashed && position == TOP|BOTTOM` per slot.
- Tap handler: pause playback (`videoFramePickerController.player?.pause()` —
  same as `openFramePicker`), then
  `viewModel.launchVisualSearch(media, target, positionMs =
  videoFramePickerController.position?.longValue.takeIf { media.isVideo })`.
- Collect `MediaViewEvent.LaunchVisualSearch` → `context.startActivity(intent)`
  wrapped in `runCatching` (`ActivityNotFoundException` → `toastError`).
- `visualSearchState` collected → passed into `MediaViewAppBar` chip params.

### Settings UI (`SettingsMediaViewerScreen`)

- `DETAIL_VISUAL_SEARCH = "visual_search"`.
- List row: `rememberSwitchPreference` "Visual search", `Position.Middle`
  beside `showFavoriteButtonPref`; `onCheck` toggles enabled, `onClick` opens
  detail. Summary = resolved provider label / "No compatible app installed" /
  "Off" state text.
- Detail: `SwitchPreferenceDetailScreen` —
  - `preview`: mock viewer chrome showing the button in its current position
    (top pill vs bottom pill), mirroring `FavoriteButtonPreview`.
  - `options`: placement radios — "Top bar" (`VISUAL_SEARCH_POSITION_TOP`) /
    "Quick actions bar" (`VISUAL_SEARCH_POSITION_BOTTOM`). Options auto-hide
    when the switch is off.
  - `customContent`: three sections (all rendered only while enabled):
    1. **Provider** — `SettingsItem` rows with app icon, label, radio; first
       row "Automatic" (`VISUAL_SEARCH_PROVIDER_AUTO`, description "Use Google
       Lens when available"). Empty list → info row "No compatible app
       installed — install Google Lens or another app that accepts image
       sharing".
    2. **Secure vault** — single switch row bound to
       `rememberVisualSearchAllowVault`: "Allow on vault items" / summary
       "Decrypt a temporary copy to send". Default ON.
    3. **Unsupported formats** — mode radios Ask every time / Always convert /
       Never convert (send original), bound to `rememberVisualSearchConvertMode`;
       a nested format picker (JPEG / PNG / WebP, bound to
       `rememberVisualSearchConvertFormat`) enabled only when mode != `never`.
- `HelpSearchIndex`: `Toggle(R.string.visual_search_title,
  Screen.SettingsMediaViewerScreen(), HelpCategory.VIEWER_SETTINGS)`.

### Manifest

```xml
<queries>
    …existing EDIT queries…
    <intent>
        <action android:name="android.intent.action.SEND" />
        <data android:mimeType="image/*" />
    </intent>
</queries>
```

### Icon — `ui/core/icons/GoogleLens.kt`

Monochrome single-fill Lens glyph as an `Icons.GoogleLens` ImageVector
extension (project convention, 960×960 viewport builder). Path source: the
official Lens glyph in monochrome form (Simple Icons `googlelens` is CC0) —
scale the 24×24 path into the 960 viewport or keep 24 dp viewport; either is
fine since `MediaViewButton`/`Icon` tint it. License header like the other
icon files.

### Strings (base `res/values/strings.xml` only — translations flow through the
project's normal pipeline)

- `visual_search_title` — "Visual search"
- `visual_search_summary` — "Search the current image with another app"
- `visual_search_description` — explains image/frame is sent to the selected
  provider + privacy note
- `visual_search_provider_header` — "Provider"
- `visual_search_provider_auto` — "Automatic" (+ summary "Google Lens when
  available")
- `visual_search_none_installed` — "No compatible app installed"
- `visual_search_position_header` — "Button position"
- `visual_search_position_top` — "Top bar"
- `visual_search_position_bottom` — "Quick actions bar"
- `visual_search_with` — "Search with %1$s" (button label/tooltip)
- `visual_search_generic_cd` — "Visual search"
- `visual_search_stage_downloading` — "Downloading…"
- `visual_search_stage_decrypting` — "Decrypting…"
- `visual_search_stage_extracting` — "Extracting frame…"
- `visual_search_stage_converting` — "Converting image…"
- `visual_search_failed` — "Couldn't start visual search"
- `visual_search_vault_title` — "Allow on vault items"
- `visual_search_vault_summary` — "Decrypt a temporary copy to send"
- `visual_search_convert_header` — "Unsupported formats"
- `visual_search_convert_ask` — "Ask every time"
- `visual_search_convert_always` — "Always convert"
- `visual_search_convert_never` — "Never convert (send original)"
- `visual_search_convert_format_header` — "Convert to"
- `visual_search_convert_sheet_title` — "Unsupported format?"
- `visual_search_convert_sheet_body` — "%1$s may not be readable by %2$s.
  Convert a temporary copy first?"
- `visual_search_convert_sheet_convert` — "Convert and share"
- `visual_search_convert_sheet_original` — "Share original"
- `visual_search_convert_sheet_remember` — "Remember my choice"

## Edge cases & decisions

| Case | Behavior |
|---|---|
| No provider installed | Setting row shows "No compatible app installed"; button never renders. Toggle stays available (feature arms itself when one appears). |
| Selected provider uninstalled | `resolveTarget` falls back to auto order — button keeps working; settings re-labels. |
| Cloud media, read-only mode | `capabilities.share == false` → `visualSearch == false` → hidden. |
| Cloud media, offline | Materializer throws `FrameSourceException(retryable)` → Idle + toast; user re-taps. |
| Vault/encrypted | Included **by default** via decrypt-to-temp-file (same exposure as Share); a "Secure vault" switch in settings can hide the button on vault items (`allowVault && isEncrypted` gate at screen level). |
| Videos | Current playback frame via `videoFramePickerController.position`; `OPTION_CLOSEST` decode; JPEG out. |
| Motion photos | Still image sent (the photo, not the clip). |
| Trashed view | Hidden (matches Share visibility). |
| Slideshow / UI hidden | Chrome hidden with the rest; no special-casing. |
| Tap during ongoing prepare | Job is cancel-relaunched (idempotent single-flight). |
| Exotic image, mode `ask` | Bottom sheet before any work; "remember" writes `always`/`never` so it never asks again. Sheet dismissed without choice → nothing sent. |
| Exotic + conversion fails | Decode/encode exception → Idle + `toastError`; original never partially written. |
| Screen left mid-prepare | `viewModelScope` cancels; `ensureActive` stops the copy; session files swept by `FrameSourceCleanup`. |
| `queryIntentActivities` mid-session installs | Discovered list is computed per viewer entry; no live package-broadcast listener (keeps it simple). |

## Testing

- `MediaActionCapabilityPolicyTest` — matrix rows: image vs video vs audio-only,
  cloud read-only, trashed, vault.
- `VisualSearchProvidersTest` (new, pure JVM) — `resolveTarget`: stored-hit,
  stored-missing→Lens, no-Lens→first-known, empty→null; `discoverTargets`
  ordering (Lens first, knowns before generic, alphabetical tail) using fake
  `VisualSearchTarget` lists — keep discovery inputs as plain data so the
  resolver is PackageManager-free.
- `VisualSearchConvertPolicy` logic — keep the `needsVisualSearchConvert`
  predicate + mode/format resolution as pure functions so JVM tests can cover:
  safe-mime passthrough, exotic+never→original, exotic+always→format, ask→sheet
  (no send), vault-off→hidden.
- Manual/device verification:
  - `./gradlew :app:compileArm64-v8aNoMLStagingKotlin`
  - `testing/test-lab.sh` device/emulator **with** Lens → icon + launch +
    correct image lands in Lens.
  - Emulator **without** Lens but with the Google app → generic icon,
    provider picker shows both.
  - No compatible app → hidden button + settings empty state.
  - Cloud (Immich/WebDAV lab account) image + video → progress chip, cancel
    mid-download.
  - Vault item → decrypt path with switch ON; hidden with switch OFF.
  - DNG/TIFF file → `ask` sheet appears, "remember" suppresses it, converted
    JPEG lands in Lens; mode `never` sends the original as-is.

## Commit conventions reminder

`Fixes #1155` in the commit body; human git author only; stage exactly the
worked-on files (no `git add -A`); do not push unless asked.
