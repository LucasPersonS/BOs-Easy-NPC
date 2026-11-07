package de.markusbordihn.easynpc.paravexia.scene;

import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;

/**
 * Loader independent access to the scene entity type. The loader module sets the supplier when it
 * registers the type; it stays empty when the renderer library (GeckoLib) is missing.
 */
public final class SceneEntityTypes {

  private static Supplier<? extends EntityType<? extends SceneEntity>> sceneType;

  private SceneEntityTypes() {}

  public static void setSceneType(Supplier<? extends EntityType<? extends SceneEntity>> supplier) {
    sceneType = supplier;
  }

  public static Optional<EntityType<? extends SceneEntity>> sceneType() {
    return sceneType == null ? Optional.empty() : Optional.ofNullable(sceneType.get());
  }
}
