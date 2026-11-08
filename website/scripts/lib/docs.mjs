/**
 * Shared helpers for the docs pipeline scripts (merge-manifest, check-registry).
 *
 * Inputs (all outside website/, overridable for CI):
 *   DOCS_OUT       capture output dir, default ../docs-out
 *   SCENE_REGISTRY docs-showcase SceneRegistry.kt, default
 *                  ../app/docs/showcase/src/commonMain/kotlin/com/dot/gallery/docs/SceneRegistry.kt
 *
 * A scene is "capture" when docs-out publishes frames for it, "live" when the
 * wasm bundle claims it (not wired yet — the manifest marks fidelity), and
 * "pending" when it is only registered in SceneRegistry.kt.
 */
import { readdirSync, readFileSync, existsSync, statSync } from 'node:fs';
import { join, dirname, resolve, isAbsolute, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

export const WEBSITE_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
export const REPO_ROOT = resolve(WEBSITE_ROOT, '..');
export const DOCS_OUT = resolve(process.env.DOCS_OUT ?? join(REPO_ROOT, 'docs-out'));
export const SCENE_REGISTRY_KT = resolve(
  process.env.SCENE_REGISTRY ??
    join(REPO_ROOT, 'app', 'docs', 'showcase', 'src', 'commonMain', 'kotlin', 'com', 'dot', 'gallery', 'docs', 'SceneRegistry.kt'),
);

export const MEDIA_EXT = new Set(['webp', 'mp4', 'webm']);

/**
 * One capture's merge key: NUL-separated so state/theme names can never
 * collide with a delimiter character. Legacy records default to
 * theme=light / state=default.
 */
export function captureKey(cap) {
  return `${cap.theme || 'light'}\u0000${cap.device}\u0000${cap.state || 'default'}`;
}
export const DEVICE_IDS = ['phone', 'tablet', 'foldable'];
export const THEME_IDS = ['light', 'dark', 'amoled'];
export const LIVE_DIR = join(WEBSITE_ROOT, 'public', 'live');

/* ------------------------------- file utils ------------------------------ */

/** Recursive list of files under dir matching `exts` (dotless, lowercase). */
export function walk(dir, exts = null) {
  if (!existsSync(dir)) return [];
  const out = [];
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) out.push(...walk(p, exts));
    else if (!exts || exts.has(name.split('.').pop().toLowerCase())) out.push(p);
  }
  return out.sort();
}

/* --------------------------- SceneRegistry.kt ---------------------------- */

const KOTLIN_STRING_LIST = /(\w+)\s*=\s*listOf\(([^)]*)\)/g;

/**
 * Every `SceneRegistry*.kt` under the showcase commonMain docs dir — the
 * registry is split by surface (SceneRegistry.kt + SceneRegistry{Browse,Setup,
 * Organize}.kt), so the authored source of truth is the file set, not one file.
 */
export function registryFiles(dir = dirname(SCENE_REGISTRY_KT)) {
  // Android-only scenes live in a SceneRegistry*.kt under the mirror androidMain
  // package; the wasm catalog never lists them but check-registry must.
  const dirs = [dir, dir.replace(`${sep}commonMain${sep}`, `${sep}androidMain${sep}`)];
  const files = [];
  for (const d of dirs) {
    if (!existsSync(d)) continue;
    files.push(
      ...readdirSync(d)
        .filter((n) => /^SceneRegistry.*\.kt$/.test(n))
        .map((n) => join(d, n)),
    );
  }
  return files.sort();
}

/**
 * Parse `DocsScene(...)` entries out of the SceneRegistry*.kt files. Pass an
 * explicit `file` to parse just that file (tests). Duplicate ids across the
 * aggregated files fail loudly — a scene id must be unique.
 */
export function parseKotlinScenes(file = null) {
  const files = file ? [file] : registryFiles();
  const scenes = new Map();
  // Args close with a paren at the same indent as `DocsScene(` (4 spaces inside
  // the registry listOf); nested calls always close deeper or inline.
  const re = /DocsScene\(([\s\S]*?)\n {4}\)/g;
  for (const f of files) {
    const text = readFileSync(f, 'utf8');
    let m;
    while ((m = re.exec(text)) !== null) {
      const args = m[1];
      const id = args.match(/\bid\s*=\s*"([^"]+)"/)?.[1];
      if (!id) continue;
      if (scenes.has(id)) {
        throw new Error(`Duplicate docs scene id "${id}" in ${f}`);
      }
      const title = args.match(/\btitle\s*=\s*"([^"]+)"/)?.[1] ?? id;
      const lists = {};
      for (const lm of args.matchAll(KOTLIN_STRING_LIST)) {
        lists[lm[1]] = [...lm[2].matchAll(/"((?:[^"\\]|\\.)*)"/g)].map((s) => s[1]);
      }
      scenes.set(id, {
        id,
        title,
        path: lists.pathTrace ?? [],
        states: lists.states ?? [],
        recordedStates: lists.recordedStates ?? [],
        presentation: args.match(/\bpresentation\s*=\s*"([^"]+)"/)?.[1],
        widthDp: Number.parseInt(args.match(/\bwidthDp\s*=\s*(\d+)/)?.[1] ?? '', 10) || undefined,
        heightDp: Number.parseInt(args.match(/\bheightDp\s*=\s*(\d+)/)?.[1] ?? '', 10) || undefined,
        captureWindow: /\bcaptureWindow\s*=\s*true/.test(args) || undefined,
      });
    }
  }
  return scenes;
}

/* ----------------------------- docs-out input ---------------------------- */

/**
 * Read docs-out/docs-manifest.json plus every per-scene fragment under
 * docs-out/manifest/*.json. Captures UNION per scene id keyed by
 * `${theme || 'light'} ${device} ${state || 'default'}` — a partial
 * capture run (e.g. dark-only) extends rather than replaces earlier frames.
 * Later sources win for the same key: the top manifest first, then fragments
 * in mtime order so the most recently written fragment takes precedence.
 *
 * Returns { generatedAtMs, entries: Map<id, entry> } where entry is
 * { id, title, pathTrace, states, presentation, widthDp, heightDp,
 *   captures: Map<key, capture>, sources: [] }.
 */
export function readDocsOutManifest(docsOut = DOCS_OUT) {
  const entries = new Map();
  let generatedAtMs = 0;

  const mergeScene = (scene, sourceFile) => {
    if (!scene || typeof scene.id !== 'string') return;
    const existing = entries.get(scene.id) ?? {
      id: scene.id,
      captures: new Map(),
      sources: [],
    };
    for (const field of ['title', 'pathTrace', 'states', 'presentation', 'widthDp', 'heightDp']) {
      if (scene[field] !== undefined) existing[field] = scene[field];
    }
    for (const cap of scene.captures ?? []) {
      if (!cap || typeof cap.file !== 'string' || typeof cap.device !== 'string') continue;
      const normalized = {
        ...cap,
        theme: cap.theme || 'light',
        state: cap.state || 'default',
      };
      existing.captures.set(captureKey(normalized), normalized);
    }
    existing.sources.push(sourceFile);
    entries.set(scene.id, existing);
  };

  const top = join(docsOut, 'docs-manifest.json');
  if (existsSync(top)) {
    const manifest = JSON.parse(readFileSync(top, 'utf8'));
    generatedAtMs = manifest.generatedAtMs ?? 0;
    for (const scene of manifest.scenes ?? []) mergeScene(scene, top);
  }
  const perScene = join(docsOut, 'manifest');
  const fragments = walk(perScene, new Set(['json']))
    .map((file) => ({ file, mtimeMs: statSync(file).mtimeMs }))
    .sort((a, b) => a.mtimeMs - b.mtimeMs);
  for (const { file } of fragments) {
    mergeScene(JSON.parse(readFileSync(file, 'utf8')), file);
  }
  return { generatedAtMs, entries };
}

/**
 * Frame files on disk per scene dir: Map<sceneId, Set<relative-path>> where
 * paths include the theme subdir (`light/phone-default.webp`).
 */
export function readDocsOutFrames(docsOut = DOCS_OUT) {
  const frames = new Map();
  if (!existsSync(docsOut)) return frames;
  for (const name of readdirSync(docsOut)) {
    const dir = join(docsOut, name);
    if (!statSync(dir).isDirectory() || name === 'manifest') continue;
    const files = new Set(
      // Asset keys use '/', so Windows separators are normalised.
      walk(dir, MEDIA_EXT).map((file) => file.slice(dir.length + 1).replaceAll(sep, '/')),
    );
    frames.set(name, files);
  }
  return frames;
}

/**
 * The published live catalog, or null when the live bundle isn't deployed.
 * Only trusted when both index.html and docs-live.json exist — the publisher
 * writes the catalog only after a successful runtime probe.
 */
export function readLiveCatalog(liveDir = LIVE_DIR) {
  const index = join(liveDir, 'index.html');
  const catalogFile = join(liveDir, 'docs-live.json');
  if (!existsSync(index) || !existsSync(catalogFile)) return null;
  try {
    const catalog = JSON.parse(readFileSync(catalogFile, 'utf8'));
    if (catalog.schemaVersion !== 1 || !Array.isArray(catalog.scenes)) return null;
    const scenes = new Map();
    for (const scene of catalog.scenes) {
      if (scene && typeof scene.id === 'string') scenes.set(scene.id, scene);
    }
    return scenes;
  } catch {
    return null;
  }
}

/* --------------------------- normalized scenes --------------------------- */

const mediaType = (file) => (file.endsWith('.mp4') || file.endsWith('.webm') ? 'video' : 'image');

/**
 * Merge the capture manifest, the Kotlin registry, and the published live
 * catalog into the normalized shape the site consumes:
 *
 *   { id, title, path, states, presentation, widthDp, heightDp, live,
 *     fidelity, assets, themes, missing, rejected, origin }
 *
 * themes = { theme: { device: { state: { src, type, file, width, height,
 * widthDp, heightDp } } } } — every theme retained explicitly. `assets` is an
 * alias for `themes.light` so older consumers keep working. `src` is relative
 * to the site root (captures/<scene>/<theme>/<file>); `file` is relative to
 * docs-out.
 *
 * A `default` alias is filled in per theme+device: the first declared state
 * with an asset, else the first present state.
 *
 * fidelity: 'live' only for ids in the published live catalog; 'capture' when
 * at least one valid asset exists; otherwise 'pending'. Missing or invalid
 * frame paths never invent fidelity — they land in `missing`/`rejected` for
 * check-registry to fail on.
 */
export function collectScenes({
  docsOut = DOCS_OUT,
  // Explicit single-file registry override (tests); null aggregates every
  // SceneRegistry*.kt under the showcase docs package.
  registryKt = null,
  liveDir = LIVE_DIR,
} = {}) {
  const { generatedAtMs, entries } = readDocsOutManifest(docsOut);
  const kotlin = parseKotlinScenes(registryKt);
  const liveCatalog = readLiveCatalog(liveDir);
  const scenes = new Map();

  const withinDocsOut = (file) =>
    !isAbsolute(file) && resolve(docsOut, file).startsWith(docsOut + sep);

  for (const [id, entry] of entries) {
    const themes = {};
    const missing = [];
    const rejected = [];
    let anyExisting = false;
    for (const cap of entry.captures.values()) {
      if (!withinDocsOut(cap.file)) {
        rejected.push(`path escapes docs-out: ${cap.file}`);
        continue;
      }
      if ((cap.width !== undefined && !(cap.width > 0)) ||
          (cap.height !== undefined && !(cap.height > 0)) ||
          (cap.widthDp !== undefined && !(cap.widthDp > 0)) ||
          (cap.heightDp !== undefined && !(cap.heightDp > 0))) {
        rejected.push(`non-positive dimensions in ${cap.file}`);
        continue;
      }
      const type = mediaType(cap.file);
      // Recordings must carry a same-frame WebP poster inside docs-out; still
      // images must not — a poster field on them has no meaning.
      if (type === 'video') {
        if (typeof cap.poster !== 'string' || !cap.poster.endsWith('.webp') ||
            !withinDocsOut(cap.poster)) {
          rejected.push(`video capture without a valid poster in docs-out: ${cap.file}`);
          continue;
        }
      } else if (cap.poster !== undefined) {
        rejected.push(`poster field on a non-video capture: ${cap.file}`);
        continue;
      }
      const asset = {
        src: `captures/${cap.file}`,
        type,
        file: cap.file,
        width: cap.width,
        height: cap.height,
        widthDp: cap.widthDp,
        heightDp: cap.heightDp,
      };
      if (type === 'video') {
        asset.poster = `captures/${cap.poster}`;
        asset.posterFile = cap.poster;
      }
      ((themes[cap.theme] ??= {})[cap.device] ??= {})[cap.state] = asset;
      const fileOk = existsSync(join(docsOut, cap.file));
      const posterOk = type !== 'video' || existsSync(join(docsOut, cap.poster));
      if (!fileOk) missing.push(cap.file);
      if (!posterOk) missing.push(cap.poster);
      if (fileOk && posterOk) anyExisting = true;
    }
    const declared = entry.states ?? kotlin.get(id)?.states ?? [];
    for (const devices of Object.values(themes)) {
      for (const states of Object.values(devices)) {
        if (!states.default) {
          const first = declared.find((s) => states[s]) ?? Object.keys(states)[0];
          if (first) states.default = states[first];
        }
      }
    }
    const k = kotlin.get(id);
    const live = liveCatalog?.get(id);
    scenes.set(id, {
      id,
      title: entry.title ?? live?.title ?? k?.title ?? id,
      path: entry.pathTrace ?? live?.pathTrace ?? k?.path ?? [],
      states: entry.states ?? live?.states ?? k?.states ?? [],
      presentation: entry.presentation ?? live?.presentation ?? k?.presentation ?? 'screen',
      widthDp: entry.widthDp ?? live?.widthDp ?? k?.widthDp ?? 412,
      heightDp: entry.heightDp ?? live?.heightDp ?? k?.heightDp ?? 915,
      live: Boolean(live),
      fidelity: live ? 'live' : anyExisting ? 'capture' : 'pending',
      assets: themes.light ?? {},
      themes,
      missing,
      rejected,
      origin: k ? 'manifest+registry' : 'manifest',
    });
  }

  // Registry ids with no capture output ship as "pending" placeholders so
  // feature pages can reference them before the capture job covers them.
  for (const [id, k] of kotlin) {
    if (scenes.has(id)) continue;
    const live = liveCatalog?.get(id);
    scenes.set(id, {
      id,
      title: live?.title ?? k.title,
      path: live?.pathTrace ?? k.path,
      states: live?.states ?? k.states,
      presentation: live?.presentation ?? k.presentation ?? 'screen',
      widthDp: live?.widthDp ?? k.widthDp ?? 412,
      heightDp: live?.heightDp ?? k.heightDp ?? 915,
      live: Boolean(live),
      fidelity: live ? 'live' : 'pending',
      assets: {},
      themes: {},
      missing: [],
      rejected: [],
      origin: live ? 'registry+live' : 'registry',
    });
  }

  return {
    generatedAtMs,
    scenes,
    kotlinIds: new Set(kotlin.keys()),
    liveIds: new Set(liveCatalog?.keys() ?? []),
  };
}

/* ------------------------------ frontmatter ------------------------------ */

const SCALAR = /^(\w[\w-]*)\s*:\s*(.*)$/;
const LIST_ITEM = /^\s*-\s+(.*)$/;

const unquote = (s) => {
  const t = s.trim();
  const q = t[0];
  if ((q === '"' || q === "'") && t.endsWith(q) && t.length > 1) return t.slice(1, -1);
  return t;
};

const scalar = (raw) => {
  const v = unquote(raw);
  if (v === 'true') return true;
  if (v === 'false') return false;
  if (/^-?\d+$/.test(v)) return Number.parseInt(v, 10);
  return v;
};

/**
 * Minimal YAML frontmatter parser: flat `key: value`, `key: [a, b]`, and
 * `key:` followed by `- item` lines. Enough for this repo's authored content —
 * the zod collection schemas remain the real validation at build time.
 */
export function parseFrontmatter(text) {
  const result = { data: {}, body: text, hasFrontmatter: false, rawLines: [] };
  const m = text.match(/^\uFEFF?---[ \t]*\r?\n([\s\S]*?)\r?\n(?:---|\.\.\.)[ \t]*\r?\n?/);
  if (!m) return result;
  result.hasFrontmatter = true;
  result.body = text.slice(m[0].length);
  // CRLF checkouts (Windows autocrlf) must parse like LF ones.
  const lines = m[1].split(/\r?\n/);
  result.rawLines = lines;
  let key = null;
  for (const line of lines) {
    if (!line.trim() || /^\s*#/.test(line)) continue;
    const item = line.match(LIST_ITEM);
    if (item && key) {
      result.data[key].push(scalar(item[1]));
      continue;
    }
    const kv = line.match(SCALAR);
    if (!kv) continue;
    key = kv[1];
    const raw = kv[2].trim();
    if (raw === '') {
      result.data[key] = [];
    } else if (raw.startsWith('[') && raw.endsWith(']')) {
      result.data[key] = raw
        .slice(1, -1)
        .split(',')
        .map((s) => scalar(s))
        .filter((s) => s !== '');
    } else {
      result.data[key] = scalar(raw);
    }
  }
  return result;
}

/** List content entries under a collection dir: [{id, file, data}] (id = filename minus ext). */
export function listEntries(dir) {
  return walk(dir, new Set(['md', 'mdx'])).map((file) => {
    const { data } = parseFrontmatter(readFileSync(file, 'utf8'));
    return { id: file.slice(dir.length + 1).replace(/\.(md|mdx)$/, ''), file, data };
  });
}
