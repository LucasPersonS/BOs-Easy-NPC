"""Pixel-art sprites for the world dialogue HUD (right-mouse prompt). Run: python tools/gen_world_dialog_sprites.py"""
import os, sys
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "core", "Common", "src", "main", "resources",
                   "assets", "easy_npc", "textures", "gui", "world_dialog")
IVORY = (232, 220, 196, 255)
INK = (26, 21, 32, 255)
INK_HI = (52, 44, 60, 255)
GOLD = (226, 186, 104, 255)
GOLD_HI = (250, 222, 150, 255)
GOLD_DARK = (150, 112, 52, 255)
WHEEL = (120, 110, 100, 255)
SHADOW = (0, 0, 0, 150)

X0, X1, Y0, Y1 = 3, 11, 2, 15  # mouse body (inclusive)
CUT = {(0, 0), (1, 0), (0, 1)}  # top corner cut (relative), mirrored per corner
CUT_BOTTOM = {(0, 0), (1, 0), (2, 0), (0, 1), (1, 1), (0, 2)}

def inside(x, y):
    if not (X0 <= x <= X1 and Y0 <= y <= Y1):
        return False
    rx = min(x - X0, X1 - x)
    if (rx, y - Y0) in CUT or (rx, Y1 - y) in CUT_BOTTOM:
        return False
    return True

def frame(pressed):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    mid = (X0 + X1) // 2  # 7
    split_y = 8
    for y in range(16):
        for x in range(16):
            if not inside(x, y):
                continue
            edge = any(not inside(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if edge or y == split_y or (x == mid and y < split_y):
                px[x, y] = INK
            elif x > mid and y < split_y:
                if pressed:
                    px[x, y] = GOLD_DARK if y == Y0 + 1 else GOLD
                else:
                    px[x, y] = GOLD_HI if (y == Y0 + 1 or x == mid + 1) else GOLD
            else:
                px[x, y] = IVORY if not (y == Y1 - 1 or x == X1 - 1) else (196, 182, 156, 255)
    px[mid, Y0 + 2] = WHEEL; px[mid, Y0 + 3] = WHEEL
    if pressed:
        # Click rays off the right button.
        for x, y in ((13, 1), (14, 0), (13, 4), (14, 4), (15, 4), (11, 0)):
            px[x, y] = GOLD_HI
    return img

def main():
    os.makedirs(OUT, exist_ok=True)
    sheet = Image.new("RGBA", (32, 16), (0, 0, 0, 0))
    sheet.paste(frame(False), (0, 0)); sheet.paste(frame(True), (16, 0))
    path = os.path.join(OUT, "mouse_right.png")
    sheet.save(path)
    if "--preview" in sys.argv:
        bg = Image.new("RGBA", (32, 16), (90, 120, 80, 255)); bg.alpha_composite(sheet)
        bg.resize((32 * 12, 16 * 12), Image.NEAREST).save(sys.argv[sys.argv.index("--preview") + 1])
    print("wrote", os.path.normpath(path))

main()
