package de.markusbordihn.easynpc.paravexia.scene;

import de.markusbordihn.easynpc.Constants;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Registers the scene entity type. Kept apart from ModEntityType so GeckoLib classes are only
 * loaded when GeckoLib is present.
 */
public final class SceneRegistration {

  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);
  private static DeferredHolder<EntityType<?>, EntityType<GeoSceneEntity>> sceneType;

  private SceneRegistration() {}

  public static void registerEntityType(DeferredRegister<EntityType<?>> entityTypes) {
    sceneType =
        entityTypes.register(
            SceneConstants.ENTITY_ID,
            () ->
                EntityType.Builder.<GeoSceneEntity>of(GeoSceneEntity::new, MobCategory.MISC)
                    .sized(1.0F, 2.0F)
                    .fireImmune()
                    // Scenes never move on their own; 6 chunks keeps them visible across a square.
                    .clientTrackingRange(6)
                    .updateInterval(40)
                    .build(SceneConstants.ENTITY_ID));
    SceneEntityTypes.setSceneType(() -> sceneType.get());
    log.info("Registered Paravexia scene entity type.");
  }

  public static EntityType<GeoSceneEntity> sceneType() {
    return sceneType.get();
  }
}
