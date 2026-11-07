package de.markusbordihn.easynpc.client.dialog;

import static de.markusbordihn.easynpc.client.dialog.WorldDialogStyle.*;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Drawing primitives of the world dialogue HUD. Everything is drawn in whole GUI pixels with opaque
 * colors that never overlap, so it stays sharp at every GUI scale and no shaderpack can touch it.
 */
final class WorldDialogHud {

  private static final String ELLIPSIS = "…";

  private WorldDialogHud() {}

  /** Double-rimmed panel: ivory rim with notched corners, ink fill, a dark inner line, drop shadow. */
  static void panel(GuiGraphics g, int x, int y, int w, int h, int fill, int rim, float alpha) {
    int shadow = fade(SHADOW, alpha);
    g.fill(x + 2, y + h, x + w, y + h + 1, shadow);
    g.fill(x + w, y + 2, x + w + 1, y + h, shadow);
    int r = fade(rim, alpha);
    g.fill(x + 1, y, x + w - 1, y + 1, r);
    g.fill(x + 1, y + h - 1, x + w - 1, y + h, r);
    g.fill(x, y + 1, x + 1, y + h - 1, r);
    g.fill(x + w - 1, y + 1, x + w, y + h - 1, r);
    g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fade(fill, alpha));
    int inner = fade(RIM_INNER, alpha);
    g.fill(x + 2, y + 2, x + w - 2, y + 3, inner);
    g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, inner);
    g.fill(x + 2, y + 3, x + 3, y + h - 3, inner);
    g.fill(x + w - 3, y + 3, x + w - 2, y + h - 3, inner);
  }

  /** Stepped tail under a panel whose bottom edge is at {@code bottom}, pointing down at the NPC. */
  static void tail(GuiGraphics g, int centerX, int bottom, int fill, int rim, float alpha) {
    int r = fade(rim, alpha);
    int f = fade(fill, alpha);
    // Open the panel's bottom rim and inner line where the tail joins.
    g.fill(centerX - 3, bottom - 3, centerX + 3, bottom, f);
    for (int row = 0; row < TAIL; row++) {
      int half = 3 - row;
      g.fill(centerX - half - 1, bottom + row, centerX + half + 1, bottom + row + 1, r);
      if (half > 0) {
        g.fill(centerX - half, bottom + row, centerX + half, bottom + row + 1, f);
      }
    }
  }

  static void text(
      GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color, float alpha) {
    if (alpha < 0.04F) {
      return;
    }
    g.drawString(font, text, x, y, fade(color, alpha), false);
  }

  static void text(GuiGraphics g, Font font, String text, int x, int y, int color, float alpha) {
    text(g, font, Component.literal(text).getVisualOrderText(), x, y, color, alpha);
  }

  /** Right-mouse sprite, frame 0 = lit button, frame 1 = pressed. */
  static void mouse(GuiGraphics g, int x, int y, boolean pressed, float alpha) {
    if (alpha < 0.04F) {
      return;
    }
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    g.setColor(1.0F, 1.0F, 1.0F, clamp01(alpha));
    g.blit(MOUSE_RIGHT, x, y, pressed ? 16.0F : 0.0F, 0.0F, 16, 16, 32, 16);
    g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    RenderSystem.disableBlend();
  }

  /** "[mouse] label" on a dark pill, used next to the crosshair and in panel footers. */
  static int prompt(
      GuiGraphics g, Font font, int x, int y, Component label, boolean pressed, boolean pill,
      float alpha) {
    int labelWidth = font.width(label);
    if (pill) {
      // One dark pill behind icon and label, so both read on bright skins and snow.
      int p = fade(PILL, alpha);
      g.fill(x - 2, y + 1, x + 22 + labelWidth, y + 16, p);
      g.fill(x - 1, y, x + 21 + labelWidth, y + 1, p);
      g.fill(x - 1, y + 16, x + 21 + labelWidth, y + 17, p);
    }
    mouse(g, x, y, pressed, alpha);
    text(g, font, label.getVisualOrderText(), x + 18, y + 5, TEXT, alpha);
    return 18 + labelWidth + 3;
  }

  /** Wraps plain text, keeping at most {@code maxLines}; a cut last line ends with "…". */
  static List<String> wrap(Font font, String text, int width, int maxLines) {
    List<FormattedText> split = font.getSplitter().splitLines(text, width, Style.EMPTY);
    List<String> lines = new ArrayList<>();
    for (int index = 0; index < Math.min(maxLines, split.size()); index++) {
      lines.add(split.get(index).getString());
    }
    if (lines.isEmpty()) {
      lines.add(ELLIPSIS);
    } else if (split.size() > maxLines) {
      int last = lines.size() - 1;
      lines.set(last, ellipsize(font, lines.get(last) + " ", width, true));
    }
    return lines;
  }

  static String ellipsize(Font font, String text, int width, boolean force) {
    if (!force && font.width(text) <= width) {
      return text;
    }
    String cut = font.plainSubstrByWidth(text, width - font.width(ELLIPSIS)).stripTrailing();
    return cut + ELLIPSIS;
  }

  static int maxWidth(Font font, List<String> lines) {
    int width = 0;
    for (String line : lines) {
      width = Math.max(width, font.width(line));
    }
    return width;
  }

  static float clamp01(float value) {
    return Math.max(0.0F, Math.min(1.0F, value));
  }

  static int fade(int color, float alpha) {
    int a = Math.round(((color >>> 24) & 0xFF) * clamp01(alpha));
    return (a << 24) | (color & 0x00FFFFFF);
  }

  static int lerp(int from, int to, float progress) {
    float t = clamp01(progress);
    int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
    int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
    int gr = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
    int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
    return (a << 24) | (r << 16) | (gr << 8) | b;
  }
}
