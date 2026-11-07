package de.markusbordihn.easynpc.paravexia.village;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;

/** Pure validation of a village spec against the server caps; returns human readable errors. */
public final class VillageValidator {

  public static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_\\-]{1,48}");
  public static final int MAX_NAME_LENGTH = 64;
  public static final int MAX_LORE_LENGTH = 4000;
  public static final int MAX_POIS = 128;
  public static final int MAX_SCHEDULE_ENTRIES = 12;
  public static final int MAX_POI_HEIGHT_OFFSET = 32;
  public static final int DAY_LENGTH = 24000;

  private VillageValidator() {}

  /**
   * @param otherVillageNpcs NPCs already used by all other villages, for the global cap
   */
  public static List<String> validate(VillageSpec spec, VillageLimits limits, int otherVillageNpcs) {
    List<String> errors = new ArrayList<>();
    if (spec.formatVersion() != VillageSpec.FORMAT_VERSION) {
      errors.add("Unsupported format_version " + spec.formatVersion() + ", expected 1");
    }
    if (!ID_PATTERN.matcher(spec.id()).matches()) {
      errors.add("Village id must match " + ID_PATTERN.pattern());
    }
    if (spec.name().length() > MAX_NAME_LENGTH) {
      errors.add("Village name is longer than " + MAX_NAME_LENGTH);
    }
    if (spec.lore().map(String::length).orElse(0) > MAX_LORE_LENGTH) {
      errors.add("Village lore is longer than " + MAX_LORE_LENGTH);
    }
    if (spec.radius() < 4 || spec.radius() > limits.maxVillageRadius()) {
      errors.add("Radius must be between 4 and " + limits.maxVillageRadius());
    }
    if (spec.wakeRadius() < spec.radius() || spec.wakeRadius() > limits.maxWakeRadius()) {
      errors.add("Wake radius must be between the radius and " + limits.maxWakeRadius());
    }
    validatePois(spec, errors);
    validateNpcs(spec, errors);
    if (spec.npcs().size() > limits.maxNpcsPerVillage()) {
      errors.add(
          "Village has " + spec.npcs().size() + " NPCs, the limit is " + limits.maxNpcsPerVillage());
    }
    if (otherVillageNpcs + spec.npcs().size() > limits.maxVillageNpcs()) {
      errors.add(
          "All villages together would have "
              + (otherVillageNpcs + spec.npcs().size())
              + " NPCs, the limit is "
              + limits.maxVillageNpcs());
    }
    return errors;
  }

  private static void validatePois(VillageSpec spec, List<String> errors) {
    if (spec.pois().size() > MAX_POIS) {
      errors.add("Too many POIs, the limit is " + MAX_POIS);
    }
    Set<String> ids = new HashSet<>();
    for (VillageSpec.Poi poi : spec.pois()) {
      if (!ID_PATTERN.matcher(poi.id()).matches()) {
        errors.add("POI id '" + poi.id() + "' must match " + ID_PATTERN.pattern());
      }
      if (!ids.add(poi.id())) {
        errors.add("Duplicate POI id '" + poi.id() + "'");
      }
      if (!isInside(spec, poi.pos())) {
        errors.add("POI '" + poi.id() + "' is outside the village radius");
      }
    }
  }

  private static void validateNpcs(VillageSpec spec, List<String> errors) {
    Set<String> keys = new HashSet<>();
    for (VillageSpec.Npc npc : spec.npcs()) {
      String label = "NPC '" + npc.key() + "'";
      if (!ID_PATTERN.matcher(npc.key()).matches()) {
        errors.add(label + " key must match " + ID_PATTERN.pattern());
      }
      if (!keys.add(npc.key())) {
        errors.add("Duplicate NPC key '" + npc.key() + "'");
      }
      if (!"easy_npc".equals(npc.entityType().getNamespace())) {
        errors.add(label + " must use an easy_npc entity type");
      }
      if (npc.name().length() > MAX_NAME_LENGTH) {
        errors.add(label + " name is longer than " + MAX_NAME_LENGTH);
      }
      if (npc.lore().map(String::length).orElse(0) > MAX_LORE_LENGTH) {
        errors.add(label + " lore is longer than " + MAX_LORE_LENGTH);
      }
      if (spec.poi(npc.home()).isEmpty()) {
        errors.add(label + " uses unknown home POI '" + npc.home() + "'");
      }
      if (npc.schedule().size() > MAX_SCHEDULE_ENTRIES) {
        errors.add(label + " has more than " + MAX_SCHEDULE_ENTRIES + " schedule entries");
      }
      if (!npc.schedule().isEmpty() && npc.profile() != VillageProfile.RESIDENT) {
        errors.add(label + " has a schedule but only residents follow one");
      }
      Set<Integer> starts = new HashSet<>();
      for (VillageSpec.ScheduleEntry entry : npc.schedule()) {
        if (entry.start() < 0 || entry.start() >= DAY_LENGTH) {
          errors.add(label + " schedule start " + entry.start() + " must be within 0-23999");
        }
        if (!starts.add(entry.start())) {
          errors.add(label + " has two schedule entries starting at " + entry.start());
        }
        if (spec.poi(entry.poi()).isEmpty()) {
          errors.add(label + " schedule uses unknown POI '" + entry.poi() + "'");
        }
      }
    }
  }

  /**
   * Problems that do not block publishing: the field is ignored and staff get a message.
   */
  public static List<String> warnings(VillageSpec spec) {
    List<String> warnings = new ArrayList<>();
    for (VillageSpec.Npc npc : spec.npcs()) {
      npc.skin()
          .filter(skin -> VillageSkins.parse(skin).kind() == VillageSkins.Kind.INVALID)
          .ifPresent(
              skin ->
                  warnings.add(
                      "NPC '"
                          + npc.key()
                          + "' skin '"
                          + skin
                          + "' is neither a texture (namespace:textures/....png) nor a player name;"
                          + " ignored"));
    }
    return warnings;
  }

  public static boolean isInside(VillageSpec spec, BlockPos pos) {
    long dx = pos.getX() - spec.center().getX();
    long dz = pos.getZ() - spec.center().getZ();
    long radius = spec.radius();
    return dx * dx + dz * dz <= radius * radius
        && Math.abs(pos.getY() - spec.center().getY()) <= MAX_POI_HEIGHT_OFFSET;
  }
}
