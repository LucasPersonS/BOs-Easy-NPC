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

package de.markusbordihn.easynpc.data.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorldDialogRequestLimiterTest {

  private final UUID player = UUID.randomUUID();
  private final UUID npcA = UUID.randomUUID();
  private final UUID npcB = UUID.randomUUID();

  @Test
  @DisplayName("Automatic requests closer than the interval are dropped")
  void testAutoInterval() {
    WorldDialogRequestLimiter limiter = new WorldDialogRequestLimiter();
    assertTrue(limiter.tryAuto(this.player, this.npcA, 100));
    assertFalse(limiter.tryAuto(this.player, this.npcB, 101));
    assertFalse(
        limiter.tryAuto(
            this.player, this.npcB, 100 + WorldDialogRequestLimiter.AUTO_INTERVAL_TICKS - 1));
    assertTrue(
        limiter.tryAuto(
            this.player, this.npcB, 100 + WorldDialogRequestLimiter.AUTO_INTERVAL_TICKS));
  }

  @Test
  @DisplayName("The same automatic request is dropped for the duplicate interval")
  void testDuplicateInterval() {
    WorldDialogRequestLimiter limiter = new WorldDialogRequestLimiter();
    assertTrue(limiter.tryAuto(this.player, this.npcA, 0));
    assertFalse(
        limiter.tryAuto(
            this.player, this.npcA, WorldDialogRequestLimiter.DUPLICATE_INTERVAL_TICKS - 1));
    assertTrue(
        limiter.tryAuto(
            this.player, this.npcA, WorldDialogRequestLimiter.DUPLICATE_INTERVAL_TICKS));
  }

  @Test
  @DisplayName("Clicks have their own budget and players do not share one")
  void testClicksAndPlayers() {
    WorldDialogRequestLimiter limiter = new WorldDialogRequestLimiter();
    UUID otherPlayer = UUID.randomUUID();
    assertTrue(limiter.tryAuto(this.player, this.npcA, 10));
    assertTrue(limiter.tryClick(this.player, 10));
    assertFalse(limiter.tryClick(this.player, 11));
    assertTrue(limiter.tryClick(this.player, 10 + WorldDialogRequestLimiter.CLICK_INTERVAL_TICKS));
    assertTrue(limiter.tryAuto(otherPlayer, this.npcA, 10));
    assertTrue(limiter.tryClick(otherPlayer, 11));
  }

  @Test
  @DisplayName("Forgetting a player removes its entry and allows requests again")
  void testForget() {
    WorldDialogRequestLimiter limiter = new WorldDialogRequestLimiter();
    assertTrue(limiter.tryAuto(this.player, this.npcA, 5));
    assertEquals(1, limiter.size());
    limiter.forget(this.player);
    assertEquals(0, limiter.size());
    assertTrue(limiter.tryAuto(this.player, this.npcA, 6));
    assertFalse(limiter.tryAuto(null, this.npcA, 7));
    assertFalse(limiter.tryClick(null, 7));
  }

  @Test
  @DisplayName("Dialogues stay in the world only within the session range and in sight")
  void testWorldPresentationRange() {
    double range = WorldDialogSessionManager.SESSION_RANGE;
    assertTrue(WorldDialogSessionManager.fitsWorldPresentation(0.0D, true));
    assertTrue(WorldDialogSessionManager.fitsWorldPresentation(range * range, true));
    assertFalse(WorldDialogSessionManager.fitsWorldPresentation(range * range + 0.01D, true));
    assertFalse(WorldDialogSessionManager.fitsWorldPresentation(16.0D * 16.0D, true));
    assertFalse(WorldDialogSessionManager.fitsWorldPresentation(1.0D, false));
  }
}
