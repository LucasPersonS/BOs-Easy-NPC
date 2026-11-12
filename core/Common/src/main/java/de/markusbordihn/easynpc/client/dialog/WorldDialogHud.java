package de.markusbordihn.easynpc.client.dialog;

import static de.markusbordihn.easynpc.client.dialog.WorldDialogStyle.*;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Drawing primitives of the world dialogue HUD, wired to {@link DialogUi} so the teaser, prompt and
 * full dialogue share the website's palette, sprites and DTM font.
 */
final class WorldDialogHud {

  private WorldDialogHud() {}

  /** Teaser / text box with the website's dark inset border. */
  static void panel(GuiGraphics g, int x, int y, int w, int h, int fill, int rim, float alpha) {
    DialogUi.textBox(g, x, y, w, h, alpha);
  }

  /** Speech tail under a panel whose bottom edge is at {@code bottom}, pointing down at the NPC. */
  static void tail(GuiGraphics g, int centerX, int bottom, int fill, int rim, float alpha) {
    DialogUi.tail(g, centerX, bottom, alpha);
  }

  static void text(
      GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color, float alpha) {
    DialogUi.draw(g, font, text, x, y, color, alpha);
  }

  static void text(GuiGraphics g, Font font, String text, int x, int y, int color, float alpha) {
    DialogUi.draw(g, font, text, x, y, color, alpha, false);
  }

  static void textShadow(
      GuiGraphics g, Font font, String text, int x, int y, int color, float alpha) {
    DialogUi.draw(g, font, text, x, y, color, alpha, true);
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
    Component styled = DialogUi.styled(label);
    int labelWidth = font.width(styled);
    if (pill) {
      int p = fade(PILL, alpha);
      g.fill(x - 2, y + 1, x + 22 + labelWidth, y + 16, p);
      g.fill(x - 1, y, x + 21 + labelWidth, y + 1, p);
      g.fill(x - 1, y + 16, x + 21 + labelWidth, y + 17, p);
    }
    mouse(g, x, y, pressed, alpha);
    DialogUi.draw(g, font, styled, x + 18, y + 5, pill ? DialogUi.TEXT : DialogUi.DIM, alpha, pill);
    return 18 + labelWidth + 3;
  }

  static List<String> wrap(Font font, String text, int width, int maxLines) {
    return DialogUi.wrap(font, text, width, maxLines);
  }

  static String ellipsize(Font font, String text, int width, boolean force) {
    return DialogUi.ellipsize(font, text, width, force);
  }

  static int maxWidth(Font font, List<String> lines) {
    int width = 0;
    for (String line : lines) {
      width = Math.max(width, DialogUi.width(font, line));
    }
    return width;
  }

  static float clamp01(float value) {
    return DialogUi.clamp01(value);
  }

  static int fade(int color, float alpha) {
    return DialogUi.fade(color, alpha);
  }

  static int lerp(int from, int to, float progress) {
    return DialogUi.lerp(from, to, progress);
  }
}
