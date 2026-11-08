package de.markusbordihn.easynpc.paravexia.scene;

import de.markusbordihn.easynpc.Constants;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Registers the scene actor NPC type. Kept apart from ModEntityType so GeckoLib classes are only
 * loaded when GeckoLib is present.
 */
public final class SceneRegistration {

  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);
  private static DeferredHolder<EntityType<?>, EntityType<GeoSceneNPC>> sceneType;

  private SceneRegistration() {}

  public static void registerEntityType(DeferredRegister<EntityType<?>> entityTypes) {
    sceneType =
        entityTypes.register(
            SceneConstants.ENTITY_ID,
            () ->
                EntityType.Builder.<GeoSceneNPC>of(GeoSceneNPC::new, MobCategory.MISC)
                    .sized(0.8F, 2.0F)
                    .clientTrackingRange(8)
                    .build(SceneConstants.ENTITY_ID));
    SceneEntityTypes.setSceneType(() -> sceneType.get());
    log.info("Registered Paravexia scene NPC type.");
  }

  public static void registerAttributes(EntityAttributeCreationEvent event) {
    if (sceneType != null) {
      event.put(sceneType.get(), SceneNPC.createAttributes().build());
    }
  }

  public static EntityType<GeoSceneNPC> sceneType() {
    return sceneType.get();
  }
}
