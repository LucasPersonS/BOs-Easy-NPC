package de.markusbordihn.easynpc.paravexia.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Locale;

/** Kind of point of interest inside a village; only used for display and tooling. */
public enum PoiType {
  HOME,
  WORK,
  SQUARE,
  BENCH,
  CUSTOM;

  public static final Codec<PoiType> CODEC =
      Codec.STRING.comapFlatMap(PoiType::parse, PoiType::serializedName);

  public String serializedName() {
    return this.name().toLowerCase(Locale.ROOT);
  }

  public static DataResult<PoiType> parse(String value) {
    for (PoiType type : values()) {
      if (type.serializedName().equalsIgnoreCase(value)) {
        return DataResult.success(type);
      }
    }
    return DataResult.error(() -> "Unknown POI type " + value);
  }
}
