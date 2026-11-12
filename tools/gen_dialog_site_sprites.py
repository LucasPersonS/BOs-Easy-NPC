"""Pixel-art sprites of the dialogue UI, copied from the website's dialogue library (Website/admin/dialogues.css).

1 texel = 1 GUI px. CSS borders of 2-3 px become 1 GUI px (at GUI scale 3 that is 3 screen px, like the site).
Uses the paravexia-ui-forge kit (grain, seamless tiling, nine-slice .mcmeta, preview sheet).

Run:  python tools/gen_dialog_site_sprites.py [--mock out.png]
Out:  core/Common/src/main/resources/assets/easy_npc/textures/gui/sprites/dialog/*.png (+ .mcmeta)
      core/Common/src/main/resources/assets/easy_npc/textures/gui/dialog_site/keycap.png
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, ".."))
ROOT = os.path.normpath(os.path.join(REPO, "..", "..", ".."))
sys.path.insert(0, os.path.join(ROOT, ".claude", "skills", "paravexia-ui-forge", "scripts"))
import ui_kit as K  # noqa: E402

ASSETS = os.path.join(REPO, "core", "Common", "src", "main", "resources", "assets", "easy_npc", "textures", "gui")
SPRITES = os.path.join(ASSETS, "sprites", "dialog")
PLAIN = os.path.join(ASSETS, "dialog_site")
FONT = os.path.join(REPO, "core", "NeoForge", "src", "main", "resources", "assets", "easy_npc", "font", "dtm_sans.ttf")

# --- Palette: Website/admin/tool-v2.css (--g-*) + dialogues.css -------------------------------------------
BLACK = (0, 0, 0)
GOLD = (241, 182, 75)          # --g-gold
DIM = (155, 143, 128)          # --g-dim
TEXT = (239, 229, 214)         # .d-text color
STONE = (44, 38, 34)           # --g-stone   (.face background)
STONE_HI = (60, 52, 46)        # --g-stone-hi (.face inset top)
SCREEN_IN = (36, 29, 25)       # .d-screen inset #241d19
SCREEN_FILL = (8, 6, 5)        # .d-screen gradient rgba(8,6,5,.55-.8)
TEXT_IN = (58, 47, 39)         # .d-text inset #3a2f27
TEXT_FILL = (16, 12, 10)       # .d-text rgba(16,12,10,.92)
REPLY = (58, 49, 43)           # .d-reply #3a312b
REPLY_HI = (90, 74, 63)        # inset 0 2px 0 #5a4a3f
REPLY_LO = (36, 29, 25)        # inset 0 -2px 0 #241d19
REPLY_HOVER = (74, 63, 55)     # :hover #4a3f37
SEG = [(232, 184, 74), (178, 140, 255), (124, 217, 146), (255, 138, 77),
       (142, 197, 255), (255, 122, 179), (217, 207, 160), (92, 200, 232)]


def grain(base, w, h, border, amp, seed):
    """Flat CSS colour with a faint 3-step stone grain; the centre cell is seamless so nine-slice tiling hides it."""
    def fn(U, V):
        n = K.band(K.fbm(U, V, 0.45, seed), 3) - 0.5
        return np.asarray(base, float)[None, None, :] * (1.0 + amp * n[..., None])
    return K.tiled(fn, w, h, border)


def box(w, h, *, fill, alpha=1.0, inset=None, top=None, bottom=None, corner=False, amp=0.0, seed=1.0, border=2):
    rgb = grain(fill, w, h, border, amp, seed) if amp else np.zeros((h, w, 3)) + fill
    a = np.full((h, w), alpha)
    if inset is not None:  # 1 px ring just inside the black edge (CSS inset box-shadow)
        rgb[1, 1:-1] = inset; rgb[-2, 1:-1] = inset; rgb[1:-1, 1] = inset; rgb[1:-1, -2] = inset
        a[1, 1:-1] = a[-2, 1:-1] = a[1:-1, 1] = a[1:-1, -2] = 1.0
    if top is not None:
        rgb[1, 1:-1] = top; a[1, 1:-1] = 1.0
    if bottom is not None:
        rgb[-2, 1:-1] = bottom; a[-2, 1:-1] = 1.0
    rgb[0, :] = rgb[-1, :] = BLACK; rgb[:, 0] = rgb[:, -1] = BLACK
    a[0, :] = a[-1, :] = a[:, 0] = a[:, -1] = 1.0
    if corner:  # border-radius 2-3 px -> one notched texel
        for y, x in ((0, 0), (0, -1), (-1, 0), (-1, -1)):
            a[y, x] = 0.0
    return K.rgba(rgb, a)


def tail():
    """Speech tail under the teaser box. Row 0/1 overlap the box's inset/black bottom rows."""
    rows = ["II.....II",
            "KI.....IK",
            " KI...IK ",
            "  KI.IK  ",
            "   KIK   ",
            "    K    "]
    out = np.zeros((len(rows), 9, 4), np.uint8)
    pal = {"K": BLACK + (255,), "I": TEXT_IN + (255,), ".": TEXT_FILL + (235,)}
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c in pal:
                out[y, x] = pal[c]
    return out


def keycap():
    """11x22: number key, idle (v=0) and selected (v=11)."""
    idle = box(11, 11, fill=SCREEN_IN, top=STONE_HI, corner=True)
    lit = box(11, 11, fill=GOLD, top=(252, 218, 146), bottom=(178, 128, 42), corner=True)
    return np.concatenate([idle, lit], 0)


def build():
    sprites = {
        # .d-screen: black edge, #241d19 inset, darkened backdrop (the world plays the banner's part)
        "panel": (box(16, 16, fill=SCREEN_FILL, alpha=0.80, inset=SCREEN_IN, corner=True, amp=0.5, seed=3.0), 2),
        # .d-text
        "text": (box(12, 12, fill=TEXT_FILL, alpha=0.92, inset=TEXT_IN, amp=0.6, seed=5.0), 2),
        # .d-speaker .face
        "face": (box(12, 12, fill=STONE, top=STONE_HI), 2),
        # .d-reply: normal / :hover (selected) / pressed / .empty-like locked
        "reply": (box(16, 16, fill=REPLY, top=REPLY_HI, bottom=REPLY_LO, corner=True, amp=0.12, seed=7.0), 2),
        "reply_hover": (box(16, 16, fill=REPLY_HOVER, top=(106, 89, 76), bottom=REPLY_LO, corner=True, amp=0.12, seed=7.0), 2),
        "reply_pressed": (box(16, 16, fill=STONE, top=REPLY_LO, bottom=REPLY_HI, corner=True, amp=0.12, seed=7.0), 2),
        "reply_locked": (box(16, 16, fill=(38, 33, 29), top=(50, 43, 38), bottom=(26, 21, 18), corner=True), 2),
    }
    for name, (img, border) in sprites.items():
        K.save_sprite(img, os.path.join(SPRITES, name + ".png"), border)
    K.save_png(tail(), os.path.join(PLAIN, "tail.png"))
    K.save_png(keycap(), os.path.join(PLAIN, "keycap.png"))
    items = [img for img, _ in sprites.values()] + [tail(), keycap()]
    K.preview_sheet(items, os.path.join(REPO, "tools", "out", "dialog_site_sheet.png"), scale=8)
    return {k: v[0] for k, v in sprites.items()}


# --- Mock of the in-game panel (same geometry as DialogUi / WorldDialogOverlay) ------------------------------
def nine(dst, img, x, y, w, h, b):
    """Vanilla nine-slice: corners copied, edges and centre tiled."""
    src = Image.fromarray(img, "RGBA")
    sw, sh = src.size
    def tile(sx, sy, sw_, sh_, dx, dy, dw, dh):
        if dw <= 0 or dh <= 0:
            return
        piece = src.crop((sx, sy, sx + sw_, sy + sh_))
        for ty in range(0, dh, sh_):
            for tx in range(0, dw, sw_):
                p = piece.crop((0, 0, min(sw_, dw - tx), min(sh_, dh - ty)))
                dst.alpha_composite(p, (dx + tx, dy + ty))
    cw, ch = sw - 2 * b, sh - 2 * b
    tile(0, 0, b, b, x, y, b, b); tile(sw - b, 0, b, b, x + w - b, y, b, b)
    tile(0, sh - b, b, b, x, y + h - b, b, b); tile(sw - b, sh - b, b, b, x + w - b, y + h - b, b, b)
    tile(b, 0, cw, b, x + b, y, w - 2 * b, b); tile(b, sh - b, cw, b, x + b, y + h - b, w - 2 * b, b)
    tile(0, b, b, ch, x, y + b, b, h - 2 * b); tile(sw - b, b, b, ch, x + w - b, y + b, b, h - 2 * b)
    tile(b, b, cw, ch, x + b, y + b, w - 2 * b, h - 2 * b)


def mock(sprites, path):
    W, H = 480, 270  # GUI px (1080p @ scale 4)
    banner = os.path.join(ROOT, "Website", "assets", "dungeons", "banners", "forest.png")
    bg = Image.open(banner).convert("RGBA").resize((W, H), Image.NEAREST) if os.path.exists(banner) \
        else Image.new("RGBA", (W, H), (60, 90, 60, 255))
    font = ImageFont.truetype(FONT, 10)
    d = ImageDraw.Draw(bg)
    d.fontmode = "1"

    def text(x, y, s, col, shadow=False):
        if shadow:
            d.text((x + 1, y + 1), s, font=font, fill=(0, 0, 0, 255))
        d.text((x, y), s, font=font, fill=col + (255,))

    # teaser over a head
    tx, ty, tw = 60, 40, 150
    th = 6 + 10 + 2 * 10 + 3
    nine(bg, sprites["text"], tx, ty, tw, th, 2)
    bg.alpha_composite(Image.fromarray(tail(), "RGBA"), (tx + 30, ty + th - 2))
    text(tx + 6, ty + 4, "Mercadora Ilse", GOLD, True)
    text(tx + 6, ty + 15, "Viajante! Tenho tecidos de", TEXT)
    text(tx + 6, ty + 25, "Visário e chá de cardo...", TEXT)

    # full panel at the bottom
    pw = 296; px = (W - pw) // 2; pad = 6
    lines = ["Ah, outro forasteiro. A estrada até a Encruzilhada",
             "anda perigosa; os lobos desceram das colinas desde",
             "que o sino da capela parou de tocar."]
    replies = [("Que sino é esse?", False, False), ("Quero ver suas mercadorias.", True, False),
               ("Preciso de um guia (nível 10)", False, True), ("Até mais.", False, False)]
    text_h = len(lines) * 10 + 9
    rows = len(replies) * 14 + (len(replies) - 1) * 3
    ph = pad + 20 + 4 + text_h + 4 + rows + 4 + 12 + pad - 2
    py = H - 52 - ph
    nine(bg, sprites["panel"], px, py, pw, ph, 2)
    nine(bg, sprites["face"], px + pad, py + pad, 20, 20, 2)
    head = Image.new("RGBA", (8, 8), (176, 132, 100, 255))
    hd = ImageDraw.Draw(head); hd.rectangle((0, 0, 7, 2), fill=(70, 46, 30, 255)); hd.point([(2, 4), (5, 4)], fill=(30, 30, 60, 255))
    bg.alpha_composite(head.resize((16, 16), Image.NEAREST), (px + pad + 2, py + pad + 2))
    text(px + pad + 26, py + pad + 6, "Mercadora Ilse", GOLD, True)
    hint = "Missões"
    bg.alpha_composite(Image.open(os.path.join(ASSETS, "world_dialog", "mouse_right.png")).crop((0, 0, 16, 16)),
                       (px + pw - pad - 18 - int(font.getlength(hint)), py + pad + 2))
    text(px + pw - pad - int(font.getlength(hint)), py + pad + 6, hint, DIM)
    ty2 = py + pad + 24
    nine(bg, sprites["text"], px + pad, ty2, pw - 2 * pad, text_h, 2)
    for i, ln in enumerate(lines):
        text(px + pad + 5, ty2 + 4 + i * 10, ln, TEXT)
    ry = ty2 + text_h + 4
    kc = keycap()
    for i, (label, merchant, locked) in enumerate(replies):
        sel = i == 0
        name = "reply_locked" if locked else "reply_hover" if sel else "reply"
        x = px + pad + (2 if sel else 0)
        nine(bg, sprites[name], x, ry, pw - 2 * pad, 14, 2)
        stripe = SEG[i % len(SEG)] if not locked else (80, 70, 62)
        d.rectangle((x + 1, ry + 1, x + 2, ry + 12), fill=stripe + (255,))
        bg.alpha_composite(Image.fromarray(kc[11:] if sel else kc[:11], "RGBA"), (x + 5, ry + 2))
        text(x + 8, ry + 2, str(i + 1), (0, 0, 0) if sel else (DIM if locked else GOLD))
        text(x + 20, ry + 2, label, GOLD if sel else (DIM if locked else TEXT))
        if merchant:
            text(x + pw - 2 * pad - 50, ry + 2, "comércio", DIM)
        ry += 17
    fy = ry + 1
    bg.alpha_composite(Image.open(os.path.join(ASSETS, "world_dialog", "mouse_right.png")).crop((0, 0, 16, 16)), (px + pad, fy - 3))
    text(px + pad + 18, fy, "Escolher   Roda: navegar   Teclas 1-4", DIM)
    out = bg.resize((W * 3, H * 3), Image.NEAREST)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    out.save(path)
    print("mock", path)


if __name__ == "__main__":
    s = build()
    print("sprites ->", SPRITES)
    if "--mock" in sys.argv:
        mock(s, sys.argv[sys.argv.index("--mock") + 1])
