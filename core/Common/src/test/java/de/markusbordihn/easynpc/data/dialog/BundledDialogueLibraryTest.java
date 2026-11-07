package de.markusbordihn.easynpc.data.dialog;

import static org.junit.jupiter.api.Assertions.*;

import de.markusbordihn.easynpc.data.action.ActionDataType;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class BundledDialogueLibraryTest {
  private static final List<String> ANCORADOURO =
      List.of(
          "odete", "bento", "helena", "joaquim", "marina", "lucia", "ezequiel", "amaro",
          "anselmo", "davi", "caio", "iara", "roque", "nina", "tito", "zelia", "tavares", "lima",
          "recem1", "recem2", "estivador1", "estivador2", "doente1", "doente2", "ouvinte",
          "hospede");
  private static final List<String> TRADERS = List.of("bento", "joaquim", "marina", "lucia", "amaro");

  private static Map<ResourceLocation, DialogDataSet> bundled() {
    TreeMap<ResourceLocation, DialogDataSet> target = new TreeMap<>();
    assertEquals(0, DialogueLibraryManager.loadBundled(target), "bundled libraries rejected");
    return target;
  }

  @Test
  void loadsEveryAncoradouroLibraryFromTheJar() {
    Map<ResourceLocation, DialogDataSet> libraries = bundled();
    for (String key : ANCORADOURO) {
      ResourceLocation id = ResourceLocation.parse("paravexia:ancoradouro/" + key);
      DialogDataSet dialogues = libraries.get(id);
      assertNotNull(dialogues, "missing " + id);
      assertTrue(dialogues.hasDialog("default"), id + " has no default dialogue");
      assertEquals(id.toString(), dialogues.getLibraryId());
      for (DialogDataEntry dialog : dialogues.getDialogsByLabel()) {
        for (DialogTextData text : dialog.getDialogTexts()) {
          assertTrue(text.text().length() <= DialogUtils.MAX_DIALOG_LINE_LENGTH, id + " text too long");
        }
        for (DialogButtonEntry button : dialog.getDialogButtons()) {
          assertFalse(button.actionDataSet().getOrderedEntries().isEmpty(), id + " button without action");
        }
      }
    }
  }

  @Test
  void tradersOfferTheirWaresFromTheGreeting() {
    Map<ResourceLocation, DialogDataSet> libraries = bundled();
    for (String key : TRADERS) {
      DialogDataEntry greeting =
          libraries.get(ResourceLocation.parse("paravexia:ancoradouro/" + key)).getDialog("default");
      assertTrue(
          greeting.getDialogButtons().stream()
              .anyMatch(b -> b.actionDataSet().hasActionDataType(ActionDataType.OPEN_TRADING_SCREEN)),
          key + " has no trade button");
    }
  }

  @Test
  void configLibrariesReplaceBundledOnesWithTheSameId() throws Exception {
    ResourceLocation id = ResourceLocation.parse("paravexia:ancoradouro/odete");
    DialogDataSet bundledOdete = bundled().get(id);
    DialogDataSet custom =
        DialogueLibraryManager.parse(
                "{Id:\"paravexia:ancoradouro/odete\",DialogDataSet:[{Label:\"default\",Name:\"Odete\","
                    + "Texts:[{Text:\"Editado\"}]}]}",
                ResourceLocation.parse("easy_npc:fallback"))
            .dialogues();
    ResourceLocation other = ResourceLocation.parse("paravexia:ancoradouro/bento");
    var merged =
        DialogueLibraryManager.merge(Map.of(id, bundledOdete, other, bundled().get(other)), Map.of(id, custom));
    assertSame(custom, merged.get(id));
    assertNotNull(merged.get(other));
    assertEquals(2, merged.size());
  }
}
