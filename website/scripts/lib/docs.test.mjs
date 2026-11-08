/**
 * docs.test.mjs — pipeline tests for the docs manifest transform.
 *
 * Run: `node --test scripts/lib/` (npm test).
 * Fixtures are tiny stub files under a temp docs-out — no app SDK involved.
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { collectScenes, parseFrontmatter, parseKotlinScenes } from './docs.mjs';

function fixture({ manifestScenes = [], fragments = {}, kotlin = '', live = null } = {}) {
  const root = mkdtempSync(join(tmpdir(), 'docs-out-'));
  const docsOut = join(root, 'docs-out');
  const registryKt = join(root, 'SceneRegistry.kt');
  const liveDir = join(root, 'live');
  mkdirSync(docsOut, { recursive: true });
  if (manifestScenes.length) {
    writeFileSync(
      join(docsOut, 'docs-manifest.json'),
      JSON.stringify({ schema: 1, scenes: manifestScenes }),
    );
  }
  for (const [id, scene] of Object.entries(fragments)) {
    mkdirSync(join(docsOut, 'manifest'), { recursive: true });
    writeFileSync(join(docsOut, 'manifest', `${id}.json`), JSON.stringify(scene));
  }
  writeFileSync(registryKt, kotlin);
  if (live) {
    mkdirSync(liveDir, { recursive: true });
    writeFileSync(join(liveDir, 'index.html'), '<html></html>');
    writeFileSync(join(liveDir, 'docs-live.json'), JSON.stringify(live));
  }
  const touch = (file) => {
    const p = join(docsOut, file);
    mkdirSync(join(p, '..'), { recursive: true });
    writeFileSync(p, 'stub');
  };
  return { docsOut, registryKt, liveDir, touch };
}

const KOTLIN = `
val sceneRegistry: List<DocsScene> = listOf(
    DocsScene(
        id = "settings-appearance",
        title = "Appearance",
        pathTrace = listOf("Settings", "Appearance"),
        states = listOf("default", "custom-seed"),
    ) { },
    DocsScene(
        id = "settings-security",
        title = "Security",
        states = listOf("default", "aapm"),
    ) { },
)
`;

const cap = (over = {}) => ({
  device: 'phone',
  state: 'default',
  theme: 'light',
  file: 'settings-appearance/light/phone-default.webp',
  width: 1080,
  height: 2400,
  widthDp: 412,
  heightDp: 915,
  ...over,
});

test('partial capture runs merge light+dark without losing earlier frames', () => {
  const f = fixture({
    kotlin: KOTLIN,
    fragments: {
      'settings-appearance': {
        id: 'settings-appearance',
        title: 'Appearance',
        states: ['default'],
        captures: [cap()],
      },
    },
    manifestScenes: [
      {
        id: 'settings-appearance',
        captures: [
          cap({
            theme: 'dark',
            file: 'settings-appearance/dark/phone-default.webp',
          }),
        ],
      },
    ],
  });
  f.touch('settings-appearance/light/phone-default.webp');
  f.touch('settings-appearance/dark/phone-default.webp');

  const { scenes } = collectScenes(f);
  const scene = scenes.get('settings-appearance');
  assert.equal(scene.themes.light.phone.default.file, 'settings-appearance/light/phone-default.webp');
  assert.equal(scene.themes.dark.phone.default.file, 'settings-appearance/dark/phone-default.webp');
  assert.equal(scene.fidelity, 'capture');
  assert.deepEqual(scene.missing, []);
});

test('default alias resolves per theme+device to the first declared state', () => {
  const f = fixture({
    kotlin: KOTLIN,
    fragments: {
      'settings-security': {
        id: 'settings-security',
        states: ['default', 'aapm'],
        captures: [
          cap({ state: 'aapm', file: 'settings-security/light/phone-aapm.webp' }),
          cap({
            state: 'aapm',
            theme: 'dark',
            file: 'settings-security/dark/phone-aapm.webp',
          }),
        ],
      },
    },
  });
  f.touch('settings-security/light/phone-aapm.webp');
  f.touch('settings-security/dark/phone-aapm.webp');

  const { scenes } = collectScenes(f);
  const scene = scenes.get('settings-security');
  // 'default' is declared but has no asset — the first declared state wins.
  assert.equal(
    scene.themes.light.phone.default.file,
    'settings-security/light/phone-aapm.webp',
  );
  assert.equal(
    scene.themes.dark.phone.default.file,
    'settings-security/dark/phone-aapm.webp',
  );
});

test('a manifest entry whose frames are absent never claims capture fidelity', () => {
  const f = fixture({
    kotlin: KOTLIN,
    fragments: {
      'settings-appearance': {
        id: 'settings-appearance',
        captures: [cap()],
      },
    },
  });
  const { scenes } = collectScenes(f);
  const scene = scenes.get('settings-appearance');
  assert.equal(scene.fidelity, 'pending');
  assert.deepEqual(scene.missing, ['settings-appearance/light/phone-default.webp']);
});

test('live fidelity requires the published catalog (index.html + docs-live.json)', () => {
  const f = fixture({ kotlin: KOTLIN });
  let { scenes } = collectScenes(f);
  assert.equal(scenes.get('settings-appearance').fidelity, 'pending');
  assert.equal(scenes.get('settings-appearance').live, false);

  const f2 = fixture({
    kotlin: KOTLIN,
    live: {
      schemaVersion: 1,
      scenes: [
        {
          id: 'settings-appearance',
          title: 'Appearance',
          pathTrace: ['Settings', 'Appearance'],
          states: ['default'],
          presentation: 'screen',
          widthDp: 412,
          heightDp: 915,
        },
      ],
    },
  });
  ({ scenes } = collectScenes(f2));
  const scene = scenes.get('settings-appearance');
  assert.equal(scene.live, true);
  assert.equal(scene.fidelity, 'live');
});

test('relative paths escaping docs-out are rejected, not shipped', () => {
  const f = fixture({
    kotlin: KOTLIN,
    fragments: {
      'settings-appearance': {
        id: 'settings-appearance',
        captures: [cap({ file: '../outside.webp' })],
      },
    },
  });
  const { scenes } = collectScenes(f);
  const scene = scenes.get('settings-appearance');
  assert.equal(scene.fidelity, 'pending');
  assert.equal(scene.rejected.length, 1);
  assert.match(scene.rejected[0], /escapes docs-out/);
});

test('non-positive dimensions are rejected', () => {
  const f = fixture({
    kotlin: KOTLIN,
    fragments: {
      'settings-appearance': {
        id: 'settings-appearance',
        captures: [cap({ width: 0 })],
      },
    },
  });
  const { scenes } = collectScenes(f);
  assert.equal(scenes.get('settings-appearance').rejected.length, 1);
});

test('legacy captures without a theme field map to light', () => {
  const f = fixture({
    kotlin: KOTLIN,
    fragments: {
      'settings-appearance': {
        id: 'settings-appearance',
        captures: [cap({ theme: undefined })],
      },
    },
  });
  f.touch('settings-appearance/light/phone-default.webp');
  const { scenes } = collectScenes(f);
  const scene = scenes.get('settings-appearance');
  assert.equal(scene.themes.light.phone.default.file, 'settings-appearance/light/phone-default.webp');
});

/* --- merge-capture-out.mjs: pulled-tree union semantics ------------------- */

const { mergeDocsOut } = await import('../merge-capture-out.mjs');

function tree(manifestScenes) {
  const dir = mkdtempSync(join(tmpdir(), 'docs-out-tree-'));
  mkdirSync(dir, { recursive: true });
  writeFileSync(
    join(dir, 'docs-manifest.json'),
    JSON.stringify({ schema: 1, scenes: manifestScenes }),
  );
  return dir;
}

test('docs-out merge keeps host light frames when a pulled run adds dark', () => {
  const host = tree([
    { id: 'settings-security', states: ['default', 'aapm'], captures: [cap()] },
  ]);
  const incoming = tree([
    {
      id: 'settings-security',
      states: ['default', 'aapm'],
      captures: [
        cap({ theme: 'dark', file: 'settings-security/dark/phone-default.webp' }),
      ],
    },
  ]);
  mkdirSync(join(incoming, 'settings-security', 'dark'), { recursive: true });
  writeFileSync(join(incoming, 'settings-security', 'dark', 'phone-default.webp'), 'stub');

  mergeDocsOut(incoming, host);
  const merged = JSON.parse(
    readFileSync(join(host, 'docs-manifest.json'), 'utf8'),
  ).scenes.find((s) => s.id === 'settings-security');
  const keys = merged.captures.map((c) => `${c.theme}/${c.state}`);
  assert.deepEqual(keys.sort(), ['dark/default', 'light/default']);
  assert.ok(
    existsSync(join(host, 'settings-security', 'dark', 'phone-default.webp')),
    'incoming media copied into host docs-out',
  );
});

test('docs-out merge prunes host captures for states the incoming scene dropped', () => {
  const host = tree([
    {
      id: 'settings-security',
      states: ['default', 'aapm'],
      captures: [
        cap({ state: 'aapm', file: 'settings-security/light/phone-aapm.webp' }),
        cap({ file: 'settings-security/light/phone-default.webp' }),
      ],
    },
  ]);
  const incoming = tree([
    {
      id: 'settings-security',
      states: ['default'],
      captures: [cap({ file: 'settings-security/light/phone-default.webp' })],
    },
  ]);

  mergeDocsOut(incoming, host);
  const merged = JSON.parse(
    readFileSync(join(host, 'docs-manifest.json'), 'utf8'),
  ).scenes.find((s) => s.id === 'settings-security');
  assert.deepEqual(merged.captures.map((c) => c.state), ['default']);
});

test('docs-out merge prunes every old state when the incoming scene declares none', () => {
  const host = tree([
    {
      id: 'settings-security',
      states: ['default', 'aapm'],
      captures: [
        cap({ state: 'aapm', file: 'settings-security/light/phone-aapm.webp' }),
        cap({ file: 'settings-security/light/phone-default.webp' }),
      ],
    },
  ]);
  const incoming = tree([
    {
      id: 'settings-security',
      states: [],
      captures: [cap({ file: 'settings-security/light/phone-default.webp' })],
    },
  ]);

  mergeDocsOut(incoming, host);
  const merged = JSON.parse(
    readFileSync(join(host, 'docs-manifest.json'), 'utf8'),
  ).scenes.find((s) => s.id === 'settings-security');
  assert.deepEqual(merged.captures.map((c) => c.state), ['default']);
});

test('docs-out merge creates host directories for a brand-new scene', () => {
  const host = tree([]);
  const incoming = tree([
    {
      id: 'browse-timeline',
      states: ['grid'],
      captures: [
        cap({ state: 'grid', file: 'browse-timeline/light/phone-grid.webp' }),
      ],
    },
  ]);
  mkdirSync(join(incoming, 'browse-timeline', 'light'), { recursive: true });
  writeFileSync(
    join(incoming, 'browse-timeline', 'light', 'phone-grid.webp'),
    'stub',
  );

  mergeDocsOut(incoming, host);
  assert.ok(
    existsSync(join(host, 'browse-timeline', 'light', 'phone-grid.webp')),
    'nested media dirs created for a scene the host never had',
  );
  const merged = JSON.parse(
    readFileSync(join(host, 'docs-manifest.json'), 'utf8'),
  ).scenes.find((s) => s.id === 'browse-timeline');
  assert.equal(merged.captures.length, 1);
});

/* --- recorded states: video + poster contract --------------------------- */

const KOTLIN_RECORDED = `
val sceneRegistry: List<DocsScene> = listOf(
    DocsScene(
        id = "viewer-photo",
        title = "Photo viewer",
        states = listOf("photo", "paging-demo"),
        recordedStates = listOf("paging-demo"),
    ) { },
)
`;

const videoCap = (over = {}) => cap({
  state: 'paging-demo',
  file: 'viewer-photo/light/phone-paging-demo.mp4',
  poster: 'viewer-photo/light/phone-paging-demo.webp',
  ...over,
});

test('recorded video keeps its poster, and the default alias carries it', () => {
  const f = fixture({
    kotlin: KOTLIN_RECORDED,
    fragments: {
      'viewer-photo': {
        id: 'viewer-photo',
        states: ['paging-demo', 'photo'],
        captures: [
          videoCap(),
          cap({
            state: 'photo',
            file: 'viewer-photo/light/phone-photo.webp',
          }),
        ],
      },
    },
  });
  f.touch('viewer-photo/light/phone-paging-demo.mp4');
  f.touch('viewer-photo/light/phone-paging-demo.webp');
  f.touch('viewer-photo/light/phone-photo.webp');

  const { scenes } = collectScenes(f);
  const scene = scenes.get('viewer-photo');
  const clip = scene.themes.light.phone['paging-demo'];
  assert.equal(clip.type, 'video');
  assert.equal(clip.poster, 'captures/viewer-photo/light/phone-paging-demo.webp');
  assert.equal(clip.posterFile, 'viewer-photo/light/phone-paging-demo.webp');
  // 'paging-demo' is the first declared state with an asset, so the default
  // alias resolves to the clip — poster fields must survive the alias.
  assert.equal(scene.themes.light.phone.default.type, 'video');
  assert.equal(scene.themes.light.phone.default.posterFile, clip.posterFile);
  // A still capture never carries poster fields.
  assert.equal(scene.themes.light.phone.photo.poster, undefined);
  assert.equal(scene.fidelity, 'capture');
});

test('a recording without its poster file stays pending, not half-capture', () => {
  const f = fixture({
    kotlin: KOTLIN_RECORDED,
    fragments: {
      'viewer-photo': {
        id: 'viewer-photo',
        states: ['photo', 'paging-demo'],
        captures: [videoCap()],
      },
    },
  });
  f.touch('viewer-photo/light/phone-paging-demo.mp4');
  // poster .webp deliberately absent

  const { scenes } = collectScenes(f);
  const scene = scenes.get('viewer-photo');
  assert.equal(scene.fidelity, 'pending');
  assert.deepEqual(scene.missing, ['viewer-photo/light/phone-paging-demo.webp']);
});

test('a poster path escaping docs-out rejects the whole video record', () => {
  const f = fixture({
    kotlin: KOTLIN_RECORDED,
    fragments: {
      'viewer-photo': {
        id: 'viewer-photo',
        states: ['photo', 'paging-demo'],
        captures: [
          videoCap({ poster: '../outside.webp' }),
          cap({ file: 'viewer-photo/light/phone-photo.webp', poster: 'viewer-photo/light/x.webp' }),
        ],
      },
    },
  });
  f.touch('viewer-photo/light/phone-paging-demo.mp4');

  const { scenes } = collectScenes(f);
  const scene = scenes.get('viewer-photo');
  assert.equal(scene.rejected.length, 2);
  assert.match(scene.rejected[0], /poster/);
  assert.match(scene.rejected[1], /poster/);
  assert.equal(scene.fidelity, 'pending');
});

test('a static recapture merge preserves the recorded clip and its poster', () => {
  const host = tree([
    {
      id: 'viewer-photo',
      states: ['photo', 'paging-demo'],
      captures: [
        cap({ state: 'photo', file: 'viewer-photo/light/phone-photo.webp' }),
        videoCap(),
      ],
    },
  ]);
  const incoming = tree([
    {
      id: 'viewer-photo',
      // Declared states still list the recorded state — a still recapture
      // must not prune the clip it never produced.
      states: ['photo', 'paging-demo'],
      captures: [
        cap({ state: 'photo', file: 'viewer-photo/light/phone-photo.webp' }),
      ],
    },
  ]);

  mergeDocsOut(incoming, host);
  const merged = JSON.parse(
    readFileSync(join(host, 'docs-manifest.json'), 'utf8'),
  ).scenes.find((s) => s.id === 'viewer-photo');
  const clip = merged.captures.find((c) => c.state === 'paging-demo');
  assert.ok(clip, 'recorded state survived a still-only recapture merge');
  assert.equal(clip.file, 'viewer-photo/light/phone-paging-demo.mp4');
  assert.equal(clip.poster, 'viewer-photo/light/phone-paging-demo.webp');
});

test('Kotlin recordedStates are parsed from the registry', () => {
  const root = mkdtempSync(join(tmpdir(), 'docs-kt-'));
  const registryKt = join(root, 'SceneRegistry.kt');
  writeFileSync(registryKt, KOTLIN_RECORDED);
  const scenes = parseKotlinScenes(registryKt);
  assert.deepEqual(
    scenes.get('viewer-photo').recordedStates,
    ['paging-demo'],
  );
});

test('an untouched fragment from an older APK does not prune a current recorded state', () => {
  const host = tree([{
    id: 'viewer-photo',
    states: ['photo', 'paging-demo'],
    captures: [videoCap()],
  }]);
  const incoming = tree([{
    id: 'viewer-photo',
    states: ['photo'],
    captures: [cap({ state: 'photo', file: 'viewer-photo/light/phone-photo.webp' })],
  }]);
  mergeDocsOut(incoming, host, new Map([
    ['viewer-photo', { recordedStates: ['paging-demo'] }],
  ]));
  const scene = JSON.parse(readFileSync(join(host, 'manifest/viewer-photo.json'), 'utf8'));
  assert.deepEqual(scene.states, ['photo', 'paging-demo']);
  assert.equal(scene.captures.find((c) => c.state === 'paging-demo').poster, videoCap().poster);
});

test('docs-out merge keeps incoming tablet logical dims truthful', () => {
  const host = tree([
    { id: 'settings-appearance', states: ['default'], captures: [cap()] },
  ]);
  const incoming = tree([
    {
      id: 'settings-appearance',
      states: ['default'],
      captures: [
        cap({
          device: 'tablet',
          file: 'settings-appearance/light/tablet-default.webp',
          width: 2520,
          height: 3540,
          widthDp: 840,
          heightDp: 1180,
        }),
      ],
    },
  ]);

  mergeDocsOut(incoming, host);
  const merged = JSON.parse(
    readFileSync(join(host, 'docs-manifest.json'), 'utf8'),
  ).scenes.find((s) => s.id === 'settings-appearance');
  const tablet = merged.captures.find((c) => c.device === 'tablet');
  assert.equal(tablet.widthDp, 840);
  assert.equal(tablet.heightDp, 1180);
  assert.equal(merged.captures.length, 2);
});

test('frontmatter parses the same from CRLF and LF checkouts', () => {
  const lf = '---\ntitle: Editor\narea: edit\npath:\n  - Media viewer\n  - Edit\n---\nBody\n';
  const crlf = lf.replaceAll('\n', '\r\n');
  const fromLf = parseFrontmatter(lf);
  const fromCrlf = parseFrontmatter(crlf);
  assert.equal(fromCrlf.data.title, 'Editor');
  assert.equal(fromCrlf.data.area, 'edit');
  assert.deepEqual(fromCrlf.data.path, ['Media viewer', 'Edit']);
  assert.deepEqual(fromCrlf.data, fromLf.data);
});
