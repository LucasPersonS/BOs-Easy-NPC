package de.markusbordihn.easynpc.server.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DialogCommandTest {

  @Test
  void libraryCommandConsumesTheWholeNamespacedPath() {
    CommandNode<CommandSourceStack> dialog = DialogCommand.register().build();
    CommandNode<CommandSourceStack> apply = child(child(dialog, "library"), "apply");
    CommandNode<CommandSourceStack> target = child(apply, "npc_targets");
    ArgumentCommandNode<?, ?> library =
        assertInstanceOf(ArgumentCommandNode.class, child(target, DialogCommand.LIBRARY_ARG));

    StringArgumentType type = assertInstanceOf(StringArgumentType.class, library.getType());
    assertEquals(StringArgumentType.StringType.GREEDY_PHRASE, type.getType());
  }

  @Test
  void libraryIdAcceptsNamespacedPathsWithOrWithoutQuotes() {
    ResourceLocation expected = ResourceLocation.parse("paravexia:crossroads/intro");

    assertEquals(expected, DialogCommand.parseLibraryId("paravexia:crossroads/intro"));
    assertEquals(expected, DialogCommand.parseLibraryId("\"paravexia:crossroads/intro\""));
  }

  @Test
  void reloadCanApplyALibraryAfterReloading() {
    CommandNode<CommandSourceStack> dialog = DialogCommand.register().build();
    CommandNode<CommandSourceStack> reload = child(child(dialog, "library"), "reload");
    CommandNode<CommandSourceStack> target = child(reload, "npc_targets");

    assertNotNull(child(target, DialogCommand.LIBRARY_ARG).getCommand());
  }

  private static CommandNode<CommandSourceStack> child(
      CommandNode<CommandSourceStack> parent, String name) {
    CommandNode<CommandSourceStack> child = parent.getChild(name);
    assertNotNull(child, "Missing command node " + name);
    return child;
  }
}
