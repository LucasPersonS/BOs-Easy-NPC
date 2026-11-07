package de.markusbordihn.easynpc.paravexia.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.markusbordihn.easynpc.Constants;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * All villages of the server, kept in the overworld data storage. Each entry carries its own
 * dimension. Bindings map the stable NPC key of the spec to the spawned entity UUID.
 */
public class VillageSavedData extends SavedData {

  public static final String DATA_NAME = Constants.MOD_ID + "_paravexia_villages";
  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);
  private static final String VILLAGES_TAG = "Villages";

  public static final Codec<Village> VILLAGE_CODEC =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      VillageSpec.CODEC.fieldOf("spec").forGetter(Village::spec),
                      Codec.unboundedMap(Codec.STRING, UUIDUtil.STRING_CODEC)
                          .optionalFieldOf("bindings", Map.of())
                          .forGetter(Village::bindings))
                  .apply(instance, Village::new));

  private final Map<String, Village> villages = new LinkedHashMap<>();
  private final Map<UUID, String> villageByNpc = new HashMap<>();

  public VillageSavedData() {}

  public static VillageSavedData get(MinecraftServer server) {
    return server
        .overworld()
        .getDataStorage()
        .computeIfAbsent(
            new SavedData.Factory<>(VillageSavedData::new, VillageSavedData::load, null), DATA_NAME);
  }

  public static VillageSavedData load(CompoundTag compoundTag, HolderLookup.Provider provider) {
    VillageSavedData data = new VillageSavedData();
    Tag villagesTag = compoundTag.get(VILLAGES_TAG);
    if (villagesTag != null) {
      VILLAGE_CODEC
          .listOf()
          .parse(NbtOps.INSTANCE, villagesTag)
          .resultOrPartial(error -> log.error("[Villages] Failed to load villages: {}", error))
          .ifPresent(list -> list.forEach(village -> data.putInternal(village)));
    }
    log.info("[Villages] Loaded {} villages.", data.villages.size());
    return data;
  }

  @Override
  public CompoundTag save(CompoundTag compoundTag, HolderLookup.Provider provider) {
    VILLAGE_CODEC
        .listOf()
        .encodeStart(NbtOps.INSTANCE, List.copyOf(this.villages.values()))
        .resultOrPartial(error -> log.error("[Villages] Failed to save villages: {}", error))
        .ifPresent(tag -> compoundTag.put(VILLAGES_TAG, tag));
    return compoundTag;
  }

  public Collection<Village> villages() {
    return Collections.unmodifiableCollection(this.villages.values());
  }

  public Optional<Village> village(String id) {
    return Optional.ofNullable(this.villages.get(id));
  }

  public Optional<String> villageOf(UUID npcUUID) {
    return Optional.ofNullable(this.villageByNpc.get(npcUUID));
  }

  public int npcCountExcept(String villageId) {
    int count = 0;
    for (Village village : this.villages.values()) {
      if (!village.spec().id().equals(villageId)) {
        count += village.spec().npcs().size();
      }
    }
    return count;
  }

  public void put(Village village) {
    this.putInternal(village);
    this.setDirty();
  }

  public Optional<Village> remove(String id) {
    Village removed = this.villages.remove(id);
    if (removed != null) {
      removed.bindings().values().forEach(this.villageByNpc::remove);
      this.setDirty();
    }
    return Optional.ofNullable(removed);
  }

  private void putInternal(Village village) {
    Village previous = this.villages.put(village.spec().id(), village);
    if (previous != null) {
      previous.bindings().values().forEach(this.villageByNpc::remove);
    }
    village.bindings().values().forEach(uuid -> this.villageByNpc.put(uuid, village.spec().id()));
  }

  /** A village spec plus the entities spawned or adopted for its NPC keys. */
  public record Village(VillageSpec spec, Map<String, UUID> bindings) {

    public Village {
      bindings = Map.copyOf(bindings);
    }

    public Optional<UUID> binding(String key) {
      return Optional.ofNullable(this.bindings.get(key));
    }

    public Optional<String> keyOf(UUID uuid) {
      for (Map.Entry<String, UUID> entry : this.bindings.entrySet()) {
        if (entry.getValue().equals(uuid)) {
          return Optional.of(entry.getKey());
        }
      }
      return Optional.empty();
    }

    public Village withSpec(VillageSpec newSpec) {
      Map<String, UUID> kept = new HashMap<>();
      for (Map.Entry<String, UUID> entry : this.bindings.entrySet()) {
        if (newSpec.npc(entry.getKey()).isPresent()) {
          kept.put(entry.getKey(), entry.getValue());
        }
      }
      return new Village(newSpec, kept);
    }

    public Village withBinding(String key, UUID uuid) {
      Map<String, UUID> updated = new HashMap<>(this.bindings);
      updated.put(key, uuid);
      return new Village(this.spec, updated);
    }

    public Village withoutBinding(String key) {
      Map<String, UUID> updated = new HashMap<>(this.bindings);
      updated.remove(key);
      return new Village(this.spec, updated);
    }
  }
}
