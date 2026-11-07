package de.markusbordihn.easynpc.paravexia.village;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Finds places where a village NPC can actually stand near a POI. Presets are often written for a
 * different build (or before anything was built), so a POI may sit inside a wall, under the floor
 * or in the air; NPCs sharing a POI must also not be stacked inside each other.
 */
public final class VillageSpots {

  /** Horizontal search radius around a slot origin. */
  public static final int SEARCH_RADIUS = 6;

  /** Vertical search range above and below a slot origin. */
  public static final int SEARCH_HEIGHT = 6;

  /** Distance between the slots of NPCs that share a POI. */
  public static final int SLOT_SPACING = 2;

  /** Default humanoid size, used when no entity is available yet. */
  public static final float DEFAULT_WIDTH = 0.6F;

  public static final float DEFAULT_HEIGHT = 1.95F;

  // Carpets, snow layers and similar thin blocks at the feet should not reject a spot.
  private static final double FEET_TOLERANCE = 0.07D;

  private static final List<int[]> SEARCH_OFFSETS = buildSearchOffsets(SEARCH_RADIUS, SEARCH_HEIGHT);
  private static final List<int[]> SLOT_OFFSETS = buildSlotOffsets(VillageValidator.MAX_POIS);

  private VillageSpots() {}

  /** Tells whether an NPC can stand with its feet in the given block. */
  @FunctionalInterface
  public interface StandProbe {
    boolean canStand(int x, int y, int z);
  }

  /** Where an NPC belongs: a POI and its index among the NPCs that share that POI. */
  public record Slot(String poi, int index) {}

  // ------------------------------------------------------------------ search

  /**
   * Nearest spot to the origin (vertical distance weighted double, preferring up on ties) where the
   * probe allows standing and that is not already taken by another NPC.
   */
  public static Optional<BlockPos> find(BlockPos origin, StandProbe probe, Set<BlockPos> taken) {
    for (int[] offset : SEARCH_OFFSETS) {
      int x = origin.getX() + offset[0];
      int y = origin.getY() + offset[1];
      int z = origin.getZ() + offset[2];
      if (taken.contains(new BlockPos(x, y, z))) {
        continue;
      }
      if (probe.canStand(x, y, z)) {
        return Optional.of(new BlockPos(x, y, z));
      }
    }
    return Optional.empty();
  }

  /** The point a slot starts searching from: slot 0 is the POI itself, the next ones ring around it. */
  public static BlockPos slotOrigin(BlockPos poi, int slot) {
    int[] offset = SLOT_OFFSETS.get(Math.floorMod(slot, SLOT_OFFSETS.size()));
    return poi.offset(offset[0], 0, offset[1]);
  }

  public static Optional<BlockPos> resolve(BlockPos poi, int slot, StandProbe probe, Set<BlockPos> taken) {
    return find(slotOrigin(poi, slot), probe, taken);
  }

  static List<int[]> buildSearchOffsets(int radius, int height) {
    List<int[]> offsets = new ArrayList<>();
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        if (dx * dx + dz * dz > radius * radius) {
          continue;
        }
        for (int dy = -height; dy <= height; dy++) {
          offsets.add(new int[] {dx, dy, dz});
        }
      }
    }
    // Vertical moves cost twice as much as horizontal ones: a POI inside a wall resolves to the
    // floor next to it instead of the roof above it, while a POI under the floor or in the air
    // still resolves to the same column.
    offsets.sort(
        Comparator.<int[]>comparingInt(o -> o[0] * o[0] + o[2] * o[2] + 2 * o[1] * o[1])
            // Prefer climbing out of the ground over digging down.
            .thenComparingInt(o -> -o[1])
            .thenComparingInt(o -> o[0])
            .thenComparingInt(o -> o[2]));
    return List.copyOf(offsets);
  }

  static List<int[]> buildSlotOffsets(int count) {
    List<int[]> offsets = new ArrayList<>();
    int ring = 0;
    while (offsets.size() < count) {
      List<int[]> candidates = new ArrayList<>();
      for (int i = -ring; i <= ring; i++) {
        for (int j = -ring; j <= ring; j++) {
          if (Math.max(Math.abs(i), Math.abs(j)) == ring) {
            candidates.add(new int[] {i * SLOT_SPACING, j * SLOT_SPACING});
          }
        }
      }
      candidates.sort(
          Comparator.<int[]>comparingInt(o -> o[0] * o[0] + o[1] * o[1])
              .thenComparingDouble(o -> Math.atan2(o[1], o[0])));
      offsets.addAll(candidates);
      ring++;
    }
    return List.copyOf(offsets.subList(0, count));
  }

  // ------------------------------------------------------------------ slots

  /**
   * Slot of every NPC at its home POI. Immovable NPCs come first, so their index is the same in
   * {@link #currentSlots} and they never change place when residents come and go.
   */
  public static Map<String, Slot> homeSlots(VillageSpec spec) {
    return assign(spec, npc -> npc.home());
  }

  /** Slot of every NPC for the given day time: residents at their schedule POI, others at home. */
  public static Map<String, Slot> currentSlots(VillageSpec spec, long dayTime) {
    return assign(
        spec,
        npc ->
            npc.profile() == VillageProfile.RESIDENT
                ? ScheduleResolver.resolve(npc.schedule(), npc.home(), dayTime)
                : npc.home());
  }

  private static Map<String, Slot> assign(
      VillageSpec spec, java.util.function.Function<VillageSpec.Npc, String> poiOf) {
    Map<String, Slot> slots = new LinkedHashMap<>();
    Map<String, Integer> next = new HashMap<>();
    for (int pass = 0; pass < 2; pass++) {
      for (VillageSpec.Npc npc : spec.npcs()) {
        boolean resident = npc.profile() == VillageProfile.RESIDENT;
        if ((pass == 0) == resident) {
          continue;
        }
        String poi = poiOf.apply(npc);
        int index = next.merge(poi, 1, Integer::sum) - 1;
        slots.put(npc.key(), new Slot(poi, index));
      }
    }
    return slots;
  }

  // ------------------------------------------------------------------ level probe

  /**
   * Probe against the real level. Only loaded chunks are read: an unloaded position simply does
   * not count as standable, so a lookup never loads chunks.
   */
  public static StandProbe probe(ServerLevel level, float width, float height) {
    double half = Math.max(0.1F, Math.min(width, 0.98F)) / 2.0D;
    return (x, y, z) -> {
      BlockPos feet = new BlockPos(x, y, z);
      if (!level.isLoaded(feet)) {
        return false;
      }
      BlockPos below = feet.below();
      BlockState ground = level.getBlockState(below);
      if (ground.getCollisionShape(level, below).isEmpty() || !ground.getFluidState().isEmpty()) {
        return false;
      }
      double centerX = x + 0.5D;
      double centerZ = z + 0.5D;
      AABB box =
          new AABB(
              centerX - half, y + FEET_TOLERANCE, centerZ - half, centerX + half, y + height, centerZ + half);
      return level.noCollision(box) && !level.containsAnyLiquid(box);
    };
  }
}
