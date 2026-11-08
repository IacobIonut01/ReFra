#!/usr/bin/env python3
"""Rebuild contact strips: 4 phone PNGs per row at native 412x892 + labels."""
import os, json
from PIL import Image, ImageDraw

DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
m = json.load(open(os.path.join(DIR, 'manifest.json')))
order = list(m['frames'].keys())
photo = [k for k in order if k.startswith('p-')]
video = [k for k in order if k.startswith('v-')]
exp = [k for k in order if k.startswith('x-')
       and 'desktop' not in k and 'landscape' not in k and k != 'x-keyboard']
groups = photo + video + exp
assert len(photo) == 28 and len(video) == 28 and len(exp) == 18, (len(photo), len(video), len(exp))

FW, FH, LBL, GAP, MARG = 412, 892, 28, 12, 8
W = MARG * 2 + 4 * FW + 3 * GAP   # 1700
H = FH + LBL + 12                  # 932 -> pad to 940
W2, H2 = 1700, 940

try:
    from PIL import ImageFont
    font = ImageFont.truetype('/System/Library/Fonts/Helvetica.ttc', 14)
except Exception:
    font = ImageFont.load_default()

idx = 0
for s in range(19):
    strip = Image.new('RGB', (W2, H2), (24, 24, 26))
    d = ImageDraw.Draw(strip)
    for col in range(4):
        if idx >= len(groups):
            break
        key = groups[idx]
        img = Image.open(os.path.join(DIR, key + '.png')).convert('RGB')
        if img.size != (FW, FH):
            img = img.resize((FW, FH))
        x = MARG + col * (FW + GAP)
        strip.paste(img, (x, 8))
        d.text((x + 2, 8 + FH + 4), key + '.png', fill=(200, 200, 205), font=font)
        idx += 1
    out = os.path.join(DIR, 'contact-%02d.png' % (s + 1))
    strip.save(out)
    print(out, strip.size)
print('total frames placed:', idx)
