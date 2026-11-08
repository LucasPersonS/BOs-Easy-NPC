package de.markusbordihn.easynpc.paravexia.scene;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class SceneDefinitionTest {

  private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("easy_npc", "ferreiro");

  private static JsonObject json(String text) {
    return JsonParser.parseString(text).getAsJsonObject();
  }

  @Test
  void parsesFullDefinition() {
    SceneDefinition scene =
        SceneDefinition.parse(
            ID,
            json(
                "{\"title\":\"Ferreiro\",\"hitbox\":[1.2,2],\"footprint\":[3,3],\"tags\":[\"Forja\"],"
                    + "\"actors\":[{\"slot\":\"actor_a\",\"offset\":[1,0,5]},{\"slot\":\"actor_b\"}],"
                    + "\"weight\":5}"));
    assertEquals("Ferreiro", scene.title());
    assertEquals("idle", scene.animation());
    assertEquals(1.2F, scene.hitboxWidth(), 1e-6);
    assertEquals(3, scene.footprintDepth());
    assertEquals("forja", scene.tags().get(0));
    assertEquals(2, scene.actors().size());
    assertEquals("actor_a", scene.primaryActor().slot());
    assertEquals(new Vector3f(1, 0, 5), scene.primaryActor().offset());
    assertEquals(new Vector3f(), scene.actor("actor_b").orElseThrow().offset());
    assertEquals(5, scene.weight());
  }

  @Test
  void clampsValuesAndDefaults() {
    SceneDefinition scene =
        SceneDefinition.parse(
            ID, json("{\"hitbox\":[99,0],\"weight\":-3,\"actors\":[{\"slot\":\"actor_a\",\"offset\":[500,0,0]}]}"));
    assertEquals(4.0F, scene.hitboxWidth(), 1e-6);
    assertEquals(0.25F, scene.hitboxHeight(), 1e-6);
    assertEquals(0, scene.weight());
    assertEquals("ferreiro", scene.title());
    assertEquals(SceneDefinition.MAX_ACTOR_OFFSET, scene.primaryActor().offset().x, 1e-6);
  }

  @Test
  void rejectsBadActors() {
    assertThrows(IllegalArgumentException.class, () -> SceneDefinition.parse(ID, json("{}")));
    assertThrows(
        IllegalArgumentException.class,
        () -> SceneDefinition.parse(ID, json("{\"actors\":[{\"slot\":\"smith\"}]}")));
    assertThrows(
        IllegalArgumentException.class,
        () -> SceneDefinition.parse(ID, json("{\"actors\":[{\"slot\":\"actor_a\"},{\"slot\":\"actor_a\"}]}")));
    assertThrows(
        IllegalArgumentException.class,
        () -> SceneDefinition.parse(ID, json("{\"actors\":[{\"slot\":\"actor_a\",\"offset\":[1,2]}]}")));
  }

  @Test
  void actorOffsetFollowsRendererTransform() {
    // Facing south (yaw 0) the renderer turns the model 180 degrees: model +z (behind) -> world -z.
    Vec3 south = SceneDefinition.worldOffset(new Vector3f(0, 0, 16), 0F);
    assertEquals(0D, south.x, 1e-5);
    assertEquals(-1D, south.z, 1e-5);
    // Model +x (the model's left) at yaw 0 -> world +x.
    Vec3 left = SceneDefinition.worldOffset(new Vector3f(16, 0, 0), 0F);
    assertEquals(1D, left.x, 1e-5);
    assertEquals(0D, left.z, 1e-5);
    // Facing north (yaw 180) no turn: model +z stays world +z.
    Vec3 north = SceneDefinition.worldOffset(new Vector3f(0, 8, 16), 180F);
    assertEquals(0.5D, north.y, 1e-5);
    assertEquals(1D, north.z, 1e-5);
  }

  @Test
  void parsesAmbienceAndHidden() {
    SceneDefinition scene =
        SceneDefinition.parse(
            ID,
            json(
                "{\"hidden\":true,\"ambience\":{\"sound\":\"paravexia_cenas:ambiente.praca\",\"volume\":3,\"radius\":200},"
                    + "\"actors\":[{\"slot\":\"actor_a\"}]}"));
    assertTrue(scene.hidden());
    SceneDefinition.Ambience ambience = scene.ambience();
    assertNotNull(ambience);
    assertEquals(ResourceLocation.fromNamespaceAndPath("paravexia_cenas", "ambiente.praca"), ambience.sound());
    assertEquals(1F, ambience.volume(), 1e-6);
    assertEquals(SceneDefinition.MAX_AMBIENCE_RADIUS, ambience.radius());
    assertEquals(ambience, SceneDefinition.Ambience.decode(ambience.encode()).orElseThrow());
    assertTrue(SceneDefinition.Ambience.decode("").isEmpty());
    assertTrue(SceneDefinition.Ambience.decode("bad|x|1").isEmpty());
    assertNull(SceneDefinition.parse(ID, json("{\"actors\":[{\"slot\":\"actor_a\"}]}")).ambience());
  }
}
