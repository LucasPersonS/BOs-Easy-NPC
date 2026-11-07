package de.markusbordihn.easynpc.paravexia.village;

import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.entity.LivingEntityManager;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.entity.easynpc.data.NavigationDataCapable;
import de.markusbordihn.easynpc.handler.PauseManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Server-side driver of all villages: wake/sleep by player proximity, resident schedules with a
 * walker limit, gradual spawning and the tick budget guard. Everything runs on the server thread
 * from the server tick, works through UUID lookups and never scans all entities.
 */
public final class VillageRuntime {

  /** Marks NoAI set by a sleeping village, so waking only undoes what the village did. */
  public static final String SLEEP_TAG = "pvx_village_sleep";

  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);
  private static final String LOG_PREFIX = "[Villages]";

  private static final Map<String, VillageState> STATES = new HashMap<>();
  private static final ArrayDeque<UUID> PENDING_JOINS = new ArrayDeque<>();
  private static final Set<UUID> PENDING_JOIN_SET = new HashSet<>();
  private static final ArrayDeque<SpawnRequest> SPAWN_QUEUE = new ArrayDeque<>();
  private static final Set<SpawnRequest> SPAWN_SET = new HashSet<>();
  private static final List<String> CHECK_ORDER = new ArrayList<>();

  private static MinecraftServer server;
  private static NpcBudgetGuard budgetGuard = new NpcBudgetGuard(VillageConfig.budgetSettings());
  private static long npcNanosThisTick;
  private static long lastNpcNanosAverage;
  private static int checkCursor;
  private static boolean checkOrderDirty = true;

  private VillageRuntime() {}

  // ------------------------------------------------------------------ lifecycle

  public static void start(MinecraftServer minecraftServer) {
    server = minecraftServer;
    reset();
    budgetGuard = new NpcBudgetGuard(VillageConfig.budgetSettings());
    VillageCommand.ensureVillageDirectory();
    VillageSavedData data = VillageSavedData.get(minecraftServer);
    for (VillageSavedData.Village village : data.villages()) {
      queueMissingSpawns(village);
    }
    log.info("{} Runtime started with {} villages.", LOG_PREFIX, data.villages().size());
  }

  public static void stop() {
    reset();
    server = null;
  }

  private static void reset() {
    STATES.clear();
    PENDING_JOINS.clear();
    PENDING_JOIN_SET.clear();
    SPAWN_QUEUE.clear();
    SPAWN_SET.clear();
    CHECK_ORDER.clear();
    checkCursor = 0;
    checkOrderDirty = true;
    npcNanosThisTick = 0;
    lastNpcNanosAverage = 0;
  }

  public static Optional<MinecraftServer> server() {
    return Optional.ofNullable(server);
  }

  public static Optional<VillageSavedData> data() {
    return server != null ? Optional.of(VillageSavedData.get(server)) : Optional.empty();
  }

  // ------------------------------------------------------------------ hooks

  /** Called by the loader specific entity tick timer for every Easy NPC entity tick. */
  public static void addNpcTickNanos(long nanos) {
    npcNanosThisTick += nanos;
  }

  /** Called when an Easy NPC joins a server level, e.g. after its chunk was loaded. */
  public static void onNpcJoin(EasyNPC<?> easyNPC) {
    if (server == null || easyNPC == null || easyNPC.isClientSideInstance()) {
      return;
    }
    UUID uuid = easyNPC.getEntityUUID();
    Mob mob = easyNPC.getMob();
    boolean villageNpc = VillageSavedData.get(server).villageOf(uuid).isPresent();
    if ((villageNpc || (mob != null && mob.getTags().contains(SLEEP_TAG)))
        && PENDING_JOIN_SET.add(uuid)) {
      PENDING_JOINS.add(uuid);
    }
  }

  public static void markVillagesChanged() {
    checkOrderDirty = true;
  }

  // ------------------------------------------------------------------ tick

  public static void tick(MinecraftServer minecraftServer) {
    if (server == null || minecraftServer != server) {
      return;
    }

    long nanos = npcNanosThisTick;
    npcNanosThisTick = 0;
    if (!VillageConfig.ENABLED) {
      return;
    }

    int levelChange = budgetGuard.tick(nanos);
    lastNpcNanosAverage = budgetGuard.averageNanos();
    if (levelChange != 0) {
      log.warn(
          "{} Budget level {} -> {} (Easy NPC average {} us per tick, budget {} us).",
          LOG_PREFIX,
          budgetGuard.level() - levelChange,
          budgetGuard.level(),
          lastNpcNanosAverage / 1000,
          VillageConfig.NPC_BUDGET_MICROS);
      for (VillageState state : STATES.values()) {
        // Re-evaluate every village on its next check with the new level.
        state.nextCheckTick = 0;
        state.levelDirty = true;
      }
    }

    long now = server.getTickCount();
    processPendingJoins();
    processSpawnQueue();
    checkVillages(now);
  }

  private static void checkVillages(long now) {
    VillageSavedData data = VillageSavedData.get(server);
    if (checkOrderDirty) {
      CHECK_ORDER.clear();
      for (VillageSavedData.Village village : data.villages()) {
        CHECK_ORDER.add(village.spec().id());
      }
      STATES.keySet().retainAll(new HashSet<>(CHECK_ORDER));
      checkOrderDirty = false;
    }
    if (CHECK_ORDER.isEmpty()) {
      return;
    }

    int checked = 0;
    for (int i = 0; i < CHECK_ORDER.size() && checked < VillageConfig.VILLAGES_CHECKED_PER_TICK; i++) {
      checkCursor = (checkCursor + 1) % CHECK_ORDER.size();
      String villageId = CHECK_ORDER.get(checkCursor);
      Optional<VillageSavedData.Village> village = data.village(villageId);
      if (village.isEmpty()) {
        continue;
      }
      VillageState state = state(villageId, now);
      if (now < state.nextCheckTick) {
        continue;
      }
      checked++;
      state.nextCheckTick = now + VillageConfig.CHECK_INTERVAL_TICKS;
      checkVillage(village.get(), state, now);
    }
  }

  private static VillageState state(String villageId, long now) {
    // Stagger first checks so villages loaded together do not all run in the same tick.
    return STATES.computeIfAbsent(
        villageId,
        id -> new VillageState(now + Math.floorMod(id.hashCode(), VillageConfig.CHECK_INTERVAL_TICKS)));
  }

  private static void checkVillage(VillageSavedData.Village village, VillageState state, long now) {
    ServerLevel level = level(village.spec());
    if (level == null) {
      return;
    }

    boolean wasAwake = state.awake;
    if (state.forced != null) {
      state.awake = state.forced;
    } else {
      int wakeRadius = effectiveWakeRadius(village.spec());
      if (hasPlayerWithin(level, village.spec().center(), wakeRadius)) {
        state.awake = true;
        state.lastPlayerTick = now;
      } else if (hasPlayerWithin(
          level, village.spec().center(), wakeRadius + VillageConfig.SLEEP_HYSTERESIS_BLOCKS)) {
        state.lastPlayerTick = now;
      } else if (state.awake && now - state.lastPlayerTick >= VillageConfig.SLEEP_DELAY_TICKS) {
        state.awake = false;
      }
    }

    if (wasAwake != state.awake || state.levelDirty) {
      state.levelDirty = false;
      if (!state.awake) {
        state.walkers.clear();
      } else {
        requeueDeferredSpawns(village);
      }
      applyStateToVillage(village, state, level);
      log.debug("{} Village {} is now {}.", LOG_PREFIX, village.spec().id(), state.awake ? "awake" : "asleep");
    }

    if (state.awake && now >= state.nextScheduleTick) {
      state.nextScheduleTick = now + VillageConfig.SCHEDULER_INTERVAL_TICKS;
      runScheduler(village, state, level, now);
    }
  }

  private static int effectiveWakeRadius(VillageSpec spec) {
    if (budgetGuard.level() >= 2) {
      return Math.min(spec.wakeRadius(), spec.radius() + VillageConfig.BUDGET_NEAR_PLAYER_BLOCKS);
    }
    return spec.wakeRadius();
  }

  private static boolean hasPlayerWithin(ServerLevel level, BlockPos center, int radius) {
    double maxDistance = (double) radius * radius;
    double x = center.getX() + 0.5;
    double y = center.getY() + 0.5;
    double z = center.getZ() + 0.5;
    for (ServerPlayer player : level.players()) {
      if (!player.isSpectator() && player.distanceToSqr(x, y, z) <= maxDistance) {
        return true;
      }
    }
    return false;
  }

  private static void applyStateToVillage(
      VillageSavedData.Village village, VillageState state, ServerLevel level) {
    for (VillageSpec.Npc npc : village.spec().npcs()) {
      village
          .binding(npc.key())
          .map(uuid -> LivingEntityManager.getServerEasyNPCEntityByUUID(uuid, level))
          .ifPresent(easyNPC -> applySleepState(easyNPC, shouldSleep(state, npc.profile())));
    }
  }

  private static boolean shouldSleep(VillageState state, VillageProfile profile) {
    return !state.awake || (budgetGuard.level() >= 3 && profile == VillageProfile.EXTRA);
  }

  /** Sleeps or wakes an NPC without touching its Easy NPC data, so no NPC file save is caused. */
  static void applySleepState(EasyNPC<?> easyNPC, boolean sleep) {
    Mob mob = easyNPC.getMob();
    if (mob == null || mob.isRemoved()) {
      return;
    }
    boolean sleptByVillage = mob.getTags().contains(SLEEP_TAG);
    if (sleep) {
      // A NoAI that the village did not set is a manual or Easy NPC pause: leave it alone.
      if (!sleptByVillage && !mob.isNoAi()) {
        mob.getNavigation().stop();
        mob.setNoAi(true);
        mob.addTag(SLEEP_TAG);
      }
    } else if (sleptByVillage) {
      mob.removeTag(SLEEP_TAG);
      if (!PauseManager.isPaused(easyNPC)) {
        mob.setNoAi(false);
      }
    }
  }

  // ------------------------------------------------------------------ scheduler

  private static void runScheduler(
      VillageSavedData.Village village, VillageState state, ServerLevel level, long now) {
    int residents = (int) village.spec().count(VillageProfile.RESIDENT);
    if (residents == 0) {
      return;
    }
    int limit =
        budgetGuard.level() >= 1
            ? 1
            : WalkerTokens.effectiveLimit(
                VillageConfig.MAX_WALKERS_PER_VILLAGE, VillageConfig.MAX_WALKING_PERCENT, residents);
    state.walkers.expire(now, VillageConfig.WALKER_TIMEOUT_TICKS);
    long dayTime = level.getDayTime();
    double arrival = (double) VillageConfig.ARRIVAL_DISTANCE * VillageConfig.ARRIVAL_DISTANCE;

    for (VillageSpec.Npc npc : village.spec().npcs()) {
      if (npc.profile() != VillageProfile.RESIDENT) {
        continue;
      }
      Optional<UUID> uuid = village.binding(npc.key());
      if (uuid.isEmpty()) {
        continue;
      }
      EasyNPC<?> easyNPC = LivingEntityManager.getServerEasyNPCEntityByUUID(uuid.get(), level);
      Mob mob = easyNPC != null ? easyNPC.getMob() : null;
      NavigationDataCapable<?> navigationData = easyNPC != null ? easyNPC.getEasyNPCNavigationData() : null;
      if (mob == null || navigationData == null || mob.isNoAi()) {
        state.walkers.release(uuid.get());
        continue;
      }

      String poiId = ScheduleResolver.resolve(npc.schedule(), npc.home(), dayTime);
      Optional<VillageSpec.Poi> poi = village.spec().poi(poiId);
      if (poi.isEmpty()) {
        continue;
      }
      BlockPos target = poi.get().pos();
      boolean arrived = horizontalDistanceSqr(mob, target) <= arrival;
      if (arrived) {
        state.walkers.release(uuid.get());
        continue;
      }
      if (state.walkers.isWalking(uuid.get())) {
        continue;
      }
      // Changing the home position starts the move-back-to-home goal; it is only done when a
      // walker slot is free, so a whole village never starts path finding at the same time.
      if (state.walkers.tryAcquire(uuid.get(), now, limit)
          && !target.equals(navigationData.getHomePosition())) {
        navigationData.setHomePosition(target);
      }
    }
  }

  private static double horizontalDistanceSqr(Mob mob, BlockPos target) {
    double dx = mob.getX() - (target.getX() + 0.5);
    double dz = mob.getZ() - (target.getZ() + 0.5);
    return dx * dx + dz * dz;
  }

  // ------------------------------------------------------------------ joins

  private static void processPendingJoins() {
    int processed = 0;
    while (!PENDING_JOINS.isEmpty() && processed < VillageConfig.JOIN_UPDATES_PER_TICK) {
      UUID uuid = PENDING_JOINS.poll();
      PENDING_JOIN_SET.remove(uuid);
      processed++;
      processJoin(uuid);
    }
  }

  private static void processJoin(UUID uuid) {
    VillageSavedData data = VillageSavedData.get(server);
    EasyNPC<?> easyNPC = LivingEntityManager.getServerEasyNPCEntityByUUID(uuid);
    if (easyNPC == null || easyNPC.getMob() == null || easyNPC.getMob().isRemoved()) {
      return;
    }
    Optional<VillageSavedData.Village> village = data.villageOf(uuid).flatMap(data::village);
    if (village.isEmpty()) {
      // The village was removed while the NPC was unloaded: undo the village sleep.
      applySleepState(easyNPC, false);
      return;
    }
    Optional<VillageSpec.Npc> npc = village.get().keyOf(uuid).flatMap(village.get().spec()::npc);
    if (npc.isEmpty()) {
      return;
    }
    ProfileApplier.apply(easyNPC, npc.get().profile());
    VillageState state = state(village.get().spec().id(), server.getTickCount());
    applySleepState(easyNPC, shouldSleep(state, npc.get().profile()));
  }

  // ------------------------------------------------------------------ spawning

  static void queueMissingSpawns(VillageSavedData.Village village) {
    for (VillageSpec.Npc npc : village.spec().npcs()) {
      if (village.binding(npc.key()).isEmpty()) {
        SpawnRequest request = new SpawnRequest(village.spec().id(), npc.key());
        if (SPAWN_SET.add(request)) {
          SPAWN_QUEUE.add(request);
        }
      }
    }
  }

  private static void requeueDeferredSpawns(VillageSavedData.Village village) {
    VillageState state = STATES.get(village.spec().id());
    if (state == null || state.deferredSpawns.isEmpty()) {
      return;
    }
    for (String key : state.deferredSpawns) {
      SpawnRequest request = new SpawnRequest(village.spec().id(), key);
      if (SPAWN_SET.add(request)) {
        SPAWN_QUEUE.add(request);
      }
    }
    state.deferredSpawns.clear();
  }

  public static int queuedSpawns() {
    return SPAWN_QUEUE.size();
  }

  private static void processSpawnQueue() {
    int spawned = 0;
    int examined = 0;
    int maxExamined = VillageConfig.SPAWN_PER_TICK * 4;
    VillageSavedData data = VillageSavedData.get(server);
    while (!SPAWN_QUEUE.isEmpty() && spawned < VillageConfig.SPAWN_PER_TICK && examined < maxExamined) {
      SpawnRequest request = SPAWN_QUEUE.poll();
      SPAWN_SET.remove(request);
      examined++;
      Optional<VillageSavedData.Village> village = data.village(request.villageId());
      if (village.isEmpty() || village.get().binding(request.key()).isPresent()) {
        continue;
      }
      Optional<VillageSpec.Npc> npc = village.get().spec().npc(request.key());
      ServerLevel level = level(village.get().spec());
      if (npc.isEmpty() || level == null) {
        continue;
      }
      Optional<VillageSpec.Poi> home = village.get().spec().poi(npc.get().home());
      if (home.isEmpty()) {
        continue;
      }
      // Never load chunks for a spawn: wait until a player brings the village in.
      if (!level.isPositionEntityTicking(home.get().pos())) {
        STATES
            .computeIfAbsent(request.villageId(), id -> new VillageState(0))
            .deferredSpawns
            .add(request.key());
        continue;
      }
      if (VillageSpawner.spawn(level, village.get(), npc.get(), home.get()).isPresent()) {
        spawned++;
      }
    }
  }

  // ------------------------------------------------------------------ commands / api

  public static void force(String villageId, Boolean awake) {
    if (server == null) {
      return;
    }
    VillageState state = state(villageId, server.getTickCount());
    state.forced = awake;
    state.nextCheckTick = 0;
  }

  public static Map<String, Object> status(String villageId) {
    Map<String, Object> status = new java.util.LinkedHashMap<>();
    VillageState state = STATES.get(villageId);
    status.put("awake", state != null && state.awake);
    status.put("forced", state != null && state.forced != null ? state.forced : "no");
    status.put("walkers", state != null ? state.walkers.size() : 0);
    status.put("deferredSpawns", state != null ? state.deferredSpawns.size() : 0);
    return status;
  }

  public static int budgetLevel() {
    return budgetGuard.level();
  }

  public static long lastNpcMicrosPerTick() {
    return lastNpcNanosAverage / 1000;
  }

  static void onVillageRemoved(VillageSavedData.Village village) {
    ServerLevel level = level(village.spec());
    if (level != null) {
      for (UUID uuid : village.bindings().values()) {
        EasyNPC<?> easyNPC = LivingEntityManager.getServerEasyNPCEntityByUUID(uuid, level);
        if (easyNPC != null) {
          applySleepState(easyNPC, false);
        }
      }
    }
    STATES.remove(village.spec().id());
    SPAWN_QUEUE.removeIf(request -> request.villageId().equals(village.spec().id()));
    SPAWN_SET.removeIf(request -> request.villageId().equals(village.spec().id()));
    markVillagesChanged();
  }

  static void onVillageUpdated(VillageSavedData.Village village) {
    VillageState state = STATES.get(village.spec().id());
    if (state != null) {
      state.levelDirty = true;
      state.nextCheckTick = 0;
      state.nextScheduleTick = 0;
    }
    queueMissingSpawns(village);
    markVillagesChanged();
  }

  static ServerLevel level(VillageSpec spec) {
    if (server == null) {
      return null;
    }
    ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, spec.dimension());
    return server.getLevel(key);
  }

  record SpawnRequest(String villageId, String key) {}

  private static final class VillageState {
    private final WalkerTokens walkers = new WalkerTokens();
    private final Set<String> deferredSpawns = new LinkedHashSet<>();
    private boolean awake;
    private boolean levelDirty;
    private Boolean forced;
    private long lastPlayerTick;
    private long nextCheckTick;
    private long nextScheduleTick;

    private VillageState(long firstCheckTick) {
      this.nextCheckTick = firstCheckTick;
      // The first check always applies the state, NPCs may still be awake from before a restart.
      this.levelDirty = true;
    }
  }
}
