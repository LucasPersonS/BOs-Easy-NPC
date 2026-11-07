/*
 * Copyright 2025 Markus Bordihn
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

package de.markusbordihn.easynpc.client;

import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.client.dialog.WorldDialogOverlay;
import de.markusbordihn.easynpc.client.compat.cobblemon.CobblemonVariantHelper;
import de.markusbordihn.easynpc.client.renderer.entity.SpeechBubbleFrameRenderer;
import de.markusbordihn.easynpc.client.renderer.layer.ShoulderNPCLayer;
import de.markusbordihn.easynpc.compat.CompatConstants;
import de.markusbordihn.easynpc.compat.cobblemon.CobblemonLoader;
import de.markusbordihn.easynpc.compat.easymodelentities.EasyModelEntitiesLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD, modid = Constants.MOD_ID)
public class ClientEventHandler {

  @SubscribeEvent
  public static void onClientSetup(FMLClientSetupEvent event) {
    event.enqueueWork(() -> ClientEvents.handleClientStartedEvent(Minecraft.getInstance()));
  }

  @SubscribeEvent
  public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
    for (PlayerSkin.Model skinModel : event.getSkins()) {
      if (event.getSkin(skinModel) instanceof PlayerRenderer playerRenderer) {
        playerRenderer.addLayer(new ShoulderNPCLayer(playerRenderer));
      }
    }
  }

  @SubscribeEvent
  public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
    event.registerAbove(
        VanillaGuiLayers.CROSSHAIR,
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "world_dialog"),
        WorldDialogOverlay::renderHud);
  }
}

@EventBusSubscriber(value = Dist.CLIENT, modid = Constants.MOD_ID)
class ClientGameEventHandler {

  @SubscribeEvent
  public static void onClientTickPre(ClientTickEvent.Pre event) {
    WorldDialogOverlay.preTick();
  }

  @SubscribeEvent
  public static void onClientTick(ClientTickEvent.Post event) {
    ClientEvents.handleClientTickEvent();
    WorldDialogOverlay.tick();
  }

  @SubscribeEvent
  public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
    if (event.isUseItem() && event.getHand() != InteractionHand.MAIN_HAND) {
      // Vanilla asks once per hand; the main-hand pass already decided.
      return;
    }
    if ((event.isAttack() || event.isUseItem()) && WorldDialogOverlay.click(event.isUseItem())) {
      event.setSwingHand(false);
      event.setCanceled(true);
    }
  }

  @SubscribeEvent
  public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
    if (WorldDialogOverlay.scroll(event.getScrollDeltaY())) {
      event.setCanceled(true);
    }
  }

  @SubscribeEvent
  public static void onRenderLevelStage(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
      return;
    }

    Minecraft minecraft = Minecraft.getInstance();
    SpeechBubbleFrameRenderer.renderFrame(
        minecraft,
        event.getPoseStack(),
        minecraft.renderBuffers().bufferSource(),
        event.getCamera(),
        event.getProjectionMatrix(),
        event.getPartialTick().getGameTimeDeltaPartialTick(false));
    WorldDialogOverlay.captureView(
        event.getCamera(),
        event.getModelViewMatrix(),
        event.getProjectionMatrix(),
        event.getPartialTick().getGameTimeDeltaPartialTick(false));
  }

  @SubscribeEvent
  public static void onTagsUpdated(TagsUpdatedEvent event) {
    if (CompatConstants.MOD_COBBLEMON_LOADED) {
      CobblemonLoader.setFemaleVariantFilter(CobblemonVariantHelper::hasFemaleVariant);
      CobblemonLoader.setAspectProvider(CobblemonVariantHelper::getAvailableAspects);
      CobblemonLoader.register();
    }
    if (CompatConstants.MOD_EASY_MODEL_ENTITIES_LOADED) {
      EasyModelEntitiesLoader.registerClient();
    }
  }
}
