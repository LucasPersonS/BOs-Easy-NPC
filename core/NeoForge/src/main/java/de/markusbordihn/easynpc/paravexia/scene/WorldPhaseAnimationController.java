package de.markusbordihn.easynpc.paravexia.scene;

import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationController;

/**
 * Loops locked to the world clock: every time the loop (re)starts, the controller jumps to
 * {@code gameTime mod length}. All actors of a scene (separate NPCs) and every client therefore
 * show the same frame, no matter when each one was first rendered.
 */
public class WorldPhaseAnimationController<T extends Entity & GeoAnimatable> extends AnimationController<T> {

  public WorldPhaseAnimationController(T animatable, String name, AnimationStateHandler<T> handler) {
    super(animatable, name, 0, handler);
  }

  @Override
  protected double adjustTick(double tick) {
    if (!this.shouldResetTick
        || this.currentAnimation == null
        || getAnimationState() == State.STOPPED) {
      return super.adjustTick(tick);
    }
    double length = this.currentAnimation.animation().length();
    if (!(length > 0) || Double.isInfinite(length)) {
      return super.adjustTick(tick);
    }
    double phase = Math.floorMod(this.animatable.level().getGameTime(), (long) Math.max(1, Math.round(length)));
    this.tickOffset = tick - phase;
    this.shouldResetTick = false;
    return this.animationSpeedModifier.apply(this.animatable) * phase;
  }
}
