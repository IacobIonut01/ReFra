// c4: chip strips -> real horizontal scroll viewports; property lists ->
// VERTICAL; row/* editable affordances; boolean switches; slider tracks.
const ALL = ['3283:45617','3283:45643','3283:45679','3283:45717','3283:45753','3283:45784','3283:45815',
  '3248:44904','3248:44965','3248:45116','3248:45196','3248:45278','3248:45343','3249:44998','3249:45083',
  '3249:45148','3249:45209','3249:45275','3249:45338','3251:45062','3251:45116','3251:45176','3251:45241',
  '3251:45313','3254:45129','3254:45199','3254:45264','3254:45325','3254:45391','3254:45446','3256:45187',
  '3256:45254','3256:45336','3256:45392','3256:45464','3261:45244','3263:45257','3264:45286','3265:45295',
  '3265:45424','3265:45549','3265:45683','3265:45810','3265:45935','3266:45377','3266:45499','3266:45636',
  '3266:45744','3266:45874','3268:45453','3268:45589','3268:45713','3268:45843','3269:45503','3269:45581',
  '3269:45653','3269:45740','3269:45999','3269:46119','3269:46168','3279:45537','3279:45598','3279:45649',
  '3279:45706','3279:45782','3279:45830','3279:45869','3279:45907','3282:45585','3282:45613','3282:45642',
  '3282:45672','3282:45702','3282:45734','3284:45692','3284:45828','3284:46100','3285:45731','3285:45869','3285:45920'];

const swComp = (await figma.getNodeByIdAsync('3244:38562')).mainComponent;
let chipVps = 0, scrolls = 0, fields = 0, switches = 0, tracks = 0;

for (const fid of ALL) {
  const root = await figma.getNodeByIdAsync(fid); if (!root) { err(fid); continue; }

  // --- 1. chip rows overflowing -> horizontal scroll viewport
  const chipParents = new Set();
  for (const c of root.findAll(n => n.name.startsWith('hit/chip-'))) {
    if (c.parent && c.parent.type === 'FRAME') chipParents.add(c.parent);
  }
  for (const p of chipParents) {
    const chips = p.children.filter(c => c.name.startsWith('hit/chip-'));
    if (chips.length < 2) continue;
    const gap = p.itemSpacing || 8;
    const px = (p.paddingLeft || 0) + (p.paddingRight || 0);
    const contentW = chips.reduce((a, c) => a + c.width, 0) + gap * (chips.length - 1) + px;
    const availW = Math.min(380, p.width);
    const wrapped = p.layoutMode === 'NONE' || p.height > 60;
    if (contentW <= p.width - 1 && !wrapped) continue;
    // rebuild: viewport vp (avail x 56, clip, H_SCROLL) -> row r (contentW x 48)
    const parent = p.parent; const idx = parent ? p.children.length && parent.children.indexOf(p) : -1;
    const vp = figma.createFrame();
    vp.name = 'chips-scroll';
    vp.layoutMode = 'HORIZONTAL'; vp.primaryAxisAlignItems = 'MIN'; vp.counterAxisAlignItems = 'CENTER';
    vp.clipsContent = true; vp.overflowDirection = 'HORIZONTAL';
    vp.fills = [];
    if (parent) { parent.insertChild(Math.max(0, idx), vp); }
    fixed(vp, Math.min(availW, Math.max(contentW, 120)), 56);
    const r = figma.createFrame();
    r.name = 'chips-row'; r.layoutMode = 'HORIZONTAL'; r.primaryAxisAlignItems = 'MIN';
    r.counterAxisAlignItems = 'CENTER'; r.itemSpacing = gap; r.paddingLeft = r.paddingRight = 8;
    r.fills = [];
    vp.appendChild(r); fixed(r, contentW, 48);
    for (const c of chips) { r.appendChild(c); try { c.resize(c.width, 48); } catch (e) {} }
    p.remove();
    chipVps++;
  }

  // --- 2. vertical content overflow -> VERTICAL
  for (const n of root.findAll(x => x.type === 'FRAME' && x.layoutMode === 'VERTICAL' && /props|inspector|list|rows|cards|sheet|content|media|bin/.test(x.name))) {
    const ch = n.children.filter(c => c.layoutPositioning !== 'ABSOLUTE');
    if (ch.length < 3) continue;
    const total = ch.reduce((a, c) => a + c.height, 0) + (n.itemSpacing || 0) * (ch.length - 1) + (n.paddingTop || 0) + (n.paddingBottom || 0);
    if (total > n.height + 4 && n.clipsContent !== false) {
      try { n.overflowDirection = 'VERTICAL'; scrolls++; } catch (e) { warn('scroll ' + n.id); }
    }
  }

  // --- 3. row/* editable affordance: raise to >=48, name hit/param-*, pill field
  for (const r of root.findAll(x => x.type === 'FRAME' && /^row\//.test(x.name) && x.layoutMode === 'HORIZONTAL')) {
    const label = r.children.find(c => c.type === 'TEXT');
    const value = [...r.children].reverse().find(c => c.type === 'TEXT' && c !== label);
    if (!label) continue;
    try { if (r.height < 48) r.resize(r.width, 48); } catch (e) {}
    if (!r.name.startsWith('hit/')) r.name = 'hit/param-' + label.characters.replace(/[^A-Za-z0-9]+/g, '-').slice(0, 24);
    if (value && /^(Off|On)$/.test(value.characters.trim())) {
      // boolean -> real switch
      value.remove();
      const sw = swComp.createInstance();
      sw.name = 'hit/switch-' + label.characters.slice(0, 16);
      r.appendChild(sw);
      try { sw.fills = [bp('primary')]; sw.strokes = [bp('outlineVariant')]; } catch (e) {}
      const hs = sw.findOne(c => c.name === 'Handle shape'); if (hs) hs.fills = [bp('onPrimary')];
      switches++;
    } else if (value) {
      // wrap value in field pill (raised, outlined, >=48 tall hit)
      const pill = box(r, Math.max(88, value.width + 40), 40, {
        name: 'field', dir: 'H', main: 'CENTER', cross: 'CENTER', gap: 4,
        fill: [bp('raised')], stroke: 'outlineVariant', r: 8,
      });
      pill.appendChild(value);
      if (r.children.findIndex(c => c === pill) >= 0) { /* already in */ }
      fields++;
    }
  }

  // --- 4. oversized drawn slider tracks -> 6px, keep 48 lane
  for (const n of root.findAll(x => (x.type === 'FRAME' || x.type === 'RECTANGLE') && /track|inactive|active|slider/i.test(x.name))) {
    if (n.height > 16 && n.width > 60 && !/hit\//.test(n.name)) {
      const cy = n.height / 2;
      try { n.resize(n.width, 6); if (n.parent && n.parent.layoutMode === 'NONE') n.y = n.y + cy - 3; tracks++; } catch (e) {}
    }
  }
  ok(fid);
}
RESULT.counts = { chipVps, scrolls, fields, switches, tracks };
return RESULT;
