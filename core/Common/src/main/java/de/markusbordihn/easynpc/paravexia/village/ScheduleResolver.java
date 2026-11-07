package de.markusbordihn.easynpc.paravexia.village;

import java.util.List;

/** Picks the schedule spot for a Minecraft day time. */
public final class ScheduleResolver {

  private ScheduleResolver() {}

  /**
   * Returns the POI of the latest entry that started at or before the time of day. Before the
   * first entry of the day the last entry of the previous day still applies. Without a schedule
   * the home POI is used.
   */
  public static String resolve(List<VillageSpec.ScheduleEntry> schedule, String home, long dayTime) {
    if (schedule == null || schedule.isEmpty()) {
      return home;
    }
    long timeOfDay = Math.floorMod(dayTime, VillageValidator.DAY_LENGTH);
    VillageSpec.ScheduleEntry current = null;
    VillageSpec.ScheduleEntry latest = null;
    for (VillageSpec.ScheduleEntry entry : schedule) {
      if (latest == null || entry.start() > latest.start()) {
        latest = entry;
      }
      if (entry.start() <= timeOfDay && (current == null || entry.start() > current.start())) {
        current = entry;
      }
    }
    return current != null ? current.poi() : latest.poi();
  }
}
