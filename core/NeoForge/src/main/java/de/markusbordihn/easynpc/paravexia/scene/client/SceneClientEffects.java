package de.markusbordihn.easynpc.paravexia.scene.client;

import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.paravexia.scene.GeoSceneNPC;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.animation.keyframe.event.ParticleKeyframeEvent;
import software.bernie.geckolib.animation.keyframe.event.SoundKeyframeEvent;

/**
 * Plays the sound and particle keyframes of scene animations on the client, so scenes never send
 * packets for their effects.
 *
 * <ul>
 *   <li>Sound keyframe: {@code "namespace:sound|volume|pitch"} (volume and pitch optional). Sounds
 *       from a resource pack work even when they are not registered.
 *   <li>Particle keyframe: effect = a simple particle id (e.g. {@code minecraft:flame}), locator =
 *       a bone name; the particle spawns at that bone's rest pivot, script = optional count (1-16).
 * </ul>
 */
public final class SceneClientEffects {

  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);
  private static final double MAX_EFFECT_DISTANCE_SQR = 32D * 32D;
  private static final int MAX_PARTICLES_PER_KEYFRAME = 16;

  private SceneClientEffects() {}

  public static void onSoundKeyframe(SoundKeyframeEvent<GeoSceneNPC> event) {
    GeoSceneNPC scene = event.getAnimatable();
    if (!inEffectRange(scene)) {
      return;
    }
    String[] parts = event.getKeyframeData().getSound().split("\\|");
    ResourceLocation soundId = ResourceLocation.tryParse(parts[0].trim());
    if (soundId == null) {
      return;
    }
    SoundEvent sound =
        BuiltInRegistries.SOUND_EVENT.getOptional(soundId).orElseGet(() -> SoundEvent.createVariableRangeEvent(soundId));
    float volume = parts.length > 1 ? parseFloat(parts[1], 1F) : 1F;
    float pitch = parts.length > 2 ? parseFloat(parts[2], 1F) : 1F;
    // Small pitch jitter keeps repeated loops from sounding mechanical.
    pitch *= 0.95F + scene.getRandom().nextFloat() * 0.1F;
    scene.level()
        .playLocalSound(
            scene.getX(),
            scene.getY() + 1.0D,
            scene.getZ(),
            sound,
            SoundSource.NEUTRAL,
            Mth.clamp(volume, 0F, 4F),
            Mth.clamp(pitch, 0.1F, 2F),
            false);
  }

  public static void onParticleKeyframe(ParticleKeyframeEvent<GeoSceneNPC> event) {
    GeoSceneNPC scene = event.getAnimatable();
    if (!inEffectRange(scene) || !(scene.level() instanceof ClientLevel level)) {
      return;
    }
    ResourceLocation particleId = ResourceLocation.tryParse(event.getKeyframeData().getEffect().trim());
    if (particleId == null) {
      return;
    }
    Optional<ParticleType<?>> type = BuiltInRegistries.PARTICLE_TYPE.getOptional(particleId);
    if (type.isEmpty() || !(type.get() instanceof SimpleParticleType particle)) {
      log.debug("[Scenes] Ignoring particle keyframe '{}' (only simple particles are supported)", particleId);
      return;
    }
    Vector3f offset = locatorOffset(scene, event.getKeyframeData().getLocator());
    int count = Mth.clamp(parseInt(event.getKeyframeData().script(), 1), 1, MAX_PARTICLES_PER_KEYFRAME);
    for (int i = 0; i < count; i++) {
      double spreadX = (scene.getRandom().nextDouble() - 0.5D) * 0.1D;
      double spreadZ = (scene.getRandom().nextDouble() - 0.5D) * 0.1D;
      level.addParticle(
          particle,
          scene.getX() + offset.x + spreadX,
          scene.getY() + offset.y,
          scene.getZ() + offset.z + spreadZ,
          spreadX * 0.2D,
          0.02D,
          spreadZ * 0.2D);
    }
  }

  /**
   * World offset of a bone's rest pivot. Uses the same transform as the renderer (rotation by 180
   * minus yaw around Y), so locator bones should be static points placed on the props.
   */
  private static Vector3f locatorOffset(GeoSceneNPC scene, String locator) {
    Vector3f offset = new Vector3f(0F, 1F, 0F);
    if (locator == null || locator.isBlank()) {
      return offset;
    }
    BakedGeoModel model = GeckoLibCache.getBakedModels().get(SceneModel.assets(scene).model());
    if (model == null) {
      return offset;
    }
    Optional<GeoBone> bone = model.getBone(locator.trim());
    if (bone.isEmpty()) {
      return offset;
    }
    // Bone pivots are in GeckoLib internal space (x negated); the anchor is in Bedrock space.
    Vector3f anchor = scene.getAnchor();
    offset.set(
        (bone.get().getPivotX() + anchor.x) / 16F,
        (bone.get().getPivotY() - anchor.y) / 16F,
        (bone.get().getPivotZ() - anchor.z) / 16F);
    return offset.rotateY((180F - scene.yBodyRot) * Mth.DEG_TO_RAD);
  }

  // Every actor NPC runs the same animation; only the primary plays the scene's effects.
  private static boolean inEffectRange(GeoSceneNPC scene) {
    if (!scene.isPrimaryActor()) {
      return false;
    }
    LocalPlayer player = Minecraft.getInstance().player;
    return player != null && player.distanceToSqr(scene) <= MAX_EFFECT_DISTANCE_SQR;
  }

  private static float parseFloat(String value, float fallback) {
    try {
      return Float.parseFloat(value.trim());
    } catch (NumberFormatException exception) {
      return fallback;
    }
  }

  private static int parseInt(String value, int fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException exception) {
      return fallback;
    }
  }
}
