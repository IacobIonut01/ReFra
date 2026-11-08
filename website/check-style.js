#!/usr/bin/env node
/**
 * check-style.js — deterministic docs-content gate (`npm run check:style`).
 *
 * Scans the files matched by style.config.json `scan.include` and enforces the
 * mechanics plus the structural contract the site relies on:
 *
 *   FAIL  missing frontmatter block
 *   FAIL  missing/empty required meta (title, summary; features also area, path)
 *   FAIL  inline style attributes in MDX (style="…" / style={…}) — styles live
 *         in the design system, not in content
 *   FAIL  curly quotes            (mechanics.quotes = "straight")
 *   FAIL  latin abbreviations     (mechanics.latinAbbrev = "never")
 *   FAIL  more than mechanics.emDashMaxPerPage em-dashes in one file
 *   WARN  heading that looks title-case (mechanics.headings = "sentence")
 *   WARN  bare number ≤ spellNumbersUpTo that may need spelling out
 *
 * Regions listed in style.config.json `scan.skip` are masked before checks:
 * frontmatter, fencedCode, inlineCode, jsxTags, linkDestinations, importExport,
 * urls. The register rules (voice, accuracy) stay human-checked — this gate is
 * only what can be verified deterministically (plan §8).
 */
import { existsSync, readFileSync } from 'node:fs';
import { join, sep } from 'node:path';
import { parseFrontmatter, walk, WEBSITE_ROOT } from './scripts/lib/docs.mjs';

const config = JSON.parse(readFileSync(join(WEBSITE_ROOT, 'style.config.json'), 'utf8'));
const mechanics = config.mechanics ?? {};
const skip = new Set(config.scan?.skip ?? []);

/* ------------------------------ file discovery ---------------------------- */

const globToRe = (pattern) =>
  new RegExp(
    `^${pattern
      .split('/')
      .map((seg) =>
        seg === '**'
          ? '.*'
          : seg.replace(/[.+^${}()|[\]\\]/g, '\\$&').replace(/\*/g, '[^/]*'),
      )
      .join('/')}$`,
  );

const patterns = (config.scan?.include ?? []).map(globToRe);
const files = walk(join(WEBSITE_ROOT, 'src', 'content')).filter((f) =>
  // Globs use '/', so Windows paths are normalised before matching.
  patterns.some((re) => re.test(f.slice(WEBSITE_ROOT.length + 1).replaceAll(sep, '/'))),
);

/* --------------------------------- masking -------------------------------- */

/** Mask inline spans (code, link destinations, bare URLs) from one line. */
function stripInline(line) {
  let out = line;
  if (skip.has('inlineCode')) out = out.replace(/`[^`]*`/g, '');
  if (skip.has('linkDestinations')) {
    out = out
      .replace(/\]\([^)]*\)/g, ']')
      .replace(/^\s*\[[^\]]*\]:\s*\S+.*$/, '')
      .replace(/^\s*\[[^\]]*\]:\s*$/, '');
  }
  if (skip.has('urls')) out = out.replace(/\bhttps?:\/\/\S+/g, '');
  return out;
}

/** Remove JSX/HTML tags, tracking {…} prop depth so a `>` inside an expression
 * does not end the tag. Multiline tags are supported via the shared state. */
function makeTagStripper() {
  let inTag = false;
  let braceDepth = 0;
  return (line) => {
    let work = line;
    let kept = '';
    while (work.length) {
      if (inTag) {
        if (braceDepth > 0) {
          const open = work.indexOf('{');
          const close = work.indexOf('}');
          if (open !== -1 && (close === -1 || open < close)) {
            braceDepth += 1;
            work = work.slice(open + 1);
            continue;
          }
          if (close === -1) {
            work = '';
            break;
          }
          braceDepth -= 1;
          work = work.slice(close + 1);
          continue;
        }
        const open = work.indexOf('{');
        const gt = work.indexOf('>');
        if (gt === -1) {
          work = '';
          break;
        }
        if (open !== -1 && open < gt) {
          braceDepth = 1;
          work = work.slice(open + 1);
          continue;
        }
        inTag = false;
        work = work.slice(gt + 1);
        continue;
      }
      const lt = work.search(/<\/?[A-Za-z]/);
      if (lt === -1) {
        kept += work;
        break;
      }
      kept += work.slice(0, lt);
      inTag = true;
      work = work.slice(lt + 1);
    }
    return kept;
  };
}

/**
 * Produce the checkable text for one file. `prose` lines have all skipped
 * regions removed (used for style mechanics); `tagText` keeps tag contents so
 * the inline-style scan can see attributes, with code still masked.
 */
function maskFile(text) {
  const { body, hasFrontmatter, data, rawLines } = parseFrontmatter(text);
  const fmOffset = text.split('\n').length - body.split('\n').length;
  const lines = body.split('\n');
  const prose = [];
  const tagText = [];
  const stripTags = makeTagStripper();
  let inFence = null;
  let inComment = false;

  for (const raw of lines) {
    const trimmed = raw.trim();

    if (inComment) {
      if (trimmed.includes('-->')) inComment = false;
      continue;
    }
    if (trimmed.startsWith('<!--')) {
      if (!trimmed.includes('-->')) inComment = true;
      continue;
    }

    const fence = trimmed.match(/^(`{3,}|~{3,})/);
    if (inFence) {
      if (fence && fence[1][0] === inFence[0] && fence[1].length >= inFence.length) inFence = null;
      continue;
    }
    if (fence && skip.has('fencedCode')) {
      inFence = fence[1];
      continue;
    }

    if (skip.has('importExport') && /^\s*(import|export)\s/.test(raw)) continue;

    tagText.push(stripInline(raw));
    prose.push(stripInline(skip.has('jsxTags') ? stripTags(raw) : raw));
  }

  return { prose, tagText, hasFrontmatter, data, fmOffset, rawLines };
}

/* --------------------------------- checks --------------------------------- */

const SMALL_WORDS = new Set([
  'a', 'an', 'and', 'as', 'at', 'but', 'by', 'for', 'in', 'of', 'on', 'or',
  'the', 'to', 'with', 'vs', 'nor', 'so', 'yet',
]);

const failures = [];
const warnings = [];
let fileCount = 0;

const isFeature = (file) => file.replaceAll(sep, '/').includes('/content/features/');

for (const file of files) {
  fileCount += 1;
  const rel = file.slice(WEBSITE_ROOT.length + 1);
  const text = readFileSync(file, 'utf8');
  const { prose, tagText, hasFrontmatter, data, fmOffset, rawLines } = maskFile(text);
  // `line` is the 1-based FILE line; prose/tagText indexes are body-relative,
  // so those call sites add fmOffset themselves.
  const report = (list, line, rule, detail = '') =>
    list.push(`${rel}:${line || '-'} ${rule}${detail ? ` — ${detail}` : ''}`);

  /* --- structure ---------------------------------------------------------- */
  if (!hasFrontmatter) {
    report(failures, 1, 'missing-frontmatter');
  } else {
    // `: ` inside an unquoted scalar is invalid YAML — the real parser fails at
    // build time, so catch it here with a clearer message.
    rawLines.forEach((line, i) => {
      const kv = line.match(/^(\w[\w-]*)\s*:\s*(.+)$/);
      if (!kv) return;
      const v = kv[2].trim();
      if (!/^["'[{]/.test(v) && /:\s/.test(v)) {
        report(failures, i + 2, 'frontmatter-yaml', `quote the value of "${kv[1]}" (it contains a colon)`);
      }
    });
    for (const key of ['title', 'summary']) {
      if (typeof data[key] !== 'string' || !data[key].trim()) {
        report(failures, 1, 'missing-meta', `"${key}" is required`);
      }
    }
    if (isFeature(file)) {
      if (typeof data.area !== 'string' || !data.area.trim()) {
        report(failures, 1, 'missing-meta', '"area" is required for features');
      }
      if (!Array.isArray(data.path) || data.path.length === 0) {
        report(failures, 1, 'missing-meta', '"path" must be a non-empty list for features');
      }
    }
  }

  /* --- inline styles (tags kept, code masked) ------------------------------ */
  tagText.forEach((line, i) => {
    if (/\bstyle\s*=\s*[{"']/.test(line)) {
      report(failures, i + 1 + fmOffset, 'inline-style', 'use design-system classes, not style= attributes');
    }
  });

  /* --- mechanics ------------------------------------------------------------ */
  if (mechanics.quotes === 'straight') {
    prose.forEach((line, i) => {
      if (/[“”‘’]/.test(line)) report(failures, i + 1 + fmOffset, 'quotes-straight');
    });
  }

  if (mechanics.latinAbbrev === 'never') {
    prose.forEach((line, i) => {
      const m = line.match(/\b(e\.g\.|i\.e\.|etc\.|viz\.|cf\.|vs\.|N\.B\.)/i);
      if (m) report(failures, i + 1 + fmOffset, 'latin-abbrev', `rewrite "${m[1]}" in words`);
    });
  }

  if (mechanics.emDash === 'sparing' && typeof mechanics.emDashMaxPerPage === 'number') {
    const count = prose.reduce((n, line) => n + (line.match(/—/g) ?? []).length, 0);
    if (count > mechanics.emDashMaxPerPage) {
      report(failures, 0, 'em-dash-rate', `${count} in one file (max ${mechanics.emDashMaxPerPage})`);
    }
  }

  if (mechanics.headings === 'sentence') {
    prose.forEach((line, i) => {
      const h = line.match(/^#{1,6}\s+(.*)$/);
      if (!h) return;
      const major = h[1]
        .replace(/[*_`]/g, '')
        .split(/\s+/)
        .slice(1)
        .map((w) => w.replace(/[^A-Za-z]/g, ''))
        .filter((w) => w && w !== w.toUpperCase() && !SMALL_WORDS.has(w.toLowerCase()));
      if (major.filter((w) => /^[A-Z]/.test(w)).length >= 2) {
        report(warnings, i + 1 + fmOffset, 'heading-sentence-case', h[1].trim());
      }
    });
  }

  if (typeof mechanics.spellNumbersUpTo === 'number') {
    prose.forEach((line, i) => {
      if (/^\s*(?:[-*+]|\d+[.)])\s/.test(line)) return; // list markers
      const nums = (line.match(/(?<![\w./:$-])\d{1,2}(?![\w.%:])/g) ?? []).filter(
        (n) => +n <= mechanics.spellNumbersUpTo,
      );
      if (nums.length) {
        report(warnings, i + 1 + fmOffset, 'number-spelling', `${[...new Set(nums)].join(', ')} may need spelling out`);
      }
    });
  }
}

/* --------------------------------- report ---------------------------------- */

for (const w of warnings) console.warn(`warn: ${w}`);
for (const f of failures) console.error(`FAIL: ${f}`);

console.log(
  `check-style: ${files.length} file(s), ${failures.length} failure(s), ${warnings.length} advisory warning(s)`,
);
if (!files.length) {
  console.error('check-style: no files matched style.config.json scan.include');
  process.exit(2);
}
process.exit(failures.length ? 1 : 0);
