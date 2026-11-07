package de.markusbordihn.easynpc.paravexia.scene;

public final class SceneConstants {

  public static final String ENTITY_ID = "scene";
  public static final String DATA_DIRECTORY = "easy_npc_scenes";
  /** Top level bones whose name starts with this are drawn with an actor skin. */
  public static final String ACTOR_BONE_PREFIX = "actor_";
  /** Separator of the synced actor skin list ("slot=ns:path;slot=ns:path"). */
  public static final char SKIN_SEPARATOR = ';';
  public static final int LINE_COOLDOWN_TICKS = 40;
  public static final int MAX_SCENES_PER_COMMAND_RADIUS = 32;

  private SceneConstants() {}
}
