package de.markusbordihn.easynpc.paravexia.scene.client;

import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.paravexia.scene.GeoSceneNPC;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** Resolves the model, animation and props texture of a scene entity from its scene id. */
public class SceneModel extends GeoModel<GeoSceneNPC> {

  /** Used only until the scene id has been synced; the renderer skips scenes without a model. */
  static final SceneAssets UNKNOWN =
      SceneAssets.of(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "unknown"));

  static SceneAssets assets(GeoSceneNPC scene) {
    return scene.getSceneId().map(SceneAssets::of).orElse(UNKNOWN);
  }

  @Override
  public ResourceLocation getModelResource(GeoSceneNPC scene) {
    return assets(scene).model();
  }

  @Override
  public ResourceLocation getTextureResource(GeoSceneNPC scene) {
    return assets(scene).texture();
  }

  @Override
  public ResourceLocation getAnimationResource(GeoSceneNPC scene) {
    return assets(scene).animation();
  }
}
