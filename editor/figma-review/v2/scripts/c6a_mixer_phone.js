// c6a: phone mixer rebuild — 4 track cards + Master, 112px cards,
// row1 name180+Mute80+Solo80; row2 slider168+gain64+pan108.
// Footer: Back | 56 audition Play | Done. Same for x-mixer-light.

function gainSlider(parent, w, frac) {
  const lane = box(parent, w, 48, { name: 'hit/gain', dir: 'H', main: 'MIN', cross: 'CENTER', gap: 0 });
  const tr = box(lane, w, 6, { name: 'track', dir: 'NONE', fill: [bp('secondaryContainer')], r: 3 });
  const fl = box(tr, Math.round(w * frac), 6, { name: 'fill', dir: 'NONE', fill: [bp('primary')], r: 3 });
  const th = figma.createEllipse(); th.name = 'thumb'; th.resize(20, 20);
  th.fills = [bp('primary')];
  lane.appendChild(th); th.layoutPositioning = 'ABSOLUTE';
  th.x = Math.round(w * frac) - 10; th.y = 14;
  return lane;
}
function field(parent, w, label) {
  const f = box(parent, w, 40, { name: 'hit/field-' + label.replace(/\W+/g, '-'), dir: 'H', main: 'CENTER', cross: 'CENTER', fill: [bp('raised')], stroke: 'outlineVariant', r: 8 });
  const t = T(label, 13, 'Medium'); f.appendChild(t);
  return f;
}
function btn48(parent, w, label, active) {
  const b = box(parent, w, 48, { name: 'hit/' + label.replace(/\W+/g, '-'), dir: 'H', main: 'CENTER', cross: 'CENTER', fill: active ? [bp('primary')] : [bp('raised')], stroke: active ? undefined : 'outlineVariant', r: 10 });
  const t = T(label, 13, 'Medium'); t.fills = [bp(active ? 'onPrimary' : 'onSurface')]; b.appendChild(t);
  return b;
}
async function mixerCard(main, name, chan, gainTxt, frac, muted, isMaster) {
  const card = box(main, 380, 112, { name: 'card/' + name, dir: 'V', gap: 8, px: 12, py: 8, fill: [bp('raised')], r: 16 });
  const r1 = box(card, 356, 48, { name: 'r1', dir: 'H', gap: 8 });
  const nm = box(r1, 180, 48, { name: 'nm', dir: 'V', gap: 2, cross: 'MIN' });
  nm.appendChild(T(name, 14, 'Medium'));
  const sub = T(chan, 12); sub.fills = [bp('onSurfaceVariant')]; nm.appendChild(sub);
  if (isMaster) {
    r1.appendChild(T('Limiter', 13));
    const sw = swComp.createInstance(); sw.name = 'hit/switch-limiter'; r1.appendChild(sw);
    sw.fills = [bp('primary')]; sw.strokes = [bp('outlineVariant')];
    const hs = sw.findOne(c => c.name === 'Handle shape'); if (hs) hs.fills = [bp('onPrimary')];
  } else if (muted) {
    btn48(r1, 80, 'Unmute', false);
    btn48(r1, 80, 'Solo', false);
  } else {
    btn48(r1, 80, 'Mute', false);
    btn48(r1, 80, 'Solo', false);
  }
  const r2 = box(card, 356, 48, { name: 'r2', dir: 'H', gap: 8 });
  gainSlider(r2, 168, frac);
  field(r2, 64, gainTxt);
  if (isMaster) {
    const mtr = box(r2, 108, 40, { name: 'meters', dir: 'H', gap: 6, main: 'CENTER', cross: 'CENTER' });
    for (const h of [22, 30]) { const b = box(mtr, 8, 40, { name: 'm', dir: 'NONE' }); b.fills = []; const in1 = box(b, 8, h, { name: 'lvl', dir: 'NONE', fill: [bp('primary')], r: 3 }); }
    mtr.name = 'meter/L-R';
  } else {
    field(r2, 108, 'Pan · Centre');
  }
  return card;
}

async function rebuildMixer(fid, links) {
  const f = await figma.getNodeByIdAsync(fid);
  const main = f.findOne(n => n.name === 'main');
  if (!main) { err('no main ' + fid); return; }
  for (const c of [...main.children]) c.remove();
  await mixerCard(main, 'Voice-over', 'A1 · Mono → Master L/R', '−3 dB', 0.62, false);
  await mixerCard(main, 'Music', 'A2 · Stereo', '−9 dB', 0.48, false);
  await mixerCard(main, 'Ambience', 'A3 · Stereo', '−12 dB', 0.4, false);
  const src = await mixerCard(main, 'Source sound', 'A4 · Muted', 'Muted', 0, true);
  await mixerCard(main, 'Master', 'Stereo out · 0 dB', '0 dB', 0.72, false, true);
  const rr = box(main, 380, 52, { name: 'rr', dir: 'H', gap: 8 });
  for (const [nm, dest] of links) {
    const ch = box(rr, 0, 48, { name: 'hit/chip-' + nm.replace(/\W+/g, '-'), dir: 'H', main: 'CENTER', cross: 'CENTER', fill: [bp('raised')], stroke: 'outlineVariant', r: 24, px: 16 });
    ch.appendChild(T(nm, 14, 'Medium'));
    fixed(ch, Math.max(96, nm.length * 8 + 32), 48);
    if (dest) link(ch, dest);
  }
  // footer: Back | audition Play | Done
  const actions = f.findOne(n => n.name === 'actions');
  if (actions) {
    for (const c of [...actions.children]) c.remove();
    const back = box(actions, 88, 48, { name: 'hit/Back', dir: 'H', main: 'CENTER', cross: 'CENTER' });
    back.appendChild(T('Back', 14, 'Medium'));
    const sp = box(actions, 0, 4, { name: 'sp', dir: 'H' }); sp.layoutSizingHorizontal = 'FILL';
    const play = box(actions, 56, 56, { name: 'hit/audition-play', dir: 'H', main: 'CENTER', cross: 'CENTER', fill: [bp('primary')], r: 28 });
    const pa = await stagedMain('play_arrow');
    const inst = pa.createInstance(); inst.resize(28, 28);
    for (const v of inst.findAllWithCriteria({ types: ['VECTOR'] })) v.fills = [bp('onPrimary')];
    play.appendChild(inst);
    const done = box(actions, 96, 48, { name: 'hit/Done', dir: 'H', main: 'CENTER', cross: 'CENTER', fill: [bp('primary')], r: 24 });
    const dt = T('Done', 14, 'Medium'); dt.fills = [bp('onPrimary')]; done.appendChild(dt);
  }
  ok('mixer rebuilt ' + fid);
}

await rebuildMixer('3269:45740', [['Channels · routing', '3269:45999'], ['Ducking', '3269:46119'], ['EQ', '3269:46168']]);
await rebuildMixer('3279:45706', [['Channels · routing', '3269:45999'], ['Ducking', '3269:46119'], ['EQ', '3269:46168']]);
return RESULT;
