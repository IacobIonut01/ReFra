// c2: high-res media swap — photo canvases -> LAKE, video preview/first clip -> BRIDGE,
// FIT main canvases, rename visible photo names to "Lake morning".
const PHOTO_FRAMES = ['3283:45617','3283:45643','3283:45679','3283:45753','3283:45784','3283:45815',
  '3248:44904','3248:44965','3248:45116','3248:45196','3248:45278','3248:45343',
  '3249:44998','3249:45083','3249:45148','3249:45209','3249:45275','3249:45338',
  '3251:45062','3251:45116','3251:45176','3251:45241','3251:45313',
  '3254:45129','3254:45199','3254:45264','3254:45325','3254:45391','3254:45446',
  '3256:45187','3256:45254','3256:45336','3256:45392','3256:45464',
  '3279:45782','3279:45830','3282:45734','3284:45692','3285:45869'];
const VIDEO_FRAMES = ['3283:45717','3261:45244','3263:45257','3264:45286','3265:45295','3265:45424','3265:45549',
  '3265:45683','3265:45810','3265:45935','3266:45377','3266:45499','3266:45636','3266:45744','3266:45874',
  '3268:45453','3268:45589','3268:45713','3268:45843','3269:45503','3269:45581','3269:45653','3269:45740',
  '3269:45999','3269:46119','3269:46168','3279:45537','3279:45598','3279:45649','3284:45828','3285:45920','3284:46100','3279:45706'];

function swapFill(n, from, to, fit) {
  if (!Array.isArray(n.fills)) return false;
  let changed = false;
  const nf = n.fills.map(p => {
    if (p.type === 'IMAGE' && p.imageHash === from) {
      changed = true;
      const q = Object.assign({}, p, { imageHash: to });
      if (fit) q.scaleMode = 'FIT';
      return q;
    }
    return p;
  });
  if (changed) n.fills = nf;
  return changed;
}
function inFx(n) {
  for (let p = n; p; p = p.parent) {
    if (/cropbox|zoomed|sharp|blurred|pip|^ov$|nav|magnifier|^lk\/|vignette|mask|thumb|looks|tile/.test(p.name)) return true;
    if (p.type === 'PAGE') return false;
  }
  return false;
}

let swapped = 0, fitted = 0, renamed = 0;
for (const id of PHOTO_FRAMES) {
  const root = await figma.getNodeByIdAsync(id); if (!root) { err(id); continue; }
  for (const n of root.findAll(() => true)) {
    // main canvas photos -> lake, FIT; overlays/thumbnails keep FILL
    const big = n.width >= 300 && n.height >= 200 && !inFx(n);
    if (swapFill(n, OLD_BASE, LAKE, big)) { swapped++; if (big) fitted++; }
    if (n.type === 'TEXT') {
      const s = n.characters;
      if (/Evening light/.test(s)) { n.characters = s.replaceAll('Evening light', 'Lake morning'); renamed++; }
      else if (s === 'Base photo' || /^Base( · |$)/.test(s)) { /* keep generic */ }
    }
  }
}
for (const id of VIDEO_FRAMES) {
  const root = await figma.getNodeByIdAsync(id); if (!root) { err(id); continue; }
  for (const n of root.findAll(() => true)) {
    // video: preview/frame + first clip (Harbour) -> bridge
    if (/^(frame|preview|clip\/Harbour)/.test(n.name) || (n.width >= 300 && n.height >= 200 && /frame|photo|preview/.test(n.name))) {
      if (swapFill(n, OLD_BASE, BRIDGE, false)) swapped++;
    }
  }
}
// rename 'Base photo' layer labels to 'Lake morning' where it is the base layer
const baseRe = /Base photo/;
for (const id of PHOTO_FRAMES) {
  const root = await figma.getNodeByIdAsync(id); if (!root) continue;
  for (const t of root.findAllWithCriteria({ types: ['TEXT'] })) {
    if (baseRe.test(t.characters)) { t.characters = t.characters.replace('Base photo', 'Lake morning'); renamed++; }
  }
}
RESULT.summary = { swapped, fitted, renamed };
return RESULT;
