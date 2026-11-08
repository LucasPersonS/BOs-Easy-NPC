package de.markusbordihn.easynpc.paravexia.scene;

import de.markusbordihn.easynpc.paravexia.scene.client.SceneClientEffects;
import java.util.Optional;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Scene actor with a single looping GeckoLib animation, driven entirely on the client. */
public class GeoSceneNPC extends SceneNPC implements GeoEntity {

  public static final String CONTROLLER = "scene";

  private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);
  // Cached so the per-frame animation handler does not allocate.
  private String cachedAnimationName;
  private RawAnimation cachedAnimation;
  // Client only: set by the scene model while the scene is held still beyond the freeze distance.
  private boolean animationFrozen;
  private String cachedAmbienceData = "";
  private Optional<SceneDefinition.Ambience> cachedAmbience = Optional.empty();

  public GeoSceneNPC(EntityType<? extends PathfinderMob> entityType, Level level) {
    super(entityType, level);
  }

  @Override
  public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    AnimationController<GeoSceneNPC> controller =
        new WorldPhaseAnimationController<>(this, CONTROLLER, this::animationState);
    // Keyframed sounds/particles only matter where the scene is drawn.
    if (this.level().isClientSide()) {
      controller.setSoundKeyframeHandler(SceneClientEffects::onSoundKeyframe);
      controller.setParticleKeyframeHandler(SceneClientEffects::onParticleKeyframe);
    }
    controllers.add(controller);
  }

  private PlayState animationState(AnimationState<GeoSceneNPC> state) {
    String name = getAnimationName();
    if (!name.equals(this.cachedAnimationName)) {
      this.cachedAnimationName = name;
      this.cachedAnimation = RawAnimation.begin().thenLoop(name);
    }
    return state.setAndContinue(this.cachedAnimation);
  }

  public boolean isAnimationFrozen() {
    return this.animationFrozen;
  }

  public void setAnimationFrozen(boolean frozen) {
    this.animationFrozen = frozen;
  }

  /** Decoded background loop of this scene (primary actor only), cached until the data changes. */
  public Optional<SceneDefinition.Ambience> getAmbience() {
    String data = getAmbienceData();
    if (!data.equals(this.cachedAmbienceData)) {
      this.cachedAmbienceData = data;
      this.cachedAmbience = SceneDefinition.Ambience.decode(data);
    }
    return this.cachedAmbience;
  }

  @Override
  public AnimatableInstanceCache getAnimatableInstanceCache() {
    return this.animatableCache;
  }
}
