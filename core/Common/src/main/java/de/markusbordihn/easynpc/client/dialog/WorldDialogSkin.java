package de.markusbordihn.easynpc.client.dialog;

import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional look for the in-world dialogue, provided by another mod (e.g. a modpack UI kit) so EasyNPC
 * does not depend on it. Without a registered skin the built-in, code-drawn panels are used.
 *
 * <p>The supplier is called once per frame on the render thread, so it may follow live theme changes.
 * Colors are ARGB.
 */
public record WorldDialogSkin(
    ResourceLocation font,
    int text,
    int muted,
    int title,
    int accent,
    int accentIdle,
    int confirm,
    int deny,
    int flash,
    int numberBox,
    int numberBoxFocus,
    int tailRim,
    int tailFill,
    NineSlice speech,
    NineSlice namePlate,
    NineSlice choice,
    NineSlice choiceHover,
    NineSlice choicePressed,
    NineSlice choiceLocked,
    NineSlice choiceSpecial) {

  private static final Logger log = LoggerFactory.getLogger(WorldDialogSkin.class);
  private static Supplier<WorldDialogSkin> provider;
  private static boolean failed;

  /** Registers the skin provider (client only). Pass {@code null} to restore the built-in look. */
  public static void register(Supplier<WorldDialogSkin> skinProvider) {
    provider = skinProvider;
    failed = false;
  }

  /** Current skin, or {@code null} for the built-in look. A failing provider is disabled. */
  public static WorldDialogSkin current() {
    if (provider == null || failed) {
      return null;
    }
    try {
      return provider.get();
    } catch (RuntimeException exception) {
      failed = true;
      log.error("World dialogue skin failed, falling back to the built-in look", exception);
      return null;
    }
  }

  /**
   * Nine-slice texture. {@code border} is in texels; {@code density} is texels per panel unit (the
   * panel's local units match GUI pixels), so a 2x texture uses density 2.
   */
  public record NineSlice(
      ResourceLocation texture, int textureWidth, int textureHeight, int border, float density) {}
}
