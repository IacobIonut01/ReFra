// c3: M3 expressive component integration — toolbar slots, pressed state,
// split buttons, switches, toolbar blur/paint.
const TOOLBAR_COMP = '1621:108991';
const SPLIT_INST = '3244:38552';
const SWITCH_INST = '3244:38562';

// find eraser icon on Common page
const pg = await figma.getNodeByIdAsync('12:1426');
let ERASER = null;
for (const s of pg.findAll(n => n.type === 'COMPONENT_SET' && /eraser|ink_eraser|format_clear|cleaning/i.test(n.name))) {
  ERASER = (s.children.find(c => /Round/.test(c.name)) || s.children[0]).id; break;
}
ok('eraser=' + ERASER);

// icon main components
const stage = await figma.getNodeByIdAsync('3210:38452');
function st(n) { const i = stage.children.find(c => c.name === 'ic/' + n); return i ? i.mainComponent : null; }
const MC = {
  tune: st('tune'), layers: st('layers'), compare: st('compare'), undo: st('undo'),
  brush: await figma.getNodeByIdAsync('349:156959'),
  erase: await figma.getNodeByIdAsync(ERASER || '349:156670'),
  cfs: await figma.getNodeByIdAsync('349:157058'),
};

// section subtree roots to search instances in
const SECTIONS = ['3244:44938', '3244:44939', '3244:44940', '3244:44941', '3244:44942', '3244:44943'];

async function setSlot(content, comp, name) {
  let swapped = false;
  try { content.setProperties({ 'Icon#756:0': comp.key }); swapped = true; }
  catch (e) { try { content.setProperties({ 'Icon#756:0': comp.id }); swapped = true; } catch (e2) {} }
  try { content.setProperties({ 'State': 'Enabled' }); } catch (e) {}
  if (swapped) {
    for (const v of content.findAllWithCriteria({ types: ['VECTOR'] })) v.fills = [bp('onSurface')];
    content.name = 'hit/' + name;
  }
  return swapped;
}

// toolbar instances by context
const TB = [
  ['3248:44904', ['tune', 'layers', 'compare', 'cfs'], ['Tools', 'Layers', 'Before', 'Focus']],
  ['3283:45643', ['brush', 'erase', 'undo', 'layers'], ['Paint', 'Erase', 'Undo', 'Layers']],
  ['3283:45753', ['brush', 'erase', 'undo', 'layers'], ['Paint', 'Erase', 'Undo', 'Layers']],
  ['3283:45784', ['brush', 'erase', 'undo', 'layers'], ['Paint', 'Erase', 'Undo', 'Layers']],
];
let tbSwap = 0;
for (const [fid, keys, names] of TB) {
  const root = await figma.getNodeByIdAsync(fid);
  const tb = root.findAll(n => n.type === 'INSTANCE' && n.mainComponent && n.mainComponent.id === TOOLBAR_COMP);
  for (const t of tb) {
    t.fills = [bp('panel', 0.88)];
    t.effects = [{ type: 'BACKGROUND_BLUR', radius: 24, visible: true }];
    const slots = t.children.filter(c => /^Slot/.test(c.name));
    for (let i = 0; i < slots.length && i < keys.length; i++) {
      const content = slots[i].children.find(c => c.type === 'INSTANCE' && c.name === 'Content');
      if (content && MC[keys[i]]) {
        if (await setSlot(content, MC[keys[i]], names[i])) tbSwap++;
        else err('slot swap ' + fid + ' ' + slots[i].name);
      }
    }
  }
  ok('tb ' + fid + ' x' + tb.length);
}

// pressed demo: make slot 2 visibly pressed
const pr = await figma.getNodeByIdAsync('3283:45784');
const ptb = pr.findAll(n => n.type === 'INSTANCE' && n.mainComponent && n.mainComponent.id === TOOLBAR_COMP)[0];
if (ptb) {
  const slot2 = ptb.children[1];
  const content = slot2 && slot2.children.find(c => c.type === 'INSTANCE');
  if (content) {
    try { content.setProperties({ 'State': 'Pressed' }); } catch (e) {}
    try { content.fills = [bp('primaryContainer')]; } catch (e) {}
    for (const v of content.findAllWithCriteria({ types: ['VECTOR'] })) v.fills = [bp('primary')];
  }
  ok('pressed styled');
}
// expanded: ensure Done button label/theming
const ex = await figma.getNodeByIdAsync('3283:45815');
for (const b of ex.findAll(n => n.type === 'INSTANCE' && n.mainComponent && /Button/.test(n.mainComponent.parent ? n.mainComponent.parent.name : n.mainComponent.name))) {
  const content = b.findOne(c => c.type === 'INSTANCE' && c.name === 'Content');
  if (content) {
    try { content.setProperties({ 'Show icon#16283:18': false, 'Label text#16304:6': 'Done' }); } catch (e) {}
    try { content.fills = [bp('primary')]; } catch (e) {}
    for (const t of content.findAllWithCriteria({ types: ['TEXT'] })) { t.fontName = { family: 'Google Sans Flex', style: 'Medium' }; t.fills = [bp('onPrimary')]; }
  }
  b.fills = [];
  ok('expanded done themed');
}

// split buttons: primary/onPrimary, hide placeholder star
const splitComp = (await figma.getNodeByIdAsync(SPLIT_INST)).mainComponent;
let splitFixed = 0;
for (const sid of SECTIONS) {
  const s = await figma.getNodeByIdAsync(sid);
  for (const inst of s.findAll(n => n.type === 'INSTANCE' && n.mainComponent && n.mainComponent.id === splitComp.id)) {
    const lead = inst.findOne(c => c.name === 'Leading button');
    const trail = inst.findOne(c => c.name === 'Trailing button');
    for (const seg of [lead, trail]) {
      if (!seg) continue;
      try { seg.fills = [bp('primary')]; } catch (e) {}
      for (const t of seg.findAllWithCriteria({ types: ['TEXT'] })) {
        t.fontName = { family: 'Google Sans Flex', style: 'Medium' }; t.fills = [bp('onPrimary')];
      }
      for (const v of seg.findAllWithCriteria({ types: ['VECTOR'] })) v.fills = [bp('onPrimary')];
    }
    const star = lead && lead.findOne(c => c.type === 'INSTANCE' && c.name === 'Icon');
    if (star) { try { star.visible = false; } catch (e) { warn('star hide ' + inst.id); } }
    splitFixed++;
  }
}
ok('splits=' + splitFixed);

// switches: primary track, onPrimary thumb, hit name on 48px Target
const swComp = (await figma.getNodeByIdAsync(SWITCH_INST)).mainComponent;
let swFixed = 0;
for (const sid of SECTIONS) {
  const s = await figma.getNodeByIdAsync(sid);
  for (const inst of s.findAll(n => n.type === 'INSTANCE' && n.mainComponent && n.mainComponent.id === swComp.id)) {
    try { inst.fills = [bp('primary')]; } catch (e) {}
    try { inst.strokes = [bp('outlineVariant')]; } catch (e) {}
    const hs = inst.findOne(c => c.name === 'Handle shape');
    if (hs) { try { hs.fills = [bp('onPrimary')]; } catch (e) {} }
    const tgt = inst.findOne(c => c.name === 'Target');
    if (tgt && tgt.width >= 47.5 && tgt.height >= 47.5) tgt.name = 'hit/switch';
    swFixed++;
  }
}
ok('switches=' + swFixed);
RESULT.counts = { tbSwap, splitFixed, swFixed };
return RESULT;
