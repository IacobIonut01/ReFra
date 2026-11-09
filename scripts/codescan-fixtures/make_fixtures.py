import subprocess, sys, os, json, random
import numpy as np
from PIL import Image, ImageFilter, ImageOps
T, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
tmp = os.path.join(OUT, '_tmp'); os.makedirs(tmp, exist_ok=True)

def encode(fmt, text, size):
    tf = os.path.join(tmp, 't.txt'); open(tf, 'wb').write(text.encode('utf-8'))
    pf = os.path.join(tmp, 'c.pgm')
    subprocess.check_call([T, 'encode', fmt, tf, str(size), pf])
    return Image.open(pf).convert('L')

def background(w, h, seed):
    rnd = np.random.default_rng(seed)
    y, x = np.mgrid[0:h, 0:w]
    base = (90 + 60 * np.sin(x / 170.0) * np.cos(y / 230.0) + 40 * (x / w))[..., None] * np.array([1.0, 0.9, 0.75])
    noise = rnd.normal(0, 10, (h, w, 3))
    return Image.fromarray(np.clip(base + noise, 0, 255).astype('uint8'), 'RGB').filter(ImageFilter.GaussianBlur(1.2))

def coeffs(dst, src):
    a = []
    for (x, y), (u, v) in zip(dst, src):
        a += [[x, y, 1, 0, 0, 0, -u * x, -u * y], [0, 0, 0, x, y, 1, -v * x, -v * y]]
    return np.linalg.lstsq(np.array(a, float), np.array([p for uv in src for p in uv], float), rcond=None)[0]

def paste(base, card, x, y):
    base.paste(card.convert('RGB'), (x, y)); return base

def warp(base, card, quad):
    w, h = card.size
    c = coeffs(quad, [(0, 0), (w, 0), (w, h), (0, h)])
    rgba = card.convert('RGBA')
    warped = rgba.transform(base.size, Image.PERSPECTIVE, tuple(c), Image.BICUBIC)
    return Image.alpha_composite(base.convert('RGBA'), warped).convert('RGB')

def pad(img, p, fill=255):
    return ImageOps.expand(img, border=p, fill=fill)

fx = {}
url = 'https://canalnights.example/programme?from=poster'
b = paste(background(1200, 900, 1), pad(encode('QRCode', url, 360), 20), 420, 260)
b.save(f'{OUT}/qr_url.jpg', quality=88); fx['qr_url.jpg'] = [['QRCode', url]]

wifi = 'WIFI:T:WPA;S:Ponte Guest;P:gondola-2026;;'
b = warp(background(1200, 1600, 2), pad(encode('QRCode', wifi, 520), 40), [(330, 520), (880, 470), (930, 1050), (360, 1110)])
b.save(f'{OUT}/qr_wifi_perspective.jpg', quality=88); fx['qr_wifi_perspective.jpg'] = [['QRCode', wifi]]

inv = ImageOps.invert(encode('QRCode', 'INVERTED-CODE-42', 360))
b = paste(background(1000, 800, 3), pad(inv, 20, 0), 300, 200)
b.save(f'{OUT}/qr_inverted.jpg', quality=88); fx['qr_inverted.jpg'] = [['QRCode', 'INVERTED-CODE-42']]

b = background(1400, 900, 4)
b = paste(b, pad(encode('QRCode', 'https://trattoria.example/menu', 320), 20), 120, 250)
b = paste(b, pad(encode('DataMatrix', 'TABLE-BOOKING-7', 280), 20), 860, 280)
b.save(f'{OUT}/two_codes.jpg', quality=88); fx['two_codes.jpg'] = [['QRCode', 'https://trattoria.example/menu'], ['DataMatrix', 'TABLE-BOOKING-7']]

b = paste(background(1000, 700, 5), pad(encode('EAN13', '4006381333931', 520), 20), 220, 220)
b.save(f'{OUT}/ean13.jpg', quality=88); fx['ean13.jpg'] = [['EAN-13', '4006381333931']]

b = paste(background(4000, 3000, 6), pad(encode('QRCode', 'https://small.example/', 300), 16), 2900, 2100)
b.save(f'{OUT}/small_qr_large_photo.jpg', quality=85); fx['small_qr_large_photo.jpg'] = [['QRCode', 'https://small.example/']]

background(1600, 1200, 7).save(f'{OUT}/no_code.jpg', quality=85); fx['no_code.jpg'] = []

json.dump(fx, open(f'{OUT}/expected.json', 'w'), indent=1)
for f in os.listdir(tmp): os.remove(os.path.join(tmp, f))
os.rmdir(tmp)
