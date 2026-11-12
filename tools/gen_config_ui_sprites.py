"""Generate Paravexia Website + Chat-Bubble themed Config UI textures & sprites for EasyNPC-Scenes.

Mirrors the visual language of:
  - Website/admin/tool-v2.css, dialogues.css, npcs.css, panel-kit.css
  - tools/gen_dialog_site_sprites.py & DialogUi.java

Palette (--g-*):
  BLACK        #000000  (  0,   0,   0)
  EDGE         #0a0807  ( 10,   8,   7)
  STAGE_FILL   #0e0b09  ( 14,  11,   9)
  TEXT_FILL    #100c0a  ( 16,  12,  10)
  BG           #110d0c  ( 17,  13,  12)
  PANEL_FILL   #1b1614  ( 27,  22,  20)
  PANEL_IN     #2c241f  ( 44,  36,  31)
  STONE        #2c2622  ( 44,  38,  34)
  STONE_HI     #3c342e  ( 60,  52,  46)
  TEXT_IN      #3a2f27  ( 58,  47,  39)
  BTN          #3a312b  ( 58,  49,  43)
  BTN_TOP      #5a4a3f  ( 90,  74,  63)
  BTN_BOT      #241d19  ( 36,  29,  25)
  BTN_HOV      #4a3f37  ( 74,  63,  55)
  BTN_HOV_TOP  #6a594c  (106,  89,  76)
  BTN_PRESS    #26201c  ( 38,  32,  28)
  BTN_LOCK     #26211d  ( 38,  33,  29)
  GOLD         #f1b64b  (241, 182,  75)
  GOLD_HI      #ffe08a  (255, 224, 138)
  GOLD_DARK    #9e6e24  (158, 110,  36)
  TEXT         #efe5d6  (239, 229, 214)
  DIM          #9b8f80  (155, 143, 128)
  MUTED        #63584e  ( 99,  88,  78)
  GREEN        #7cd992  (124, 217, 146)
  GREEN_DARK   #3b7a4c  ( 59, 122,  76)
  RED          #ff6b5a  (255, 107,  90)
  RED_DARK     #8f2d24  (143,  45,  36)
  BLUE         #8ec5ff  (142, 197, 255)
  BLUE_DARK    #3b699e  ( 59, 105, 158)
  PURPLE       #b28cff  (178, 140, 255)
  PURPLE_DARK  #5c3d99  ( 92,  61, 153)
"""
from __future__ import annotations

import json
import struct
import sys
import zlib
from pathlib import Path
import numpy as np

TOOLKIT = Path(r"C:/Users/Lucas/Documents/Servidor Paravexia/models/_toolkit")
SKILL = Path(r"C:/Users/Lucas/Documents/Servidor Paravexia/.claude/skills/paravexia-ui-forge/scripts")
sys.path.insert(0, str(TOOLKIT))
sys.path.insert(0, str(SKILL))

from paint_lib import vnoise  # noqa: E402


class _Kit:
    @staticmethod
    def zeros(w: int, h: int) -> np.ndarray:
        return np.zeros((h, w, 4), dtype=np.float64)

    @staticmethod
    def grid(w: int, h: int) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
        V, U = np.mgrid[0:h, 0:w].astype(np.float64)
        return U + 0.5, V + 0.5, (U + 0.5) / max(w, 1), (V + 0.5) / max(h, 1)

    @staticmethod
    def band(t: np.ndarray, n: int = 6) -> np.ndarray:
        return np.floor(np.clip(t, 0.0, 0.999) * n) / max(n - 1, 1)

    @staticmethod
    def fbm(U: np.ndarray, V: np.ndarray, s: float, seed: float = 0.0, octaves: int = 3) -> np.ndarray:
        r, a, f = 0.0, 0.5, 1.0
        for _ in range(octaves):
            r = r + a * vnoise(U * s * f + seed, V * s * f + seed * 1.7)
            a *= 0.5
            f *= 2.0
        return r / 0.875

    @staticmethod
    def tiled(fn, U: np.ndarray, V: np.ndarray) -> np.ndarray:
        # Seamless 2D blend across [0..1]x[0..1]
        wx = np.sin(np.clip(U, 0.0, 1.0) * np.pi) ** 2
        wy = np.sin(np.clip(V, 0.0, 1.0) * np.pi) ** 2
        w = wx * wy
        a = fn(U * 16.0, V * 16.0)
        b = fn(((U + 0.5) % 1.0) * 16.0, ((V + 0.5) % 1.0) * 16.0)
        return a * w + b * (1.0 - w)

    saved: dict[Path, np.ndarray] = {}

    @staticmethod
    def save_png(buf: np.ndarray, path: Path | str) -> None:
        p = Path(path)
        p.parent.mkdir(parents=True, exist_ok=True)
        arr = np.clip(np.round(buf), 0, 255).astype(np.uint8)
        _Kit.saved[p] = arr
        h, w = arr.shape[:2]
        raw = b"".join(b"\x00" + arr[y].tobytes() for y in range(h))

        def chunk(tag: bytes, data: bytes) -> bytes:
            crc = zlib.crc32(tag + data) & 0xFFFFFFFF
            return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", crc)

        ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
        png_bytes = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
        p.write_bytes(png_bytes)

    @staticmethod
    def preview_sheet(paths: list[Path], out_path: Path | str, scale: int = 3, cols: int = 5) -> None:
        pad = 12
        scaled_items: list[tuple[str, np.ndarray]] = []
        for p in paths:
            arr = _Kit.saved.get(Path(p))
            if arr is None:
                continue
            s_Factor = scale if max(arr.shape[0], arr.shape[1]) <= 96 else 2
            s_arr = np.repeat(np.repeat(arr, s_Factor, axis=0), s_Factor, axis=1)
            scaled_items.append((Path(p).name, s_arr))

        max_w = 960
        rows: list[list[tuple[str, np.ndarray]]] = []
        cur_row: list[tuple[str, np.ndarray]] = []
        cur_w = pad
        for name, s_arr in scaled_items:
            iw = s_arr.shape[1]
            if cur_row and cur_w + iw + pad > max_w:
                rows.append(cur_row)
                cur_row = [(name, s_arr)]
                cur_w = pad + iw + pad
            else:
                cur_row.append((name, s_arr))
                cur_w += iw + pad
        if cur_row:
            rows.append(cur_row)

        total_h = pad + sum(max(it[1].shape[0] for it in r) + pad for r in rows)
        total_w = max(pad + sum(it[1].shape[1] + pad for it in r) for r in rows)
        canvas = np.zeros((total_h, total_w, 4), dtype=np.float64)
        # Checkerboard dark background so transparency is visible
        yy, xx = np.mgrid[0:total_h, 0:total_w]
        chk = ((xx // 8 + yy // 8) % 2 == 0)
        canvas[..., 0] = np.where(chk, 20, 28)
        canvas[..., 1] = np.where(chk, 16, 23)
        canvas[..., 2] = np.where(chk, 14, 20)
        canvas[..., 3] = 255

        y_cursor = pad
        for r in rows:
            x_cursor = pad
            row_h = max(it[1].shape[0] for it in r)
            for _name, s_arr in r:
                ih, iw = s_arr.shape[:2]
                alpha = s_arr[..., 3:4].astype(np.float64) / 255.0
                dst = canvas[y_cursor : y_cursor + ih, x_cursor : x_cursor + iw, :3]
                canvas[y_cursor : y_cursor + ih, x_cursor : x_cursor + iw, :3] = s_arr[..., :3] * alpha + dst * (1.0 - alpha)
                x_cursor += iw + pad
            y_cursor += row_h + pad

        _Kit.save_png(canvas, out_path)


K = _Kit()

ROOT = Path(__file__).resolve().parents[1]
CORE_GUI = ROOT / "core/Common/src/main/resources/assets/easy_npc/textures/gui"
CORE_SPRITES = CORE_GUI / "sprites/config"
CONFIG_GUI = ROOT / "config-ui/Common/src/main/resources/assets/easy_npc_config_ui/textures/gui"
PREVIEW_DIR = ROOT / "tools/out"

# Palette RGB tuples
BLACK = (0, 0, 0)
EDGE = (10, 8, 7)
STAGE_FILL = (12, 9, 8)
TEXT_FILL = (16, 12, 10)
PANEL_FILL = (27, 22, 20)
PANEL_IN = (44, 36, 31)
STONE = (44, 38, 34)
STONE_HI = (60, 52, 46)
STONE_DARK = (22, 18, 15)
TEXT_IN = (58, 47, 39)

BTN_FILL = (58, 49, 43)
BTN_TOP = (90, 74, 63)
BTN_BOT = (36, 29, 25)
BTN_SIDE = (49, 41, 36)

BTN_HOV_FILL = (74, 63, 55)
BTN_HOV_TOP = (112, 94, 80)
BTN_HOV_SIDE = (92, 76, 62)

BTN_PRESS_FILL = (38, 32, 28)
BTN_PRESS_TOP = (24, 19, 16)
BTN_PRESS_BOT = (56, 47, 41)

BTN_LOCK_FILL = (34, 29, 26)
BTN_LOCK_TOP = (46, 39, 35)
BTN_LOCK_BOT = (24, 20, 17)

GOLD = (241, 182, 75)
GOLD_HI = (255, 224, 138)
GOLD_DARK = (148, 102, 32)

TEXT = (239, 229, 214)
DIM = (155, 143, 128)
MUTED = (95, 84, 74)

GREEN = (124, 217, 146)
GREEN_HI = (184, 242, 197)
GREEN_DARK = (54, 112, 68)

RED = (255, 107, 90)
RED_HI = (255, 168, 158)
RED_DARK = (138, 42, 34)

BLUE = (142, 197, 255)
BLUE_HI = (196, 226, 255)
BLUE_DARK = (58, 102, 156)

PURPLE = (178, 140, 255)
PURPLE_HI = (216, 196, 255)
PURPLE_DARK = (92, 61, 153)


def _stone_grain(w: int, h: int, base: tuple[int, int, int], amp: float, seed: int) -> np.ndarray:
    buf = K.zeros(w, h)
    X, Y, _, _ = K.grid(w, h)
    # Use non-tiled fbm for large sheets or tiled for small nine-slice sprites
    if w <= 64 and h <= 64:
        U = X / float(max(w, 8))
        V = Y / float(max(h, 8))
        n = K.band(K.tiled(lambda u, v: K.fbm(u, v, 0.45, seed, octaves=2), U, V), 3)
    else:
        U = X / 24.0
        V = Y / 24.0
        n = K.band(K.fbm(U, V, 0.45, seed, octaves=2), 3)
    delta = (n - 0.5) * 2.0 * amp
    for c in range(3):
        buf[..., c] = np.clip(base[c] + delta, 0, 255)
    buf[..., 3] = 255.0
    return buf


def _set_px(buf: np.ndarray, x: int, y: int, rgb: tuple[int, int, int], a: int = 255) -> None:
    h, w = buf.shape[:2]
    if 0 <= x < w and 0 <= y < h:
        buf[y, x, 0] = rgb[0]
        buf[y, x, 1] = rgb[1]
        buf[y, x, 2] = rgb[2]
        buf[y, x, 3] = a


def _fill_rect(buf: np.ndarray, x0: int, y0: int, w: int, h: int, rgb: tuple[int, int, int], a: int = 255) -> None:
    H, W = buf.shape[:2]
    xa, xb = max(0, x0), min(W, x0 + w)
    ya, yb = max(0, y0), min(H, y0 + h)
    if xa < xb and ya < yb:
        buf[ya:yb, xa:xb, 0] = rgb[0]
        buf[ya:yb, xa:xb, 1] = rgb[1]
        buf[ya:yb, xa:xb, 2] = rgb[2]
        buf[ya:yb, xa:xb, 3] = a


def _frame_box(
    buf: np.ndarray,
    x0: int,
    y0: int,
    w: int,
    h: int,
    fill_rgb: tuple[int, int, int],
    top_rgb: tuple[int, int, int],
    bot_rgb: tuple[int, int, int],
    side_rgb: tuple[int, int, int] | None = None,
    outer_rgb: tuple[int, int, int] = BLACK,
    notched: bool = True,
    amp: float = 2.0,
    seed: int = 1,
) -> None:
    if side_rgb is None:
        side_rgb = bot_rgb
    sub = _stone_grain(w, h, fill_rgb, amp=amp, seed=seed)
    # Outer 1px border
    sub[0, :, :3] = outer_rgb
    sub[-1, :, :3] = outer_rgb
    sub[:, 0, :3] = outer_rgb
    sub[:, -1, :3] = outer_rgb
    # Inner 1px bevel
    if h > 2 and w > 2:
        sub[1, 1:-1, :3] = top_rgb
        sub[-2, 1:-1, :3] = bot_rgb
        sub[1:-1, 1, :3] = side_rgb
        sub[1:-1, -2, :3] = side_rgb
        sub[1, 1:-1, :3] = top_rgb
        sub[-2, 1:-1, :3] = bot_rgb
    if notched and w >= 6 and h >= 6:
        for cy, cx in ((0, 0), (0, w - 1), (h - 1, 0), (h - 1, w - 1)):
            sub[cy, cx] = (0, 0, 0, 0)
        for cy, cx in ((1, 1), (1, w - 2), (h - 2, 1), (h - 2, w - 2)):
            sub[cy, cx, :3] = outer_rgb
            sub[cy, cx, 3] = 255
    buf[y0 : y0 + h, x0 : x0 + w] = sub


def _save_nine_slice(name: str, buf: np.ndarray, border: int) -> Path:
    W, H = buf.shape[1], buf.shape[0]
    png = CORE_SPRITES / f"{name}.png"
    K.save_png(buf, png)
    meta = {
        "gui": {
            "scaling": {
                "type": "nine_slice",
                "width": W,
                "height": H,
                "border": border,
                "stretch_inner": False,
            }
        }
    }
    (CORE_SPRITES / f"{name}.png.mcmeta").write_text(json.dumps(meta, indent=2) + "\n")
    return png


def build_nine_slice_sprites() -> dict[str, Path]:
    out: dict[str, Path] = {}

    # 1. window (32x32, border 4): Main dark stone panel
    win = K.zeros(32, 32)
    _frame_box(win, 0, 0, 32, 32, PANEL_FILL, PANEL_IN, (18, 14, 12), PANEL_IN, BLACK, notched=True, amp=2.2, seed=10)
    # Corner rivets at (3,3), (28,3), (3,28), (28,28)
    for rx, ry in ((3, 3), (28, 3), (3, 28), (28, 28)):
        _set_px(win, rx, ry, STONE_HI)
    out["window"] = _save_nine_slice("window", win, border=4)

    # 2. header (32x18, border 4): Top header plaque with gold bottom accent line
    hdr = K.zeros(32, 18)
    _frame_box(hdr, 0, 0, 32, 18, STONE, STONE_HI, STONE_DARK, PANEL_IN, BLACK, notched=True, amp=2.0, seed=20)
    # Gold accent bar along bottom inner row (y=16)
    hdr[16, 2:-2, :3] = GOLD_DARK
    hdr[15, 2:-2, :3] = (30, 24, 20)
    out["header"] = _save_nine_slice("header", hdr, border=4)

    # 3. subnav (32x22, border 3): Sub-navigation bar
    subnav = K.zeros(32, 22)
    _frame_box(subnav, 0, 0, 32, 22, (21, 17, 15), (34, 28, 24), (12, 9, 8), (28, 23, 20), BLACK, notched=False, amp=1.5, seed=30)
    out["subnav"] = _save_nine_slice("subnav", subnav, border=3)

    # 4. inset (24x24, border 3): Recessed dark stage/well
    inset = K.zeros(24, 24)
    _frame_box(inset, 0, 0, 24, 24, TEXT_FILL, (8, 6, 5), TEXT_IN, (36, 29, 25), BLACK, notched=True, amp=1.8, seed=40)
    out["inset"] = _save_nine_slice("inset", inset, border=3)

    # 5. card (24x24, border 3): Raised stone card/section
    card = K.zeros(24, 24)
    _frame_box(card, 0, 0, 24, 24, (36, 30, 26), STONE_HI, (20, 16, 14), PANEL_IN, BLACK, notched=True, amp=2.0, seed=50)
    out["card"] = _save_nine_slice("card", card, border=3)

    # 6. slot (18x18, border 2): Dark stone 18x18 item slot (.t-slot)
    slot = K.zeros(18, 18)
    _frame_box(slot, 0, 0, 18, 18, (18, 14, 12), (8, 6, 5), TEXT_IN, (36, 29, 25), BLACK, notched=False, amp=1.6, seed=60)
    # Subtle corner shadow inside the well
    _set_px(slot, 2, 2, (10, 8, 7))
    _set_px(slot, 15, 2, (12, 9, 8))
    _set_px(slot, 2, 15, (12, 9, 8))
    out["slot"] = _save_nine_slice("slot", slot, border=2)

    # 7. button (24x20, border 3): Normal stone button (.tv-pxbtn / .d-reply)
    btn = K.zeros(24, 20)
    _frame_box(btn, 0, 0, 24, 20, BTN_FILL, BTN_TOP, BTN_BOT, BTN_SIDE, BLACK, notched=True, amp=2.4, seed=70)
    out["button"] = _save_nine_slice("button", btn, border=3)

    # 8. button_hover (24x20, border 3): Hovered stone button
    btn_h = K.zeros(24, 20)
    _frame_box(btn_h, 0, 0, 24, 20, BTN_HOV_FILL, BTN_HOV_TOP, BTN_BOT, BTN_HOV_SIDE, BLACK, notched=True, amp=2.4, seed=71)
    out["button_hover"] = _save_nine_slice("button_hover", btn_h, border=3)

    # 9. button_pressed (24x20, border 3): Pressed stone button
    btn_p = K.zeros(24, 20)
    _frame_box(btn_p, 0, 0, 24, 20, BTN_PRESS_FILL, BTN_PRESS_TOP, BTN_PRESS_BOT, (30, 25, 22), BLACK, notched=True, amp=2.0, seed=72)
    out["button_pressed"] = _save_nine_slice("button_pressed", btn_p, border=3)

    # 10. button_disabled (24x20, border 3): Disabled/locked stone button
    btn_d = K.zeros(24, 20)
    _frame_box(btn_d, 0, 0, 24, 20, BTN_LOCK_FILL, BTN_LOCK_TOP, BTN_LOCK_BOT, (28, 24, 21), BLACK, notched=True, amp=1.5, seed=73)
    out["button_disabled"] = _save_nine_slice("button_disabled", btn_d, border=3)

    # 11. tab_active (24x20, border 3): Selected tab / active breadcrumb with 2px gold bottom bar
    tab_a = K.zeros(24, 20)
    _frame_box(tab_a, 0, 0, 24, 20, (44, 36, 31), (74, 60, 48), GOLD, (58, 47, 39), BLACK, notched=True, amp=2.0, seed=74)
    tab_a[-2, 2:-2, :3] = GOLD
    tab_a[-3, 2:-2, :3] = GOLD_DARK
    out["tab_active"] = _save_nine_slice("tab_active", tab_a, border=3)

    # 12. input & input_focused (24x18, border 3)
    inp = K.zeros(24, 18)
    _frame_box(inp, 0, 0, 24, 18, STAGE_FILL, (6, 4, 3), TEXT_IN, (38, 31, 26), BLACK, notched=True, amp=1.5, seed=80)
    out["input"] = _save_nine_slice("input", inp, border=3)

    inp_f = K.zeros(24, 18)
    _frame_box(inp_f, 0, 0, 24, 18, (20, 15, 12), GOLD_DARK, GOLD, GOLD_DARK, BLACK, notched=True, amp=1.5, seed=81)
    out["input_focused"] = _save_nine_slice("input_focused", inp_f, border=3)

    # 13. slider_track (24x16, border 3), slider_handle (8x16, border 2), slider_handle_hover (8x16, border 2)
    sl_t = K.zeros(24, 16)
    _frame_box(sl_t, 0, 0, 24, 16, (14, 11, 9), (6, 4, 3), (48, 39, 33), (32, 26, 22), BLACK, notched=True, amp=1.5, seed=90)
    # Center groove line at y=7..8
    sl_t[7, 3:-3, :3] = (6, 4, 3)
    sl_t[8, 3:-3, :3] = (36, 29, 25)
    out["slider_track"] = _save_nine_slice("slider_track", sl_t, border=3)

    sl_h = K.zeros(8, 16)
    _frame_box(sl_h, 0, 0, 8, 16, BTN_FILL, BTN_TOP, BTN_BOT, BTN_SIDE, BLACK, notched=True, amp=2.0, seed=91)
    # Gold center grip line
    sl_h[5:11, 3, :3] = GOLD_DARK
    sl_h[5:11, 4, :3] = (30, 24, 20)
    out["slider_handle"] = _save_nine_slice("slider_handle", sl_h, border=2)

    sl_hh = K.zeros(8, 16)
    _frame_box(sl_hh, 0, 0, 8, 16, BTN_HOV_FILL, GOLD_HI, GOLD_DARK, GOLD, BLACK, notched=True, amp=2.0, seed=92)
    sl_hh[5:11, 3, :3] = GOLD_HI
    sl_hh[5:11, 4, :3] = GOLD_DARK
    out["slider_handle_hover"] = _save_nine_slice("slider_handle_hover", sl_hh, border=2)

    return out


# ==============================================================================
# PIXEL ART ICON PRIMITIVES (drawn from ASCII pattern strings)
# ==============================================================================
def _draw_pattern(
    buf: np.ndarray,
    x0: int,
    y0: int,
    rows: list[str],
    palette: dict[str, tuple[int, int, int]],
) -> None:
    """Draw an ASCII pixel-art icon at (x0, y0). '.' or ' ' is transparent."""
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch in (".", " "):
                continue
            rgb = palette.get(ch)
            if rgb is not None:
                _set_px(buf, x0 + dx, y0 + dy, rgb, 255)


def _draw_skin_card(buf: np.ndarray, x0: int, y0: int, selected: bool) -> None:
    """Draw 60x84 SkinSelectionButton card at (x0, y0)."""
    w, h = 60, 84
    if not selected:
        # Normal unselected card: recessed dark stage with subtle stone frame
        _frame_box(buf, x0, y0, w, h, (16, 12, 10), (36, 29, 25), (48, 39, 33), (36, 29, 25), BLACK, notched=True, amp=1.8, seed=101)
        # Inner stage border (2px inset)
        _fill_rect(buf, x0 + 3, y0 + 3, w - 6, h - 6, (12, 9, 8))
        # Subtle stage pedestal at bottom (y0 + 68 .. y0 + 78)
        _fill_rect(buf, x0 + 10, y0 + 71, w - 20, 3, (28, 22, 19))
        _fill_rect(buf, x0 + 14, y0 + 70, w - 28, 1, (44, 36, 31))
        # Corner ticks in warm stone
        for cx, cy in ((3, 3), (w - 4, 3), (3, h - 4), (w - 4, h - 4)):
            _set_px(buf, x0 + cx, y0 + cy, STONE_HI)
    else:
        # Selected card (rendered when button.active == false!): glowing gold frame + pedestal
        _frame_box(buf, x0, y0, w, h, (26, 20, 16), GOLD_HI, GOLD, GOLD, BLACK, notched=True, amp=2.0, seed=102)
        _fill_rect(buf, x0 + 2, y0 + 2, w - 4, h - 4, GOLD_DARK)
        _fill_rect(buf, x0 + 3, y0 + 3, w - 6, h - 6, (18, 14, 11))
        # Golden stage pedestal at bottom
        _fill_rect(buf, x0 + 8, y0 + 71, w - 16, 3, GOLD_DARK)
        _fill_rect(buf, x0 + 12, y0 + 70, w - 24, 1, GOLD)
        # Gold corner brackets (3x3 L-shapes)
        for bx, by, sx, sy in ((4, 4, 1, 1), (w - 5, 4, -1, 1), (4, h - 5, 1, -1), (w - 5, h - 5, -1, -1)):
            _set_px(buf, x0 + bx, y0 + by, GOLD_HI)
            _set_px(buf, x0 + bx + sx, y0 + by, GOLD)
            _set_px(buf, x0 + bx, y0 + by + sy, GOLD)
        # Small checkmark badge at top-right corner of selected card
        badge_rows = [
            ".KKKKK.",
            "KGGGGGK",
            "KG...GK",
            "KG..HGK",
            "KGH.HGK",
            "KGHH.GK",
            ".KKKKK.",
        ]
        _draw_pattern(buf, x0 + w - 11, y0 + 4, badge_rows, {"K": BLACK, "G": GOLD_DARK, "H": GOLD_HI})


def build_configuration_sheet() -> Path:
    """Build 256x256 configuration.png with all icons & SkinSelectionButton cards."""
    buf = K.zeros(256, 256)

    # 1. SkinSelectionButton (60x84) at (0, 0) [unselected] and (0, 84) [selected]
    _draw_skin_card(buf, 0, 0, selected=False)
    _draw_skin_card(buf, 0, 84, selected=True)

    # Helper for active + inactive icon pair
    def draw_pair(
        ux: int,
        uy: int,
        h: int,
        rows: list[str],
        pal_active: dict[str, tuple[int, int, int]],
        pal_inactive: dict[str, tuple[int, int, int]] | None = None,
    ) -> None:
        _draw_pattern(buf, ux, uy, rows, pal_active)
        if pal_inactive is None:
            pal_inactive = {
                k: (BLACK if v == BLACK else MUTED if v != STONE_DARK else STONE_DARK)
                for k, v in pal_active.items()
            }
        _draw_pattern(buf, ux, uy + h, rows, pal_inactive)

    # 2. SaveButton (13x13) at (64, 4), inactive at (64, 17)
    save_rows = [
        ".KKKKKKKKKK..",
        "KGGGGGGGGGGK.",
        "KGHHHHHHHHGGK",
        "KGHKKKKKKHGGK",
        "KGHKTTTTKHGGK",
        "KGHKKKKKKHGGK",
        "KGGGGGGGGGGGK",
        "KGKKKKKKKKKGK",
        "KGKSSSSSSSKGK",
        "KGKSLLLLLSKGK",
        "KGKSSSSSSSKGK",
        "KGGGGGGGGGGGK",
        ".KKKKKKKKKKK.",
    ]
    draw_pair(
        64,
        4,
        13,
        save_rows,
        {"K": BLACK, "G": GREEN_DARK, "H": TEXT, "T": GOLD, "S": GREEN, "L": GREEN_HI},
        {"K": BLACK, "G": (52, 46, 40), "H": DIM, "T": MUTED, "S": MUTED, "L": DIM},
    )

    # 3. CancelButton (10x10) at (64, 47), inactive at (64, 57)
    cancel_rows = [
        ".KK....KK.",
        "KRRK..KRRK",
        "KRHRKKRHRK",
        ".KRRRRRRK.",
        "..KRRRRK..",
        "..KRRRRK..",
        ".KRRRRRRK.",
        "KRHRKKRHRK",
        "KRRK..KRRK",
        ".KK....KK.",
    ]
    draw_pair(
        64,
        47,
        10,
        cancel_rows,
        {"K": BLACK, "R": RED, "H": RED_HI},
        {"K": BLACK, "R": MUTED, "H": DIM},
    )

    # 4. EditButton & ConditionButton Edit (12x12) at (64, 79), inactive at (64, 91)
    edit_rows = [
        "........KKK.",
        ".......KHHGK",
        "......KHHGGK",
        ".....KGGGGK.",
        "....KGGGGK..",
        "...KGGGGK...",
        "..KGGGGK....",
        ".KTTTTK.....",
        "KTTTTK......",
        "KTTKK.......",
        "KKKK........",
        "............",
    ]
    draw_pair(
        64,
        79,
        12,
        edit_rows,
        {"K": BLACK, "H": GOLD_HI, "G": GOLD, "T": TEXT},
        {"K": BLACK, "H": DIM, "G": MUTED, "T": DIM},
    )

    # 5. AddButton & ConditionButton Add (12x12) at (65, 104), inactive at (65, 116)
    add_rows = [
        "....KKKK....",
        "...KLLLLK...",
        "...KLGGLK...",
        ".KKKLGGLKKK.",
        "KLLLLGGLLLLK",
        "KLGGGGGGGGLK",
        "KLGGGGGGGGLK",
        ".KKKLGGLKKK.",
        "...KLGGLK...",
        "...KGGGGK...",
        "....KKKK....",
        "............",
    ]
    draw_pair(
        65,
        104,
        12,
        add_rows,
        {"K": BLACK, "L": GREEN_HI, "G": GREEN},
        {"K": BLACK, "L": DIM, "G": MUTED},
    )

    # 6. CopyButton (12x12) at (65, 130), inactive at (65, 142)
    copy_rows = [
        ".KKKKKKK....",
        "KBBBBBBBK...",
        "KBTTTTTBKKKK",
        "KBTKKKTKHHHK",
        "KBTTTTTKTHHK",
        "KBTKKKTKTKHK",
        "KBTTTTTKTTTK",
        "KBBBBBBKTKTK",
        ".KKKKKKKTTTK",
        ".......KTTTK",
        ".......KKKKK",
        "............",
    ]
    draw_pair(
        65,
        130,
        12,
        copy_rows,
        {"K": BLACK, "B": BLUE, "H": TEXT, "T": GOLD_HI},
        {"K": BLACK, "B": MUTED, "H": DIM, "T": MUTED},
    )

    # 7. DeleteButton (12x12) at (65, 155), inactive at (65, 167)
    delete_rows = [
        "...KKKKKK...",
        ".KKRRRRRRKK.",
        "KHHHHHHHHHHK",
        ".KKKKKKKKKK.",
        ".KRRRRRRRRK.",
        ".KRKRRRRKRK.",
        ".KRKRRRRKRK.",
        ".KRKRRRRKRK.",
        ".KRKRRRRKRK.",
        ".KRRRRRRRRK.",
        "..KKKKKKKK..",
        "............",
    ]
    draw_pair(
        65,
        155,
        12,
        delete_rows,
        {"K": BLACK, "R": RED, "H": RED_HI},
        {"K": BLACK, "R": MUTED, "H": DIM},
    )

    # 8. ConditionButton.renderIndicator (12x12) at (64, 178) — Golden Padlock
    lock_rows = [
        "...KKKKKK...",
        "..KGGGGGGK..",
        "..KGKKKKGK..",
        "..KGK..KGK..",
        ".KKKKKKKKKK.",
        "KHHHHHHHHHHK",
        "KGGGGGGGGGGK",
        "KGGGGKKGGGGK",
        "KGGGGKKGGGGK",
        "KGGGGGGGGGGK",
        ".KKKKKKKKKK.",
        "............",
    ]
    _draw_pattern(buf, 64, 178, lock_rows, {"K": BLACK, "G": GOLD, "H": GOLD_HI})

    # 9. Hourglass cooldown indicator (8x10) at (82, 1)
    hourglass_rows = [
        "KKKKKKKK",
        "KGGGGGGK",
        ".KBBBBK.",
        "..KBBK..",
        "..KGGK..",
        "..KGGK..",
        ".KBBBBK.",
        "KBBBBBBK",
        "KGGGGGGK",
        "KKKKKKKK",
    ]
    _draw_pattern(buf, 82, 1, hourglass_rows, {"K": BLACK, "G": GOLD, "B": BLUE})

    # 10. DialogButton (12x12) at (80, 30), inactive at (80, 42) — Speech Bubble
    dialog_rows = [
        ".KKKKKKKKKK.",
        "KTTTTTTTTTTK",
        "KTGGGGGGGGTK",
        "KTTTTTTTTTTK",
        "KTGGGGGGTTTK",
        "KTTTTTTTTTTK",
        "KTTTTTTTTTTK",
        ".KKKKTTTTKK.",
        "...KTKTTK...",
        "..KTTK......",
        "..KKK.......",
        "............",
    ]
    draw_pair(
        80,
        30,
        12,
        dialog_rows,
        {"K": BLACK, "T": TEXT, "G": GOLD_DARK},
        {"K": BLACK, "T": DIM, "G": MUTED},
    )

    # 11. DialogButtonButton (12x12) at (80, 54), inactive at (80, 66) — Reply choice pill
    dbtn_rows = [
        "............",
        ".KKKKKKKKKK.",
        "KGGTTTTTTTTK",
        "KGGTTTTTTTTK",
        "KGGKKKKKKTTK",
        "KGGTTTTTTTTK",
        "KGGKKKKTTTTK",
        "KGGTTTTTTTTK",
        "KGGTTTTTTTTK",
        ".KKKKKKKKKK.",
        "............",
        "............",
    ]
    draw_pair(
        80,
        54,
        12,
        dbtn_rows,
        {"K": BLACK, "G": GOLD, "T": TEXT},
        {"K": BLACK, "G": MUTED, "T": DIM},
    )

    # 12. dialogNameToLabelButton (12x12) at (80, 80), inactive at (80, 92) — Sync/Tag arrow
    sync_rows = [
        "....KKKK....",
        "..KKGGGGKK..",
        ".KGGKKKKGGK.",
        ".KGK....KGK.",
        "....KKK.KGK.",
        "...KGGGKGGK.",
        "....KKKKKK..",
        ".KGK........",
        ".KGKKKKKK...",
        "..KGGGGGGK..",
        "...KKKKKK...",
        "............",
    ]
    draw_pair(
        80,
        80,
        12,
        sync_rows,
        {"K": BLACK, "G": GOLD},
        {"K": BLACK, "G": MUTED},
    )

    # 13. TextEditButton (12x12) at (80, 104), inactive at (80, 116) — Scroll / Script page
    text_edit_rows = [
        ".KKKKKKKKK..",
        "KTTTTTTTTTK.",
        "KTGGGGGGGTKK",
        "KTTTTTTTTGGK",
        "KTKKKKKKGGK.",
        "KTTTTTTGGK..",
        "KTKKKKGGK...",
        "KTTTTTTK....",
        "KTTTTTTTTK..",
        "KTTTTTTTTK..",
        ".KKKKKKKK...",
        "............",
    ]
    draw_pair(
        80,
        104,
        12,
        text_edit_rows,
        {"K": BLACK, "T": TEXT, "G": GOLD},
        {"K": BLACK, "T": DIM, "G": MUTED},
    )

    # 14. ActionsButton (12x12) at (80, 130), inactive at (80, 142) — Action Gear + Bolt
    actions_rows = [
        "....KKKK....",
        "..KKPPPPKK..",
        ".KPPGGGGPPK.",
        ".KPGHHHHGPK.",
        "KPPGHGGHGPPK",
        "KPPGGGGGGPPK",
        "KPPGHGGHGPPK",
        ".KPGHHHHGPK.",
        ".KPPGGGGPPK.",
        "..KKPPPPKK..",
        "....KKKK....",
        "............",
    ]
    draw_pair(
        80,
        130,
        12,
        actions_rows,
        {"K": BLACK, "P": PURPLE, "G": GOLD, "H": GOLD_HI},
        {"K": BLACK, "P": MUTED, "G": DIM, "H": DIM},
    )

    # 15. ActionButton (12x12) at (80, 155), inactive at (80, 167) — Lightning Bolt
    action_rows = [
        ".....KKKKK..",
        "....KGGGGK..",
        "...KGGHHK...",
        "..KGGHHK....",
        ".KGGGGGGGGK.",
        "KGGGGGGGGGGK",
        ".KKKKGGHHK..",
        "...KGGHHK...",
        "..KGGGGK....",
        ".KGGKK......",
        ".KKK........",
        "............",
    ]
    draw_pair(
        80,
        155,
        12,
        action_rows,
        {"K": BLACK, "G": GOLD, "H": GOLD_HI},
        {"K": BLACK, "G": MUTED, "H": DIM},
    )

    # 16. WarningIcon (80, 178) / (80, 190) & ExperimentalButton (79, 179) / (79, 191) (12x12)
    # Drawn at (80, 179) and (80, 191) with 10x10 footprint so both (80,178) and (79,179) capture it cleanly!
    warn_rows = [
        "....KK....",
        "...KGGK...",
        "...KHHK...",
        "..KGKKGK..",
        "..KGKKGK..",
        ".KGGKKGGK.",
        ".KGGGGGGK.",
        "KGGGKKGGGK",
        "KGGGGGGGGK",
        ".KKKKKKKK.",
    ]
    _draw_pattern(buf, 81, 179, warn_rows, {"K": BLACK, "G": GOLD, "H": GOLD_HI})
    _draw_pattern(buf, 81, 191, warn_rows, {"K": BLACK, "G": RED, "H": GOLD_HI})

    # 17. ReloadButton (12x12) at (92, 4), inactive at (92, 16)
    reload_rows = [
        "...KKKKKK...",
        "..KBBBBBBK..",
        ".KBBKKKKBBK.",
        "KBBK...KBBBK",
        "KBBK..KBBBBK",
        "KKKK...KKKK.",
        ".KKKK...KKKK",
        "KBBBBK..KBBK",
        "KBBBK...KBBK",
        ".KBBKKKKBBK.",
        "..KBBBBBBK..",
        "...KKKKKK...",
    ]
    draw_pair(
        92,
        4,
        12,
        reload_rows,
        {"K": BLACK, "B": BLUE},
        {"K": BLACK, "B": MUTED},
    )

    # 18. SearchField Magnifier (12x12) at (93, 30) [focused] and (93, 42) [unfocused]
    search_rows = [
        "..KKKKKK....",
        ".KGGGGGGK...",
        "KGGKKKKGGK..",
        "KGK....KGK..",
        "KGK....KGK..",
        "KGGKKKKGGK..",
        ".KGGGGGGKK..",
        "..KKKKKKGGK.",
        ".......KGGGK",
        "........KGGK",
        ".........KK.",
        "............",
    ]
    _draw_pattern(buf, 93, 30, search_rows, {"K": BLACK, "G": GOLD})
    _draw_pattern(buf, 93, 42, search_rows, {"K": BLACK, "G": DIM})

    # 19. UpDownButton chevrons (9x6):
    # Up active: (93, 59), Up inactive: (93, 65)
    # Down active: (93, 84), Down inactive: (93, 90)
    up_rows = [
        "...KKK...",
        "..KGGGK..",
        ".KGGHGGK.",
        "KGGKKKGGK",
        "KKK...KKK",
        ".........",
    ]
    down_rows = [
        "KKK...KKK",
        "KGGKKKGGK",
        ".KGGHGGK.",
        "..KGGGK..",
        "...KKK...",
        ".........",
    ]
    draw_pair(93, 59, 6, up_rows, {"K": BLACK, "G": GOLD, "H": GOLD_HI}, {"K": BLACK, "G": MUTED, "H": DIM})
    draw_pair(93, 84, 6, down_rows, {"K": BLACK, "G": GOLD, "H": GOLD_HI}, {"K": BLACK, "G": MUTED, "H": DIM})

    # 20. NameVisibilityToggleButton (12x12) at X=92:
    # ALWAYS (92, 154), NEVER (92, 166), MID (92, 178), NEAR (92, 190), MOUSE_OVER (92, 202)
    eye_open = [
        "............",
        "...KKKKKK...",
        ".KKTTTTTTKK.",
        "KTTGGKKGGTTK",
        "KTTGKKKKGTTK",
        "KTTGGKKGGTTK",
        ".KKTTTTTTKK.",
        "...KKKKKK...",
        "............",
        ".KKKKKKKKKK.",
        "............",
        "............",
    ]
    eye_never = [
        ".........KK.",
        "...KKKKKRRK.",
        ".KKTTTTKRK..",
        "KTTKKKKRKTTK",
        "KTTRRRRRTTTK",
        "KTTKRKKKKTTK",
        ".KKRTTTTTKK.",
        ".KRRKKKKK...",
        ".KK.........",
        ".KKKKKKKKKK.",
        "............",
        "............",
    ]
    _draw_pattern(buf, 92, 154, eye_open, {"K": BLACK, "T": TEXT, "G": GREEN})
    _draw_pattern(buf, 92, 166, eye_never, {"K": BLACK, "T": DIM, "R": RED})
    _draw_pattern(buf, 92, 178, eye_open, {"K": BLACK, "T": TEXT, "G": BLUE})
    _draw_pattern(buf, 92, 190, eye_open, {"K": BLACK, "T": TEXT, "G": GOLD})
    _draw_pattern(buf, 92, 202, eye_open, {"K": BLACK, "T": TEXT, "G": PURPLE})
    _draw_pattern(buf, 92, 214, eye_open, {"K": BLACK, "T": MUTED, "G": MUTED})

    # 21. ExportButton (12x12) at (105, 4), inactive at (105, 16) — Upload/Export
    export_rows = [
        "....KKKK....",
        "...KPPPPK...",
        "..KPPPHPPK..",
        ".KPPKPHKPPK.",
        ".KKK.PH.KKK.",
        ".....PH.....",
        ".KKKKPHKKKK.",
        "KTTTTPHTTTTK",
        "KTTTTTTTTTTK",
        "KGGGGGGGGGGK",
        ".KKKKKKKKKK.",
        "............",
    ]
    draw_pair(
        105,
        4,
        12,
        export_rows,
        {"K": BLACK, "P": PURPLE, "H": PURPLE_HI, "T": TEXT, "G": GOLD_DARK},
        {"K": BLACK, "P": MUTED, "H": DIM, "T": DIM, "G": MUTED},
    )

    # 22. ImportButton (12x12) at (105, 29), inactive at (105, 41) — Download/Import
    import_rows = [
        "....KKKK....",
        "....KBBK....",
        "....KBHK....",
        ".KKKKBHKKKK.",
        ".KBBKBHKBBK.",
        "..KBBBBBBK..",
        "...KBBBBK...",
        "KTTTKKKKTTTK",
        "KTTTTTTTTTTK",
        "KGGGGGGGGGGK",
        ".KKKKKKKKKK.",
        "............",
    ]
    draw_pair(
        105,
        29,
        12,
        import_rows,
        {"K": BLACK, "B": BLUE, "H": BLUE_HI, "T": TEXT, "G": GOLD_DARK},
        {"K": BLACK, "B": MUTED, "H": DIM, "T": DIM, "G": MUTED},
    )

    # 23. FileBrowserButton (12x12) at (105, 56), inactive at (105, 68) — Folder
    folder_rows = [
        ".KKKKK......",
        "KGGGGGK.....",
        "KHHHHHGKKKK.",
        "KGGGGGGGGGGK",
        "KHHHHHHHHHHK",
        "KGGGGGGGGGGK",
        "KGGGGGGGGGGK",
        "KGGGGGGGGGGK",
        "KGGGGGGGGGGK",
        "KDDDDDDDDDDK",
        ".KKKKKKKKKK.",
        "............",
    ]
    draw_pair(
        105,
        56,
        12,
        folder_rows,
        {"K": BLACK, "G": GOLD, "H": GOLD_HI, "D": GOLD_DARK},
        {"K": BLACK, "G": MUTED, "H": DIM, "D": STONE_DARK},
    )

    # 24. HelpIcon (12x12) at (105, 81) [hover] and (105, 93) [resting]
    help_rows = [
        "...KKKKKK...",
        ".KKBBBBBBKK.",
        "KBBGGGGGGGBK",
        "KBGKKGGKKGBK",
        "KBGK...KGGBK",
        "KBGG..KGGGBK",
        "KBBG.KGGGBBK",
        "KBBGGGGGGGBK",
        "KBBGGKKGGGBK",
        ".KKGGKKGGKK.",
        "...KKKKKK...",
        "............",
    ]
    _draw_pattern(buf, 105, 81, help_rows, {"K": BLACK, "B": GOLD_HI, "G": GOLD_DARK})
    _draw_pattern(buf, 105, 93, help_rows, {"K": BLACK, "B": DIM, "G": STONE})

    path = CONFIG_GUI / "configuration.png"
    K.save_png(buf, path)
    return path


def build_checkbox_sheet() -> Path:
    """Build 64x64 checkbox.png:
    X=0 (normal), X=16 (hover), X=32 (disabled)
    Y=0 (unchecked), Y=16 (checked)
    """
    buf = K.zeros(64, 64)
    check_rows = [
        "............",
        ".........KK.",
        "........KLLK",
        ".......KLGGK",
        "..KK..KLGGK.",
        ".KLLKKLGGK..",
        "KLGGGLGGK...",
        "KGGGGGGK....",
        ".KGGGGK.....",
        "..KKKK......",
        "............",
        "............",
    ]
    # Unchecked row (Y=0)
    _frame_box(buf, 1, 1, 14, 14, TEXT_FILL, (8, 6, 5), TEXT_IN, (36, 29, 25), BLACK, notched=True, amp=1.5, seed=201)
    _frame_box(buf, 17, 1, 14, 14, (24, 19, 16), GOLD_DARK, GOLD, GOLD_DARK, BLACK, notched=True, amp=1.5, seed=202)
    _frame_box(buf, 33, 1, 14, 14, (18, 15, 13), (26, 22, 19), (26, 22, 19), (26, 22, 19), BLACK, notched=True, amp=1.0, seed=203)

    # Checked row (Y=16)
    _frame_box(buf, 1, 17, 14, 14, (22, 32, 24), (14, 20, 16), GREEN_DARK, (36, 58, 42), BLACK, notched=True, amp=1.5, seed=204)
    _draw_pattern(buf, 2, 18, check_rows, {"K": BLACK, "L": GREEN_HI, "G": GREEN})

    _frame_box(buf, 17, 17, 14, 14, (28, 40, 30), GOLD, GOLD, GOLD_DARK, BLACK, notched=True, amp=1.5, seed=205)
    _draw_pattern(buf, 18, 18, check_rows, {"K": BLACK, "L": GOLD_HI, "G": GREEN_HI})

    _frame_box(buf, 33, 17, 14, 14, (20, 22, 20), (28, 30, 28), (28, 30, 28), (28, 30, 28), BLACK, notched=True, amp=1.0, seed=206)
    _draw_pattern(buf, 34, 18, check_rows, {"K": BLACK, "L": DIM, "G": MUTED})

    path = CONFIG_GUI / "checkbox.png"
    K.save_png(buf, path)
    return path


def build_close_and_nav_buttons() -> tuple[Path, Path]:
    """Build 64x64 close_button.png and navigation_button.png in core."""
    # close_button.png (10x10 sprite at (0,0) active and (0,10) inactive)
    close_buf = K.zeros(64, 64)
    close_active = [
        ".KKKKKKKK.",
        "KRRRRRRRRK",
        "KRTHRRHTRK",
        "KRRTHHTRRK",
        "KRRRTTTRRK",
        "KRRRTTTRRK",
        "KRRTHHTRRK",
        "KRTHRRHTRK",
        "KDDDDDDDDK",
        ".KKKKKKKK.",
    ]
    _draw_pattern(close_buf, 0, 0, close_active, {"K": BLACK, "R": RED_DARK, "D": (72, 20, 16), "T": TEXT, "H": RED_HI})
    _draw_pattern(close_buf, 0, 10, close_active, {"K": BLACK, "R": STONE, "D": STONE_DARK, "T": MUTED, "H": STONE_HI})
    close_path = CORE_GUI / "close_button.png"
    K.save_png(close_buf, close_path)

    # navigation_button.png (12x12 forward at (0,0), backward at (12,0), inactive at +12Y)
    nav_buf = K.zeros(64, 64)
    fwd_rows = [
        ".KKKKKKKKKK.",
        "KSSSSSSSSSSK",
        "KSGGGGGGGGSK",
        "KSGTTTTGGGSK",
        "KSGGTTTTGGSK",
        "KSGGGTTTGGSK",
        "KSGGGTTTGGSK",
        "KSGGTTTTGGSK",
        "KSGTTTTGGGSK",
        "KSGGGGGGGGSK",
        "KDDDDDDDDDDK",
        ".KKKKKKKKKK.",
    ]
    bwd_rows = [
        ".KKKKKKKKKK.",
        "KSSSSSSSSSSK",
        "KSGGGGGGGGSK",
        "KSGGGTTTTGSK",
        "KSGGTTTTGGSK",
        "KSGGTTTGGGSK",
        "KSGGTTTGGGSK",
        "KSGGTTTTGGSK",
        "KSGGGTTTTGSK",
        "KSGGGGGGGGSK",
        "KDDDDDDDDDDK",
        ".KKKKKKKKKK.",
    ]
    pal_nav_act = {"K": BLACK, "S": STONE_HI, "G": BTN_FILL, "D": BTN_BOT, "T": GOLD_HI}
    pal_nav_inact = {"K": BLACK, "S": STONE, "G": BTN_LOCK_FILL, "D": STONE_DARK, "T": MUTED}
    _draw_pattern(nav_buf, 0, 0, fwd_rows, pal_nav_act)
    _draw_pattern(nav_buf, 12, 0, bwd_rows, pal_nav_act)
    _draw_pattern(nav_buf, 0, 12, fwd_rows, pal_nav_inact)
    _draw_pattern(nav_buf, 12, 12, bwd_rows, pal_nav_inact)
    nav_path = CORE_GUI / "navigation_button.png"
    K.save_png(nav_buf, nav_path)

    return close_path, nav_path


def build_background_sheets() -> list[Path]:
    """Build config_screen/background.png (512x512), background_small.png (512x256),
    preset_browser/background.png (512x512), and dialog/scene_*.png (512x256).
    """
    paths: list[Path] = []

    # 1. config_screen/background.png (512x512)
    # Blitted at (1, 1) with size 333x263 (normal) or at (1, 17) with size 333x247 (compact)
    bg = K.zeros(512, 512)
    W, H = 330, 263
    # Header bar (y=1..17, height 16)
    _frame_box(bg, 1, 1, W, 17, STONE, STONE_HI, GOLD_DARK, PANEL_IN, BLACK, notched=True, amp=2.0, seed=301)
    # Gold accent bar at y=16
    bg[16, 3 : W - 1, :3] = GOLD_DARK
    # Left gold badge pip in header
    _fill_rect(bg, 4, 4, 2, 10, GOLD)

    # Body panel (y=17..263, height 247)
    _frame_box(bg, 1, 17, W, 247, PANEL_FILL, PANEL_IN, (16, 12, 10), PANEL_IN, BLACK, notched=True, amp=2.2, seed=302)
    # Sub-navigation strip at top of body (y=18..38, 20px tall, right above contentTopPos = topPos + 23)
    _fill_rect(bg, 2, 18, W - 2, 20, (20, 16, 14))
    bg[38, 2 : W, :3] = BLACK
    bg[39, 2 : W, :3] = PANEL_IN
    # Corner rivets on main body
    for rx, ry in ((4, 42), (W - 3, 42), (4, 260), (W - 3, 260)):
        _set_px(bg, rx, ry, STONE_HI)

    bg_path = CONFIG_GUI / "config_screen/background.png"
    K.save_png(bg, bg_path)
    paths.append(bg_path)

    # 2. config_screen/background_small.png (512x256)
    # Blitted at (1, 1) with width 333, height 128 (or 80)
    bg_sm = K.zeros(512, 256)
    _frame_box(bg_sm, 1, 1, W, 128, PANEL_FILL, PANEL_IN, (16, 12, 10), PANEL_IN, BLACK, notched=True, amp=2.2, seed=303)
    _fill_rect(bg_sm, 2, 2, W - 2, 20, (20, 16, 14))
    bg_sm[22, 2 : W, :3] = BLACK
    bg_sm[23, 2 : W, :3] = PANEL_IN
    bg_sm_path = CONFIG_GUI / "config_screen/background_small.png"
    K.save_png(bg_sm, bg_sm_path)
    paths.append(bg_sm_path)

    # 3. preset_browser/background.png (512x512)
    # CustomScreen stretches header from (1, 1, 333, 19) and body from (1, 20, 333, 244)
    pb = K.zeros(512, 512)
    _frame_box(pb, 1, 1, 333, 19, STONE, STONE_HI, GOLD_DARK, PANEL_IN, BLACK, notched=False, amp=2.0, seed=304)
    pb[18, 2:333, :3] = GOLD_DARK
    _frame_box(pb, 1, 20, 333, 244, PANEL_FILL, PANEL_IN, (16, 12, 10), PANEL_IN, BLACK, notched=False, amp=2.2, seed=305)
    pb_path = CONFIG_GUI / "preset_browser/background.png"
    K.save_png(pb, pb_path)
    paths.append(pb_path)

    # 4. Legacy dialog scene sheets in core (scene_large, scene_medium, scene_small)
    for name, w_dlg, h_dlg in (("scene_large", 320, 204), ("scene_medium", 320, 160), ("scene_small", 320, 118)):
        sc = K.zeros(512, 256)
        _frame_box(sc, 1, 1, w_dlg, h_dlg, PANEL_FILL, PANEL_IN, (16, 12, 10), PANEL_IN, BLACK, notched=True, amp=2.2, seed=310)
        _fill_rect(sc, 2, 2, w_dlg - 2, 16, STONE)
        sc[18, 2:w_dlg, :3] = GOLD_DARK
        sc_path = CORE_GUI / f"dialog/{name}.png"
        K.save_png(sc, sc_path)
        paths.append(sc_path)

    return paths


def main() -> None:
    CORE_SPRITES.mkdir(parents=True, exist_ok=True)
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)

    nine_slices = build_nine_slice_sprites()
    cfg_sheet = build_configuration_sheet()
    chk_sheet = build_checkbox_sheet()
    close_path, nav_path = build_close_and_nav_buttons()
    bg_paths = build_background_sheets()

    preview_items = list(nine_slices.values()) + [chk_sheet, close_path, nav_path, cfg_sheet]
    K.preview_sheet(preview_items, PREVIEW_DIR / "config_ui_sheet.png", scale=3, cols=5)
    print(f"Generated {len(nine_slices)} nine-slice sprites, configuration.png, checkbox.png, close/nav buttons, and {len(bg_paths)} backgrounds.")


if __name__ == "__main__":
    main()
