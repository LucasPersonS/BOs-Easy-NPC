package de.markusbordihn.easynpc.paravexia.village;

import de.markusbordihn.easynpc.data.attribute.EntityAttributes;
import de.markusbordihn.easynpc.data.attribute.InteractionAttributeType;
import de.markusbordihn.easynpc.data.attribute.MovementAttributeType;
import de.markusbordihn.easynpc.data.objective.ObjectiveDataEntry;
import de.markusbordihn.easynpc.data.objective.ObjectiveType;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.entity.easynpc.data.AttributeDataCapable;
import de.markusbordihn.easynpc.entity.easynpc.data.ObjectiveDataCapable;
import de.markusbordihn.easynpc.handler.AttributeHandler;
import java.util.EnumSet;
import java.util.Set;

/**
 * Applies a {@link VillageProfile} through the regular Easy NPC objective and attribute APIs. Every
 * change is only made when the current value differs, so applying the same profile again neither
 * rebuilds goals nor marks the NPC data as changed.
 */
public final class ProfileApplier {

  /** Objectives that make an NPC look around or wander on its own; villages drive movement. */
  private static final Set<ObjectiveType> AUTONOMOUS_OBJECTIVES =
      EnumSet.of(
          ObjectiveType.LOOK_AT_MOB,
          ObjectiveType.LOOK_AT_ANIMAL,
          ObjectiveType.LOOK_RANDOM_AROUND,
          ObjectiveType.RANDOM_STROLL,
          ObjectiveType.RANDOM_STROLL_IN_VILLAGE,
          ObjectiveType.WATER_AVOIDING_RANDOM_STROLL,
          ObjectiveType.MOVE_THROUGH_VILLAGE,
          ObjectiveType.MOVE_BACK_TO_VILLAGE);

  private ProfileApplier() {}

  /**
   * @return true when anything was changed
   */
  public static boolean apply(EasyNPC<?> easyNPC, VillageProfile profile) {
    if (easyNPC == null || profile == null || !profile.isManaged() || easyNPC.isClientSideInstance()) {
      return false;
    }

    boolean changed = applyObjectives(easyNPC, profile);
    changed |= applyAttributes(easyNPC, profile);
    return changed;
  }

  private static boolean applyObjectives(EasyNPC<?> easyNPC, VillageProfile profile) {
    ObjectiveDataCapable<?> objectiveData = easyNPC.getEasyNPCObjectiveData();
    if (objectiveData == null) {
      return false;
    }

    boolean resident = profile == VillageProfile.RESIDENT;
    boolean idleStroll = resident && VillageConfig.RESIDENT_IDLE_STROLL;
    boolean changed = false;
    for (ObjectiveType objectiveType : AUTONOMOUS_OBJECTIVES) {
      changed |= remove(objectiveData, objectiveType);
    }
    changed |= ensure(objectiveData, ObjectiveType.LOOK_AT_RESET);
    changed |= ensure(objectiveData, ObjectiveType.LOOK_AT_PLAYER);
    changed |=
        resident
            ? ensure(objectiveData, ObjectiveType.MOVE_BACK_TO_HOME)
            : remove(objectiveData, ObjectiveType.MOVE_BACK_TO_HOME);
    changed |=
        idleStroll
            ? ensure(objectiveData, ObjectiveType.RANDOM_STROLL_AROUND_HOME)
            : remove(objectiveData, ObjectiveType.RANDOM_STROLL_AROUND_HOME);
    return changed;
  }

  private static boolean ensure(ObjectiveDataCapable<?> objectiveData, ObjectiveType type) {
    return !objectiveData.hasObjective(type)
        && objectiveData.addOrUpdateCustomObjective(new ObjectiveDataEntry(type));
  }

  private static boolean remove(ObjectiveDataCapable<?> objectiveData, ObjectiveType type) {
    return objectiveData.hasObjective(type) && objectiveData.removeCustomObjective(type);
  }

  private static boolean applyAttributes(EasyNPC<?> easyNPC, VillageProfile profile) {
    AttributeDataCapable<?> attributeData = easyNPC.getEasyNPCAttributeData();
    EntityAttributes attributes = attributeData != null ? attributeData.getEntityAttributes() : null;
    if (attributes == null) {
      return false;
    }

    boolean changed = false;
    // Village NPCs never push or get pushed: crowds would otherwise run collision checks.
    if (attributes.getInteractionAttributes().isPushable()) {
      changed |=
          AttributeHandler.setInteractionAttribute(
              easyNPC, InteractionAttributeType.IS_PUSHABLE, false);
    }
    if (attributeData.getEntityAttributes().getInteractionAttributes().pushEntities()) {
      changed |=
          AttributeHandler.setInteractionAttribute(
              easyNPC, InteractionAttributeType.PUSH_ENTITIES, false);
    }
    boolean immovable = profile.isImmovable();
    if (attributeData.getEntityAttributes().getMovementAttributes().isImmovable() != immovable) {
      changed |=
          AttributeHandler.setMovementAttribute(
              easyNPC, MovementAttributeType.IS_IMMOVABLE, immovable);
    }
    return changed;
  }
}
