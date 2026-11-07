package de.markusbordihn.easynpc.paravexia.scene;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import de.markusbordihn.easynpc.Constants;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Scene definitions loaded from data packs. Replaced as a whole on every /reload. */
public final class SceneRegistry {

  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);
  private static volatile Map<ResourceLocation, SceneDefinition> definitions = Map.of();

  private SceneRegistry() {}

  public static Optional<SceneDefinition> get(ResourceLocation id) {
    return Optional.ofNullable(definitions.get(id));
  }

  public static Collection<SceneDefinition> all() {
    return definitions.values();
  }

  static void replace(Map<ResourceLocation, SceneDefinition> loaded) {
    definitions = Map.copyOf(loaded);
  }

  /** Resolves "ferreiro" to easy_npc:ferreiro, keeps explicit namespaces. */
  public static ResourceLocation resolveId(String value) {
    return SceneDefinition.parseLocation(value, Constants.MOD_ID);
  }

  public static final class ReloadListener extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().create();

    public ReloadListener() {
      super(GSON, SceneConstants.DATA_DIRECTORY);
    }

    @Override
    protected void apply(
        Map<ResourceLocation, JsonElement> entries,
        ResourceManager resourceManager,
        ProfilerFiller profiler) {
      Map<ResourceLocation, SceneDefinition> loaded = new LinkedHashMap<>();
      entries.forEach(
          (id, json) -> {
            try {
              loaded.put(id, SceneDefinition.parse(id, json.getAsJsonObject()));
            } catch (RuntimeException exception) {
              // One broken scene must not hide the others.
              log.error("[Scenes] Skipping scene {}: {}", id, exception.getMessage());
            }
          });
      replace(loaded);
      log.info("[Scenes] Loaded {} scene definitions.", loaded.size());
    }
  }
}
