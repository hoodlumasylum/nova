#!/usr/bin/env python3
"""Build soft eye masks and skin-coloured lids from Nova's original portrait."""
from __future__ import annotations

import math
import random
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
FACE = ROOT / "nova" / "assets" / "nova-face.jpg"
OUT = ROOT / "nova" / "assets"

# Keep in sync with nova/face_layout.py
LEFT_LID = (0.345, 0.358, 0.120, 0.075)
RIGHT_LID = (0.516, 0.336, 0.132, 0.068)
# Clean-ish lid-skin patches (no brow, no iris) for colour and soft texture.
LEFT_SKIN_U = (0.360, 0.338, 0.090, 0.018)
LEFT_SKIN_L = (0.360, 0.430, 0.090, 0.022)
RIGHT_SKIN_U = (0.540, 0.322, 0.090, 0.016)
RIGHT_SKIN_L = (0.545, 0.400, 0.090, 0.020)


def _ellipse_mask(w: int, h: int, feather: float, inset: float = 0.0) -> Image.Image:
    scale = 4
    W, H = w * scale, h * scale
    m = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(m)
    pad = int(min(W, H) * (0.02 + inset))
    d.ellipse((pad, pad, W - 1 - pad, H - 1 - pad), fill=255)
    rad = max(1, int(min(W, H) * feather))
    m = m.filter(ImageFilter.GaussianBlur(radius=rad))
    return m.resize((w, h), Image.Resampling.LANCZOS)


def _crop_norm(im: Image.Image, box):
    x, y, w, h = box
    iw, ih = im.size
    return im.crop((int(x * iw), int(y * ih), int((x + w) * iw), int((y + h) * ih)))


def _median_color(im: Image.Image) -> tuple[int, int, int]:
    px = list(im.getdata())
    if not px:
        return (90, 50, 30)
    # Drop very dark lashes and very bright highlights, then take median.
    def lum(p):
        return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]
    mid = sorted(px, key=lum)
    trimmed = mid[len(mid) // 8 : max(len(mid) // 8 + 1, 7 * len(mid) // 8)]
    ch = list(zip(*trimmed))
    return tuple(sorted(c)[len(c) // 2] for c in ch)  # type: ignore[return-value]


def _lid_texture(skin: Image.Image, w: int, h: int, upper: bool, crease: tuple[int, int, int]) -> Image.Image:
    mid = _median_color(skin)
    # Softly stretch the real skin, then fade it into the median so lashes
    # and pores don't drag down the face during a blink.
    tex = skin.resize((w, max(8, h)), Image.Resampling.LANCZOS).filter(ImageFilter.GaussianBlur(radius=2.4))
    base = Image.new("RGB", (w, h), mid)
    # Vertical lighting: slightly darker at the crease, mid in the middle.
    shade = Image.new("RGB", (w, h), mid)
    sd = ImageDraw.Draw(shade)
    for y in range(h):
        t = y / max(1, h - 1)
        if not upper:
            t = 1 - t
        # t=0 is the crease (top of an upper lid)
        mix = 0.22 * (1 - t)  # more crease colour near the root
        col = tuple(int(mid[i] * (1 - mix) + crease[i] * mix) for i in range(3))
        sd.line((0, y, w, y), fill=col)
    # Subtle grain so it doesn't look like a flat sticker.
    rng = random.Random(12 + mid[0])
    grain = Image.new("RGB", (w, h), mid)
    gd = ImageDraw.Draw(grain)
    for _ in range(w * h // 14):
        x = rng.randint(0, w - 1)
        y = rng.randint(0, h - 1)
        j = rng.randint(-5, 5)
        p = tuple(max(0, min(255, mid[i] + j)) for i in range(3))
        gd.point((x, y), fill=p)
    grain = grain.filter(ImageFilter.GaussianBlur(radius=0.8))
    lid_rgb = Image.blend(shade, Image.blend(base, tex.convert("RGB"), 0.35), 0.55)
    lid_rgb = Image.blend(lid_rgb, grain, 0.12)

    alpha = Image.new("L", (w, h), 255)
    ad = ImageDraw.Draw(alpha)
    fade = max(4, h // 14)
    for i in range(fade):
        t = i / max(1, fade - 1)
        s = t * t * (3 - 2 * t)
        a = int(255 * (1 - s))
        y = (h - 1 - i) if upper else i
        ad.line((0, y, w, y), fill=a)
    alpha = alpha.filter(ImageFilter.GaussianBlur(radius=1.6))

    lid = lid_rgb.convert("RGBA")
    lid.putalpha(alpha)

    # Soft lash fringe along the leading edge (irregular, not a stamped row).
    fringe = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    fd = ImageDraw.Draw(fringe)
    edge = h - 3 if upper else 2
    lash = tuple(max(0, c - 40) for c in crease)
    for x in range(w):
        wave = 0.5 + 0.5 * math.sin(x * 0.33) + 0.28 * math.sin(x * 0.81 + 1.1)
        length = 1 + int((2.0 + wave * 2.8) * (h / 70))
        # Thin out near the corners.
        corner = min(x, w - 1 - x) / (w * 0.5)
        length = max(1, int(length * (0.35 + 0.65 * min(1, corner * 1.6))))
        for k in range(length):
            yy = edge - k if upper else edge + k
            if 0 <= yy < h:
                a = int(185 * (1 - k / length) * (0.7 + 0.3 * wave))
                fd.point((x, yy), fill=lash + (max(0, min(255, a)),))
    fringe = fringe.filter(ImageFilter.GaussianBlur(radius=0.8))
    return Image.alpha_composite(lid, fringe)


def _catchlight(size: int = 64) -> Image.Image:
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    cx = cy = size / 2
    for r, a, col in (
        (size * 0.48, 28, (255, 236, 180)),
        (size * 0.30, 70, (255, 248, 220)),
        (size * 0.16, 160, (255, 255, 255)),
        (size * 0.07, 230, (255, 255, 255)),
    ):
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=col + (a,))
    return im.filter(ImageFilter.GaussianBlur(radius=1.2))


def _wetline(w: int, h: int) -> Image.Image:
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    y = int(h * 0.72)
    for i in range(5):
        a = 70 - i * 12
        d.arc((int(w * 0.12), y - 6 + i, int(w * 0.88), y + 18 + i), 200, 340, fill=(255, 230, 200, a), width=2)
    return im.filter(ImageFilter.GaussianBlur(radius=1.3))


def _socket_shadow(w: int, h: int) -> Image.Image:
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for i in range(h // 2):
        a = int(78 * (1 - i / (h / 2)) ** 1.35)
        d.rectangle((0, i, w, i + 1), fill=(20, 8, 4, a))
    mask = _ellipse_mask(w, h, feather=0.10, inset=0.04)
    r, g, b, a = im.split()
    im.putalpha(ImageChops.multiply(a, mask))
    return im


def _warm_light(size: int = 512) -> Image.Image:
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    cx = cy = size / 2
    for i, (r, a) in enumerate(((0.5, 40), (0.32, 70), (0.16, 90), (0.07, 50))):
        rad = size * r
        d.ellipse((cx - rad, cy - rad, cx + rad, cy + rad), fill=(255, 210, 140, a))
    return im.filter(ImageFilter.GaussianBlur(radius=18))


def main() -> None:
    im = Image.open(FACE).convert("RGB")
    OUT.mkdir(parents=True, exist_ok=True)

    mask = _ellipse_mask(256, 160, feather=0.16, inset=0.02)
    Image.merge("RGBA", (Image.new("L", mask.size, 255),) * 3 + (mask,)).save(OUT / "eye-mask.png")
    _catchlight(64).save(OUT / "eye-catchlight.png")
    _wetline(256, 160).save(OUT / "eye-wetline.png")
    _socket_shadow(256, 160).save(OUT / "eye-socket-shadow.png")
    _warm_light(512).save(OUT / "face-warm-light.png")

    lu, ll = _crop_norm(im, LEFT_SKIN_U), _crop_norm(im, LEFT_SKIN_L)
    ru, rl = _crop_norm(im, RIGHT_SKIN_U), _crop_norm(im, RIGHT_SKIN_L)
    _lid_texture(lu, 256, 160, True, (40, 20, 12)).save(OUT / "lid-left-upper.png")
    _lid_texture(ll, 256, 160, False, (55, 30, 18)).save(OUT / "lid-left-lower.png")
    _lid_texture(ru, 256, 160, True, (28, 14, 8)).save(OUT / "lid-right-upper.png")
    _lid_texture(rl, 256, 160, False, (70, 40, 24)).save(OUT / "lid-right-lower.png")
    print("Wrote eye assets to", OUT)


if __name__ == "__main__":
    main()
