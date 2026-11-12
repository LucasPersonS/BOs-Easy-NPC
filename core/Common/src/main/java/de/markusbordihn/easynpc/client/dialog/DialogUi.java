package de.markusbordihn.easynpc.client.dialog;

import com.mojang.blaze3d.systems.RenderSystem;
import de.markusbordihn.easynpc.Constants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;

/**
 * Look of the Paravexia website's dialogue library (Website/admin/dialogues.css) in the game: DTM
 * font, gold speaker name, dark text box, stone replies with a coloured stripe. Shared by the world
 * dialogue HUD and the dialogue screen. Sprites come from {@code tools/gen_dialog_site_sprites.py}.
 */
public final class DialogUi {

  public static final ResourceLocation FONT = id("dtm_sans");
  public static final Style STYLE = Style.EMPTY.withFont(FONT);

  // Palette (ARGB), straight from the site's CSS variables.
  public static final int GOLD = 0xFFF1B64B;
  public static final int DIM = 0xFF9B8F80;
  public static final int TEXT = 0xFFEFE5D6;
  public static final int TEXT_BRIGHT = 0xFFFFF6E6;
  public static final int PLACEHOLDER = 0xFF8B8174;
  public static final int DENY = 0xFFFF6B5A;
  public static final int LOCKED_STRIPE = 0xFF50463E;
  public static final int KEY_LIT_TEXT = 0xFF1B1410;
  public static final int[] SEGMENTS = {
    0xFFE8B84A, 0xFFB28CFF, 0xFF7CD992, 0xFFFF8A4D, 0xFF8EC5FF, 0xFFFF7AB3, 0xFFD9CFA0, 0xFF5CC8E8
  };

  // Nine-slice sprites (textures/gui/sprites/dialog/*.png).
  public static final ResourceLocation PANEL = id("dialog/panel");
  public static final ResourceLocation TEXT_BOX = id("dialog/text");
  public static final ResourceLocation FACE = id("dialog/face");
  public static final ResourceLocation REPLY = id("dialog/reply");
  public static final ResourceLocation REPLY_HOVER = id("dialog/reply_hover");
  public static final ResourceLocation REPLY_PRESSED = id("dialog/reply_pressed");
  public static final ResourceLocation REPLY_LOCKED = id("dialog/reply_locked");

  // Plain textures.
  private static final ResourceLocation TAIL = id("textures/gui/dialog_site/tail.png");
  private static final ResourceLocation KEYCAP = id("textures/gui/dialog_site/keycap.png");

  // Metrics (GUI px).
  public static final int PAD = 6;
  public static final int LINE = 10;
  public static final int FACE_SIZE = 20;
  public static final int ROW = 14;
  public static final int ROW_LINE_EXTRA = 10;
  public static final int ROW_GAP = 3;
  public static final int KEY = 11;
  public static final int LABEL_X = 20;
  public static final int TAIL_HEIGHT = 4;

  private static final String ELLIPSIS = "…";

  public enum ReplyState {
    IDLE,
    HOVER,
    PRESSED,
    LOCKED
  }

  private DialogUi() {}

  private static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path);
  }

  // ---------------------------------------------------------------------------------------------
  // Text
  // ---------------------------------------------------------------------------------------------

  public static Component text(String value) {
    return Component.literal(value).withStyle(STYLE);
  }

  public static Component styled(Component component) {
    return component.copy().withStyle(style -> style.withFont(FONT));
  }

  public static int width(Font font, String value) {
    return font.width(text(value));
  }

  public static int width(Font font, Component value) {
    return font.width(styled(value));
  }

  /** Wraps to {@code width}, keeping at most {@code maxLines}; a cut last line ends with "…". */
  public static List<String> wrap(Font font, String value, int width, int maxLines) {
    List<FormattedText> split = font.getSplitter().splitLines(value, width, STYLE);
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

  public static List<FormattedCharSequence> split(Font font, String value, int width) {
    return font.split(text(value), width);
  }

  public static String ellipsize(Font font, String value, int width, boolean force) {
    if (!force && width(font, value) <= width) {
      return value;
    }
    String cut =
        font.getSplitter()
            .plainHeadByWidth(value, Math.max(0, width - width(font, ELLIPSIS)), STYLE)
            .stripTrailing();
    return cut + ELLIPSIS;
  }

  public static void draw(
      GuiGraphics g, Font font, String value, int x, int y, int color, float alpha, boolean shadow) {
    if (alpha < 0.04F) {
      return;
    }
    g.drawString(font, text(value), x, y, fade(color, alpha), shadow);
  }

  public static void draw(
      GuiGraphics g, Font font, Component value, int x, int y, int color, float alpha,
      boolean shadow) {
    if (alpha < 0.04F) {
      return;
    }
    g.drawString(font, styled(value), x, y, fade(color, alpha), shadow);
  }

  public static void draw(
      GuiGraphics g, Font font, FormattedCharSequence value, int x, int y, int color, float alpha) {
    if (alpha < 0.04F) {
      return;
    }
    g.drawString(font, value, x, y, fade(color, alpha), false);
  }

  // ---------------------------------------------------------------------------------------------
  // Pieces
  // ---------------------------------------------------------------------------------------------

  public static void sprite(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h,
      float alpha) {
    if (alpha < 0.02F || w <= 0 || h <= 0) {
      return;
    }
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    g.setColor(1.0F, 1.0F, 1.0F, clamp01(alpha));
    g.blitSprite(sprite, x, y, w, h);
    g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    RenderSystem.disableBlend();
  }

  /** {@code .d-screen}: the outer panel. */
  public static void panel(GuiGraphics g, int x, int y, int w, int h, float alpha) {
    sprite(g, PANEL, x, y, w, h, alpha);
  }

  /** {@code .d-text}: the box with the NPC's words. */
  public static void textBox(GuiGraphics g, int x, int y, int w, int h, float alpha) {
    sprite(g, TEXT_BOX, x, y, w, h, alpha);
  }

  /** Speech tail below a text box whose bottom edge is at {@code bottom}, pointing down. */
  public static void tail(GuiGraphics g, int centerX, int bottom, float alpha) {
    blit(g, TAIL, centerX - 4, bottom - 2, 0, 0, 9, 6, 9, 6, alpha);
  }

  /** {@code .d-speaker .face}: frame with the NPC's skin face (8x8 at 2x) inside. */
  public static void face(GuiGraphics g, LivingEntity entity, int x, int y, float alpha) {
    sprite(g, FACE, x, y, FACE_SIZE, FACE_SIZE, alpha);
    ResourceLocation skin = skinOf(entity);
    if (skin == null || alpha < 0.02F) {
      return;
    }
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    g.setColor(1.0F, 1.0F, 1.0F, clamp01(alpha));
    PlayerFaceRenderer.draw(g, skin, x + 2, y + 2, FACE_SIZE - 4);
    g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    RenderSystem.disableBlend();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static ResourceLocation skinOf(LivingEntity entity) {
    if (entity == null) {
      return null;
    }
    try {
      EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
      return renderer != null ? renderer.getTextureLocation(entity) : null;
    } catch (RuntimeException exception) {
      return null;
    }
  }

  /** {@code .d-reply}: stone button with the coloured stripe on the left. */
  public static void reply(
      GuiGraphics g, int x, int y, int w, int h, ReplyState state, int stripe, float alpha) {
    ResourceLocation sprite =
        switch (state) {
          case HOVER -> REPLY_HOVER;
          case PRESSED -> REPLY_PRESSED;
          case LOCKED -> REPLY_LOCKED;
          default -> REPLY;
        };
    sprite(g, sprite, x, y, w, h, alpha);
    int color = state == ReplyState.LOCKED ? LOCKED_STRIPE : stripe;
    g.fill(x + 1, y + 1, x + 3, y + h - 1, fade(color, alpha));
  }

  public static int segment(int index) {
    return SEGMENTS[Math.floorMod(index, SEGMENTS.length)];
  }

  /** Number key cap (11x11); lit = selected. Draws the digit too. */
  public static void keycap(
      GuiGraphics g, Font font, int x, int y, String digit, boolean lit, boolean usable,
      float alpha) {
    blit(g, KEYCAP, x, y, 0, lit ? KEY : 0, KEY, KEY, KEY, KEY * 2, alpha);
    int color = lit ? KEY_LIT_TEXT : usable ? GOLD : DIM;
    draw(g, font, digit, x + (KEY - width(font, digit)) / 2 + 1, y + 2, color, alpha, false);
  }

  /** Height of a reply row with {@code lines} label lines. */
  public static int rowHeight(int lines) {
    return ROW + Math.max(0, lines - 1) * ROW_LINE_EXTRA;
  }

  private static void blit(
      GuiGraphics g, ResourceLocation texture, int x, int y, int u, int v, int w, int h, int tw,
      int th, float alpha) {
    if (alpha < 0.02F) {
      return;
    }
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    g.setColor(1.0F, 1.0F, 1.0F, clamp01(alpha));
    g.blit(texture, x, y, (float) u, (float) v, w, h, tw, th);
    g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    RenderSystem.disableBlend();
  }

  // ---------------------------------------------------------------------------------------------
  // Colour helpers
  // ---------------------------------------------------------------------------------------------

  public static float clamp01(float value) {
    return Math.max(0.0F, Math.min(1.0F, value));
  }

  public static int fade(int color, float alpha) {
    int a = Math.round(((color >>> 24) & 0xFF) * clamp01(alpha));
    return (a << 24) | (color & 0x00FFFFFF);
  }

  public static int lerp(int from, int to, float progress) {
    float t = clamp01(progress);
    int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
    int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
    int gr = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
    int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
    return (a << 24) | (r << 16) | (gr << 8) | b;
  }
}
