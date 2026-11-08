package de.markusbordihn.easynpc.paravexia.scene.client;

import de.markusbordihn.easynpc.paravexia.scene.SceneConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Decides which bones draw their cubes in each render pass of a scene actor NPC.
 *
 * <ul>
 *   <li>Skin bones: everything inside a top level {@code actor_*} bone, except subtrees whose bone
 *       name starts with {@code prop_} (a hammer held in the hand stays on the props texture while
 *       still following the arm animation).
 *   <li>Props pass (drawn by the primary actor only): cubes of every non-skin bone.
 *   <li>Skin pass: cubes of the skin bones of this NPC's own actor slot.
 * </ul>
 *
 * Hiding only a bone's own cubes (not its children) keeps the bone hierarchy and its transforms
 * intact in every pass.
 */
final class ScenePasses {

  static final String PROP_BONE_PREFIX = "prop_";

  // Baked models are replaced on resource reload, so weak identity keys never go stale.
  private static final Map<BakedGeoModel, List<Entry>> PLANS =
      Collections.synchronizedMap(new WeakHashMap<>());

  private ScenePasses() {}

  /** actor = slot of the actor subtree (null for props); skin = drawn with the actor skin. */
  record Entry(GeoBone bone, String actor, boolean skin) {}

  static List<Entry> plan(BakedGeoModel model) {
    return PLANS.computeIfAbsent(model, ScenePasses::buildPlan);
  }

  private static List<Entry> buildPlan(BakedGeoModel model) {
    List<Entry> entries = new ArrayList<>();
    for (GeoBone top : model.topLevelBones()) {
      boolean actor = top.getName().startsWith(SceneConstants.ACTOR_BONE_PREFIX);
      collect(top, actor ? top.getName() : null, actor, entries);
    }
    return List.copyOf(entries);
  }

  private static void collect(GeoBone bone, String actor, boolean skin, List<Entry> entries) {
    boolean isSkin = skin && !bone.getName().startsWith(PROP_BONE_PREFIX);
    entries.add(new Entry(bone, actor, isSkin));
    for (GeoBone child : bone.getChildBones()) {
      collect(child, actor, isSkin, entries);
    }
  }

  /**
   * Props pass of one actor NPC: the primary draws all props (also props held by other actors so
   * they follow their limbs); secondary actors draw nothing here.
   */
  static void showProps(BakedGeoModel model, boolean primary) {
    for (Entry entry : plan(model)) {
      setCubesVisible(entry.bone(), primary && !entry.skin());
    }
  }

  static void showActorSkin(BakedGeoModel model, String actor) {
    for (Entry entry : plan(model)) {
      setCubesVisible(entry.bone(), entry.skin() && actor.equals(entry.actor()));
    }
  }

  private static void setCubesVisible(GeoBone bone, boolean visible) {
    bone.setHidden(!visible);
    bone.setChildrenHidden(false);
  }
}
