#!/usr/bin/env node
/**
 * publish-live.mjs — build + verify + deploy the wasmJs live docs bundle.
 *
 * Steps:
 *   1. `./gradlew :app:docs:showcase:wasmJsBrowserDistribution` from the repo root.
 *   2. Stage app/docs/showcase/build/dist/wasmJs/productionExecutable into a
 *      temporary sibling of public/live/ (README.md carried over).
 *   3. Serve the STAGED directory and drive it with Playwright: the bundle must
 *      post `docs:scenes` with a valid catalog and `docs:ready` for
 *      `?scene=settings-appearance`, and the canvas must render non-uniform
 *      pixels. The existing public/live/ stays untouched until this passes.
 *   4. Write docs-live.json into the stage, then swap directories
 *      (current → backup, stage → live, delete backup). If the swap fails
 *      part-way the backup is restored so a stale catalog can never claim a
 *      bundle that does not boot.
 *
 * Fails non-zero on any build/protocol error.
 *
 * Prereqs: `npm install` (Playwright + pngjs) and `npx playwright install chromium`.
 */
import { execFileSync } from 'node:child_process';
import {
  cpSync,
  existsSync,
  mkdirSync,
  mkdtempSync,
  readdirSync,
  readFileSync,
  renameSync,
  rmSync,
  statSync,
  writeFileSync,
} from 'node:fs';
import { createServer } from 'node:http';
import { extname, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const WEBSITE_ROOT = resolve(fileURLToPath(import.meta.url), '..', '..');
const REPO_ROOT = resolve(WEBSITE_ROOT, '..');
const LIVE_DIR = join(WEBSITE_ROOT, 'public', 'live');
const DIST = join(
  REPO_ROOT,
  'app', 'docs', 'showcase', 'build', 'dist', 'wasmJs', 'productionExecutable',
);
const PROBE_SCENE = 'settings-appearance';
const PROBE_W = 412;
const PROBE_H = 915;

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.wasm': 'application/wasm',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
};

function fail(msg) {
  throw new Error(msg);
}

/** Uniformity gate: sample a ~64x64 lattice of pixels; all-equal means blank. */
function assertNotUniform(png, label) {
  const first = png.data.subarray(0, 4);
  let different = false;
  const stepY = Math.max(1, Math.floor(png.height / 64));
  const stepX = Math.max(1, Math.floor(png.width / 64));
  for (let y = 0; y < png.height && !different; y += stepY) {
    for (let x = 0; x < png.width; x += stepX) {
      const i = (y * png.width + x) * 4;
      if (
        png.data[i] !== first[0] ||
        png.data[i + 1] !== first[1] ||
        png.data[i + 2] !== first[2] ||
        png.data[i + 3] !== first[3]
      ) {
        different = true;
        break;
      }
    }
  }
  if (!different) throw new Error(`${label} is a uniform frame`);
}

async function main() {
  /* 1 — build the wasmJs distribution */
  console.log('publish-live: building :app:docs:showcase:wasmJsBrowserDistribution …');
  // Windows runs the batch wrapper by absolute path through cmd.exe.
  const task = ':app:docs:showcase:wasmJsBrowserDistribution';
  const [cmd, args] = process.platform === 'win32'
    ? ['cmd.exe', ['/d', '/c', join(REPO_ROOT, 'gradlew.bat'), task]]
    : ['./gradlew', [task]];
  execFileSync(cmd, args, { cwd: REPO_ROOT, stdio: 'inherit' });
  if (!existsSync(join(DIST, 'index.html')) || !existsSync(join(DIST, 'docs-showcase.js'))) {
    fail(`Gradle distribution incomplete under ${DIST}`);
  }

  /* 2 — stage the distribution next to public/live */
  const stage = mkdtempSync(join(dirname(LIVE_DIR), '.live-next-'));
  const backup = `${join(dirname(LIVE_DIR), '.live-prev-')}${process.pid}`;
  let server;
  let browser;
  try {
    for (const name of readdirSync(DIST)) {
      cpSync(join(DIST, name), join(stage, name), { recursive: true });
    }
    const readme = join(LIVE_DIR, 'README.md');
    if (existsSync(readme)) cpSync(readme, join(stage, 'README.md'));

    /* 3 — serve the stage and probe it with Playwright */
    server = createServer((req, res) => {
      const url = new URL(req.url, 'http://localhost');
      const path = join(stage, url.pathname === '/' ? 'index.html' : url.pathname);
      if (!path.startsWith(stage) || !existsSync(path) || !statSync(path).isFile()) {
        res.writeHead(404).end('not found');
        return;
      }
      res.writeHead(200, { 'content-type': MIME[extname(path)] ?? 'application/octet-stream' });
      res.end(readFileSync(path));
    });
    await new Promise((r) => server.listen(0, '127.0.0.1', r));
    const port = server.address().port;
    console.log(`publish-live: probing bundle on http://127.0.0.1:${port}/`);

    const { chromium } = await import('@playwright/test');
    const { PNG } = await import('pngjs');
    // Like check-ui.mjs: the bundled Chromium first, an installed Chrome when it is missing.
    // GPU-less CI runners need --enable-unsafe-swiftshader or Chrome refuses the
    // software WebGL context Skiko renders through and the bundle never boots.
    const launchArgs = ['--enable-unsafe-swiftshader'];
    try {
      browser = await chromium.launch({ args: launchArgs });
    } catch (err) {
      if (!/executable/i.test(String(err))) throw err;
      browser = await chromium.launch({ channel: 'chrome', args: launchArgs });
    }
    const page = await browser.newPage({ viewport: { width: PROBE_W, height: PROBE_H } });

    await page.addInitScript(() => {
      window.__docsMessages = [];
      window.addEventListener('message', (event) => {
        try {
          const data = typeof event.data === 'string' ? JSON.parse(event.data) : event.data;
          window.__docsMessages.push(data);
        } catch { /* non-json */ }
      });
    });

    await page.goto(`http://127.0.0.1:${port}/index.html?scene=${PROBE_SCENE}&theme=light`);
    await page.waitForFunction(
      () => window.__docsMessages?.some((m) => m?.type === 'docs:ready'),
      undefined,
      { timeout: 180_000 },
    );
    const captured = await page.evaluate(() => window.__docsMessages);

    const catalog = captured.find((m) => m?.type === 'docs:scenes');
    if (!catalog || !Array.isArray(catalog.scenes) || catalog.scenes.length === 0) {
      fail('bundle booted without a docs:scenes catalog');
    }
    for (const scene of catalog.scenes) {
      for (const field of ['id', 'title', 'pathTrace', 'states', 'presentation', 'widthDp', 'heightDp']) {
        if (!(field in scene)) fail(`docs:scenes entry missing "${field}": ${JSON.stringify(scene)}`);
      }
    }
    const ready = captured.find((m) => m?.type === 'docs:ready');
    if (ready?.scene !== PROBE_SCENE) fail(`docs:ready reported "${ready?.scene}", expected ${PROBE_SCENE}`);

    /* non-uniform gate: decode actual pixels — compressed byte entropy is not
       a content check */
    const shot = await page.screenshot({ fullPage: false });
    const decoded = PNG.sync.read(shot);
    if (decoded.width !== PROBE_W || decoded.height !== PROBE_H) {
      fail('Unexpected live probe viewport');
    }
    assertNotUniform(decoded, `${PROBE_SCENE} live probe`);

    /* publish the verified catalog into the stage */
    const catalogOut = {
      schemaVersion: 1,
      generatedAt: new Date().toISOString(),
      scenes: catalog.scenes.map((s) => ({
        id: s.id,
        title: s.title,
        pathTrace: s.pathTrace,
        states: s.states,
        presentation: s.presentation,
        widthDp: s.widthDp,
        heightDp: s.heightDp,
      })),
    };
    writeFileSync(join(stage, 'docs-live.json'), `${JSON.stringify(catalogOut, null, 2)}\n`);

    /* 4 — atomic swap: current → backup, stage → live, delete backup */
    const hadLive = existsSync(LIVE_DIR);
    if (hadLive) renameSync(LIVE_DIR, backup);
    try {
      renameSync(stage, LIVE_DIR);
    } catch (swapError) {
      if (hadLive && existsSync(backup)) renameSync(backup, LIVE_DIR);
      throw swapError;
    }
    if (hadLive && existsSync(backup)) rmSync(backup, { recursive: true, force: true });
    console.log(
      `publish-live: bundle boot verified; ${catalogOut.scenes.length} scenes registered → public/live/docs-live.json`,
    );
  } finally {
    await browser?.close().catch(() => {});
    server?.close();
    rmSync(stage, { recursive: true, force: true });
    // A backup only survives if the rename crashed mid-swap — restore it.
    if (existsSync(backup) && !existsSync(LIVE_DIR)) renameSync(backup, LIVE_DIR);
    if (existsSync(backup)) rmSync(backup, { recursive: true, force: true });
  }
}

try {
  await main();
} catch (error) {
  console.error(`publish-live: ${error instanceof Error ? error.message : error}`);
  process.exitCode = 1;
}
