package de.markusbordihn.easynpc.paravexia.village;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Versioned village description (format_version 1). The same JSON is written by the panel, read
 * from config/easy_npc/villages and persisted in the village saved data.
 */
public record VillageSpec(
    int formatVersion,
    String id,
    String name,
    ResourceLocation dimension,
    BlockPos center,
    int radius,
    int wakeRadius,
    Optional<String> lore,
    List<Poi> pois,
    List<Npc> npcs) {

  public static final int FORMAT_VERSION = 1;
  public static final ResourceLocation OVERWORLD = ResourceLocation.withDefaultNamespace("overworld");

  /** {x, y, z} object, easier to write by hand and by the panel than an int array. */
  public static final Codec<BlockPos> POS_CODEC =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      Codec.INT.fieldOf("x").forGetter(BlockPos::getX),
                      Codec.INT.fieldOf("y").forGetter(BlockPos::getY),
                      Codec.INT.fieldOf("z").forGetter(BlockPos::getZ))
                  .apply(instance, BlockPos::new));

  public static final Codec<ScheduleEntry> SCHEDULE_CODEC =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      Codec.INT.fieldOf("start").forGetter(ScheduleEntry::start),
                      Codec.STRING.fieldOf("poi").forGetter(ScheduleEntry::poi))
                  .apply(instance, ScheduleEntry::new));

  public static final Codec<Poi> POI_CODEC =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      Codec.STRING.fieldOf("id").forGetter(Poi::id),
                      PoiType.CODEC.optionalFieldOf("type", PoiType.CUSTOM).forGetter(Poi::type),
                      POS_CODEC.fieldOf("pos").forGetter(Poi::pos))
                  .apply(instance, Poi::new));

  public static final Codec<Npc> NPC_CODEC =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      Codec.STRING.fieldOf("key").forGetter(Npc::key),
                      ResourceLocation.CODEC.fieldOf("entity_type").forGetter(Npc::entityType),
                      Codec.STRING.optionalFieldOf("name", "").forGetter(Npc::name),
                      VillageProfile.CODEC.fieldOf("profile").forGetter(Npc::profile),
                      Codec.STRING.fieldOf("home").forGetter(Npc::home),
                      SCHEDULE_CODEC
                          .listOf()
                          .optionalFieldOf("schedule", List.of())
                          .forGetter(Npc::schedule),
                      Codec.STRING.optionalFieldOf("dialogue_library").forGetter(Npc::dialogueLibrary),
                      Codec.STRING.optionalFieldOf("skin").forGetter(Npc::skin),
                      Codec.STRING.optionalFieldOf("lore").forGetter(Npc::lore))
                  .apply(instance, Npc::new));

  public static final Codec<VillageSpec> CODEC =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      Codec.INT.fieldOf("format_version").forGetter(VillageSpec::formatVersion),
                      Codec.STRING.fieldOf("id").forGetter(VillageSpec::id),
                      Codec.STRING.optionalFieldOf("name", "").forGetter(VillageSpec::name),
                      ResourceLocation.CODEC
                          .optionalFieldOf("dimension", OVERWORLD)
                          .forGetter(VillageSpec::dimension),
                      POS_CODEC.fieldOf("center").forGetter(VillageSpec::center),
                      Codec.INT.fieldOf("radius").forGetter(VillageSpec::radius),
                      Codec.INT.optionalFieldOf("wake_radius", 0).forGetter(VillageSpec::wakeRadius),
                      Codec.STRING.optionalFieldOf("lore").forGetter(VillageSpec::lore),
                      POI_CODEC.listOf().optionalFieldOf("pois", List.of()).forGetter(VillageSpec::pois),
                      NPC_CODEC.listOf().optionalFieldOf("npcs", List.of()).forGetter(VillageSpec::npcs))
                  .apply(instance, VillageSpec::new));

  private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

  public VillageSpec {
    pois = List.copyOf(pois);
    npcs = List.copyOf(npcs);
    // A missing wake radius means "radius + 32", the usual distance a player sees NPCs from.
    if (wakeRadius <= 0) {
      wakeRadius = radius + 32;
    }
  }

  public static VillageSpec empty(String id, ResourceLocation dimension, BlockPos center, int radius) {
    return new VillageSpec(
        FORMAT_VERSION, id, id, dimension, center, radius, 0, Optional.empty(), List.of(), List.of());
  }

  public static DataResult<VillageSpec> parseJson(String json) {
    JsonElement element;
    try {
      element = JsonParser.parseString(json);
    } catch (RuntimeException exception) {
      return DataResult.error(() -> "Invalid JSON: " + exception.getMessage());
    }
    return CODEC.parse(JsonOps.INSTANCE, element);
  }

  public String toJson() {
    return CODEC
        .encodeStart(JsonOps.INSTANCE, this)
        .map(GSON::toJson)
        .getOrThrow(error -> new IllegalStateException("Unable to encode village: " + error));
  }

  /** SHA-1 of the canonical JSON; equal specs give equal hashes, so re-publishing is a no-op. */
  public String hash() {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-1").digest(this.toJson().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-1 is not available", exception);
    }
  }

  public Optional<Poi> poi(String poiId) {
    for (Poi poi : this.pois) {
      if (poi.id().equals(poiId)) {
        return Optional.of(poi);
      }
    }
    return Optional.empty();
  }

  public Optional<Npc> npc(String key) {
    for (Npc npc : this.npcs) {
      if (npc.key().equals(key)) {
        return Optional.of(npc);
      }
    }
    return Optional.empty();
  }

  public VillageSpec withPoi(Poi poi) {
    List<Poi> updated = new ArrayList<>();
    for (Poi existing : this.pois) {
      if (!existing.id().equals(poi.id())) {
        updated.add(existing);
      }
    }
    updated.add(poi);
    return new VillageSpec(
        formatVersion, id, name, dimension, center, radius, wakeRadius, lore, updated, npcs);
  }

  public VillageSpec withNpc(Npc npc) {
    List<Npc> updated = new ArrayList<>();
    boolean replaced = false;
    for (Npc existing : this.npcs) {
      if (existing.key().equals(npc.key())) {
        updated.add(npc);
        replaced = true;
      } else {
        updated.add(existing);
      }
    }
    if (!replaced) {
      updated.add(npc);
    }
    return new VillageSpec(
        formatVersion, id, name, dimension, center, radius, wakeRadius, lore, pois, updated);
  }

  public VillageSpec withoutNpc(String key) {
    List<Npc> updated = new ArrayList<>();
    for (Npc existing : this.npcs) {
      if (!existing.key().equals(key)) {
        updated.add(existing);
      }
    }
    return new VillageSpec(
        formatVersion, id, name, dimension, center, radius, wakeRadius, lore, pois, updated);
  }

  public long count(VillageProfile profile) {
    return this.npcs.stream().filter(npc -> npc.profile() == profile).count();
  }

  public record Poi(String id, PoiType type, BlockPos pos) {}

  public record ScheduleEntry(int start, String poi) {}

  /**
   * One NPC of the village. The key is stable inside the village and maps to the spawned entity,
   * so publishing the same spec again does not spawn duplicates.
   */
  public record Npc(
      String key,
      ResourceLocation entityType,
      String name,
      VillageProfile profile,
      String home,
      List<ScheduleEntry> schedule,
      Optional<String> dialogueLibrary,
      Optional<String> skin,
      Optional<String> lore) {

    public Npc {
      schedule = List.copyOf(schedule);
    }

    public Npc withSchedule(List<ScheduleEntry> newSchedule) {
      return new Npc(key, entityType, name, profile, home, newSchedule, dialogueLibrary, skin, lore);
    }
  }
}
