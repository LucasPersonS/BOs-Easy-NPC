package de.markusbordihn.easynpc.client.dialog;

import de.markusbordihn.easynpc.Constants;
import net.minecraft.resources.ResourceLocation;

/** Neutral ink-and-ivory palette and metrics of the world dialogue HUD (GUI pixels, ARGB). */
final class WorldDialogStyle {

  static final int INK = 0xFF15111B;
  static final int INK_RAISED = 0xFF201A28;
  static final int INK_HOVER = 0xFF2E2638;
  static final int INK_LOCKED = 0xFF131017;
  static final int RIM = 0xFFE6D9BF;
  static final int RIM_DIM = 0xFF6E6557;
  static final int RIM_INNER = 0xFF352E3C;
  static final int TEXT = 0xFFEFE6D4;
  static final int TEXT_BRIGHT = 0xFFFFFBF2;
  static final int MUTED = 0xFF8C8374;
  static final int GOLD = 0xFFE0B868;
  static final int GOLD_DIM = 0xFF977639;
  static final int DENY = 0xFFC4505E;
  static final int SHADOW = 0x70000000;
  static final int PILL = 0xC0110D16;

  static final int PAD = 6;
  static final int LINE = 10;
  static final int TAIL = 4;

  static final int TEASER_WIDTH = 160;
  static final int TEASER_LINES = 2;
  static final int SPEECH_WIDTH = 200;
  static final int SPEECH_LINES = 9;
  static final int CHOICE_WIDTH = 156;
  static final int CHOICE_GAP = 3;
  static final int CHOICE_MAX_LINES = 2;
  static final int MAX_VISIBLE_CHOICES = 4;
  static final int COLUMN_GAP = 6;
  static final int SCREEN_MARGIN = 3;

  static final ResourceLocation MOUSE_RIGHT =
      ResourceLocation.fromNamespaceAndPath(
          Constants.MOD_ID, "textures/gui/world_dialog/mouse_right.png");

  private WorldDialogStyle() {}
}
