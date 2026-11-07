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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player rate limit for world dialogue requests. A client asks again every 30 ticks while
 * nothing opens, and once per NPC it looks at; without a limit a modified or buggy client can make
 * the server raycast and build dialogue packets for every request it sends.
 */
public final class WorldDialogRequestLimiter {

  /** Shortest gap between two automatic (teaser) requests of one player. */
  public static final int AUTO_INTERVAL_TICKS = 5;

  /** Repeating the same automatic request for the same NPC is dropped for this long. */
  public static final int DUPLICATE_INTERVAL_TICKS = 20;

  /** Shortest gap between two clicks (engage or switch) of one player. */
  public static final int CLICK_INTERVAL_TICKS = 4;

  private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

  /** Returns {@code true} when an automatic request may be processed and records it. */
  public boolean tryAuto(UUID player, UUID npc, long tick) {
    if (player == null || npc == null) {
      return false;
    }
    Entry entry = this.entries.computeIfAbsent(player, key -> new Entry());
    synchronized (entry) {
      if (tick - entry.lastAutoTick < AUTO_INTERVAL_TICKS
          || (npc.equals(entry.lastAutoNpc)
              && tick - entry.lastAutoTick < DUPLICATE_INTERVAL_TICKS)) {
        return false;
      }
      entry.lastAutoTick = tick;
      entry.lastAutoNpc = npc;
      return true;
    }
  }

  /** Returns {@code true} when a click request may be processed and records it. */
  public boolean tryClick(UUID player, long tick) {
    if (player == null) {
      return false;
    }
    Entry entry = this.entries.computeIfAbsent(player, key -> new Entry());
    synchronized (entry) {
      if (tick - entry.lastClickTick < CLICK_INTERVAL_TICKS) {
        return false;
      }
      entry.lastClickTick = tick;
      return true;
    }
  }

  public void forget(UUID player) {
    if (player != null) {
      this.entries.remove(player);
    }
  }

  public void clear() {
    this.entries.clear();
  }

  public int size() {
    return this.entries.size();
  }

  private static final class Entry {
    private long lastAutoTick = Long.MIN_VALUE / 2;
    private long lastClickTick = Long.MIN_VALUE / 2;
    private UUID lastAutoNpc;
  }
}
