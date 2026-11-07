package de.markusbordihn.easynpc.paravexia.village;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class VillageSpotsTest {

  private static final ResourceLocation HUMANOID =
      ResourceLocation.fromNamespaceAndPath("easy_npc", "humanoid");

  /** Flat ground at y=64 (stand on y=64), a solid wall block column at x=0..2,z=0 up to y=70. */
  private static final VillageSpots.StandProbe FLAT_WITH_WALL =
      (x, y, z) -> y == 64 && !(z == 0 && x >= 0 && x <= 2);

  private static VillageSpec.Npc npc(String key, VillageProfile profile, String home, List<VillageSpec.ScheduleEntry> schedule) {
    return new VillageSpec.Npc(
        key, HUMANOID, key, profile, home, schedule, Optional.empty(), Optional.empty(), Optional.empty());
  }

  private static VillageSpec spec(List<VillageSpec.Npc> npcs) {
    return new VillageSpec(
        1,
        "v",
        "V",
        VillageSpec.OVERWORLD,
        new BlockPos(0, 64, 0),
        40,
        0,
        Optional.empty(),
        List.of(
            new VillageSpec.Poi("praca", PoiType.SQUARE, new BlockPos(0, 64, 0)),
            new VillageSpec.Poi("forja", PoiType.WORK, new BlockPos(10, 64, 10))),
        npcs);
  }

  @Test
  void findsOriginWhenStandable() {
    assertEquals(
        Optional.of(new BlockPos(5, 64, 5)),
        VillageSpots.find(new BlockPos(5, 64, 5), FLAT_WITH_WALL, Set.of()));
  }

  @Test
  void climbsOutOfTheGroundBeforeMovingSideways() {
    // POI two blocks under the floor: same column, two up.
    assertEquals(
        Optional.of(new BlockPos(5, 64, 5)),
        VillageSpots.find(new BlockPos(5, 62, 5), FLAT_WITH_WALL, Set.of()));
  }

  @Test
  void leavesWallForNearestFreeColumn() {
    Optional<BlockPos> spot = VillageSpots.find(new BlockPos(1, 64, 0), FLAT_WITH_WALL, Set.of());
    assertTrue(spot.isPresent());
    BlockPos pos = spot.get();
    assertEquals(64, pos.getY());
    assertTrue(FLAT_WITH_WALL.canStand(pos.getX(), pos.getY(), pos.getZ()));
    int dx = pos.getX() - 1;
    int dz = pos.getZ();
    assertEquals(1, dx * dx + dz * dz, "nearest free column is one block away");
  }

  @Test
  void skipsTakenSpots() {
    BlockPos origin = new BlockPos(5, 64, 5);
    Optional<BlockPos> spot = VillageSpots.find(origin, FLAT_WITH_WALL, Set.of(origin));
    assertTrue(spot.isPresent());
    assertNotEquals(origin, spot.get());
  }

  @Test
  void givesUpWhenNothingFitsInRange() {
    assertTrue(VillageSpots.find(new BlockPos(0, 64, 0), (x, y, z) -> false, Set.of()).isEmpty());
  }

  @Test
  void slotsArePairwiseApart() {
    BlockPos poi = new BlockPos(0, 64, 0);
    Set<BlockPos> seen = new HashSet<>();
    for (int slot = 0; slot < 40; slot++) {
      BlockPos origin = VillageSpots.slotOrigin(poi, slot);
      assertTrue(seen.add(origin), "slot " + slot + " repeats an origin");
    }
    assertEquals(poi, VillageSpots.slotOrigin(poi, 0));
    for (BlockPos a : seen) {
      for (BlockPos b : seen) {
        if (a != b) {
          assertTrue(a.distSqr(b) >= VillageSpots.SLOT_SPACING * VillageSpots.SLOT_SPACING);
        }
      }
    }
  }

  @Test
  void resolvingSharedPoiNeverStacksNpcs() {
    BlockPos poi = new BlockPos(1, 64, 0);
    Set<BlockPos> taken = new HashSet<>();
    for (int slot = 0; slot < 12; slot++) {
      BlockPos spot = VillageSpots.resolve(poi, slot, FLAT_WITH_WALL, taken).orElseThrow();
      assertTrue(taken.add(spot), "slot " + slot + " stacked on another NPC");
    }
  }

  @Test
  void immovablesKeepTheirIndexAcrossSchedules() {
    VillageSpec spec =
        spec(
            List.of(
                npc("ana", VillageProfile.RESIDENT, "praca", List.of(new VillageSpec.ScheduleEntry(1000, "forja"))),
                npc("joao", VillageProfile.FUNCTIONAL, "praca", List.of()),
                npc("bia", VillageProfile.RESIDENT, "praca", List.of()),
                npc("rui", VillageProfile.EXTRA, "praca", List.of())));
    Map<String, VillageSpots.Slot> home = VillageSpots.homeSlots(spec);
    assertEquals(new VillageSpots.Slot("praca", 0), home.get("joao"));
    assertEquals(new VillageSpots.Slot("praca", 1), home.get("rui"));
    assertEquals(new VillageSpots.Slot("praca", 2), home.get("ana"));
    assertEquals(new VillageSpots.Slot("praca", 3), home.get("bia"));

    Map<String, VillageSpots.Slot> day = VillageSpots.currentSlots(spec, 2000);
    assertEquals(home.get("joao"), day.get("joao"));
    assertEquals(home.get("rui"), day.get("rui"));
    assertEquals(new VillageSpots.Slot("forja", 0), day.get("ana"));
    assertEquals(new VillageSpots.Slot("praca", 2), day.get("bia"));
  }

  @Test
  void shiftMovesCenterAndPoisTogether() {
    VillageSpec spec = spec(List.of());
    ResourceLocation nether = ResourceLocation.withDefaultNamespace("the_nether");
    VillageSpec moved = spec.shiftedTo(new BlockPos(100, 70, -50), nether);
    assertEquals(new BlockPos(100, 70, -50), moved.center());
    assertEquals(nether, moved.dimension());
    assertEquals(new BlockPos(100, 70, -50), moved.poi("praca").orElseThrow().pos());
    assertEquals(new BlockPos(110, 70, -40), moved.poi("forja").orElseThrow().pos());
    assertEquals(spec.radius(), moved.radius());
    assertTrue(VillageValidator.isInside(moved, moved.poi("forja").orElseThrow().pos()));
  }

  @Test
  void parsesSkinField() {
    VillageSkins.Parsed texture = VillageSkins.parse("easy_npc:textures/entity/paravexia/ancoradouro/odete.png");
    assertEquals(VillageSkins.Kind.TEXTURE, texture.kind());
    assertEquals("textures/entity/paravexia/ancoradouro/odete.png", texture.texture().getPath());
    assertEquals(VillageSkins.Kind.PLAYER, VillageSkins.parse("Notch").kind());
    assertEquals(VillageSkins.Kind.INVALID, VillageSkins.parse("easy_npc:entity/odete.png").kind());
    assertEquals(VillageSkins.Kind.INVALID, VillageSkins.parse("easy_npc:textures/odete").kind());
    assertEquals(VillageSkins.Kind.INVALID, VillageSkins.parse("https://example.com/skin.png").kind());
    assertEquals(VillageSkins.Kind.INVALID, VillageSkins.parse("no").kind());
    assertEquals(VillageSkins.Kind.INVALID, VillageSkins.parse("Bad Name").kind());
  }

  @Test
  void invalidSkinIsAWarningNotAnError() {
    VillageSpec.Npc odete =
        new VillageSpec.Npc(
            "odete", HUMANOID, "Odete", VillageProfile.FUNCTIONAL, "praca", List.of(),
            Optional.empty(), Optional.of("not a skin!"), Optional.empty());
    VillageSpec spec = spec(List.of(odete));
    assertTrue(VillageValidator.validate(spec, new VillageLimits(40, 600, 48, 160), 0).isEmpty());
    assertEquals(1, VillageValidator.warnings(spec).size());
  }
}
