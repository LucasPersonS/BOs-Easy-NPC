package de.markusbordihn.easynpc.paravexia.scene.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.markusbordihn.easynpc.client.renderer.entity.EasyNPCEntityRenderer;
import de.markusbordihn.easynpc.paravexia.scene.GeoSceneNPC;
import de.markusbordihn.easynpc.paravexia.scene.SceneConstants;
import de.markusbordihn.easynpc.paravexia.scene.SceneSkinVariant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Draws one scene actor NPC. The whole scene model is placed around the scene origin (the NPC
 * stands on its actor pivot), then drawn in passes:
 *
 * <ol>
 *   <li>props with the scene texture — primary actor only;
 *   <li>optional glowmask ({@code textures/scene/<id>_glowmask.png}) — primary actor only;
 *   <li>this NPC's actor with its Easy NPC skin (default list, player name, URL, uploaded file or
 *       server texture), falling back to the base {@code npc_skin}.
 * </ol>
 *
 * <p>Actor cubes use the 64x64 player skin layout; 64x64 and 128x128 skins both work because UVs
 * are normalized.
 */
public class SceneRenderer extends GeoEntityRenderer<GeoSceneNPC> implements EasyNPCEntityRenderer {

  // Resource packs can't change while a world renders, so presence checks are cached per path.
  private static final Map<ResourceLocation, Boolean> RESOURCE_PRESENT = new ConcurrentHashMap<>();

  public SceneRenderer(EntityRendererProvider.Context context) {
    super(context, new SceneModel());
    this.shadowRadius = 0.4F;
    addRenderLayer(new GlowLayer(this));
    addRenderLayer(new ActorSkinLayer(this));
  }

  static boolean resourcePresent(ResourceLocation location) {
    return RESOURCE_PRESENT.computeIfAbsent(
        location, key -> Minecraft.getInstance().getResourceManager().getResource(key).isPresent());
  }

  @Override
  public ResourceLocation getDefaultTexture() {
    return SceneSkinVariant.NPC_SKIN.getTextureLocation();
  }

  /** Skin of this actor through the normal Easy NPC skin managers. */
  ResourceLocation actorSkin(GeoSceneNPC scene) {
    ResourceLocation skin = getEntityPlayerTexture(scene);
    return skin != null ? skin : getDefaultTexture();
  }

  @Override
  public void render(
      GeoSceneNPC scene,
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
      GeoSceneNPC scene,
      BakedGeoModel model,
      @Nullable MultiBufferSource bufferSource,
      @Nullable VertexConsumer buffer,
      boolean isReRender,
      float partialTick,
      int packedLight,
      int packedOverlay,
      int colour) {
    if (!isReRender) {
      ScenePasses.showProps(model, scene.isPrimaryActor());
    }
    super.preRender(
        poseStack, scene, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
  }

  /**
   * After the body rotation, shift the model so its origin lands on the scene origin instead of on
   * this actor (GeckoLib internal space: x negated).
   */
  @Override
  protected void applyRotations(
      GeoSceneNPC scene,
      PoseStack poseStack,
      float ageInTicks,
      float rotationYaw,
      float partialTick,
      float nativeScale) {
    super.applyRotations(scene, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
    Vector3f anchor = scene.getAnchor();
    poseStack.translate(
        anchor.x / SceneConstants.PIXELS_PER_BLOCK,
        -anchor.y / SceneConstants.PIXELS_PER_BLOCK,
        -anchor.z / SceneConstants.PIXELS_PER_BLOCK);
  }

  /** Emissive pass from {@code textures/scene/<id>_glowmask.png}; primary actor only. */
  static final class GlowLayer extends AutoGlowingGeoLayer<GeoSceneNPC> {

    GlowLayer(GeoRenderer<GeoSceneNPC> renderer) {
      super(renderer);
    }

    @Override
    public void render(
        PoseStack poseStack,
        GeoSceneNPC scene,
        BakedGeoModel bakedModel,
        @Nullable RenderType renderType,
        MultiBufferSource bufferSource,
        @Nullable VertexConsumer buffer,
        float partialTick,
        int packedLight,
        int packedOverlay) {
      if (scene.isPrimaryActor() && resourcePresent(SceneModel.assets(scene).glowmask())) {
        super.render(
            poseStack, scene, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
      }
    }
  }

  /** Re-renders the skin bones of this NPC's actor slot with the NPC skin. */
  static final class ActorSkinLayer extends GeoRenderLayer<GeoSceneNPC> {

    private final SceneRenderer sceneRenderer;

    ActorSkinLayer(SceneRenderer renderer) {
      super(renderer);
      this.sceneRenderer = renderer;
    }

    @Override
    public void render(
        PoseStack poseStack,
        GeoSceneNPC scene,
        BakedGeoModel bakedModel,
        @Nullable RenderType renderType,
        MultiBufferSource bufferSource,
        @Nullable VertexConsumer buffer,
        float partialTick,
        int packedLight,
        int packedOverlay) {
      String slot = scene.getActorSlot();
      if (!slot.isEmpty()) {
        ResourceLocation skin = this.sceneRenderer.actorSkin(scene);
        int colour = getRenderer().getRenderColor(scene, partialTick, packedLight).argbInt();
        ScenePasses.showActorSkin(bakedModel, slot);
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
      ScenePasses.showProps(bakedModel, scene.isPrimaryActor());
    }
  }
}
