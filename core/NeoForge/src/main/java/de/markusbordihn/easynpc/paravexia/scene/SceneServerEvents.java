package de.markusbordihn.easynpc.paravexia.scene;

import de.markusbordihn.easynpc.Constants;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** Loads scene definitions from data packs (data/<ns>/easy_npc_scenes/*.json) on start and /reload. */
@EventBusSubscriber(modid = Constants.MOD_ID)
public final class SceneServerEvents {

  private SceneServerEvents() {}

  @SubscribeEvent
  public static void onAddReloadListeners(AddReloadListenerEvent event) {
    event.addListener(new SceneRegistry.ReloadListener());
  }
}
