package de.markusbordihn.easynpc.paravexia.scene.client;

import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.paravexia.scene.GeoSceneNPC;
import de.markusbordihn.easynpc.paravexia.scene.SceneClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/** Resolves the model, animation and props texture of a scene entity from its scene id. */
public class SceneModel extends GeoModel<GeoSceneNPC> {

  /** Used only until the scene id has been synced; the renderer skips scenes without a model. */
  static final SceneAssets UNKNOWN =
      SceneAssets.of(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "unknown"));

  static SceneAssets assets(GeoSceneNPC scene) {
    return scene.getSceneId().map(SceneAssets::of).orElse(UNKNOWN);
  }

  /**
   * Distance freeze: beyond {@link SceneClientConfig#ANIMATION_FREEZE_DISTANCE} the scene is
   * animated once more and then keeps that pose, so far away scenes cost only their drawing.
   *
   * <p>GeckoLib keeps bone transforms on the baked model, which every actor of the same scene id
   * shares. Skipping the update therefore leaves the pose of the last processed instance; scenes
   * are locked to the world clock, so that pose is the right frame for every copy of the scene.
   */
  @Override
  public void handleAnimations(
      GeoSceneNPC scene, long instanceId, AnimationState<GeoSceneNPC> state, float partialTick) {
    if (isBeyondFreezeDistance(scene)) {
      if (scene.isAnimationFrozen()) {
        return;
      }
      scene.setAnimationFrozen(true);
    } else {
      scene.setAnimationFrozen(false);
    }
    super.handleAnimations(scene, instanceId, state, partialTick);
  }

  private static boolean isBeyondFreezeDistance(GeoSceneNPC scene) {
    int distance = SceneClientConfig.ANIMATION_FREEZE_DISTANCE;
    if (distance <= 0) {
      return false;
    }
    Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
    return camera.getPosition().distanceToSqr(scene.getX(), scene.getY(), scene.getZ())
        > (double) distance * distance;
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
