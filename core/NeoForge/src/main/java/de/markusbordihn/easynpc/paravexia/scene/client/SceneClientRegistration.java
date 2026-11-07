package de.markusbordihn.easynpc.paravexia.scene.client;

import de.markusbordihn.easynpc.paravexia.scene.SceneRegistration;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Only called when GeckoLib is installed, so the renderer classes never load without it. */
public final class SceneClientRegistration {

  private SceneClientRegistration() {}

  public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
    event.registerEntityRenderer(SceneRegistration.sceneType(), SceneRenderer::new);
  }
}
