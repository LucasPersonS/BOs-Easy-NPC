package de.markusbordihn.easynpc.paravexia.scene.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * One looping background bed (market chatter, village murmur). It is not positional: the manager
 * sets a target volume from the distance to the closest emitter and this sound eases towards it, so
 * walking through a village fades the loop in and out instead of panning it around.
 */
final class SceneAmbienceSound extends AbstractTickableSoundInstance {

  private static final float FADE_PER_TICK = 0.02F;

  private float targetVolume;
  private boolean fadingOut;

  SceneAmbienceSound(ResourceLocation soundId) {
    super(SoundEvent.createVariableRangeEvent(soundId), SoundSource.AMBIENT, RandomSource.create());
    this.looping = true;
    this.delay = 0;
    this.volume = 0F;
    this.relative = true;
    this.attenuation = SoundInstance.Attenuation.NONE;
    this.x = 0D;
    this.y = 0D;
    this.z = 0D;
  }

  void setTargetVolume(float volume) {
    this.targetVolume = volume;
    this.fadingOut = false;
  }

  void fadeOut() {
    this.targetVolume = 0F;
    this.fadingOut = true;
  }

  boolean isFadingOut() {
    return this.fadingOut;
  }

  @Override
  public void tick() {
    if (this.volume < this.targetVolume) {
      this.volume = Math.min(this.targetVolume, this.volume + FADE_PER_TICK);
    } else if (this.volume > this.targetVolume) {
      this.volume = Math.max(this.targetVolume, this.volume - FADE_PER_TICK);
    }
    if (this.fadingOut && this.volume <= 0.001F) {
      this.stop();
    }
  }

  // Starts at volume 0 and fades in; without this the sound engine would drop it as inaudible.
  @Override
  public boolean canStartSilent() {
    return true;
  }
}
