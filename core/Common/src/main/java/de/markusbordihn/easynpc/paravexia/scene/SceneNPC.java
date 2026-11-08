package de.markusbordihn.easynpc.paravexia.scene;

import de.markusbordihn.easynpc.api.npc.raw.PathfinderMobRaw;
import de.markusbordihn.easynpc.data.configuration.ConfigurationData;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * One actor of a Paravexia scene, as a regular Easy NPC: its skin, name, dialogs, actions and
 * presets are edited with the normal NPC editor (skin from the default list, a player name, a URL,
 * an uploaded file or a server texture). The scene animation drives the body, so poses, model
 * editing and objectives are disabled (see {@link ConfigurationData#SCENE}).
 *
 * <p>The NPC stands on its actor's pivot. {@code anchor} is that pivot in model pixels so the
 * renderer can draw the whole model around the scene origin; the primary actor also draws the
 * props and plays the keyframed sounds/particles. All actors of one placed scene share a group id.
 */
public class SceneNPC extends PathfinderMobRaw {

  public static final String ID = SceneConstants.ENTITY_ID;

  private static final EntityDataAccessor<String> DATA_SCENE_ID =
      SynchedEntityData.defineId(SceneNPC.class, EntityDataSerializers.STRING);
  private static final EntityDataAccessor<String> DATA_SLOT =
      SynchedEntityData.defineId(SceneNPC.class, EntityDataSerializers.STRING);
  private static final EntityDataAccessor<Boolean> DATA_PRIMARY =
      SynchedEntityData.defineId(SceneNPC.class, EntityDataSerializers.BOOLEAN);
  private static final EntityDataAccessor<String> DATA_ANIMATION =
      SynchedEntityData.defineId(SceneNPC.class, EntityDataSerializers.STRING);
  private static final EntityDataAccessor<Vector3f> DATA_ANCHOR =
      SynchedEntityData.defineId(SceneNPC.class, EntityDataSerializers.VECTOR3);
  private static final EntityDataAccessor<Float> DATA_HITBOX_WIDTH =
      SynchedEntityData.defineId(SceneNPC.class, EntityDataSerializers.FLOAT);
  private static final EntityDataAccessor<Float> DATA_HITBOX_HEIGHT =
      SynchedEntityData.defineId(SceneNPC.class, EntityDataSerializers.FLOAT);

  private static final String TAG_SCENE = "ParavexiaScene";
  private static final String TAG_ID = "Id";
  private static final String TAG_SLOT = "Slot";
  private static final String TAG_PRIMARY = "Primary";
  private static final String TAG_ANIMATION = "Animation";
  private static final String TAG_ANCHOR = "Anchor";
  private static final String TAG_GROUP = "Group";
  private static final String TAG_HITBOX = "Hitbox";

  // Server side only: links the actors of one placed scene for remove/rotate.
  @Nullable private UUID groupId;

  public SceneNPC(EntityType<? extends PathfinderMob> entityType, Level level) {
    super(entityType, level, SceneSkinVariant.NPC_SKIN);
  }

  public static AttributeSupplier.Builder createAttributes() {
    return Mob.createMobAttributes()
        .add(Attributes.MAX_HEALTH, 20.0D)
        .add(Attributes.FOLLOW_RANGE, 16.0D)
        .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
        .add(Attributes.MOVEMENT_SPEED, 0.0D)
        .add(Attributes.ATTACK_DAMAGE, 0.0D)
        .add(Attributes.ATTACK_KNOCKBACK, 0.0D)
        .add(Attributes.ATTACK_SPEED, 0.0D)
        .add(Attributes.ARMOR, 0.0D)
        .add(Attributes.ARMOR_TOUGHNESS, 0.0D);
  }

  @Override
  protected void defineSynchedData(SynchedEntityData.Builder builder) {
    super.defineSynchedData(builder);
    builder.define(DATA_SCENE_ID, "");
    builder.define(DATA_SLOT, "");
    builder.define(DATA_PRIMARY, true);
    builder.define(DATA_ANIMATION, SceneDefinition.DEFAULT_ANIMATION);
    builder.define(DATA_ANCHOR, new Vector3f());
    builder.define(DATA_HITBOX_WIDTH, 0.8F);
    builder.define(DATA_HITBOX_HEIGHT, 2.0F);
  }

  /**
   * Turns this NPC into {@code actor} of {@code definition}, standing on the actor pivot of a scene
   * placed at {@code origin} facing {@code yaw}. Server side only.
   */
  public void setupScene(
      SceneDefinition definition, SceneDefinition.Actor actor, Vec3 origin, float yaw, UUID group) {
    this.entityData.set(DATA_SCENE_ID, definition.id().toString());
    this.entityData.set(DATA_SLOT, actor.slot());
    this.entityData.set(DATA_PRIMARY, definition.primaryActor().slot().equals(actor.slot()));
    this.entityData.set(DATA_ANIMATION, definition.animation());
    this.entityData.set(DATA_ANCHOR, actor.offset());
    this.entityData.set(DATA_HITBOX_WIDTH, definition.hitboxWidth());
    this.entityData.set(DATA_HITBOX_HEIGHT, definition.hitboxHeight());
    this.groupId = group;
    placeAt(origin, yaw);
    this.refreshDimensions();
  }

  /** Moves the actor so the scene origin is {@code origin} and the scene faces {@code yaw}. */
  public void placeAt(Vec3 origin, float yaw) {
    Vec3 position = origin.add(actorWorldOffset(getAnchor(), yaw));
    this.moveTo(position.x, position.y, position.z, yaw, 0F);
    this.setYBodyRot(yaw);
    this.setYHeadRot(yaw);
    this.yBodyRotO = yaw;
    this.yHeadRotO = yaw;
  }

  /** Scene origin derived from this actor's position, rotation and anchor. */
  public Vec3 sceneOrigin() {
    return this.position().subtract(actorWorldOffset(getAnchor(), this.getYRot()));
  }

  /** See {@link SceneDefinition#worldOffset}. */
  public static Vec3 actorWorldOffset(Vector3f modelPixels, float yaw) {
    return SceneDefinition.worldOffset(modelPixels, yaw);
  }

  public Optional<ResourceLocation> getSceneId() {
    String value = this.entityData.get(DATA_SCENE_ID);
    return value.isEmpty() ? Optional.empty() : Optional.ofNullable(ResourceLocation.tryParse(value));
  }

  public Optional<SceneDefinition> getSceneDefinition() {
    return getSceneId().flatMap(SceneRegistry::get);
  }

  public String getActorSlot() {
    return this.entityData.get(DATA_SLOT);
  }

  public boolean isPrimaryActor() {
    return this.entityData.get(DATA_PRIMARY);
  }

  public String getAnimationName() {
    return this.entityData.get(DATA_ANIMATION);
  }

  /** Actor pivot in model pixels (Bedrock space). */
  public Vector3f getAnchor() {
    return new Vector3f(this.entityData.get(DATA_ANCHOR));
  }

  @Nullable
  public UUID getGroupId() {
    return this.groupId;
  }

  @Override
  public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
    super.onSyncedDataUpdated(accessor);
    if (DATA_HITBOX_WIDTH.equals(accessor) || DATA_HITBOX_HEIGHT.equals(accessor)) {
      this.refreshDimensions();
    }
  }

  @Override
  public EntityDimensions getDefaultDimensions(Pose pose) {
    return EntityDimensions.scalable(
        Mth.clamp(this.entityData.get(DATA_HITBOX_WIDTH), 0.25F, 4.0F),
        Mth.clamp(this.entityData.get(DATA_HITBOX_HEIGHT), 0.25F, 4.0F));
  }

  @Override
  public ConfigurationData getConfigurationData() {
    return ConfigurationData.SCENE;
  }

  // The scene animation moves the body; no look/stroll objectives by default.
  @Override
  public void registerStandardObjectives() {
    log.debug("Scene actor {} uses no standard objectives.", this);
  }

  @Override
  public boolean isPushable() {
    return false;
  }

  @Override
  protected void doPush(Entity entity) {
    // Scene actors never push others; the scene keeps its layout.
  }

  @Override
  public boolean canUseArmor() {
    return false;
  }

  @Override
  public Enum<?>[] getSkinVariantTypes() {
    return SceneSkinVariant.values();
  }

  @Override
  public Enum<?> getDefaultSkinVariantType() {
    return SceneSkinVariant.NPC_SKIN;
  }

  @Override
  public Enum<?> getSkinVariantType(String name) {
    try {
      return SceneSkinVariant.valueOf(name);
    } catch (IllegalArgumentException e) {
      return SceneSkinVariant.NPC_SKIN;
    }
  }

  @Override
  public void addAdditionalSaveData(CompoundTag compoundTag) {
    super.addAdditionalSaveData(compoundTag);
    CompoundTag scene = new CompoundTag();
    scene.putString(TAG_ID, this.entityData.get(DATA_SCENE_ID));
    scene.putString(TAG_SLOT, getActorSlot());
    scene.putBoolean(TAG_PRIMARY, isPrimaryActor());
    scene.putString(TAG_ANIMATION, getAnimationName());
    Vector3f anchor = getAnchor();
    scene.putFloat(TAG_ANCHOR + "X", anchor.x);
    scene.putFloat(TAG_ANCHOR + "Y", anchor.y);
    scene.putFloat(TAG_ANCHOR + "Z", anchor.z);
    scene.putFloat(TAG_HITBOX + "W", this.entityData.get(DATA_HITBOX_WIDTH));
    scene.putFloat(TAG_HITBOX + "H", this.entityData.get(DATA_HITBOX_HEIGHT));
    if (this.groupId != null) {
      scene.putUUID(TAG_GROUP, this.groupId);
    }
    compoundTag.put(TAG_SCENE, scene);
  }

  @Override
  public void readAdditionalSaveData(CompoundTag compoundTag) {
    super.readAdditionalSaveData(compoundTag);
    if (!compoundTag.contains(TAG_SCENE)) {
      return;
    }
    CompoundTag scene = compoundTag.getCompound(TAG_SCENE);
    this.entityData.set(DATA_SCENE_ID, scene.getString(TAG_ID));
    this.entityData.set(DATA_SLOT, scene.getString(TAG_SLOT));
    this.entityData.set(DATA_PRIMARY, !scene.contains(TAG_PRIMARY) || scene.getBoolean(TAG_PRIMARY));
    if (scene.contains(TAG_ANIMATION)) {
      this.entityData.set(DATA_ANIMATION, scene.getString(TAG_ANIMATION));
    }
    this.entityData.set(
        DATA_ANCHOR,
        new Vector3f(
            scene.getFloat(TAG_ANCHOR + "X"),
            scene.getFloat(TAG_ANCHOR + "Y"),
            scene.getFloat(TAG_ANCHOR + "Z")));
    if (scene.contains(TAG_HITBOX + "W")) {
      this.entityData.set(DATA_HITBOX_WIDTH, scene.getFloat(TAG_HITBOX + "W"));
      this.entityData.set(DATA_HITBOX_HEIGHT, scene.getFloat(TAG_HITBOX + "H"));
    }
    this.groupId = scene.hasUUID(TAG_GROUP) ? scene.getUUID(TAG_GROUP) : null;
    this.refreshDimensions();
  }
}
