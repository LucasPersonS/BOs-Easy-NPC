package de.markusbordihn.easynpc.paravexia.scene.client;

import de.markusbordihn.easynpc.paravexia.scene.SceneRegistration;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Only called when GeckoLib is installed, so the renderer classes never load without it. */
public final class SceneClientRegistration {

  private SceneClientRegistration() {}

  public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
    event.registerEntityRenderer(SceneRegistration.sceneType(), SceneRenderer::new);
  }

  /** Game bus listeners for the scene background loops (client only). */
  public static void registerGameEvents() {
    NeoForge.EVENT_BUS.addListener(SceneAmbience::onEntityJoin);
    NeoForge.EVENT_BUS.addListener(SceneAmbience::onEntityLeave);
    NeoForge.EVENT_BUS.addListener(SceneAmbience::onClientTick);
  }
}
