// c1: global text/font/hit-target sweep across all 80 frames.
const MANIFEST = {
  'x-photo-view':'3283:45617','x-photo-brush':'3283:45643','x-photo-composite':'3283:45679','x-video-focus':'3283:45717',
  'x-toolbar-rest':'3283:45753','x-toolbar-pressed':'3283:45784','x-toolbar-expanded':'3283:45815',
  'p-home':'3248:44904','p-tools':'3248:44965','p-light':'3248:45116','p-colour':'3248:45196','p-curves':'3248:45278','p-white-balance':'3248:45343',
  'p-crop':'3249:44998','p-perspective':'3249:45083','p-text':'3249:45148','p-frames':'3249:45209','p-looks':'3249:45275','p-grain':'3249:45338',
  'p-lut':'3251:45062','p-raw':'3251:45116','p-batch':'3251:45176','p-history':'3251:45241','p-export':'3251:45313',
  'p-selective':'3254:45129','p-brush':'3254:45199','p-healing':'3254:45264','p-details':'3254:45325','p-vignette':'3254:45391','p-blur':'3254:45446',
  'p-double':'3256:45187','p-layers':'3256:45254','p-add-image':'3256:45336','p-transform':'3256:45392','p-blend':'3256:45464',
  'v-home':'3261:45244','v-tools':'3263:45257','v-add-media':'3264:45286','v-arrange':'3265:45295','v-trim':'3265:45424','v-transform':'3265:45549',
  'v-colour':'3265:45683','v-speed':'3265:45810','v-reverse':'3265:45935','v-transitions':'3266:45377','v-keyframes':'3266:45499','v-layers':'3266:45636',
  'v-mask':'3266:45744','v-chroma':'3266:45874','v-looks':'3268:45453','v-text':'3268:45589','v-captions':'3268:45713','v-animation':'3268:45843',
  'v-add-audio':'3269:45503','v-voice':'3269:45581','v-clip-audio':'3269:45653','v-mixer':'3269:45740','v-routing':'3269:45999','v-ducking':'3269:46119','v-eq':'3269:46168',
  'v-project':'3279:45537','v-export':'3279:45598','v-assets':'3279:45649',
  'x-mixer-light':'3279:45706','x-large-text':'3279:45782','x-numeric':'3279:45830','x-empty':'3279:45869','x-layer-menu':'3279:45907',
  'x-importing':'3282:45585','x-exporting':'3282:45613','x-export-error':'3282:45642','x-mic-permission':'3282:45672','x-no-blur':'3282:45702','x-photo-light':'3282:45734',
  'x-photo-desktop':'3284:45692','x-video-desktop':'3284:45828','x-mixer-desktop':'3284:46100','x-keyboard':'3285:45731',
  'x-photo-landscape':'3285:45869','x-video-landscape':'3285:45920'
};

let fontFixed = 0, sizeFixed = 0, hitsFixed = 0;
function inHit(n) { for (let p = n.parent; p; p = p.parent) { if (/^(hit|chip|tool|opt|mi|lr|btn|field|sw|seg)\b/.test(p.name) || p.name.startsWith('hit/')) return true; if (p.type === 'PAGE') return false; } return false; }

for (const [key, id] of Object.entries(MANIFEST)) {
  const root = await figma.getNodeByIdAsync(id);
  if (!root) { err(key + ' missing'); continue; }
  const all = root.findAll(() => true);
  for (const n of all) {
    if (!isVisible(n)) continue;
    if (n.type === 'TEXT') {
      try {
        const fams = new Set(n.getStyledTextSegments(['fontName']).map(s => s.fontName.family));
        if (![...fams].every(f => f === 'Google Sans Flex')) {
          const st = n.fontName;
          const style = st && st.style && /bold|semi/i.test(st.style) ? 'SemiBold' : (/medium/i.test(st && st.style || '') ? 'Medium' : 'Regular');
          n.fontName = { family: 'Google Sans Flex', style };
          fontFixed++;
        }
      } catch (e) { try { n.fontName = { family: 'Google Sans Flex', style: 'Regular' }; fontFixed++; } catch (e2) {} }
      if (typeof n.fontSize === 'number') {
        if (n.fontSize < 12) { n.fontSize = 12; sizeFixed++; }
        else if (n.fontSize < 14 && inHit(n)) { n.fontSize = 14; sizeFixed++; }
      } else {
        for (const s of n.getStyledTextSegments(['fontSize'])) {
          try { n.setRangeFontSize(s.start, s.end, Math.max(s.fontSize < 14 && inHit(n) ? 14 : 12, s.fontSize)); } catch (e) {}
        }
      }
    }
    if (n.name.startsWith('hit/') && (n.width < 47.5 || n.height < 47.5)) {
      try { n.resize(Math.max(48, n.width), Math.max(48, n.height)); hitsFixed++; } catch (e) { err(key + ' hit resize ' + n.id); }
    }
    // menu/layer rows count as targets: rename mi/* and lr/* to hit/* when >=48
    if (/^(mi|lr)\//.test(n.name) && n.width >= 47.5 && n.height >= 47.5) n.name = 'hit/' + n.name.slice(3);
  }
  ok(key);
}
RESULT.summary = { fontFixed, sizeFixed, hitsFixed };
return RESULT;
