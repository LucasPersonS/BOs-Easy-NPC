package de.markusbordihn.easynpc.paravexia.scene.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/**
 * Asset paths of a scene, derived once from its id: {@code <ns>:geo/scene/<path>.geo.json},
 * {@code animations/scene/<path>.animation.json}, {@code textures/scene/<path>.png} and the
 * optional {@code textures/scene/<path>_glowmask.png}.
 */
public record SceneAssets(
    ResourceLocation model, ResourceLocation animation, ResourceLocation texture, ResourceLocation glowmask) {

  private static final Map<ResourceLocation, SceneAssets> CACHE = new ConcurrentHashMap<>();

  public static SceneAssets of(ResourceLocation sceneId) {
    return CACHE.computeIfAbsent(
        sceneId,
        id ->
            new SceneAssets(
                id.withPath(path -> "geo/scene/" + path + ".geo.json"),
                id.withPath(path -> "animations/scene/" + path + ".animation.json"),
                id.withPath(path -> "textures/scene/" + path + ".png"),
                id.withPath(path -> "textures/scene/" + path + "_glowmask.png")));
  }
}
