// c6b: x-mixer-desktop rebuild — vertical faders, dB taper, meters, 48px M/S.
// v-routing: real channel faders + dropdowns.
const swComp = (await figma.getNodeByIdAsync('3244:38562')).mainComponent;

const dbY = db => db >= 0 ? (6 - db) * 10 : 60 + (-db) * (db >= -12 ? 10 : db >= -24 ? 7.5 : 2.5);
// taper: +6->0, 0->60, -12->180, -24->270, -60->360
function taperY(db) {
  const k = [[6, 0], [0, 60], [-12, 180], [-24, 270], [-60, 360]];
  if (db >= 6) return 0; if (db <= -60) return 360;
  for (let i = 0; i < k.length - 1; i++) {
    if (db <= k[i][0] && db >= k[i + 1][0]) {
      const t = (db - k[i][0]) / (k[i + 1][0] - k[i][0]);
      return k[i][1] + t * (k[i + 1][1] - k[i][1]);
    }
  }
  return 0;
}
function tickCol(parent, w) {
  const c = box(parent, w, 372, { name: 'ticks', dir: 'NONE' });
  for (const [db, y] of [[6, 0], [0, 60], [-12, 180], [-24, 270], [-60, 360]]) {
    const t = T((db > 0 ? '+' : '') + db, 12);
    t.fills = [bp('onSurfaceVariant')];
    c.appendChild(t); t.x = 0; t.y = y + 6 - 8;
  }
  return c;
}
function vFader(parent, db) {
  const lane = box(parent, 48, 372, { name: 'hit/fader', dir: 'NONE' });
  const tr = box(lane, 6, 360, { name: 'track', dir: 'NONE', fill: [bp('secondaryContainer')], r: 3 });
  tr.x = 21; tr.y = 6;
  const y = taperY(db) + 6;
  const fl = box(lane, 6, Math.max(6, y - 6), { name: 'fill', dir: 'NONE', fill: [bp('primary')], r: 3 });
  fl.x = 21; fl.y = 6;
  const th = figma.createEllipse(); th.name = 'thumb'; th.resize(24, 24); th.fills = [bp('primary')];
  lane.appendChild(th); th.x = 12; th.y = y - 12;
  return lane;
}
function vMeter(parent, h, frac) {
  const m = box(parent, 8, h, { name: 'meter', dir: 'NONE', fill: [bp('secondaryContainer')], r: 3 });
  const lvl = box(m, 8, Math.round(h * frac), { name: 'lvl', dir: 'NONE', fill: [bp('primary')], r: 3 });
  lvl.y = h - Math.round(h * frac);
  return m;
}
function db48(parent, w, label, active) {
  const b = box(parent, w, 48, { name: 'hit/' + label.replace(/\W+/g, '-'), dir: 'H', main: 'CENTER', cross: 'CENTER', fill: active ? [bp('primary')] : [bp('raised')], stroke: active ? undefined : 'outlineVariant', r: 10 });
  const t = T(label, 13, 'Medium'); t.fills = [bp(active ? 'onPrimary' : 'onSurface')]; b.appendChild(t);
  return b;
}
function dField(parent, w, label) {
  const f = box(parent, w, 40, { name: 'hit/field-' + label.replace(/\W+/g, '-'), dir: 'H', main: 'CENTER', cross: 'CENTER', gap: 4, fill: [bp('raised')], stroke: 'outlineVariant', r: 8 });
  f.appendChild(T(label, 13, 'Medium'));
  return f;
}

// --- desktop mixer ---
{
  const f = await figma.getNodeByIdAsync('3284:46100');
  const strips = f.findOne(n => n.name === 'strips');
  for (const c of [...strips.children]) c.remove();
  strips.layoutMode = 'VERTICAL'; strips.itemSpacing = 12;
  const chans = box(strips, 928, 700, { name: 'channels', dir: 'H', gap: 12, px: 8 });
  const CH = [
    ['A1 · Voice-over', -3, 1], ['A2 · Music', -9, 2], ['A3 · Ambience', -12, 2], ['A4 · Source', -18, 2, true], ['Master', 0, 2, false, true],
  ];
  for (const [nm, db, meters, muted, master] of CH) {
    const s = box(chans, 172, 700, { name: 'strip/' + nm, dir: 'V', gap: 10, py: 8, cross: 'CENTER', fill: [bp('raised')], r: 12 });
    s.appendChild(T(nm, 14, 'Medium'));
    const fz = box(s, 160, 372, { name: 'fz', dir: 'H', gap: 6, cross: 'MIN' });
    tickCol(fz, 30);
    vFader(fz, db);
    if (meters === 1) vMeter(fz, 360, 0.55);
    else { const mm = box(fz, 20, 360, { name: 'meters', dir: 'H', gap: 4 }); vMeter(mm, 360, muted ? 0 : 0.6); vMeter(mm, 360, muted ? 0 : 0.5); }
    dField(s, 96, (db > 0 ? '+' : '') + db + ' dB');
    if (master) {
      const lr = box(s, 150, 48, { name: 'hit/sw-limiter', dir: 'H', gap: 8, main: 'CENTER' });
      lr.appendChild(T('Limiter', 13));
      const sw = swComp.createInstance(); sw.name = 'hit/switch'; lr.appendChild(sw);
      sw.fills = [bp('primary')]; sw.strokes = [bp('outlineVariant')];
      const hs = sw.findOne(c => c.name === 'Handle shape'); if (hs) hs.fills = [bp('onPrimary')];
    } else if (muted) {
      db48(s, 68, 'Unmute');
      db48(s, 68, 'Solo');
    } else {
      const ms = box(s, 150, 48, { name: 'ms', dir: 'H', gap: 8, main: 'CENTER' });
      db48(ms, 68, 'Mute'); db48(ms, 68, 'Solo');
    }
    dField(s, 110, 'Pan · Centre');
  }
  const tr = box(strips, 928, 64, { name: 'transport', dir: 'H', gap: 16, main: 'CENTER' });
  const pa = await stagedMain('play_arrow');
  const pb = box(tr, 56, 56, { name: 'hit/audition-play', dir: 'H', main: 'CENTER', cross: 'CENTER', fill: [bp('primary')], r: 28 });
  const pi = pa.createInstance(); pi.resize(28, 28); for (const v of pi.findAllWithCriteria({ types: ['VECTOR'] })) v.fills = [bp('onPrimary')]; pb.appendChild(pi);
  tr.appendChild(T('Audition · soloed channels', 13));
  ok('desktop mixer rebuilt');
}

// --- v-routing phone: channel faders + dropdowns ---
{
  const f = await figma.getNodeByIdAsync('3269:45999');
  const main = f.findOne(n => n.name === 'main');
  for (const ch of main.findAll(n => /^ch\//.test(n.name))) {
    const g = ch.findOne(c => c.name === 'g');
    if (g) {
      // convert gain box into fader row
      for (const c of [...g.children]) c.remove();
      g.name = 'hit/fader-' + ch.name.split('/').pop();
      g.layoutMode = 'HORIZONTAL'; g.counterAxisAlignItems = 'CENTER'; g.itemSpacing = 6;
      g.fills = [];
      try { g.resize(170, 40); } catch (e) {}
      const lane = box(g, 110, 40, { name: 'lane', dir: 'NONE' });
      const tr = box(lane, 110, 6, { name: 'track', dir: 'NONE', fill: [bp('secondaryContainer')], r: 3 }); tr.y = 17;
      const fl = box(lane, 36, 6, { name: 'fill', dir: 'NONE', fill: [bp('primary')], r: 3 }); fl.y = 17;
      const th = figma.createEllipse(); th.resize(16, 16); th.fills = [bp('primary')]; lane.appendChild(th); th.x = 30; th.y = 12;
      g.appendChild(T('−18 dB', 12, 'Medium'));
    }
  }
  // add dropdown rows after row/Pan
  const pan = main.children.find(c => c.name.startsWith('hit/param-Pan') || c.name === 'row/Pan · Output');
  if (pan) {
    const idx = main.children.indexOf(pan) + 1;
    for (const [i, [lbl, val]] of [['Left', 'Master left'], ['Right', 'Master right']].entries()) {
      const r = box(main, 380, 48, { name: 'hit/param-' + lbl + '-route', dir: 'H', gap: 8 });
      r.appendChild(T(lbl + ' →', 14));
      const sp = box(r, 0, 4, { name: 'sp', dir: 'H' }); sp.layoutSizingHorizontal = 'FILL';
      const fld = dField(r, 150, val);
      await icon(fld, 'unfold_more', 18, 'onSurfaceVariant');
      main.insertChild(idx + i, r);
    }
    ok('routing dropdowns');
  }
}
return RESULT;
