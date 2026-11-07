package de.markusbordihn.easynpc.paravexia.village;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Limits how many residents of one village walk at the same time. Every walk keeps its slot until
 * it is released (arrived) or times out, so path finding and movement packets stay bounded.
 */
public final class WalkerTokens {

  private final Map<UUID, Long> walkers = new HashMap<>();

  public static int effectiveLimit(int maxWalkers, int maxWalkingPercent, int residents) {
    if (residents <= 0) {
      return 0;
    }
    int byShare = (int) Math.ceil(residents * (maxWalkingPercent / 100.0));
    return Math.max(1, Math.min(maxWalkers, byShare));
  }

  public boolean isWalking(UUID uuid) {
    return this.walkers.containsKey(uuid);
  }

  public int size() {
    return this.walkers.size();
  }

  public boolean tryAcquire(UUID uuid, long tick, int limit) {
    if (this.walkers.containsKey(uuid)) {
      return true;
    }
    if (this.walkers.size() >= limit) {
      return false;
    }
    this.walkers.put(uuid, tick);
    return true;
  }

  public void release(UUID uuid) {
    this.walkers.remove(uuid);
  }

  /** Frees slots of walks older than the timeout and returns how many were freed. */
  public int expire(long tick, int timeoutTicks) {
    int expired = 0;
    Iterator<Map.Entry<UUID, Long>> iterator = this.walkers.entrySet().iterator();
    while (iterator.hasNext()) {
      if (tick - iterator.next().getValue() >= timeoutTicks) {
        iterator.remove();
        expired++;
      }
    }
    return expired;
  }

  public void clear() {
    this.walkers.clear();
  }
}
