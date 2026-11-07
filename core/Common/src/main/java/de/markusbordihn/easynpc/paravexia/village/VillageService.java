package de.markusbordihn.easynpc.paravexia.village;

import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.api.handler.EasyNPCEntityHandler;
import de.markusbordihn.easynpc.entity.LivingEntityManager;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * All changes to villages go through here, on the server thread: validation against the caps,
 * persistence, despawning removed NPCs and queueing missing ones for the gradual spawner.
 */
public final class VillageService {

  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);

  private VillageService() {}

  /** Creates or updates a village from a spec. Publishing an unchanged spec does nothing. */
  public static Result apply(VillageSpec spec) {
    Optional<VillageSavedData> optionalData = VillageRuntime.data();
    if (optionalData.isEmpty()) {
      return Result.failure("The server is not running.");
    }
    VillageSavedData data = optionalData.get();
    List<String> errors =
        VillageValidator.validate(spec, VillageConfig.limits(), data.npcCountExcept(spec.id()));
    if (VillageRuntime.level(spec) == null) {
      errors.add("Unknown dimension " + spec.dimension());
    }
    if (!errors.isEmpty()) {
      return Result.failure(errors);
    }

    String hash = spec.hash();
    Optional<VillageSavedData.Village> existing = data.village(spec.id());
    if (existing.isPresent() && existing.get().spec().hash().equals(hash)) {
      VillageRuntime.queueMissingSpawns(existing.get());
      return new Result(true, List.of("Village " + spec.id() + " is unchanged."), 0, 0, 0, hash);
    }

    int removed = 0;
    int updated = 0;
    VillageSavedData.Village village;
    if (existing.isPresent()) {
      ServerLevel oldLevel = VillageRuntime.level(existing.get().spec());
      for (Map.Entry<String, UUID> binding : existing.get().bindings().entrySet()) {
        if (spec.npc(binding.getKey()).isEmpty()) {
          removed += removeNpc(oldLevel, binding.getKey(), binding.getValue()) ? 1 : 0;
        }
      }
      village = existing.get().withSpec(spec);
      updated = reapplyProfiles(village);
    } else {
      village = new VillageSavedData.Village(spec, Map.of());
    }
    data.put(village);
    int before = VillageRuntime.queuedSpawns();
    VillageRuntime.onVillageUpdated(village);
    int queued = VillageRuntime.queuedSpawns() - before;
    log.info(
        "[Villages] Applied {} ({}): {} NPCs, {} queued for spawn, {} updated, {} removed.",
        spec.id(),
        hash,
        spec.npcs().size(),
        queued,
        updated,
        removed);
    return new Result(true, List.of("Village " + spec.id() + " applied."), queued, updated, removed, hash);
  }

  /** Spawned NPCs are deleted, adopted ones (key = their UUID) are only released. */
  private static boolean removeNpc(ServerLevel level, String key, UUID uuid) {
    EasyNPC<?> easyNPC = level != null ? LivingEntityManager.getServerEasyNPCEntityByUUID(uuid, level) : null;
    if (key.equals(uuid.toString())) {
      if (easyNPC != null) {
        VillageRuntime.applySleepState(easyNPC, false);
      }
      return true;
    }
    return level != null && EasyNPCEntityHandler.delete(uuid, level);
  }

  private static int reapplyProfiles(VillageSavedData.Village village) {
    ServerLevel level = VillageRuntime.level(village.spec());
    if (level == null) {
      return 0;
    }
    int updated = 0;
    for (VillageSpec.Npc npc : village.spec().npcs()) {
      Optional<UUID> uuid = village.binding(npc.key());
      EasyNPC<?> easyNPC =
          uuid.map(value -> LivingEntityManager.getServerEasyNPCEntityByUUID(value, level)).orElse(null);
      if (easyNPC != null) {
        if (ProfileApplier.apply(easyNPC, npc.profile())) {
          updated++;
        }
        npc.dialogueLibrary().ifPresent(libraryId -> VillageSpawner.linkLibrary(easyNPC, libraryId));
      }
    }
    return updated;
  }

  public static Result create(String id, ServerLevel level, BlockPos center, int radius) {
    Optional<VillageSavedData> data = VillageRuntime.data();
    if (data.isPresent() && data.get().village(id).isPresent()) {
      return Result.failure("Village " + id + " already exists.");
    }
    return apply(VillageSpec.empty(id, level.dimension().location(), center, radius));
  }

  public static Result remove(String id, boolean deleteNpcs) {
    Optional<VillageSavedData> data = VillageRuntime.data();
    Optional<VillageSavedData.Village> village = data.flatMap(value -> value.village(id));
    if (village.isEmpty()) {
      return Result.failure("Unknown village " + id + ".");
    }
    int deleted = 0;
    if (deleteNpcs) {
      ServerLevel level = VillageRuntime.level(village.get().spec());
      for (Map.Entry<String, UUID> binding : village.get().bindings().entrySet()) {
        if (!binding.getKey().equals(binding.getValue().toString()) && level != null
            && EasyNPCEntityHandler.delete(binding.getValue(), level)) {
          deleted++;
        }
      }
    }
    VillageRuntime.onVillageRemoved(village.get());
    data.get().remove(id);
    return new Result(true, List.of("Removed village " + id + "."), 0, 0, deleted, "");
  }

  public static Result addPoi(String villageId, String poiId, PoiType type, BlockPos pos) {
    return update(villageId, spec -> spec.withPoi(new VillageSpec.Poi(poiId, type, pos)));
  }

  /** Adopts an existing NPC into the village; its key is its UUID. */
  public static Result addNpc(String villageId, EasyNPC<?> easyNPC, VillageProfile profile, String homePoi) {
    Optional<VillageSavedData> data = VillageRuntime.data();
    UUID uuid = easyNPC.getEntityUUID();
    Optional<String> owner = data.flatMap(value -> value.villageOf(uuid));
    if (owner.isPresent() && !owner.get().equals(villageId)) {
      return Result.failure("NPC already belongs to village " + owner.get() + ".");
    }
    String key = uuid.toString();
    VillageSpec.Npc npc =
        new VillageSpec.Npc(
            key,
            net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(easyNPC.getEntity().getType()),
            easyNPC.getEntity().getName().getString(),
            profile,
            homePoi,
            List.of(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    Result result = update(villageId, spec -> spec.withNpc(npc));
    if (result.ok()) {
      data.get().village(villageId).ifPresent(village -> data.get().put(village.withBinding(key, uuid)));
      ProfileApplier.apply(easyNPC, profile);
      VillageRuntime.onNpcJoin(easyNPC);
    }
    return result;
  }

  public static Result removeNpc(String villageId, UUID uuid) {
    Optional<VillageSavedData.Village> village =
        VillageRuntime.data().flatMap(data -> data.village(villageId));
    Optional<String> key = village.flatMap(value -> value.keyOf(uuid));
    if (key.isEmpty()) {
      return Result.failure("NPC " + uuid + " is not part of village " + villageId + ".");
    }
    return update(villageId, spec -> spec.withoutNpc(key.get()));
  }

  public static Result setSchedule(String villageId, UUID uuid, int start, String poiId) {
    Optional<VillageSavedData.Village> village =
        VillageRuntime.data().flatMap(data -> data.village(villageId));
    Optional<String> key = village.flatMap(value -> value.keyOf(uuid));
    if (key.isEmpty()) {
      return Result.failure("NPC " + uuid + " is not part of village " + villageId + ".");
    }
    return update(
        villageId,
        spec -> {
          VillageSpec.Npc npc = spec.npc(key.get()).orElseThrow();
          List<VillageSpec.ScheduleEntry> schedule = new ArrayList<>();
          for (VillageSpec.ScheduleEntry entry : npc.schedule()) {
            if (entry.start() != start) {
              schedule.add(entry);
            }
          }
          schedule.add(new VillageSpec.ScheduleEntry(start, poiId));
          schedule.sort((a, b) -> Integer.compare(a.start(), b.start()));
          return spec.withNpc(npc.withSchedule(schedule));
        });
  }

  private static Result update(String villageId, java.util.function.UnaryOperator<VillageSpec> change) {
    Optional<VillageSavedData.Village> village =
        VillageRuntime.data().flatMap(data -> data.village(villageId));
    if (village.isEmpty()) {
      return Result.failure("Unknown village " + villageId + ".");
    }
    return apply(change.apply(village.get().spec()));
  }

  /** Outcome of a village change; only JDK types, so bridges can read it by reflection. */
  public record Result(
      boolean ok, List<String> messages, int queuedSpawns, int updatedNpcs, int removedNpcs, String hash) {

    public Result {
      messages = List.copyOf(messages);
    }

    static Result failure(String message) {
      return failure(List.of(message));
    }

    static Result failure(List<String> messages) {
      return new Result(false, messages, 0, 0, 0, "");
    }
  }
}
