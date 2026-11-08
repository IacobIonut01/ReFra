#!/usr/bin/env node
/**
 * check-registry.mjs — registry integrity gate (`npm run check:registry`).
 *
 * Enforces the plan §8 contract that the site cannot reference a missing scene,
 * and keeps docs-out and the registry in lockstep:
 *
 *   FAIL  docs-out scene dir with frames but no scene entry (orphan dir)
 *   FAIL  asset referenced by a scene but absent under docs-out (missing frame)
 *   FAIL  frame on disk referenced by no scene asset (orphan frame)
 *   FAIL  feature frontmatter `scenes`/`related` pointing at unknown ids
 *   FAIL  feature frontmatter `area` outside the collection enum
 *   WARN  manifest scene missing from SceneRegistry.kt (registry drift)
 *   WARN  registered scene with no captures yet (pending fidelity)
 *   WARN  scene not referenced by any feature page (coverage)
 *
 * Exit 0 when there are no failures; warnings never fail the build.
 */
import { join } from 'node:path';
import {
  collectScenes,
  listEntries,
  readDocsOutFrames,
  readLiveCatalog,
  DEVICE_IDS,
  DOCS_OUT,
  WEBSITE_ROOT,
} from './lib/docs.mjs';

const AREAS = new Set([
  'browse',
  'view-play',
  'edit',
  'discover',
  'organize-secure',
  'cloud',
  'customize',
  'utilities',
  'edit-legacy',
]);

const failures = [];
const warnings = [];
const fail = (msg) => failures.push(msg);
const warn = (msg) => warnings.push(msg);

const { scenes, kotlinIds } = collectScenes();
const onDisk = readDocsOutFrames();

/* --- docs-out dirs vs registry ------------------------------------------ */
for (const [dir, files] of onDisk) {
  if (!scenes.has(dir)) {
    fail(`orphan scene dir: docs-out/${dir}/ has ${files.size} frame(s) but no registry entry`);
  }
}

/* --- registry assets vs disk (both directions) -------------------------- */
const referenced = new Set();
for (const scene of scenes.values()) {
  if (scene.origin === 'manifest' && !kotlinIds.has(scene.id)) {
    warn(`scene "${scene.id}" is in docs-out but not in SceneRegistry.kt — registry drift`);
  }
  for (const [theme, devices] of Object.entries(scene.themes)) {
    for (const [device, states] of Object.entries(devices)) {
      if (!DEVICE_IDS.includes(device)) {
        warn(`scene "${scene.id}" (${theme}) declares unknown device preset "${device}"`);
      }
      const files = new Set();
      for (const asset of Object.values(states)) {
        files.add(asset.file);
        if (asset.posterFile) files.add(asset.posterFile);
      }
      for (const file of files) referenced.add(file);
    }
  }
  for (const file of scene.missing) {
    fail(`missing frame: "${scene.id}" references docs-out/${file} which does not exist`);
  }
  for (const reason of scene.rejected ?? []) {
    fail(`invalid capture record: scene "${scene.id}" — ${reason}`);
  }
}

/* --- published live catalog ⊆ registry ---------------------------------- */
const liveCatalog = readLiveCatalog();
if (liveCatalog) {
  for (const id of liveCatalog.keys()) {
    if (!scenes.has(id)) {
      fail(`live catalog publishes unknown scene "${id}" — not in registry/manifest`);
    }
  }
}
for (const [dir, files] of onDisk) {
  if (!scenes.has(dir)) continue;
  for (const file of files) {
    if (!referenced.has(`${dir}/${file}`)) {
      fail(`orphan frame: docs-out/${dir}/${file} is not referenced by scene "${dir}"`);
    }
  }
}

/* --- feature pages vs registry ------------------------------------------ */
const featuresDir = join(WEBSITE_ROOT, 'src', 'content', 'features');
const features = listEntries(featuresDir);
const featureIds = new Set(features.map((f) => f.id));
const referencedScenes = new Set();

for (const feature of features) {
  const rel = `src/content/features/${feature.id}.mdx`;
  const { area, path, related = [], scenes: used = [] } = feature.data;
  if (area !== undefined && !AREAS.has(area)) {
    fail(`${rel}: area "${area}" is not one of ${[...AREAS].join(', ')}`);
  }
  if (path !== undefined && (!Array.isArray(path) || path.length === 0)) {
    fail(`${rel}: "path" must be a non-empty list`);
  }
  for (const r of related) {
    if (!featureIds.has(r)) fail(`${rel}: related id "${r}" has no feature page`);
  }
  for (const s of used) {
    if (!scenes.has(s)) {
      fail(`${rel}: scene "${s}" is not in the registry — the site may not reference missing scenes`);
    } else {
      referencedScenes.add(s);
    }
  }
}

/* --- coverage warnings ---------------------------------------------------- */
let pending = 0;
for (const scene of scenes.values()) {
  if (scene.fidelity === 'pending') pending += 1;
  if (!referencedScenes.has(scene.id)) {
    warn(`scene "${scene.id}" is not referenced by any feature page`);
  }
}

/* --- report --------------------------------------------------------------- */
console.log(`registry: ${scenes.size} scenes, ${referenced.size} frames referenced, ${features.length} feature pages`);
if (pending) console.log(`  ${pending} scene(s) have neither captures nor a published live preview`);
for (const w of warnings) console.warn(`warn: ${w}`);
for (const f of failures) console.error(`FAIL: ${f}`);

if (failures.length > 0) {
  console.error(`\ncheck-registry: ${failures.length} failure(s), ${warnings.length} warning(s)`);
  process.exit(1);
}
console.log(`check-registry: clean (${warnings.length} warning(s))`);
