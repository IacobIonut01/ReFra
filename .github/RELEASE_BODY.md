## What's new in 4.2.1

### New Features

- **FCast Video Casting** — Cast videos and images to FCast-compatible devices on your local network. Includes mDNS device discovery, remote playback controls (play, pause, seek, volume, speed), local streaming server, and encrypted vault media casting via temporary decryption.
- **Auto-Contrast (Beta)** — Real-time luminance detection automatically enhances display contrast in the media viewer. Dark images become easier to see and bright images appear more vivid — without modifying your files.
- **Vault Overhaul** — Complete redesign of vault security and navigation:
  - Global gate authentication before the vault selector (None / Device Security / Custom Password)
  - Custom per-vault passwords with independent security levels
  - Streamlined vault creation flow: create → set password → configure gate
  - Vault deletion returns to selector with re-authentication; last vault exits to library
  - Unified confirmation sheets replacing duplicated dialog code
- **Right-Align Selection Actions** — New option to push top action buttons to the right edge of the selection bar for easier one-handed reach on larger phones.
- **NoMaps WithML Build Variant** — New build variant that includes AI models but strips all map dependencies and network permissions.

### Bug Fixes & Improvements

- Fixed phantom selections when media is externally deleted (#854)
- Corrected permission list on setup screen for maps/nomaps variants
- Capitalized ML in product flavor names (#848)
- Improved image loading with sketch lib update (4.4.0 → 4.5.0-alpha03)

