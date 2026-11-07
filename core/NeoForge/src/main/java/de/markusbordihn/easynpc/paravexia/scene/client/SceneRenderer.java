package de.markusbordihn.easynpc.paravexia.scene.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.markusbordihn.easynpc.paravexia.scene.GeoSceneEntity;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Draws a scene in passes: the props with the scene texture (actor bones hidden), an optional
 * glowmask pass, then one pass per actor with that actor's 64x64 player-layout skin.
 *
 * <p>Actor cubes must use UVs laid out for a 64x64 skin relative to the model's texture size (the
 * scene generator scales them when the props atlas is larger than 64x64).
 */
public class SceneRenderer extends GeoEntityRenderer<GeoSceneEntity> {

  // Resource packs can't change while a world renders, so presence checks are cached per path.
  private static final Map<ResourceLocation, Boolean> RESOURCE_PRESENT = new ConcurrentHashMap<>();

  public SceneRenderer(EntityRendererProvider.Context context) {
    super(context, new SceneModel());
    this.shadowRadius = 0.0F;
    addRenderLayer(new GlowLayer(this));
    addRenderLayer(new ActorSkinLayer(this));
  }

  static boolean resourcePresent(ResourceLocation location) {
    return RESOURCE_PRESENT.computeIfAbsent(
        location, key -> Minecraft.getInstance().getResourceManager().getResource(key).isPresent());
  }

  @Override
  public void render(
      GeoSceneEntity scene,
      float entityYaw,
      float partialTick,
      PoseStack poseStack,
      MultiBufferSource bufferSource,
      int packedLight) {
    // GeckoLib throws on a missing model; a scene whose assets are not in the resource pack is
    // simply not drawn (its hitbox stays clickable so staff can find and remove it).
    if (scene.getSceneId().isEmpty()
        || !GeckoLibCache.getBakedModels().containsKey(SceneModel.assets(scene).model())) {
      return;
    }
    super.render(scene, entityYaw, partialTick, poseStack, bufferSource, packedLight);
  }

  @Override
  public void preRender(
      PoseStack poseStack,
      GeoSceneEntity scene,
      BakedGeoModel model,
      @Nullable MultiBufferSource bufferSource,
      @Nullable VertexConsumer buffer,
      boolean isReRender,
      float partialTick,
      int packedLight,
      int packedOverlay,
      int colour) {
    if (!isReRender) {
      ScenePasses.showProps(model);
    }
    super.preRender(
        poseStack, scene, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
  }

  // Non-living entities get yaw 0 from GeckoLib; scenes use the entity's own rotation.
  @Override
  protected void applyRotations(
      GeoSceneEntity scene,
      PoseStack poseStack,
      float ageInTicks,
      float rotationYaw,
      float partialTick,
      float nativeScale) {
    super.applyRotations(
        scene, poseStack, ageInTicks, Mth.rotLerp(partialTick, scene.yRotO, scene.getYRot()), partialTick, nativeScale);
  }

  /** Emissive pass from {@code textures/scene/<id>_glowmask.png}, skipped when there is none. */
  static final class GlowLayer extends AutoGlowingGeoLayer<GeoSceneEntity> {

    GlowLayer(GeoRenderer<GeoSceneEntity> renderer) {
      super(renderer);
    }

    @Override
    public void render(
        PoseStack poseStack,
        GeoSceneEntity scene,
        BakedGeoModel bakedModel,
        @Nullable RenderType renderType,
        MultiBufferSource bufferSource,
        @Nullable VertexConsumer buffer,
        float partialTick,
        int packedLight,
        int packedOverlay) {
      if (resourcePresent(SceneModel.assets(scene).glowmask())) {
        super.render(
            poseStack, scene, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
      }
    }
  }

  /** Re-renders the skin bones of each top level actor_* bone with that actor's skin. */
  static final class ActorSkinLayer extends GeoRenderLayer<GeoSceneEntity> {

    ActorSkinLayer(GeoRenderer<GeoSceneEntity> renderer) {
      super(renderer);
    }

    @Override
    public void render(
        PoseStack poseStack,
        GeoSceneEntity scene,
        BakedGeoModel bakedModel,
        @Nullable RenderType renderType,
        MultiBufferSource bufferSource,
        @Nullable VertexConsumer buffer,
        float partialTick,
        int packedLight,
        int packedOverlay) {
      Map<String, ResourceLocation> skins = scene.getActorSkins();
      int colour = getRenderer().getRenderColor(scene, partialTick, packedLight).argbInt();
      for (String actor : ScenePasses.plan(bakedModel).actors()) {
        ResourceLocation skin = skins.get(actor);
        if (skin == null || !resourcePresent(skin)) {
          skin = DefaultPlayerSkin.getDefaultTexture();
        }
        ScenePasses.showActor(bakedModel, actor);
        RenderType skinType = RenderType.entityCutoutNoCull(skin);
        getRenderer()
            .reRender(
                bakedModel,
                poseStack,
                bufferSource,
                scene,
                skinType,
                bufferSource.getBuffer(skinType),
                partialTick,
                packedLight,
                packedOverlay,
                colour);
      }
      ScenePasses.showProps(bakedModel);
    }
  }
}
