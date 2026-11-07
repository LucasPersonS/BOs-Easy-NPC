package de.markusbordihn.easynpc.paravexia.village;

import de.markusbordihn.easynpc.config.Config;
import java.io.File;
import java.util.Properties;

/** Paravexia village runtime settings, stored in config/easy_npc/village.cfg. */
public class VillageConfig extends Config {

  public static final String CONFIG_FILE_NAME = "village.cfg";
  public static final String CONFIG_FILE_HEADER =
"""
Paravexia Village Configuration

enabled: Runs the village runtime (wake/sleep, schedules, spawn queue, budget guard) (default: true)

checkIntervalTicks: How often each village checks for nearby players (default: 40)
sleepDelayTicks: Ticks without a player near a village before it falls asleep (default: 200)
sleepHysteresisBlocks: Extra blocks on top of the wake radius before a village may sleep (default: 16)
villagesCheckedPerTick: Maximum villages checked for players in a single tick (default: 4)
joinUpdatesPerTick: Maximum loaded village NPCs updated per tick after loading (default: 16)

schedulerIntervalTicks: How often an awake village moves residents to their schedule spot (default: 100)
maxWalkersPerVillage: Residents allowed to walk at the same time per village (default: 4)
maxWalkingPercent: Upper limit of walking residents as percent of all residents (default: 25)
walkerTimeoutTicks: A walk is given up after this many ticks, freeing its slot (default: 1200)
arrivalDistance: Blocks from the spot that count as arrived (default: 3)
residentIdleStroll: Lets residents stroll around their spot while idle, costs packets (default: false)

spawnPerTick: Village NPCs spawned per tick at most (default: 2)
maxNpcsPerVillage: Maximum NPCs in one village (default: 40)
maxVillageNpcs: Maximum NPCs in all villages together (default: 600)
maxVillageRadius: Maximum village radius in blocks, keeps paths short (default: 48)
maxWakeRadius: Maximum wake radius in blocks (default: 160)

npcBudgetMicros: Easy NPC tick time budget per server tick in microseconds (default: 4000)
budgetWindowTicks: Ticks averaged by the budget guard (default: 100)
budgetRecoverPercent: Recovers one level when below this percent of the budget (default: 70)
budgetRecoverTicks: Ticks below the recover level before one level is recovered (default: 600)
budgetNearPlayerBlocks: Level 2 keeps villages awake only with a player this close (default: 24)

""";

  public static boolean ENABLED = true;
  public static int CHECK_INTERVAL_TICKS = 40;
  public static int SLEEP_DELAY_TICKS = 200;
  public static int SLEEP_HYSTERESIS_BLOCKS = 16;
  public static int VILLAGES_CHECKED_PER_TICK = 4;
  public static int JOIN_UPDATES_PER_TICK = 16;
  public static int SCHEDULER_INTERVAL_TICKS = 100;
  public static int MAX_WALKERS_PER_VILLAGE = 4;
  public static int MAX_WALKING_PERCENT = 25;
  public static int WALKER_TIMEOUT_TICKS = 1200;
  public static int ARRIVAL_DISTANCE = 3;
  public static boolean RESIDENT_IDLE_STROLL = false;
  public static int SPAWN_PER_TICK = 2;
  public static int MAX_NPCS_PER_VILLAGE = 40;
  public static int MAX_VILLAGE_NPCS = 600;
  public static int MAX_VILLAGE_RADIUS = 48;
  public static int MAX_WAKE_RADIUS = 160;
  public static int NPC_BUDGET_MICROS = 4000;
  public static int BUDGET_WINDOW_TICKS = 100;
  public static int BUDGET_RECOVER_PERCENT = 70;
  public static int BUDGET_RECOVER_TICKS = 600;
  public static int BUDGET_NEAR_PLAYER_BLOCKS = 24;

  private VillageConfig() {}

  public static void registerConfig() {
    registerConfigFile(CONFIG_FILE_NAME, CONFIG_FILE_HEADER);
    parseConfigFile();
  }

  public static void parseConfigFile() {
    File configFile = getConfigFile(CONFIG_FILE_NAME);
    if (configFile == null) {
      return;
    }
    Properties properties = readConfigFile(configFile);
    Properties unmodifiedProperties = (Properties) properties.clone();

    ENABLED = parseConfigValue(properties, "enabled", ENABLED);
    CHECK_INTERVAL_TICKS =
        parseConfigValue(properties, "checkIntervalTicks", CHECK_INTERVAL_TICKS, 10, 1200);
    SLEEP_DELAY_TICKS =
        parseConfigValue(properties, "sleepDelayTicks", SLEEP_DELAY_TICKS, 20, 12000);
    SLEEP_HYSTERESIS_BLOCKS =
        parseConfigValue(properties, "sleepHysteresisBlocks", SLEEP_HYSTERESIS_BLOCKS, 0, 64);
    VILLAGES_CHECKED_PER_TICK =
        parseConfigValue(properties, "villagesCheckedPerTick", VILLAGES_CHECKED_PER_TICK, 1, 64);
    JOIN_UPDATES_PER_TICK =
        parseConfigValue(properties, "joinUpdatesPerTick", JOIN_UPDATES_PER_TICK, 1, 256);
    SCHEDULER_INTERVAL_TICKS =
        parseConfigValue(properties, "schedulerIntervalTicks", SCHEDULER_INTERVAL_TICKS, 20, 6000);
    MAX_WALKERS_PER_VILLAGE =
        parseConfigValue(properties, "maxWalkersPerVillage", MAX_WALKERS_PER_VILLAGE, 1, 64);
    MAX_WALKING_PERCENT =
        parseConfigValue(properties, "maxWalkingPercent", MAX_WALKING_PERCENT, 1, 100);
    WALKER_TIMEOUT_TICKS =
        parseConfigValue(properties, "walkerTimeoutTicks", WALKER_TIMEOUT_TICKS, 100, 24000);
    ARRIVAL_DISTANCE = parseConfigValue(properties, "arrivalDistance", ARRIVAL_DISTANCE, 1, 16);
    RESIDENT_IDLE_STROLL =
        parseConfigValue(properties, "residentIdleStroll", RESIDENT_IDLE_STROLL);
    SPAWN_PER_TICK = parseConfigValue(properties, "spawnPerTick", SPAWN_PER_TICK, 1, 32);
    MAX_NPCS_PER_VILLAGE =
        parseConfigValue(properties, "maxNpcsPerVillage", MAX_NPCS_PER_VILLAGE, 1, 1000);
    MAX_VILLAGE_NPCS = parseConfigValue(properties, "maxVillageNpcs", MAX_VILLAGE_NPCS, 1, 10000);
    MAX_VILLAGE_RADIUS =
        parseConfigValue(properties, "maxVillageRadius", MAX_VILLAGE_RADIUS, 8, 128);
    MAX_WAKE_RADIUS = parseConfigValue(properties, "maxWakeRadius", MAX_WAKE_RADIUS, 16, 512);
    NPC_BUDGET_MICROS =
        parseConfigValue(properties, "npcBudgetMicros", NPC_BUDGET_MICROS, 100, 50000);
    BUDGET_WINDOW_TICKS =
        parseConfigValue(properties, "budgetWindowTicks", BUDGET_WINDOW_TICKS, 20, 1200);
    BUDGET_RECOVER_PERCENT =
        parseConfigValue(properties, "budgetRecoverPercent", BUDGET_RECOVER_PERCENT, 10, 95);
    BUDGET_RECOVER_TICKS =
        parseConfigValue(properties, "budgetRecoverTicks", BUDGET_RECOVER_TICKS, 20, 72000);
    BUDGET_NEAR_PLAYER_BLOCKS =
        parseConfigValue(properties, "budgetNearPlayerBlocks", BUDGET_NEAR_PLAYER_BLOCKS, 4, 128);

    updateConfigFileIfChanged(configFile, CONFIG_FILE_HEADER, properties, unmodifiedProperties);
  }

  public static VillageLimits limits() {
    return new VillageLimits(
        MAX_NPCS_PER_VILLAGE, MAX_VILLAGE_NPCS, MAX_VILLAGE_RADIUS, MAX_WAKE_RADIUS);
  }

  public static BudgetSettings budgetSettings() {
    return new BudgetSettings(
        NPC_BUDGET_MICROS * 1000L,
        BUDGET_WINDOW_TICKS,
        BUDGET_RECOVER_PERCENT,
        BUDGET_RECOVER_TICKS);
  }
}
