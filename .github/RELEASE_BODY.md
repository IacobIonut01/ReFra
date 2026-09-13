## What's new in 5.1.4

ReFra 5.1.4 gives you more control over modified-date sorting while making cloud cleanup, maps, editing, search, navigation, and native compatibility more reliable.

### New Features

- **Optional modified-date updates** — Choose whether copying, renaming, editing, or rotating media updates its modified date and moves it to the top of modified-date views; the setting is off by default (#1163)

### Improvements

- **Android 16 KB compatibility** — ReFra now builds JPEG 2000 support from pinned sources, supports flexible native page sizes, and verifies APK and App Bundle alignment in release workflows (#1198)
- **Easier Obtainium installation** — The repository now offers a one-click Obtainium download option (#1164)

### Bug Fixes

- **Safer cloud storage cleanup** — Free Up Space verifies exact backups on every configured destination, preserves cleanup options, rechecks each deletion batch, and handles partial failures safely (#1157, #1162)
- **Complete, resilient cloud sync** — Manual refresh processes every cloud page, SMB and NFS connections recover after network or VPN changes, and failed backup retries are bounded (#1186, #1190)
- **Reliable cloud account removal** — Removing a server now finishes cleanup before leaving settings, prevents duplicate requests, and shows progress and retry feedback if removal fails (#1199)
- **Reliable maps and locations** — Map previews recover more gracefully, unresolved location names are repaired, and nearby locations no longer collide in carousels (#1167, #1192)
- **Reliable editing and markup** — Editor state, highlighter and eraser behavior, text selection, face blur cancellation, previews, undo, and full-resolution saves remain consistent (#1078)
- **Smoother media navigation** — Predictive back keeps the visible media selected, external media launches reuse one viewer task, and the timeline scrubber remains actionable longer (#1179, #1150, #1160)
- **Correct search and Smart Features results** — Date-tab results are newest-first, and category embeddings persist correctly so classification scans can complete (#1128, #1159)
- **Layout and accessibility polish** — Filter chips keep a generous touch target without appearing oversized, and Albums respects side-navigation insets (#1169)
