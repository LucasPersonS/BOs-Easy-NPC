package de.markusbordihn.easynpc.paravexia.village;

import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.data.dialog.DialogDataSet;
import de.markusbordihn.easynpc.data.dialog.DialogueLibraryManager;
import de.markusbordihn.easynpc.data.display.NameVisibilityType;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.entity.easynpc.data.DialogDataCapable;
import de.markusbordihn.easynpc.entity.easynpc.data.NavigationDataCapable;
import de.markusbordihn.easynpc.handler.NameHandler;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Creates the entity for one village NPC entry and binds it to the village. */
final class VillageSpawner {

  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);

  private VillageSpawner() {}

  static Optional<UUID> spawn(
      ServerLevel level,
      VillageSavedData.Village village,
      VillageSpec.Npc npc,
      VillageSpec.Poi home) {
    Optional<EntityType<?>> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(npc.entityType());
    if (entityType.isEmpty()) {
      log.warn("[Villages] Unknown entity type {} for {}/{}.", npc.entityType(), village.spec().id(), npc.key());
      return Optional.empty();
    }
    Entity entity = entityType.get().create(level);
    if (!(entity instanceof Mob mob) || !(entity instanceof EasyNPC<?> easyNPC)) {
      if (entity != null) {
        entity.discard();
      }
      log.warn("[Villages] {} is not an Easy NPC, skipping {}/{}.", npc.entityType(), village.spec().id(), npc.key());
      return Optional.empty();
    }

    BlockPos pos = home.pos();
    mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360F, 0F);
    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null);
    if (!npc.name().isEmpty()) {
      NameHandler.setCustomName(easyNPC, npc.name(), -1, NameVisibilityType.ALWAYS);
    }
    NavigationDataCapable<?> navigationData = easyNPC.getEasyNPCNavigationData();
    if (navigationData != null) {
      navigationData.setHomePosition(pos);
    }
    npc.dialogueLibrary().ifPresent(libraryId -> linkLibrary(easyNPC, libraryId));

    // Bind before adding, so the join hook already knows the NPC belongs to the village.
    VillageSavedData data = VillageSavedData.get(level.getServer());
    UUID uuid = mob.getUUID();
    data.put(village.withBinding(npc.key(), uuid));
    if (!level.addFreshEntity(mob)) {
      data.village(village.spec().id()).ifPresent(current -> data.put(current.withoutBinding(npc.key())));
      log.warn("[Villages] Could not add {}/{} to the level.", village.spec().id(), npc.key());
      return Optional.empty();
    }
    ProfileApplier.apply(easyNPC, npc.profile());
    return Optional.of(uuid);
  }

  static boolean linkLibrary(EasyNPC<?> easyNPC, String libraryId) {
    ResourceLocation id = ResourceLocation.tryParse(libraryId.trim());
    DialogDataCapable<?> dialogData = easyNPC.getEasyNPCDialogData();
    if (id == null || dialogData == null) {
      return false;
    }
    Optional<DialogDataSet> copy = DialogueLibraryManager.copy(id);
    if (copy.isEmpty()) {
      log.warn("[Villages] Unknown dialogue library {} for {}.", id, easyNPC.getEntityUUID());
      return false;
    }
    if (!id.toString().equals(dialogData.getDialogDataSet().getLibraryId())) {
      dialogData.setDialogDataSet(copy.get());
    }
    return true;
  }
}
