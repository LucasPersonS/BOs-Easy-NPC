package de.markusbordihn.easynpc.paravexia.village;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;

/**
 * Entry points for other mods (e.g. the paravexiaadmin bridge `village.publish` op). Only JDK types
 * cross this boundary so it can be called by reflection; all calls run on the server thread.
 */
public final class VillageApi {

  public static final int API_VERSION = 1;
  private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

  private VillageApi() {}

  /** Applies a village JSON (format_version 1) and returns the result as JSON. */
  public static String apply(String json) {
    return GSON.toJson(applyResult(json));
  }

  /** Same as {@link #apply(String)} but returns a map with ok, messages, counts and hash. */
  public static Map<String, Object> applyResult(String json) {
    return onServerThread(
        () -> {
          DataResult<VillageSpec> parsed = VillageSpec.parseJson(json);
          if (parsed.error().isPresent()) {
            return toMap(VillageService.Result.failure(parsed.error().get().message()));
          }
          return toMap(VillageService.apply(parsed.getOrThrow()));
        });
  }

  /** Validates without applying, e.g. for a publish preview. */
  public static Map<String, Object> validate(String json) {
    return onServerThread(
        () -> {
          DataResult<VillageSpec> parsed = VillageSpec.parseJson(json);
          if (parsed.error().isPresent()) {
            return toMap(VillageService.Result.failure(parsed.error().get().message()));
          }
          VillageSpec spec = parsed.getOrThrow();
          int others = VillageRuntime.data().map(data -> data.npcCountExcept(spec.id())).orElse(0);
          List<String> errors = VillageValidator.validate(spec, VillageConfig.limits(), others);
          Map<String, Object> result = new LinkedHashMap<>();
          result.put("ok", errors.isEmpty());
          result.put("messages", errors);
          result.put("hash", spec.hash());
          return result;
        });
  }

  public static Map<String, Object> remove(String villageId, boolean deleteNpcs) {
    return onServerThread(() -> toMap(VillageService.remove(villageId, deleteNpcs)));
  }

  /** Spec JSON of a village, or an empty string when it does not exist. */
  public static String export(String villageId) {
    return onServerThread(
        () ->
            VillageRuntime.data()
                .flatMap(data -> data.village(villageId))
                .map(village -> village.spec().toJson())
                .orElse(""));
  }

  /** Runtime status of all villages and the budget guard as JSON. */
  public static String status() {
    return GSON.toJson(onServerThread(VillageApi::statusMap));
  }

  static Map<String, Object> statusMap() {
    Map<String, Object> status = new LinkedHashMap<>();
    status.put("apiVersion", API_VERSION);
    status.put("budgetLevel", VillageRuntime.budgetLevel());
    status.put("npcMicrosPerTick", VillageRuntime.lastNpcMicrosPerTick());
    status.put("budgetMicros", VillageConfig.NPC_BUDGET_MICROS);
    status.put("queuedSpawns", VillageRuntime.queuedSpawns());
    List<Map<String, Object>> villages = new ArrayList<>();
    VillageRuntime.data()
        .ifPresent(
            data ->
                data.villages()
                    .forEach(
                        village -> {
                          Map<String, Object> entry = new LinkedHashMap<>();
                          entry.put("id", village.spec().id());
                          entry.put("name", village.spec().name());
                          entry.put("dimension", village.spec().dimension().toString());
                          entry.put("hash", village.spec().hash());
                          entry.put("npcs", village.spec().npcs().size());
                          entry.put("spawned", village.bindings().size());
                          Map<String, Long> profiles = new LinkedHashMap<>();
                          for (VillageProfile profile : VillageProfile.values()) {
                            profiles.put(profile.serializedName(), village.spec().count(profile));
                          }
                          entry.put("profiles", profiles);
                          entry.putAll(VillageRuntime.status(village.spec().id()));
                          villages.add(entry);
                        }));
    status.put("villages", villages);
    return status;
  }

  private static Map<String, Object> toMap(VillageService.Result result) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("ok", result.ok());
    map.put("messages", result.messages());
    map.put("queuedSpawns", result.queuedSpawns());
    map.put("updatedNpcs", result.updatedNpcs());
    map.put("removedNpcs", result.removedNpcs());
    map.put("hash", result.hash());
    return map;
  }

  private static <T> T onServerThread(Supplier<T> supplier) {
    Optional<MinecraftServer> server = VillageRuntime.server();
    if (server.isEmpty() || server.get().isSameThread()) {
      return supplier.get();
    }
    return server.get().submit(supplier).join();
  }
}
