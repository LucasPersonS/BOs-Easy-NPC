package de.markusbordihn.easynpc.paravexia.scene;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
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
                "{\"title\":\"Ferreiro\",\"hitbox\":[1.6,2],\"footprint\":[3,3],\"tags\":[\"Forja\"],"
                    + "\"actors\":[{\"slot\":\"actor_a\",\"skins\":[\"textures/scene_skins/a.png\",\"x:y.png\"]}],"
                    + "\"lines\":[\"oi\",\" \"],\"weight\":5}"));
    assertEquals("Ferreiro", scene.title());
    assertEquals("idle", scene.animation());
    assertEquals(1.6F, scene.hitboxWidth(), 1e-6);
    assertEquals(3, scene.footprintDepth());
    assertEquals("forja", scene.tags().get(0));
    assertEquals(ResourceLocation.fromNamespaceAndPath("easy_npc", "textures/scene_skins/a.png"), scene.actors().get(0).skins().get(0));
    assertEquals(ResourceLocation.fromNamespaceAndPath("x", "y.png"), scene.actors().get(0).skins().get(1));
    assertEquals(1, scene.lines().size());
    assertEquals(5, scene.weight());
  }

  @Test
  void clampsValuesAndDefaults() {
    SceneDefinition scene = SceneDefinition.parse(ID, json("{\"hitbox\":[99,0],\"weight\":-3}"));
    assertEquals(8.0F, scene.hitboxWidth(), 1e-6);
    assertEquals(0.25F, scene.hitboxHeight(), 1e-6);
    assertEquals(0, scene.weight());
    assertEquals("ferreiro", scene.title());
    assertTrue(scene.actors().isEmpty());
  }

  @Test
  void rejectsBadActors() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SceneDefinition.parse(ID, json("{\"actors\":[{\"slot\":\"smith\",\"skins\":[\"a.png\"]}]}")));
    assertThrows(
        IllegalArgumentException.class,
        () -> SceneDefinition.parse(ID, json("{\"actors\":[{\"slot\":\"actor_a\",\"skins\":[]}]}")));
  }

  @Test
  void parsesSyncedSkinList() {
    Map<String, ResourceLocation> skins =
        SceneDefinition.parseSkinList("actor_a=easy_npc:textures/a.png;broken;actor_b=minecraft:textures/b.png");
    assertEquals(2, skins.size());
    assertEquals(ResourceLocation.parse("minecraft:textures/b.png"), skins.get("actor_b"));
    assertTrue(SceneDefinition.parseSkinList("").isEmpty());
  }
}
