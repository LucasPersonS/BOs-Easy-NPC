package de.markusbordihn.easynpc.paravexia.scene;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.markusbordihn.easynpc.Constants;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Server side description of a scene: a fixed "living picture" (blacksmith at the anvil, bard
 * playing) rendered from one GeckoLib model with one looping animation.
 *
 * <p>Loaded from {@code data/<namespace>/easy_npc_scenes/<path>.json}. Every actor of the scene is
 * its own Easy NPC (skin, name, dialogs set in the normal NPC editor), standing on the pivot of its
 * {@code actor_*} bone; the first actor also draws the props. The client resolves model, animation
 * and props texture from the scene id: {@code <ns>:geo/scene/<path>.geo.json},
 * {@code animations/scene/<path>.animation.json}, {@code textures/scene/<path>.png}.
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
    int weight) {

  public static final int MAX_ACTORS = 4;
  public static final float MAX_ACTOR_OFFSET = 64F;
  public static final String DEFAULT_ANIMATION = "idle";

  public SceneDefinition {
    tags = List.copyOf(tags);
    actors = List.copyOf(actors);
  }

  /**
   * One humanoid of the scene. {@code offset} is the pivot of its top level bone in model pixels
   * (Bedrock space: +x = the model's left, -z = front); the actor NPC stands there.
   */
  public record Actor(String slot, Vector3f offset) {
    public Actor {
      offset = new Vector3f(offset);
    }

    @Override
    public Vector3f offset() {
      return new Vector3f(this.offset);
    }
  }

  public Actor primaryActor() {
    return this.actors.get(0);
  }

  public Optional<Actor> actor(String slot) {
    return this.actors.stream().filter(actor -> actor.slot().equals(slot)).findFirst();
  }

  public static SceneDefinition parse(ResourceLocation id, JsonObject json) {
    String title = GsonHelper.getAsString(json, "title", id.getPath());
    String animation = GsonHelper.getAsString(json, "animation", DEFAULT_ANIMATION);
    if (animation.isBlank() || animation.length() > 64) {
      throw new IllegalArgumentException("invalid animation name");
    }

    float hitboxWidth = 0.8F;
    float hitboxHeight = 2.0F;
    if (json.has("hitbox")) {
      JsonArray hitbox = GsonHelper.getAsJsonArray(json, "hitbox");
      if (hitbox.size() != 2) {
        throw new IllegalArgumentException("hitbox must be [width, height]");
      }
      hitboxWidth = Mth.clamp(hitbox.get(0).getAsFloat(), 0.25F, 4.0F);
      hitboxHeight = Mth.clamp(hitbox.get(1).getAsFloat(), 0.25F, 4.0F);
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
    Set<String> slots = new HashSet<>();
    for (JsonElement element : GsonHelper.getAsJsonArray(json, "actors", new JsonArray())) {
      JsonObject actorJson = element.getAsJsonObject();
      String slot = GsonHelper.getAsString(actorJson, "slot");
      if (!slot.startsWith(SceneConstants.ACTOR_BONE_PREFIX) || slot.length() > 32) {
        throw new IllegalArgumentException(
            "actor slot '" + slot + "' must start with " + SceneConstants.ACTOR_BONE_PREFIX);
      }
      if (!slots.add(slot)) {
        throw new IllegalArgumentException("duplicate actor slot '" + slot + "'");
      }
      Vector3f offset = new Vector3f();
      if (actorJson.has("offset")) {
        JsonArray values = GsonHelper.getAsJsonArray(actorJson, "offset");
        if (values.size() != 3) {
          throw new IllegalArgumentException("actor '" + slot + "' offset must be [x, y, z]");
        }
        offset.set(
            Mth.clamp(values.get(0).getAsFloat(), -MAX_ACTOR_OFFSET, MAX_ACTOR_OFFSET),
            Mth.clamp(values.get(1).getAsFloat(), -MAX_ACTOR_OFFSET, MAX_ACTOR_OFFSET),
            Mth.clamp(values.get(2).getAsFloat(), -MAX_ACTOR_OFFSET, MAX_ACTOR_OFFSET));
      }
      actors.add(new Actor(slot, offset));
    }
    if (actors.isEmpty() || actors.size() > MAX_ACTORS) {
      throw new IllegalArgumentException("a scene needs 1.." + MAX_ACTORS + " actors");
    }

    int weight = Mth.clamp(GsonHelper.getAsInt(json, "weight", 10), 0, 1000);
    return new SceneDefinition(
        id, title, animation, hitboxWidth, hitboxHeight, footprintWidth, footprintDepth, tags, actors, weight);
  }

  /**
   * World offset of a model point given in Bedrock pixels, using the same transform as the
   * GeckoLib entity renderer (x negated, then rotated by 180 - yaw around Y).
   */
  public static Vec3 worldOffset(Vector3f modelPixels, float yaw) {
    Vector3f offset =
        new Vector3f(-modelPixels.x, modelPixels.y, modelPixels.z)
            .div(SceneConstants.PIXELS_PER_BLOCK)
            .rotateY((180F - yaw) * Mth.DEG_TO_RAD);
    return new Vec3(offset.x, offset.y, offset.z);
  }

  /** Accepts {@code ns:path} or a bare path in the given namespace. */
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
