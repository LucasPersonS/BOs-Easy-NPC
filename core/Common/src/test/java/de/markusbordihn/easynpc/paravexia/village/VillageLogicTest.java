package de.markusbordihn.easynpc.paravexia.village;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class VillageLogicTest {

  private static final VillageLimits LIMITS = new VillageLimits(40, 600, 48, 160);
  private static final ResourceLocation HUMANOID =
      ResourceLocation.fromNamespaceAndPath("easy_npc", "humanoid");

  private static VillageSpec.Npc npc(String key, VillageProfile profile, String home) {
    return new VillageSpec.Npc(
        key, HUMANOID, "Npc " + key, profile, home, List.of(), Optional.empty(), Optional.empty(), Optional.empty());
  }

  private static VillageSpec village(List<VillageSpec.Npc> npcs) {
    return new VillageSpec(
        1,
        "aldeia_norte",
        "Aldeia do Norte",
        VillageSpec.OVERWORLD,
        new BlockPos(100, 64, -200),
        32,
        0,
        Optional.of("Lore"),
        List.of(
            new VillageSpec.Poi("casa_1", PoiType.HOME, new BlockPos(105, 64, -195)),
            new VillageSpec.Poi("praca", PoiType.SQUARE, new BlockPos(100, 64, -200)),
            new VillageSpec.Poi("forja", PoiType.WORK, new BlockPos(90, 65, -210))),
        npcs);
  }

  // --------------------------------------------------------------- schedule

  @Test
  void scheduleUsesLatestStartedEntryAndWrapsAroundMidnight() {
    List<VillageSpec.ScheduleEntry> schedule =
        List.of(
            new VillageSpec.ScheduleEntry(1000, "forja"),
            new VillageSpec.ScheduleEntry(6000, "praca"),
            new VillageSpec.ScheduleEntry(13000, "casa_1"));
    assertEquals("casa_1", ScheduleResolver.resolve(schedule, "casa_1", 500));
    assertEquals("forja", ScheduleResolver.resolve(schedule, "casa_1", 1000));
    assertEquals("forja", ScheduleResolver.resolve(schedule, "casa_1", 5999));
    assertEquals("praca", ScheduleResolver.resolve(schedule, "casa_1", 6000));
    assertEquals("casa_1", ScheduleResolver.resolve(schedule, "casa_1", 23999));
    // Day time keeps counting across days.
    assertEquals("praca", ScheduleResolver.resolve(schedule, "casa_1", 24000L * 5 + 7000));
  }

  @Test
  void scheduleWithoutEntriesStaysHome() {
    assertEquals("casa_1", ScheduleResolver.resolve(List.of(), "casa_1", 9000));
  }

  // --------------------------------------------------------------- walkers

  @Test
  void walkerLimitUsesShareOfResidents() {
    assertEquals(0, WalkerTokens.effectiveLimit(4, 25, 0));
    assertEquals(1, WalkerTokens.effectiveLimit(4, 25, 2));
    assertEquals(3, WalkerTokens.effectiveLimit(4, 25, 10));
    assertEquals(4, WalkerTokens.effectiveLimit(4, 25, 100));
  }

  @Test
  void walkerTokensLimitAndExpire() {
    WalkerTokens tokens = new WalkerTokens();
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();
    assertTrue(tokens.tryAcquire(a, 0, 2));
    assertTrue(tokens.tryAcquire(b, 10, 2));
    assertFalse(tokens.tryAcquire(c, 20, 2));
    assertTrue(tokens.tryAcquire(a, 30, 2), "a walker keeps its own slot");
    tokens.release(a);
    assertTrue(tokens.tryAcquire(c, 40, 2));
    assertEquals(1, tokens.expire(1010, 1000));
    assertFalse(tokens.isWalking(b));
    assertTrue(tokens.isWalking(c));
  }

  // --------------------------------------------------------------- budget guard

  @Test
  void budgetGuardRaisesOneLevelPerWindowAndRecoversSlowly() {
    NpcBudgetGuard guard = new NpcBudgetGuard(new BudgetSettings(4_000_000L, 10, 70, 30));
    for (int i = 0; i < 9; i++) {
      assertEquals(0, guard.tick(9_000_000L), "no decision before a full window");
    }
    assertEquals(1, guard.tick(9_000_000L));
    for (int i = 0; i < 9; i++) {
      assertEquals(0, guard.tick(9_000_000L), "at most one raise per window");
    }
    assertEquals(1, guard.tick(9_000_000L));
    assertEquals(2, guard.level());

    // Between recover threshold and budget: hold the level.
    for (int i = 0; i < 100; i++) {
      guard.tick(3_500_000L);
    }
    assertEquals(2, guard.level());

    // Below 70 %: recover one level after 30 ticks of a full low window.
    int changes = 0;
    for (int i = 0; i < 40; i++) {
      changes += guard.tick(1_000_000L);
    }
    assertEquals(-1, changes);
    assertEquals(1, guard.level());
  }

  @Test
  void budgetGuardNeverExceedsMaxLevel() {
    NpcBudgetGuard guard = new NpcBudgetGuard(new BudgetSettings(1_000L, 5, 70, 5));
    for (int i = 0; i < 200; i++) {
      guard.tick(1_000_000L);
    }
    assertEquals(NpcBudgetGuard.MAX_LEVEL, guard.level());
  }

  // --------------------------------------------------------------- spec + validation

  @Test
  void jsonRoundTripKeepsSpecAndHash() {
    VillageSpec.Npc resident =
        npc("ferreiro", VillageProfile.RESIDENT, "casa_1")
            .withSchedule(
                List.of(
                    new VillageSpec.ScheduleEntry(1000, "forja"),
                    new VillageSpec.ScheduleEntry(12000, "casa_1")));
    VillageSpec spec = village(List.of(resident, npc("guarda", VillageProfile.GUARD, "praca")));
    String json = spec.toJson();
    VillageSpec parsed = VillageSpec.parseJson(json).getOrThrow();
    assertEquals(spec, parsed);
    assertEquals(spec.hash(), parsed.hash());
    assertEquals(64, spec.wakeRadius(), "default wake radius is radius + 32");
    assertTrue(VillageValidator.validate(parsed, LIMITS, 0).isEmpty());
  }

  @Test
  void jsonDefaultsAndIgnoredHashField() {
    String json =
        """
        {"format_version":1,"id":"vila","center":{"x":0,"y":70,"z":0},"radius":20,"hash":"ignored",
         "pois":[{"id":"casa","pos":{"x":1,"y":70,"z":1}}],
         "npcs":[{"key":"ana","entity_type":"easy_npc:humanoid","profile":"extra","home":"casa"}]}
        """;
    VillageSpec spec = VillageSpec.parseJson(json).getOrThrow();
    assertEquals(VillageSpec.OVERWORLD, spec.dimension());
    assertEquals(PoiType.CUSTOM, spec.pois().get(0).type());
    assertEquals(VillageProfile.EXTRA, spec.npcs().get(0).profile());
    assertTrue(VillageValidator.validate(spec, LIMITS, 0).isEmpty());
  }

  @Test
  void invalidJsonAndUnknownProfileFailToParse() {
    assertTrue(VillageSpec.parseJson("{not json").error().isPresent());
    String json =
        """
        {"format_version":1,"id":"vila","center":{"x":0,"y":70,"z":0},"radius":20,
         "npcs":[{"key":"ana","entity_type":"easy_npc:humanoid","profile":"dragon","home":"casa"}]}
        """;
    assertTrue(VillageSpec.parseJson(json).error().isPresent());
  }

  @Test
  void validationReportsBrokenReferencesAndBounds() {
    VillageSpec.Npc badHome = npc("ana", VillageProfile.EXTRA, "nowhere");
    VillageSpec.Npc badSchedule =
        npc("bia", VillageProfile.EXTRA, "praca")
            .withSchedule(List.of(new VillageSpec.ScheduleEntry(30000, "lua")));
    VillageSpec.Npc wrongType =
        new VillageSpec.Npc(
            "cris",
            ResourceLocation.withDefaultNamespace("villager"),
            "",
            VillageProfile.EXTRA,
            "praca",
            List.of(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    VillageSpec spec =
        village(List.of(badHome, badSchedule, wrongType, npc("ana", VillageProfile.EXTRA, "praca")))
            .withPoi(new VillageSpec.Poi("longe", PoiType.CUSTOM, new BlockPos(300, 64, -200)));
    List<String> errors = VillageValidator.validate(spec, LIMITS, 0);
    assertTrue(errors.stream().anyMatch(e -> e.contains("unknown home POI 'nowhere'")), errors::toString);
    assertTrue(errors.stream().anyMatch(e -> e.contains("only residents follow one")), errors::toString);
    assertTrue(errors.stream().anyMatch(e -> e.contains("within 0-23999")), errors::toString);
    assertTrue(errors.stream().anyMatch(e -> e.contains("unknown POI 'lua'")), errors::toString);
    assertTrue(errors.stream().anyMatch(e -> e.contains("easy_npc entity type")), errors::toString);
    assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate NPC key 'ana'")), errors::toString);
    assertTrue(errors.stream().anyMatch(e -> e.contains("'longe' is outside")), errors::toString);
  }

  @Test
  void validationEnforcesCaps() {
    List<VillageSpec.Npc> npcs = new ArrayList<>();
    for (int i = 0; i < 41; i++) {
      npcs.add(npc("npc_" + i, VillageProfile.EXTRA, "praca"));
    }
    List<String> errors = VillageValidator.validate(village(npcs), LIMITS, 0);
    assertTrue(errors.stream().anyMatch(e -> e.contains("the limit is 40")), errors::toString);

    List<String> global =
        VillageValidator.validate(village(npcs.subList(0, 10)), LIMITS, 595);
    assertTrue(global.stream().anyMatch(e -> e.contains("All villages together")), global::toString);

    VillageSpec tooBig =
        new VillageSpec(1, "x", "", VillageSpec.OVERWORLD, BlockPos.ZERO, 200, 0, Optional.empty(), List.of(), List.of());
    assertFalse(VillageValidator.validate(tooBig, LIMITS, 0).isEmpty());
  }

  @Test
  void specEditingHelpersReplaceByKey() {
    VillageSpec spec = village(List.of(npc("ana", VillageProfile.EXTRA, "praca")));
    VillageSpec changed = spec.withNpc(npc("ana", VillageProfile.FUNCTIONAL, "praca"));
    assertEquals(1, changed.npcs().size());
    assertEquals(VillageProfile.FUNCTIONAL, changed.npc("ana").orElseThrow().profile());
    assertNotEquals(spec.hash(), changed.hash());
    assertTrue(changed.withoutNpc("ana").npcs().isEmpty());
  }

  @Test
  void savedDataBindingsFollowSpecChanges() {
    VillageSpec spec =
        village(List.of(npc("ana", VillageProfile.EXTRA, "praca"), npc("bia", VillageProfile.EXTRA, "praca")));
    UUID ana = UUID.randomUUID();
    UUID bia = UUID.randomUUID();
    VillageSavedData.Village village =
        new VillageSavedData.Village(spec, java.util.Map.of()).withBinding("ana", ana).withBinding("bia", bia);
    VillageSavedData.Village updated = village.withSpec(spec.withoutNpc("bia"));
    assertEquals(Optional.of(ana), updated.binding("ana"));
    assertTrue(updated.binding("bia").isEmpty());
    assertEquals(Optional.of("ana"), updated.keyOf(ana));

    VillageSavedData data = new VillageSavedData();
    data.put(updated);
    assertEquals(Optional.of("aldeia_norte"), data.villageOf(ana));
    assertTrue(data.villageOf(bia).isEmpty());
    data.remove("aldeia_norte");
    assertTrue(data.villageOf(ana).isEmpty());
  }
}
