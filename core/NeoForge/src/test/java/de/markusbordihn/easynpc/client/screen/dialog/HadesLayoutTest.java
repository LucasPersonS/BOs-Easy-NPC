package de.markusbordihn.easynpc.client.screen.dialog;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HadesLayoutTest {
  @Test
  void nameTextAndBothChoicesStayInsideSupportedGuiSizes() {
    for (int width : new int[] {320, 360, 426, 480, 640, 960, 1280, 1920}) {
      for (int height : new int[] {180, 240, 270, 360, 540, 1080}) {
        HadesLayout layout = HadesLayout.fit(width, height);
        assertTrue(layout.x() >= 12, "left filigree must remain visible");
        assertTrue(layout.y() - 45 >= 0, "nameplate crown must remain visible");
        assertTrue(layout.x() + layout.width() + 12 <= width);
        assertTrue(layout.y() + layout.height() + 51 < height - 15,
            "two response rows must not overlap keyboard hints");
        assertTrue(layout.width() - 36 >= 100, "dialogue must have a readable line width");
      }
    }
  }

  @Test
  void exactPageBoundariesNeverProduceAnEmptyPage() {
    assertEquals(0, HadesLayout.lastPage(0, 5));
    assertEquals(0, HadesLayout.lastPage(5, 5));
    assertEquals(1, HadesLayout.lastPage(6, 5));
    assertEquals(1, HadesLayout.lastPage(10, 5));
    assertEquals(2, HadesLayout.lastPage(11, 5));
  }

  @Test
  void narrowGuiPrioritizesReadableDialogueAndWideGuiCapsLineLength() {
    assertFalse(HadesLayout.fit(320, 240).portraitVisible());
    assertTrue(HadesLayout.fit(480, 270).portraitVisible());
    assertEquals(540, HadesLayout.fit(1920, 1080).width());
  }
}
