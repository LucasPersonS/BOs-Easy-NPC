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
 * Decides which bones draw their cubes in each render pass of a scene.
 *
 * <ul>
 *   <li>Skin bones: everything inside a top level {@code actor_*} bone, except subtrees whose bone
 *       name starts with {@code prop_} (a hammer held in the hand stays on the props texture while
 *       still following the arm animation).
 *   <li>Props pass: cubes of every non-skin bone.
 *   <li>Actor pass: cubes of the skin bones of that actor only.
 * </ul>
 *
 * Hiding only a bone's own cubes (not its children) keeps the bone hierarchy and its transforms
 * intact in every pass.
 */
final class ScenePasses {

  static final String PROP_BONE_PREFIX = "prop_";

  // Baked models are replaced on resource reload, so weak identity keys never go stale.
  private static final Map<BakedGeoModel, Plan> PLANS = Collections.synchronizedMap(new WeakHashMap<>());

  private ScenePasses() {}

  record Entry(GeoBone bone, String actor, boolean skin) {}

  record Plan(List<Entry> entries, List<String> actors) {}

  static Plan plan(BakedGeoModel model) {
    return PLANS.computeIfAbsent(model, ScenePasses::buildPlan);
  }

  private static Plan buildPlan(BakedGeoModel model) {
    List<Entry> entries = new ArrayList<>();
    List<String> actors = new ArrayList<>();
    for (GeoBone top : model.topLevelBones()) {
      boolean actor = top.getName().startsWith(SceneConstants.ACTOR_BONE_PREFIX);
      if (actor) {
        actors.add(top.getName());
      }
      collect(top, actor ? top.getName() : null, actor, entries);
    }
    return new Plan(List.copyOf(entries), List.copyOf(actors));
  }

  private static void collect(GeoBone bone, String actor, boolean skin, List<Entry> entries) {
    boolean isSkin = skin && !bone.getName().startsWith(PROP_BONE_PREFIX);
    entries.add(new Entry(bone, actor, isSkin));
    for (GeoBone child : bone.getChildBones()) {
      collect(child, actor, isSkin, entries);
    }
  }

  static void showProps(BakedGeoModel model) {
    for (Entry entry : plan(model).entries()) {
      setCubesVisible(entry.bone(), !entry.skin());
    }
  }

  static void showActor(BakedGeoModel model, String actor) {
    for (Entry entry : plan(model).entries()) {
      setCubesVisible(entry.bone(), entry.skin() && actor.equals(entry.actor()));
    }
  }

  private static void setCubesVisible(GeoBone bone, boolean visible) {
    bone.setHidden(!visible);
    bone.setChildrenHidden(false);
  }
}
