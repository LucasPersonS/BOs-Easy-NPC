package de.markusbordihn.easynpc.paravexia.scene.client;

import de.markusbordihn.easynpc.paravexia.scene.GeoSceneNPC;
import de.markusbordihn.easynpc.paravexia.scene.SceneClientConfig;
import de.markusbordihn.easynpc.paravexia.scene.SceneDefinition;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/**
 * Plays the background loops of scenes on the client. Emitters are the primary actors of loaded
 * scenes whose definition has an {@code ambience}; they are tracked through join/leave events, so
 * nothing scans the level. Every {@link #UPDATE_INTERVAL_TICKS} ticks each sound id gets one loop,
 * driven by the closest emitter in range: two markets next to each other never stack the same loop.
 */
public final class SceneAmbience {

  private static final int UPDATE_INTERVAL_TICKS = 10;

  private static final Set<GeoSceneNPC> EMITTERS = Collections.newSetFromMap(new WeakHashMap<>());
  private static final Map<ResourceLocation, SceneAmbienceSound> PLAYING = new HashMap<>();
  // Reused every update so the tick handler does not allocate.
  private static final Map<ResourceLocation, Float> TARGETS = new HashMap<>();

  private static int ticks;

  private SceneAmbience() {}

  public static void onEntityJoin(EntityJoinLevelEvent event) {
    if (event.getLevel().isClientSide() && event.getEntity() instanceof GeoSceneNPC scene) {
      EMITTERS.add(scene);
    }
  }

  public static void onEntityLeave(EntityLeaveLevelEvent event) {
    if (event.getLevel().isClientSide() && event.getEntity() instanceof GeoSceneNPC scene) {
      EMITTERS.remove(scene);
    }
  }

  public static void onClientTick(ClientTickEvent.Post event) {
    if (++ticks % UPDATE_INTERVAL_TICKS != 0) {
      return;
    }
    Minecraft minecraft = Minecraft.getInstance();
    LocalPlayer player = minecraft.player;
    if (player == null || minecraft.level == null || !SceneClientConfig.AMBIENCE_ENABLED) {
      fadeOutAll();
      if (player == null || minecraft.level == null) {
        EMITTERS.clear();
      }
      return;
    }

    float configVolume = SceneClientConfig.AMBIENCE_VOLUME_PERCENT / 100F;
    TARGETS.clear();
    Iterator<GeoSceneNPC> iterator = EMITTERS.iterator();
    while (iterator.hasNext()) {
      GeoSceneNPC scene = iterator.next();
      if (scene.isRemoved() || scene.level() != minecraft.level) {
        iterator.remove();
        continue;
      }
      SceneDefinition.Ambience ambience = scene.getAmbience().orElse(null);
      if (ambience == null) {
        continue;
      }
      double distance = Math.sqrt(player.distanceToSqr(scene));
      if (distance >= ambience.radius()) {
        continue;
      }
      // Full volume in the inner half of the radius, smooth fade to silence at the edge.
      float edge = (float) Mth.clamp((ambience.radius() - distance) / (ambience.radius() * 0.5D), 0D, 1D);
      float volume = ambience.volume() * configVolume * edge * edge * (3F - 2F * edge);
      TARGETS.merge(ambience.sound(), volume, Math::max);
    }

    for (Map.Entry<ResourceLocation, Float> target : TARGETS.entrySet()) {
      SceneAmbienceSound sound = PLAYING.get(target.getKey());
      if (sound == null || sound.isStopped() || !minecraft.getSoundManager().isActive(sound)) {
        if (sound != null && !sound.isStopped() && !sound.isFadingOut()) {
          // Still queued in the sound engine (it was just started); keep driving it.
          sound.setTargetVolume(target.getValue());
          continue;
        }
        sound = new SceneAmbienceSound(target.getKey());
        PLAYING.put(target.getKey(), sound);
        minecraft.getSoundManager().play(sound);
      }
      sound.setTargetVolume(target.getValue());
    }

    Iterator<Map.Entry<ResourceLocation, SceneAmbienceSound>> playing = PLAYING.entrySet().iterator();
    while (playing.hasNext()) {
      Map.Entry<ResourceLocation, SceneAmbienceSound> entry = playing.next();
      if (entry.getValue().isStopped()) {
        playing.remove();
      } else if (!TARGETS.containsKey(entry.getKey())) {
        entry.getValue().fadeOut();
      }
    }
  }

  private static void fadeOutAll() {
    PLAYING.values().forEach(SceneAmbienceSound::fadeOut);
    PLAYING.values().removeIf(SceneAmbienceSound::isStopped);
  }
}
