package de.markusbordihn.easynpc.paravexia.scene;

import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A placed scene. Purely decorative: no AI, no physics, no movement and an empty tick, so a scene
 * costs about as much as a marker on the server and only sends its spawn packet. Everything visual
 * (model, looping animation, keyframed sounds and particles) happens on the client.
 */
public class SceneEntity extends Entity {

  private static final EntityDataAccessor<String> DATA_SCENE_ID =
      SynchedEntityData.defineId(SceneEntity.class, EntityDataSerializers.STRING);
  private static final EntityDataAccessor<String> DATA_ANIMATION =
      SynchedEntityData.defineId(SceneEntity.class, EntityDataSerializers.STRING);
  private static final EntityDataAccessor<String> DATA_SKINS =
      SynchedEntityData.defineId(SceneEntity.class, EntityDataSerializers.STRING);
  private static final EntityDataAccessor<Float> DATA_HITBOX_WIDTH =
      SynchedEntityData.defineId(SceneEntity.class, EntityDataSerializers.FLOAT);
  private static final EntityDataAccessor<Float> DATA_HITBOX_HEIGHT =
      SynchedEntityData.defineId(SceneEntity.class, EntityDataSerializers.FLOAT);

  private static final String TAG_SCENE = "Scene";
  private static final String TAG_ANIMATION = "Animation";
  private static final String TAG_SKINS = "Skins";
  private static final String TAG_HITBOX_WIDTH = "HitboxWidth";
  private static final String TAG_HITBOX_HEIGHT = "HitboxHeight";

  // Parsed client/server cache of DATA_SKINS, rebuilt only when the synced value changes.
  private Map<String, ResourceLocation> skinCache = Map.of();
  private long nextLineTick;

  public SceneEntity(EntityType<? extends SceneEntity> entityType, Level level) {
    super(entityType, level);
    this.noPhysics = true;
    this.setNoGravity(true);
    this.setInvulnerable(true);
  }

  @Override
  protected void defineSynchedData(SynchedEntityData.Builder builder) {
    builder.define(DATA_SCENE_ID, "");
    builder.define(DATA_ANIMATION, SceneDefinition.DEFAULT_ANIMATION);
    builder.define(DATA_SKINS, "");
    builder.define(DATA_HITBOX_WIDTH, 1.0F);
    builder.define(DATA_HITBOX_HEIGHT, 2.0F);
  }

  /** Copies a definition onto this entity and rolls one skin per actor. Server side only. */
  public void applyDefinition(SceneDefinition definition, RandomSource random) {
    this.entityData.set(DATA_SCENE_ID, definition.id().toString());
    this.entityData.set(DATA_ANIMATION, definition.animation());
    this.entityData.set(DATA_HITBOX_WIDTH, definition.hitboxWidth());
    this.entityData.set(DATA_HITBOX_HEIGHT, definition.hitboxHeight());
    rollSkins(definition, random);
    this.refreshDimensions();
  }

  public void rollSkins(SceneDefinition definition, RandomSource random) {
    StringBuilder skins = new StringBuilder();
    for (SceneDefinition.Actor actor : definition.actors()) {
      if (!skins.isEmpty()) {
        skins.append(SceneConstants.SKIN_SEPARATOR);
      }
      ResourceLocation skin = actor.skins().get(random.nextInt(actor.skins().size()));
      skins.append(actor.slot()).append('=').append(skin);
    }
    this.entityData.set(DATA_SKINS, skins.toString());
  }

  public Optional<ResourceLocation> getSceneId() {
    String value = this.entityData.get(DATA_SCENE_ID);
    return value.isEmpty() ? Optional.empty() : Optional.ofNullable(ResourceLocation.tryParse(value));
  }

  public Optional<SceneDefinition> getDefinition() {
    return getSceneId().flatMap(SceneRegistry::get);
  }

  public String getAnimationName() {
    return this.entityData.get(DATA_ANIMATION);
  }

  /** Skin per actor slot, e.g. actor_a -> easy_npc:textures/scene_skins/ferreiro_1.png. */
  public Map<String, ResourceLocation> getActorSkins() {
    return this.skinCache;
  }

  @Override
  public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
    super.onSyncedDataUpdated(accessor);
    if (DATA_SKINS.equals(accessor)) {
      this.skinCache = SceneDefinition.parseSkinList(this.entityData.get(DATA_SKINS));
    } else if (DATA_HITBOX_WIDTH.equals(accessor) || DATA_HITBOX_HEIGHT.equals(accessor)) {
      this.refreshDimensions();
    }
  }

  @Override
  public EntityDimensions getDimensions(Pose pose) {
    return EntityDimensions.scalable(
        Mth.clamp(this.entityData.get(DATA_HITBOX_WIDTH), 0.25F, 8.0F),
        Mth.clamp(this.entityData.get(DATA_HITBOX_HEIGHT), 0.25F, 8.0F));
  }

  // Decorative: nothing to simulate. tickCount still advances (it is incremented by the level).
  @Override
  public void tick() {}

  @Override
  public InteractionResult interact(Player player, InteractionHand hand) {
    if (hand != InteractionHand.MAIN_HAND) {
      return InteractionResult.PASS;
    }
    if (this.level().isClientSide()) {
      return InteractionResult.SUCCESS;
    }
    Optional<SceneDefinition> definition = getDefinition();
    if (definition.isEmpty() || definition.get().lines().isEmpty()) {
      return InteractionResult.PASS;
    }
    // Per scene cooldown so spam-clicking can't flood chat; no per-player state is kept.
    long gameTime = this.level().getGameTime();
    if (gameTime < this.nextLineTick) {
      return InteractionResult.CONSUME;
    }
    this.nextLineTick = gameTime + SceneConstants.LINE_COOLDOWN_TICKS;
    SceneDefinition scene = definition.get();
    String line = scene.lines().get(this.random.nextInt(scene.lines().size()));
    player.displayClientMessage(Component.literal(scene.title() + ": ").append(Component.literal(line)), false);
    return InteractionResult.CONSUME;
  }

  @Override
  public boolean isPickable() {
    return true;
  }

  @Override
  public boolean isPushable() {
    return false;
  }

  @Override
  public boolean isAttackable() {
    return false;
  }

  @Override
  public boolean skipAttackInteraction(Entity attacker) {
    return true;
  }

  @Override
  public boolean hurt(DamageSource source, float amount) {
    return false;
  }

  @Override
  protected void readAdditionalSaveData(CompoundTag tag) {
    this.entityData.set(DATA_SCENE_ID, tag.getString(TAG_SCENE));
    if (tag.contains(TAG_ANIMATION)) {
      this.entityData.set(DATA_ANIMATION, tag.getString(TAG_ANIMATION));
    }
    this.entityData.set(DATA_SKINS, tag.getString(TAG_SKINS));
    if (tag.contains(TAG_HITBOX_WIDTH)) {
      this.entityData.set(DATA_HITBOX_WIDTH, tag.getFloat(TAG_HITBOX_WIDTH));
      this.entityData.set(DATA_HITBOX_HEIGHT, tag.getFloat(TAG_HITBOX_HEIGHT));
    }
    this.refreshDimensions();
  }

  @Override
  protected void addAdditionalSaveData(CompoundTag tag) {
    tag.putString(TAG_SCENE, this.entityData.get(DATA_SCENE_ID));
    tag.putString(TAG_ANIMATION, this.entityData.get(DATA_ANIMATION));
    tag.putString(TAG_SKINS, this.entityData.get(DATA_SKINS));
    tag.putFloat(TAG_HITBOX_WIDTH, this.entityData.get(DATA_HITBOX_WIDTH));
    tag.putFloat(TAG_HITBOX_HEIGHT, this.entityData.get(DATA_HITBOX_HEIGHT));
  }
}
