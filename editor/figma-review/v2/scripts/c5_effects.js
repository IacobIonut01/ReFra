// c5: depicted effects + semantics — vignette gradient, lens blur mask,
// healing connector, looks filters, magnifier crops, Screen65 composites,
// chroma eyedropper, preview play removal, v-layers labels, boolean switches,
// blend slider track, v-speed constant 2x.
const ALLV = ['3261:45244','3263:45257','3264:45286','3265:45295','3265:45424','3265:45549','3265:45683',
  '3265:45810','3265:45935','3266:45377','3266:45499','3266:45636','3266:45744','3266:45874','3268:45453',
  '3268:45589','3268:45713','3268:45843','3269:45503','3269:45581','3269:45653','3269:45740','3269:45999',
  '3269:46119','3269:46168','3279:45537','3279:45598','3279:45649','3284:45828','3285:45920','3279:45706','3284:46100'];
const ALLV2 = ['3269:45999','3265:45810','3251:45116','3269:45740','3279:45706','3284:46100','3269:45581','3269:45653','3268:45713','3251:45313','3279:45598'];
const swComp = (await figma.getNodeByIdAsync('3244:38562')).mainComponent;
const switchesDone = [];


// --- p-vignette: radial overlay, ellipse = thin guide
{
  const f = await figma.getNodeByIdAsync('3254:45391');
  const vig = f.findOne(n => n.name === 'vignette');
  if (vig) {
    vig.fills = [{
      type: 'GRADIENT_RADIAL',
      gradientTransform: [[1, 0, 0], [0, 1, 0]],
      gradientStops: [
        { position: 0, color: { r: 0, g: 0, b: 0, a: 0 } },
        { position: 0.55, color: { r: 0, g: 0, b: 0, a: 0 } },
        { position: 1, color: { r: 0, g: 0, b: 0, a: 0.62 } },
      ],
    }];
    ok('vignette radial');
  }
  const inner = f.findOne(n => n.name === 'inner');
  if (inner) { inner.fills = []; inner.strokes = [bp('primary')]; inner.strokeWeight = 1.5; }
}

// --- p-blur: blurred base + aligned sharp duplicate inside focus mask
{
  const f = await figma.getNodeByIdAsync('3254:45446');
  const canvas = f.findOne(n => n.name === 'canvas');
  const photo = canvas.findOne(n => n.name === 'photo');
  const blurred = canvas.findOne(n => n.name === 'blurred');
  const sharp = canvas.findOne(n => n.name === 'sharp');
  if (photo && blurred && sharp) {
    blurred.fills = photo.fills;
    blurred.effects = [{ type: 'LAYER_BLUR', radius: 16, visible: true }];
    // sharp: ellipse clip mask same position; child full-size aligned image
    for (const c of [...sharp.children]) c.remove();
    sharp.clipsContent = true; sharp.cornerRadius = 999;
    sharp.fills = [];
    const img = figma.createRectangle();
    img.name = 'sharp-img';
    sharp.appendChild(img);
    img.resize(photo.width, photo.height);
    img.fills = photo.fills;
    img.x = photo.x - sharp.x; img.y = photo.y - sharp.y;
    ok('blur mask ' + Math.round(sharp.width) + 'x' + Math.round(sharp.height));
  }
}

// --- p-healing: real connector between src/dst centres
{
  const f = await figma.getNodeByIdAsync('3254:45264');
  const canvas = f.findOne(n => n.name === 'canvas');
  const src = canvas.findOne(n => n.name === 'src');
  const dst = canvas.findOne(n => n.name === 'dst');
  const old = canvas.findOne(n => n.name === 'link');
  if (src && dst) {
    const sx = src.x + src.width / 2, sy = src.y + src.height / 2;
    const dx = dst.x + dst.width / 2, dy = dst.y + dst.height / 2;
    const mx = (sx + dx) / 2, my = Math.min(sy, dy) - 30;
    const v = figma.createVector();
    v.name = 'link';
    canvas.appendChild(v);
    v.vectorPaths = [{ windingRule: 'NONE', data: `M ${sx} ${sy} Q ${mx} ${my} ${dx} ${dy}` }];
    v.strokes = [bp('primary')]; v.strokeWeight = 2; v.strokeCap = 'ROUND';
    v.x = 0; v.y = 0;
    if (old) old.remove();
    ok('healing link ' + Math.round(sx) + ',' + Math.round(sy) + '->' + Math.round(dx) + ',' + Math.round(dy));
  }
}

// --- p-looks: differentiate thumbnails via image filters
{
  const f = await figma.getNodeByIdAsync('3249:45275');
  const looks = f.findOne(n => n.name === 'looks');
  const FILT = { 'Original': null, 'Soft': { contrast: -0.3, exposure: 0.05 }, 'Film': { contrast: 0.18, saturation: -0.2, temperature: 0.08 }, 'Mono': { saturation: -1 }, 'Warm': { temperature: 0.4 } };
  for (const cell of looks.children) {
    const nm = cell.name.split('/').pop();
    const fil = FILT[nm];
    if (!fil) continue;
    for (const im of cell.findAll(x => Array.isArray(x.fills) && x.fills.some(p => p.type === 'IMAGE'))) {
      im.fills = im.fills.map(p => p.type === 'IMAGE' ? Object.assign({}, p, { filters: Object.assign({}, p.filters || {}, fil) }) : p);
    }
    ok('look ' + nm);
  }
}

// --- magnifiers: aligned zoomed crop via imageTransform on same image
async function magnify(fid, z, u, v) {
  const f = await figma.getNodeByIdAsync(fid);
  const mag = f.findOne(n => n.name === 'magnifier');
  const photo = f.findOne(n => n.name === 'photo');
  if (!mag || !photo) { warn('no magnifier ' + fid); return; }
  const imgP = photo.fills.find(p => p.type === 'IMAGE');
  if (!imgP) { warn('no img ' + fid); return; }
  mag.clipsContent = true; mag.cornerRadius = 999;
  let im = mag.children.find(c => c.fills && Array.isArray(c.fills) && c.fills.some(p => p.type === 'IMAGE'));
  if (!im) { im = figma.createRectangle(); mag.insertChild(0, im); }
  im.resize(mag.width, mag.height);
  im.x = 0; im.y = 0;
  im.fills = [Object.assign({}, imgP, { scaleMode: 'CROP', imageTransform: [[z, 0, 0.5 - z * u], [0, z, 0.5 - z * v]] })];
  ok('magnify ' + fid);
}
await magnify('3254:45325', 3, 0.45, 0.4);   // p-details
await magnify('3249:45338', 3, 0.5, 0.45);   // p-grain
await magnify('3248:45343', 2, 0.5, 0.45);   // p-white-balance

// --- composites: overlay image SCREEN .65 everywhere + metadata text
for (const fid of ['3256:45187', '3283:45679', '3282:45734', '3284:45692', '3285:45869']) {
  const f = await figma.getNodeByIdAsync(fid); if (!f) continue;
  const ovs = f.findAll(n => Array.isArray(n.fills) && n.fills.some(p => p.type === 'IMAGE') && /overlay|palm|^ov$|second/i.test(n.name));
  for (const o of ovs) { o.blendMode = 'SCREEN'; o.opacity = 0.65; }
  for (const t of f.findAllWithCriteria({ types: ['TEXT'] })) {
    t.characters = t.characters.replace(/Multiply[^·]*·?\s*\d*%?/g, 'Screen · 65%').replace(/Normal\s*·?\s*100%|Normal 100%|100% Normal/g, 'Screen · 65%');
  }
  if (ovs.length) ok('screen65 ' + fid + ' x' + ovs.length); else warn('no overlay ' + fid);
}

// --- v-chroma: no greenscreen stand-in; footage + eyedropper state
{
  const f = await figma.getNodeByIdAsync('3266:45874');
  const green = f.findAll(n => Array.isArray(n.fills) && n.fills.some(p => p.type === 'SOLID' && p.color.g > 0.5 && p.color.r < 0.4));
  for (const g of green) { if (g.width > 100 && g.height > 100) { g.fills = [{ type: 'IMAGE', imageHash: BRIDGE, scaleMode: 'FILL', imageTransform: [[1, 0, 0], [0, 1, 0]] }]; ok('chroma footage ' + g.id); } }
  const canvas = f.findOne(n => /preview|canvas/.test(n.name));
  if (canvas && !canvas.findOne(n => n.name === 'hit/pick-colour')) {
    const chip = box(canvas, 148, 48, { name: 'hit/pick-colour', dir: 'H', main: 'CENTER', gap: 8, fill: [bp('raised', 0.92)], stroke: 'outlineVariant', r: 24 });
    await icon(chip, 'colorize', 20, 'primary');
    chip.appendChild(T('Pick a colour', 14, 'Medium'));
    chip.layoutPositioning = 'ABSOLUTE';
    chip.x = canvas.width - 160; chip.y = canvas.height - 60;
    ok('chroma pick chip');
  }
}

// --- remove big preview play buttons (transport owns play)
for (const fid of ALLV) {
  const f = await figma.getNodeByIdAsync(fid); if (!f) continue;
  for (const pv of f.findAll(n => n.name === 'preview')) {
    for (const c of [...pv.children]) if (c.name === 'play') { c.remove(); ok('play removed ' + fid); }
  }
}

// --- v-layers: human names
{
  const f = await figma.getNodeByIdAsync('3266:45636');
  const NAMES = { text_fields: 'Title — Golden hour', image: 'Palm overlay', movie: 'Harbour clip', mic: 'Voice-over', music_note: 'Music', headphones: 'Ambience', volume_off: 'Source audio (muted)' };
  for (const r of f.findAll(n => /^hit\/|^lr\//.test(n.name) && n.type === 'FRAME' && n.layoutMode)) {
    const key = r.name.split('/').pop();
    if (!NAMES[key]) continue;
    const nmBox = r.findOne(c => c.name === 'nm');
    const txt = nmBox && nmBox.findOne(c => c.type === 'TEXT');
    if (txt) { txt.characters = NAMES[key]; }
    r.name = 'hit/layer-' + NAMES[key].replace(/[^A-Za-z]+/g, '-').slice(0, 20);
    ok('vlayer ' + key + '->' + NAMES[key]);
  }
}

// --- boolean switches: 'Off' values in sw/row-like parents + specific chips
for (const fid of ALLV2) {
  const f = await figma.getNodeByIdAsync(fid); if (!f) continue;
  for (const t of f.findAllWithCriteria({ types: ['TEXT'] })) {
    if (!/^(Off|On)$/.test(t.characters.trim())) continue;
    const row = t.parent;
    if (!row || row.type !== 'FRAME') continue;
    const label = row.children.find(c => c.type === 'TEXT' && c !== t);
    t.remove();
    const sw = swComp.createInstance();
    sw.name = 'hit/switch';
    row.appendChild(sw);
    try { sw.fills = [bp('primary')]; sw.strokes = [bp('outlineVariant')]; } catch (e) {}
    const hs = sw.findOne(c => c.name === 'Handle shape'); if (hs) hs.fills = [bp('onPrimary')];
    if (!row.name.startsWith('hit/')) row.name = 'hit/sw-' + (label ? label.characters.slice(0, 16).replace(/\W+/g, '-') : 'toggle');
    if (row.height < 48) try { row.resize(row.width, 48); } catch (e) {}
    switchesDone.push(fid + ':' + row.name);
  }
}

// --- p-blend: giant slider track -> thin track + thumb in 48 lane
{
  const f = await figma.getNodeByIdAsync('3256:45464');
  const cand = f.findAll(n => (n.type === 'FRAME' || n.type === 'RECTANGLE') && n.width > 140 && n.height >= 20 && n.height <= 64 && n.name !== 'sp' && !/^hit\//.test(n.name) && n.parent && /row|slider|opacity|blend|inspec|panel/i.test(n.parent.name + '/' + n.name));
  for (const t of cand) {
    // keep as 48px lane? only shrink paint: add thumb circle
    try {
      const cy = t.y + t.height / 2;
      t.resize(t.width, 6);
      if (t.parent.layoutMode === 'NONE') t.y = cy - 3;
      const th = figma.createEllipse();
      th.name = 'thumb';
      t.parent.appendChild(th);
      th.resize(20, 20);
      th.fills = [bp('primary')];
      th.x = t.x + t.width * 0.65 - 10; th.y = cy - 10;
      if (t.parent.layoutMode !== 'NONE') th.layoutPositioning = 'ABSOLUTE';
      ok('blend track ' + t.id);
    } catch (e) { warn('blend ' + t.id); }
  }
}

// --- v-speed: constant 2x, 8.4s -> 4.2s, flat line + source/timeline distinction
{
  const f = await figma.getNodeByIdAsync('3265:45810');
  const insp = f.findOne(n => n.name === 'inspector');
  for (const t of insp.findAllWithCriteria({ types: ['TEXT'] })) {
    if (/Speed · /.test(t.characters)) t.characters = 'Speed · 2× · constant';
    if (/12\.0 s/.test(t.characters)) t.characters = t.characters.replace('12.0 s', '4.2 s');
  }
  const graph = insp.findOne(n => n.name === 'graph');
  if (graph) {
    const ramp = graph.findOne(n => n.type === 'VECTOR' || n.name === 'ramp');
    if (ramp) {
      ramp.name = 'const';
      ramp.vectorPaths = [{ windingRule: 'NONE', data: 'M 0 8 L 320 8' }];
      ramp.resize(320, 16);
      ramp.strokes = [bp('primary')]; ramp.strokeWeight = 2;
    }
    const note = graph.findOne(n => n.name === 'srcnote') || T('Source 8.4 s → timeline 4.2 s · 2× constant', 12);
    if (!note.parent) { note.name = 'srcnote'; note.fills = [bp('onSurfaceVariant')]; graph.appendChild(note); }
    ok('speed constant');
  }
}
RESULT.counts = { switchesDone };
return RESULT;
