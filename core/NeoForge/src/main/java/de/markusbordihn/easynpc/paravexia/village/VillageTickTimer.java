package de.markusbordihn.easynpc.paravexia.village;

import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Measures the tick time of server side Easy NPC entities for the village budget guard. Entity
 * ticks run one after another on the server thread, so one start time is enough.
 */
@EventBusSubscriber(modid = Constants.MOD_ID)
public final class VillageTickTimer {

  private static Entity currentEntity;
  private static long startNanos;

  private VillageTickTimer() {}

  // LOWEST so the time of other mods' Pre handlers is not charged to the NPC.
  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void onEntityTickPre(EntityTickEvent.Pre event) {
    Entity entity = event.getEntity();
    if (entity instanceof EasyNPC<?> && !entity.level().isClientSide()) {
      currentEntity = entity;
      startNanos = System.nanoTime();
    }
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void onEntityTickPost(EntityTickEvent.Post event) {
    if (event.getEntity() == currentEntity) {
      VillageRuntime.addNpcTickNanos(System.nanoTime() - startNanos);
      currentEntity = null;
    }
  }
}
