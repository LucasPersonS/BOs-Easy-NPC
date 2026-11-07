package de.markusbordihn.easynpc.paravexia.scene;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.markusbordihn.easynpc.Constants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;

/**
 * Server side description of a scene: a fixed "living picture" (blacksmith at the anvil, bard
 * playing) rendered from one GeckoLib model with one looping animation.
 *
 * <p>Loaded from {@code data/<namespace>/easy_npc_scenes/<path>.json}. The client resolves the
 * model, animation and props texture by convention from the scene id:
 * {@code <namespace>:geo/scene/<path>.geo.json}, {@code animations/scene/<path>.animation.json} and
 * {@code textures/scene/<path>.png}, so only the values the client cannot derive (animation name,
 * actor skins, hitbox) are synced on the entity.
 */
public record SceneDefinition(
    ResourceLocation id,
    String title,
    String animation,
    float hitboxWidth,
    float hitboxHeight,
    int footprintWidth,
    int footprintDepth,
    List<String> tags,
    List<Actor> actors,
    List<String> lines,
    int weight) {

  public static final int MAX_ACTORS = 4;
  public static final int MAX_SKINS_PER_ACTOR = 32;
  public static final int MAX_LINES = 32;
  public static final int MAX_LINE_LENGTH = 200;
  public static final String DEFAULT_ANIMATION = "idle";

  public SceneDefinition {
    tags = List.copyOf(tags);
    actors = List.copyOf(actors);
    lines = List.copyOf(lines);
  }

  /** One humanoid in the scene. Bones under the top level bone {@code slot} use the actor skin. */
  public record Actor(String slot, List<ResourceLocation> skins) {
    public Actor {
      skins = List.copyOf(skins);
    }
  }

  public static SceneDefinition parse(ResourceLocation id, JsonObject json) {
    String title = GsonHelper.getAsString(json, "title", id.getPath());
    String animation = GsonHelper.getAsString(json, "animation", DEFAULT_ANIMATION);
    if (animation.isBlank() || animation.length() > 64) {
      throw new IllegalArgumentException("invalid animation name");
    }

    float hitboxWidth = 1.0F;
    float hitboxHeight = 2.0F;
    if (json.has("hitbox")) {
      JsonArray hitbox = GsonHelper.getAsJsonArray(json, "hitbox");
      if (hitbox.size() != 2) {
        throw new IllegalArgumentException("hitbox must be [width, height]");
      }
      hitboxWidth = Mth.clamp(hitbox.get(0).getAsFloat(), 0.25F, 8.0F);
      hitboxHeight = Mth.clamp(hitbox.get(1).getAsFloat(), 0.25F, 8.0F);
    }

    int footprintWidth = 1;
    int footprintDepth = 1;
    if (json.has("footprint")) {
      JsonArray footprint = GsonHelper.getAsJsonArray(json, "footprint");
      if (footprint.size() != 2) {
        throw new IllegalArgumentException("footprint must be [width, depth]");
      }
      footprintWidth = Mth.clamp(footprint.get(0).getAsInt(), 1, 16);
      footprintDepth = Mth.clamp(footprint.get(1).getAsInt(), 1, 16);
    }

    List<String> tags = new ArrayList<>();
    for (JsonElement tag : GsonHelper.getAsJsonArray(json, "tags", new JsonArray())) {
      tags.add(tag.getAsString().toLowerCase(Locale.ROOT));
    }

    List<Actor> actors = new ArrayList<>();
    for (JsonElement element : GsonHelper.getAsJsonArray(json, "actors", new JsonArray())) {
      JsonObject actorJson = element.getAsJsonObject();
      String slot = GsonHelper.getAsString(actorJson, "slot");
      if (!slot.startsWith(SceneConstants.ACTOR_BONE_PREFIX)) {
        throw new IllegalArgumentException(
            "actor slot '" + slot + "' must start with " + SceneConstants.ACTOR_BONE_PREFIX);
      }
      List<ResourceLocation> skins = new ArrayList<>();
      for (JsonElement skin : GsonHelper.getAsJsonArray(actorJson, "skins", new JsonArray())) {
        skins.add(parseLocation(skin.getAsString(), id.getNamespace()));
      }
      if (skins.isEmpty() || skins.size() > MAX_SKINS_PER_ACTOR) {
        throw new IllegalArgumentException("actor '" + slot + "' needs 1.." + MAX_SKINS_PER_ACTOR + " skins");
      }
      actors.add(new Actor(slot, skins));
    }
    if (actors.size() > MAX_ACTORS) {
      throw new IllegalArgumentException("at most " + MAX_ACTORS + " actors per scene");
    }

    List<String> lines = new ArrayList<>();
    for (JsonElement line : GsonHelper.getAsJsonArray(json, "lines", new JsonArray())) {
      String text = line.getAsString();
      if (!text.isBlank()) {
        lines.add(text.length() > MAX_LINE_LENGTH ? text.substring(0, MAX_LINE_LENGTH) : text);
      }
    }
    if (lines.size() > MAX_LINES) {
      lines = lines.subList(0, MAX_LINES);
    }

    int weight = Mth.clamp(GsonHelper.getAsInt(json, "weight", 10), 0, 1000);
    return new SceneDefinition(
        id,
        title,
        animation,
        hitboxWidth,
        hitboxHeight,
        footprintWidth,
        footprintDepth,
        tags,
        actors,
        lines,
        weight);
  }

  /** Parses the synced actor skin list "slot=ns:path;slot=ns:path", skipping broken entries. */
  public static Map<String, ResourceLocation> parseSkinList(String value) {
    if (value == null || value.isEmpty()) {
      return Map.of();
    }
    Map<String, ResourceLocation> skins = new LinkedHashMap<>();
    for (String entry : value.split(String.valueOf(SceneConstants.SKIN_SEPARATOR))) {
      int split = entry.indexOf('=');
      if (split <= 0) {
        continue;
      }
      ResourceLocation skin = ResourceLocation.tryParse(entry.substring(split + 1));
      if (skin != null) {
        skins.put(entry.substring(0, split), skin);
      }
    }
    return Collections.unmodifiableMap(skins);
  }

  /** Accepts {@code ns:path} or a bare path in the scene's namespace. */
  static ResourceLocation parseLocation(String value, String defaultNamespace) {
    ResourceLocation location =
        value.indexOf(':') >= 0
            ? ResourceLocation.tryParse(value)
            : ResourceLocation.tryBuild(
                defaultNamespace == null ? Constants.MOD_ID : defaultNamespace, value);
    if (location == null) {
      throw new IllegalArgumentException("invalid resource location '" + value + "'");
    }
    return location;
  }
}
