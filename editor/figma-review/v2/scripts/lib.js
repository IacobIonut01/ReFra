// ReFra v2 correction preamble — prepend to every use_figma call in scripts/.
await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('9:1425'));
const VC = 'VariableCollectionId:3211:44405';
const VARS = {};
for (const v of await figma.variables.getLocalVariablesAsync('COLOR')) {
  if (v.variableCollectionId === VC) VARS[v.name.split('/').pop()] = v;
}
const DARK = '3211:0', LIGHT = '3211:1';
for (const f of [
  { family: 'Google Sans Flex', style: 'Regular' },
  { family: 'Google Sans Flex', style: 'Medium' },
  { family: 'Google Sans Flex', style: 'SemiBold' },
  { family: 'Roboto', style: 'Regular' },
  { family: 'Roboto', style: 'Medium' },
]) await figma.loadFontAsync(f);

const LAKE = '0b10390de72d2becd9ec75fe9f95b971c520424a';
const BRIDGE = '97d9abdd37500dc211bec83bfe024fb03c92e9fe';
const OLD_BASE = '32212b497ad6113a909c8168e9225ba8dc9921e0';

function bp(name, op) {
  let p = { type: 'SOLID', color: { r: 1, g: 1, b: 1 } };
  if (op !== undefined) p.opacity = op;
  const v = VARS[name];
  return v ? figma.variables.setBoundVariableForPaint(p, 'color', v) : p;
}
function T(s, size, w) {
  const t = figma.createText();
  t.fontName = { family: 'Google Sans Flex', style: w || 'Regular' };
  t.characters = s;
  t.fontSize = size;
  t.fills = [bp('onSurface')];
  return t;
}
function fixed(n, w, h) {
  n.resize(w, h);
  if ('layoutMode' in n && n.layoutMode !== 'NONE') {
    n.primaryAxisSizingMode = 'FIXED'; n.counterAxisSizingMode = 'FIXED';
  }
  try { n.layoutSizingHorizontal = 'FIXED'; n.layoutSizingVertical = 'FIXED'; } catch (e) {}
}
function box(parent, w, h, o) {
  o = o || {};
  const f = figma.createFrame();
  f.name = o.name || 'box';
  if (o.dir !== 'NONE') {
    f.layoutMode = o.dir === 'V' ? 'VERTICAL' : 'HORIZONTAL';
    f.primaryAxisAlignItems = o.main || 'MIN';
    f.counterAxisAlignItems = o.cross || 'CENTER';
    f.itemSpacing = o.gap !== undefined ? o.gap : 8;
    f.paddingLeft = f.paddingRight = o.px || 0;
    f.paddingTop = f.paddingBottom = o.py || 0;
  }
  f.fills = o.fill || [];
  if (o.r) f.cornerRadius = o.r;
  if (o.stroke) { f.strokes = [bp(o.stroke)]; f.strokeWeight = o.sw || 1; }
  if (o.clip !== undefined) f.clipsContent = o.clip;
  parent.appendChild(f);
  fixed(f, w, h);
  return f;
}
function abs(n) { try { n.layoutPositioning = 'ABSOLUTE'; } catch (e) {} return n; }
function isVisible(n) {
  for (let p = n; p; p = p.parent) {
    if ('visible' in p && p.visible === false) return false;
    if ('opacity' in p && p.opacity === 0) return false;
  }
  return true;
}

// Round-style icon components on Common page (COMPONENT nodes, Style=Round).
const ICON = {
  brush: '349:156959', speed: '349:169251', colorize: '349:157136',
  visibility: '349:175209', cfs: '349:157058', erase: '349:156670',
  check_circle: '349:170593', error: '349:169534', folder: '349:161166',
  folder_open: '349:161223', save: '349:166322', delete: '349:170936',
  edit: '349:157502', share: '349:149466', done: '349:171082',
  refresh: '349:153034', link: '349:166094', link_off: '349:166105',
  timer: '349:159097', closed_caption: '349:168287', subtitles: '349:169299',
  slowmo: '349:169218', movie_filter: '349:158472', photo_filter: '349:158782',
  grain: '349:157953', grid_on: '349:157976', hdr: '349:158048',
  looks: '349:158263', tonality: '349:159153', wb_sunny: '349:159346',
  wb_auto: '349:159285', exposure: '349:157526', contrast: '349:157160',
  dehaze: '349:157464', openfull: '349:172669', numbers: '349:162486',
  backspace: '349:165635', kreturn: '349:160370', kbd_hide: '349:160344',
  space_bar: '349:162697', title: '349:162826', record_voice: '349:173257',
  surround: '349:169311', speaker: '349:160669', vol_down: '349:169416',
  ff: '349:168363', fr: '349:168379', skip_next: '349:169194',
  skip_prev: '349:169206', replay: '349:169097', timelapse: '349:159085',
  drop_down: '349:152577', drop_up: '349:152600', unfold_more: '349:153135',
  chev_r: '349:152732', chev_l: '349:152721', restart: '349:164622',
  drag: '349:161788', linear: '349:162298', show_chart: '349:162686',
  blur_on: '349:156831', brightness: '349:156922', color_lens: '349:157120',
  crop_free: '349:157275', videocam: '349:169392', reorder: '349:173308',
  pinch: '349:173044', swap_h: '349:174175', swap_v: '349:174198',
  auto_fix_normal: '349:156670', star: '349:147358', schedule: '349:173517',
  info: '349:171927', key: '349:167117', n123: '349:169594', abc: '349:169617',
};
async function icon(parent, key, size, color) {
  const c = await figma.getNodeByIdAsync(ICON[key] || key);
  const i = c.createInstance();
  i.name = 'ic/' + (VARS[key] ? key : key);
  i.resize(size, size);
  for (const v of i.findAllWithCriteria({ types: ['VECTOR'] })) {
    v.fills = [bp(color || 'onSurface')];
  }
  if (parent) parent.appendChild(i);
  return i;
}
function recolorInst(inst, color) {
  for (const v of inst.findAllWithCriteria({ types: ['VECTOR'] })) {
    v.fills = [bp(color || 'onSurface')];
  }
}
// stage icon instance main-components by name
async function stagedMain(name) {
  const stage = await figma.getNodeByIdAsync('3210:38452');
  const inst = stage.children.find(c => c.name === 'ic/' + name);
  return inst ? inst.mainComponent : null;
}
function link(n, dest, instant) {
  n.reactions = [{
    trigger: { type: 'ON_CLICK' },
    actions: [{
      type: 'NODE', destinationId: dest, navigation: 'NAVIGATE',
      transition: instant ? null : { type: 'DISSOLVE', duration: 0.2, easing: { type: 'EASE_OUT' } },
    }],
  }];
}
const RESULT = { ok: [], warn: [], err: [] };
function ok(m) { RESULT.ok.push(m); }
function warn(m) { RESULT.warn.push(m); }
function err(m) { RESULT.err.push(m); }
