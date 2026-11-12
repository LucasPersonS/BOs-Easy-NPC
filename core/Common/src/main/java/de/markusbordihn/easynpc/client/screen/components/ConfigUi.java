/*
 * Copyright 2026 Markus Bordihn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package de.markusbordihn.easynpc.client.screen.components;

import com.mojang.blaze3d.systems.RenderSystem;
import de.markusbordihn.easynpc.Constants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Shared Paravexia Website + NPC Chat-Bubble UI rendering helper for EasyNPC configuration screens.
 * Mirrors {@code Website/admin/tool-v2.css}, {@code dialogues.css}, {@code npcs.css}, and {@code DialogUi}.
 */
public final class ConfigUi {

  // Palette (--g-* from Website/admin/tool-v2.css & dialogues.css)
  public static final int BLACK = 0xFF000000;
  public static final int EDGE = 0xFF0A0807;
  public static final int STAGE_FILL = 0xFF0E0B09;
  public static final int TEXT_FILL = 0xFF100C0A;
  public static final int BG = 0xFF110D0C;
  public static final int PANEL_FILL = 0xFF1B1614;
  public static final int PANEL_IN = 0xFF2C241F;
  public static final int STONE = 0xFF2C2622;
  public static final int STONE_HI = 0xFF3C342E;
  public static final int STONE_DARK = 0xFF16120F;
  public static final int TEXT_IN = 0xFF3A2F27;

  public static final int GOLD = 0xFFF1B64B;
  public static final int GOLD_HI = 0xFFFFE08A;
  public static final int GOLD_DARK = 0xFF946620;
  public static final int TEXT = 0xFFEFE5D6;
  public static final int TEXT_BRIGHT = 0xFFFFF6E6;
  public static final int DIM = 0xFF9B8F80;
  public static final int MUTED = 0xFF6B6055;

  public static final int GREEN = 0xFF7CD992;
  public static final int GREEN_DARK = 0xFF367044;
  public static final int RED = 0xFFFF6B5A;
  public static final int RED_DARK = 0xFF8A2A22;
  public static final int BLUE = 0xFF8EC5FF;
  public static final int PURPLE = 0xFFB28CFF;
  public static final int ORANGE = 0xFFFF8A4D;
  public static final int CYAN = 0xFF5CC8E8;

  public static final int[] SEGMENTS = {
    0xFFE8B84A, 0xFFB28CFF, 0xFF7CD992, 0xFFFF8A4D, 0xFF8EC5FF, 0xFFFF7AB3, 0xFFD9CFA0, 0xFF5CC8E8
  };

  // Nine-slice GUI sprites (assets/easy_npc/textures/gui/sprites/config/*.png)
  public static final ResourceLocation WINDOW = sprite("config/window");
  public static final ResourceLocation HEADER = sprite("config/header");
  public static final ResourceLocation SUBNAV = sprite("config/subnav");
  public static final ResourceLocation INSET = sprite("config/inset");
  public static final ResourceLocation CARD = sprite("config/card");
  public static final ResourceLocation SLOT = sprite("config/slot");
  public static final ResourceLocation BUTTON = sprite("config/button");
  public static final ResourceLocation BUTTON_HOVER = sprite("config/button_hover");
  public static final ResourceLocation BUTTON_PRESSED = sprite("config/button_pressed");
  public static final ResourceLocation BUTTON_DISABLED = sprite("config/button_disabled");
  public static final ResourceLocation TAB_ACTIVE = sprite("config/tab_active");
  public static final ResourceLocation INPUT = sprite("config/input");
  public static final ResourceLocation INPUT_FOCUSED = sprite("config/input_focused");
  public static final ResourceLocation SLIDER_TRACK = sprite("config/slider_track");
  public static final ResourceLocation SLIDER_HANDLE = sprite("config/slider_handle");
  public static final ResourceLocation SLIDER_HANDLE_HOVER = sprite("config/slider_handle_hover");

  private ConfigUi() {}

  private static ResourceLocation sprite(String path) {
    return ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path);
  }

  public static int segmentColor(int index) {
    return SEGMENTS[Math.floorMod(index, SEGMENTS.length)];
  }

  /**
   * Maps legacy light-theme text colors (black, 0x404040, 0x555555, etc.) to high-contrast
   * Paravexia dark-stone colors while preserving explicit custom colors.
   */
  public static int mapTextColor(int color) {
    int rgb = color & 0x00FFFFFF;
    int alpha = (color >>> 24) & 0xFF;
    int aMask = alpha == 0 ? 0xFF000000 : (alpha << 24);

    return switch (rgb) {
      case 0x000000, 0x404040, 0x3F3F3F, 0x2B2B2B -> (TEXT & 0x00FFFFFF) | aMask;
      case 0xFFFFFF -> (TEXT_BRIGHT & 0x00FFFFFF) | aMask;
      case 0x555555, 0xFFFF55, 0xAA7700 -> (GOLD & 0x00FFFFFF) | aMask;
      case 0x606060, 0x666666, 0x707070, 0x7F7F7F, 0x808080, 0x888888, 0x999999, 0xA0A0A0, 0xAAAAAA ->
          (DIM & 0x00FFFFFF) | aMask;
      case 0x00AA00, 0x55FF55 -> (GREEN & 0x00FFFFFF) | aMask;
      case 0xAA0000, 0xFF5555 -> (RED & 0x00FFFFFF) | aMask;
      default -> (rgb | aMask);
    };
  }

  public static void drawWindow(
      GuiGraphics g, int x, int y, int width, int height, boolean compactMode) {
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    if (!compactMode) {
      // Top title header plaque at (x, y - 16, width, 18)
      g.blitSprite(HEADER, x, y - 16, width, 18);
      // Gold accent pip on left of title bar
      g.fill(x + 3, y - 12, x + 5, y - 3, GOLD);
    }
    // Main panel body
    g.blitSprite(WINDOW, x, y, width, height);
    // Sub-navigation strip at top of body (21px tall)
    if (height >= 60) {
      g.blitSprite(SUBNAV, x + 2, y + 1, width - 4, 21);
    }
  }

  public static void drawSmallWindow(GuiGraphics g, int x, int y, int width, int height) {
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    g.blitSprite(WINDOW, x, y, width, height);
    if (height >= 40) {
      g.blitSprite(SUBNAV, x + 2, y + 1, width - 4, 21);
    }
  }

  public static void drawCustomWindow(
      GuiGraphics g, int x, int y, int width, int height, int headerHeight) {
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    g.blitSprite(HEADER, x, y, width, headerHeight);
    g.fill(x + 3, y + 4, x + 5, y + headerHeight - 4, GOLD);
    g.blitSprite(WINDOW, x, y + headerHeight - 1, width, height - headerHeight + 1);
  }

  public static void drawInset(GuiGraphics g, int x, int y, int width, int height) {
    if (width <= 0 || height <= 0) {
      return;
    }
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    g.blitSprite(INSET, x, y, width, height);
  }

  public static void drawCard(GuiGraphics g, int x, int y, int width, int height) {
    if (width <= 0 || height <= 0) {
      return;
    }
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    g.blitSprite(CARD, x, y, width, height);
  }

  /**
   * Renders a dark stage preview box (like {@code .tv-stage} / {@code .d-screen}) for 3D NPC previews.
   */
  public static void drawEntityStage(GuiGraphics g, int x, int y, int width, int height) {
    drawEntityStage(g, x, y, width, height, true);
  }

  public static void drawEntityStage(
      GuiGraphics g, int x, int y, int width, int height, boolean withPedestal) {
    if (width <= 0 || height <= 0) {
      return;
    }
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    g.blitSprite(INSET, x, y, width, height);

    // Subtle stage grid lines inside the well
    int innerLeft = x + 3;
    int innerRight = x + width - 3;
    int innerTop = y + 3;
    int innerBottom = y + height - 3;
    if (withPedestal && innerRight > innerLeft + 12 && innerBottom > innerTop + 12) {
      int gridColor = 0x189B8F80;
      for (int gy = innerTop + 14; gy < innerBottom - 6; gy += 16) {
        g.fill(innerLeft, gy, innerRight, gy + 1, gridColor);
      }
      int midX = (innerLeft + innerRight) / 2;
      g.fill(midX, innerTop, midX + 1, innerBottom, gridColor);

      // Subtle warm pedestal bar near bottom of stage
      int pedW = Math.min(width - 16, 48);
      if (pedW > 8 && height >= 40) {
        int pedX = x + (width - pedW) / 2;
        int pedY = y + height - 10;
        g.fill(pedX, pedY, pedX + pedW, pedY + 2, 0x66946620);
        g.fill(pedX + 4, pedY - 1, pedX + pedW - 4, pedY, 0x88F1B64B);
      }
    }
  }

  /**
   * Renders a dark stone 18x18 item slot or grid of slots (matching {@code .t-slot} from tool-v2.css).
   */
  public static void drawSlot(GuiGraphics g, int x, int y) {
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    g.blitSprite(SLOT, x, y, 18, 18);
  }

  public static void drawSlotRegion(GuiGraphics g, int x, int y, int width, int height) {
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    int cols = Math.max(1, width / 18);
    int rows = Math.max(1, height / 18);
    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        g.blitSprite(SLOT, x + c * 18, y + r * 18, 18, 18);
      }
    }
  }

  public static void drawButton(
      GuiGraphics g,
      int x,
      int y,
      int width,
      int height,
      boolean active,
      boolean hovered,
      boolean isSelectedTab,
      int stripeColor) {
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    ResourceLocation sprite;
    if (isSelectedTab) {
      sprite = TAB_ACTIVE;
    } else if (!active) {
      sprite = BUTTON_DISABLED;
    } else if (hovered) {
      sprite = BUTTON_HOVER;
    } else {
      sprite = BUTTON;
    }
    g.blitSprite(sprite, x, y, width, height);

    if (stripeColor != 0 && height >= 8 && width >= 14 && !isSelectedTab) {
      int barColor = active ? stripeColor : 0xFF4A4038;
      g.fill(x + 2, y + 2, x + 4, y + height - 2, barColor);
    }
  }

  public static void drawInput(
      GuiGraphics g, int x, int y, int width, int height, boolean focused) {
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    g.blitSprite(focused ? INPUT_FOCUSED : INPUT, x, y, width, height);
  }

  public static void drawTableHeader(GuiGraphics g, int left, int top, int right, int bottom) {
    drawCard(g, left, top, right - left, bottom - top);
    g.fill(left + 2, bottom - 1, right - 2, bottom, GOLD_DARK);
  }

  public static void drawTableBody(GuiGraphics g, int left, int top, int right, int bottom) {
    drawInset(g, left, top, right - left, bottom - top);
  }

  public static void drawTableFooter(GuiGraphics g, int left, int top, int right, int bottom) {
    drawCard(g, left, top, right - left, bottom - top);
  }
}
