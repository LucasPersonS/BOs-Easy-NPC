package de.markusbordihn.easynpc.paravexia.scene;

public final class SceneConstants {

  public static final String ENTITY_ID = "scene";
  public static final String DATA_DIRECTORY = "easy_npc_scenes";
  /** Top level bones whose name starts with this are scene actors drawn with the NPC skin. */
  public static final String ACTOR_BONE_PREFIX = "actor_";
  public static final int MAX_COMMAND_RADIUS = 32;
  /** Model pixels per block. */
  public static final float PIXELS_PER_BLOCK = 16F;

  private SceneConstants() {}
}
