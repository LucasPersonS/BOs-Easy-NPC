package de.markusbordihn.easynpc.paravexia.scene;

import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.api.skin.VariantTexture;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

/**
 * Built-in skins offered in the "Default skin" screen of scene actors. NPC_SKIN is the neutral base
 * every scene starts with; any other skin comes from the normal Easy NPC skin setup (player name,
 * URL, uploaded file or server texture).
 */
public enum SceneSkinVariant implements VariantTexture {
  NPC_SKIN,
  WORKER_1,
  WORKER_2,
  WORKER_3,
  WORKER_4;

  private final ResourceLocation texture =
      ResourceLocation.fromNamespaceAndPath(
          Constants.MOD_ID, "textures/scene_skins/" + this.name().toLowerCase(Locale.ROOT) + ".png");

  @Override
  public ResourceLocation getTextureLocation() {
    return this.texture;
  }
}
