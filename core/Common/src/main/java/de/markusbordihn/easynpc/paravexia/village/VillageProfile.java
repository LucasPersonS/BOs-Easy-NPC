package de.markusbordihn.easynpc.paravexia.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Locale;

/**
 * Behaviour tier of a village NPC. Each tier only keeps the objectives and attributes it needs,
 * because behaviour (walking, pushing, looking at mobs, movement packets) is what costs tick time.
 */
public enum VillageProfile {
  /** Background crowd: stands, only looks at players, no collisions. */
  EXTRA,
  /** Moves between schedule spots driven by the village scheduler, no collisions. */
  RESIDENT,
  /** Merchant, quest giver or trainer: stands at its spot, keeps dialogue and trades. */
  FUNCTIONAL,
  /** Combat NPC: its configuration is left untouched. */
  GUARD;

  public static final Codec<VillageProfile> CODEC =
      Codec.STRING.comapFlatMap(VillageProfile::parse, VillageProfile::serializedName);

  public String serializedName() {
    return this.name().toLowerCase(Locale.ROOT);
  }

  public boolean isImmovable() {
    return this == EXTRA || this == FUNCTIONAL;
  }

  public boolean isManaged() {
    return this != GUARD;
  }

  public static DataResult<VillageProfile> parse(String value) {
    for (VillageProfile profile : values()) {
      if (profile.serializedName().equalsIgnoreCase(value)) {
        return DataResult.success(profile);
      }
    }
    return DataResult.error(() -> "Unknown village profile " + value);
  }
}
