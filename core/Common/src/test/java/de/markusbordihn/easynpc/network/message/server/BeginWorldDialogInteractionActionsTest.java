/*
 * Copyright 2026 Markus Bordihn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package de.markusbordihn.easynpc.network.message.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.markusbordihn.easynpc.data.action.ActionDataEntry;
import de.markusbordihn.easynpc.data.action.ActionDataSet;
import de.markusbordihn.easynpc.data.action.ActionDataType;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BeginWorldDialogInteractionActionsTest {

  @BeforeAll
  static void bootstrap() {
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
  }

  private static ActionDataSet actions(ActionDataType... types) {
    ActionDataSet actionDataSet = new ActionDataSet();
    for (ActionDataType type : types) {
      actionDataSet.add(new ActionDataEntry(type, "say hi"));
    }
    return actionDataSet;
  }

  private static List<ActionDataType> types(ActionDataSet actionDataSet) {
    return actionDataSet.getOrderedEntries().stream().map(ActionDataEntry::actionDataType).toList();
  }

  @Test
  @DisplayName("Dialog opening actions are skipped, the world dialogue already shows the dialog")
  void testDialogActionsSkipped() {
    ActionDataSet filtered =
        BeginWorldDialogMessage.filterInteractionActions(
            actions(
                ActionDataType.OPEN_DEFAULT_DIALOG,
                ActionDataType.OPEN_NAMED_DIALOG,
                ActionDataType.OPEN_NAMED_DIALOG_CONDITIONAL,
                ActionDataType.COMMAND),
            false);
    assertEquals(List.of(ActionDataType.COMMAND), types(filtered));
  }

  @Test
  @DisplayName("Trading runs on click unless a dialog button already offers it")
  void testTrading() {
    ActionDataSet defaults =
        actions(ActionDataType.OPEN_DEFAULT_DIALOG, ActionDataType.OPEN_TRADING_SCREEN);
    assertEquals(
        List.of(ActionDataType.OPEN_TRADING_SCREEN),
        types(BeginWorldDialogMessage.filterInteractionActions(defaults, false)));
    assertEquals(
        List.of(), types(BeginWorldDialogMessage.filterInteractionActions(defaults, true)));
  }

  @Test
  @DisplayName("Missing interaction actions give an empty set")
  void testNull() {
    assertEquals(0, BeginWorldDialogMessage.filterInteractionActions(null, false).size());
  }
}
