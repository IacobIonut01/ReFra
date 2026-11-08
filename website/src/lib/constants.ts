/** Shared site constants. Only facts that are true of the repo today. */

export const REPO = 'https://github.com/IacobIonut01/ReFra';
export const REPO_TREE = `${REPO}/tree/main`;
export const REPO_BLOB = `${REPO}/blob/main`;
export const REPO_ISSUES = `${REPO}/issues`;
export const REPO_RELEASES = `${REPO}/releases`;
export const FDROID_URL = 'https://f-droid.org/packages/com.dot.gallery';
export const GPLAY_URL =
  'https://play.google.com/store/apps/details?id=com.dot.gallery.gplay';
export const OBTAINIUM_URL =
  'https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/IacobIonut01/ReFra';
export const CROWDIN_URL = 'https://crowdin.com/project/gallery-compose';
export const TELEGRAM_URL = 'https://t.me/GalleryCompose';

/**
 * Device presets shared with the docs-showcase/capture contract
 * (DOCS_SITE_PLAN.md §6). `device` is the vocabulary sent over the embed
 * protocol; px sizes drive the on-page stage frame.
 */
export const DEVICE_PRESETS = {
  phone: { label: 'Phone', width: 412, height: 915, corner: 24 },
  tablet: { label: 'Tablet', width: 840, height: 1180, corner: 28 },
  foldable: { label: 'Foldable', width: 768, height: 932, corner: 18 },
} as const;

export type DeviceId = keyof typeof DEVICE_PRESETS;

/** Logical dp the PhoneFrame status-bar overlay reserves; sent to the live scene as safe-area padding. */
export const PHONE_STATUS_BAR_HEIGHT = 64;

/** Theme vocabulary understood by LiveSceneHost (?theme= and docs:set). */
export const THEMES = ['auto', 'light', 'dark', 'amoled'] as const;
export type ThemeId = (typeof THEMES)[number];

export const AREA_LABELS: Record<string, string> = {
  browse: 'Browse',
  'view-play': 'View and play',
  edit: 'Edit',
  discover: 'Discover',
  'organize-secure': 'Organize and secure',
  cloud: 'Cloud',
  customize: 'Customize',
  utilities: 'Utilities',
  'edit-legacy': 'Built-in editor (legacy)',
};
