package de.markusbordihn.easynpc.data.dialog;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DialogueLibraryManagerTest {
  private static final String VALID = """
      {Id:"paravexia:crossroads/intro",DialogData:{Type:"STANDARD",DialogDataSet:[
        {Label:"default",Name:"Intro",Texts:[{Text:"Hello"}]}
      ]}}
      """;

  @Test
  void parsesWrappedLibraryAndPersistsLink() throws Exception {
    var library = DialogueLibraryManager.parse(VALID, ResourceLocation.parse("easy_npc:fallback"));
    assertEquals("paravexia:crossroads/intro", library.id().toString());
    assertTrue(library.dialogues().hasDialog("default"));
    assertEquals("paravexia:crossroads/intro", library.dialogues().getLibraryId());
    assertEquals("paravexia:crossroads/intro",
        new DialogDataSet(library.dialogues().createTag()).getLibraryId());
  }

  @Test
  void derivesNamespaceAndNestedPathFromFileLocation() {
    Path root = Path.of("config", "easy_npc", "dialogues");
    Path file = root.resolve(Path.of("paravexia", "act_01", "oracle.dialog.snbt"));
    assertEquals("paravexia:act_01/oracle", DialogueLibraryManager.idFromPath(root, file).toString());
  }

  @Test
  void rejectsEmptyDialogueTrees() {
    assertThrows(IllegalArgumentException.class, () -> DialogueLibraryManager.parse(
        "{Id:\"paravexia:empty\",DialogData:{Type:\"STANDARD\"}}",
        ResourceLocation.parse("easy_npc:fallback")));
  }

  @Test
  void rejectsBrokenNamedDialogueLinksBeforeTheyReachNpcs() {
    String broken = """
        {DialogDataSet:[{Label:"default",Name:"Intro",Texts:[{Text:"Hello"}],Buttons:[{
          Label:"next",Name:"Continue",Actions:[{Type:"OPEN_NAMED_DIALOG",Cmd:"missing"}]
        }]}]}
        """;
    var error = assertThrows(IllegalArgumentException.class, () -> DialogueLibraryManager.parse(
        broken, ResourceLocation.parse("paravexia:broken")));
    assertTrue(error.getMessage().contains("missing dialogue missing"));
  }
}
