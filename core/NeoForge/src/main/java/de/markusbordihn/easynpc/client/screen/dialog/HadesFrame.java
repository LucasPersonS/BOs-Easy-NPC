package de.markusbordihn.easynpc.client.screen.dialog;

import net.minecraft.client.gui.GuiGraphics;

/** Original, resolution-independent silver filigree. No extracted game artwork. */
final class HadesFrame {
  static final int INK = 0xf5081014;
  static final int SILVER = 0xff91bbb9;
  static final int JADE = 0xff70d8b7;
  private HadesFrame() {}

  static void line(GuiGraphics g, int x, int y, int xx, int yy, int color) {
    int steps = Math.max(Math.abs(xx - x), Math.abs(yy - y));
    for (int i = 0; i <= steps; i++) {
      int px = x + (xx - x) * i / Math.max(1, steps);
      int py = y + (yy - y) * i / Math.max(1, steps);
      g.fill(px, py, px + 1, py + 1, color);
    }
  }

  static void arc(GuiGraphics g, int x, int y, int radius, double start, double end, int color) {
    for (double a = start; a < end; a += 0.035) {
      int px = x + (int) (Math.cos(a) * radius);
      int py = y + (int) (Math.sin(a) * radius);
      g.fill(px, py, px + 1, py + 1, color);
    }
  }

  static void diamond(GuiGraphics g, int x, int y, int size, int color) {
    for (int i = -size; i <= size; i++) {
      int half = size - Math.abs(i);
      g.fill(x - half, y + i, x + half + 1, y + i + 1, color);
    }
  }

  static void panel(GuiGraphics g, int x, int y, int w, int h) {
    g.fill(x + 8, y, x + w - 8, y + h, INK);
    g.fill(x, y + 8, x + w, y + h - 8, INK);
    for (int i = 0; i < 8; i++) {
      g.fill(x + 8 - i, y + i, x + w - 8 + i, y + i + 1, INK);
      g.fill(x + i, y + h - 8 + i, x + w - i, y + h - 7 + i, INK);
    }
    line(g, x + 9, y, x + w - 9, y, 0xff4c686b);
    line(g, x + 9, y + h - 1, x + w - 9, y + h - 1, 0xff314b51);
    line(g, x, y + 9, x, y + h - 9, 0xff4c686b);
    line(g, x + w - 1, y + 9, x + w - 1, y + h - 9, 0xff314b51);
    for (int side : new int[] {-1, 1}) {
      int cx = side < 0 ? x : x + w;
      arc(g, cx, y + 5, 10, side < 0 ? 1.0 : 2.0, side < 0 ? 4.8 : 5.9, SILVER);
      arc(g, cx + side * 3, y + 7, 6, 0.2, 5.3, 0xff42686c);
      line(g, cx, y + 18, cx + side * 5, y + 34, SILVER);
      line(g, cx + side * 5, y + 34, cx, y + 48, 0xff35575d);
      diamond(g, cx, y + 22, 2, JADE);
      for (int i = 0; i < 3; i++) {
        line(g, cx - side * (10 + i * 7), y - 2,
            cx - side * (17 + i * 7), y - 8 + i * 2, SILVER);
      }
    }
  }

  static void nameplate(GuiGraphics g, int x, int y, int w) {
    panel(g, x, y, w, 32);
    int cx = x + w / 2;
    arc(g, cx, y - 3, 8, 0.1, 3.05, SILVER);
    diamond(g, cx, y - 8, 3, JADE);
    line(g, x + 10, y + 29, cx - 6, y + 29, 0xff467c74);
    line(g, cx + 6, y + 29, x + w - 10, y + 29, 0xff467c74);
  }
}
