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

import de.markusbordihn.easynpc.data.display.DisplayAttributeType;
import de.markusbordihn.easynpc.entity.LivingEntityManager;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.entity.easynpc.data.DialogDataCapable;
import de.markusbordihn.easynpc.entity.easynpc.data.DisplayAttributeDataCapable;
import de.markusbordihn.easynpc.entity.easynpc.handlers.VisibilityHandler;
import de.markusbordihn.easynpc.menu.dialog.DialogMenuHandler;
import de.markusbordihn.easynpc.network.NetworkHandlerManager;
import de.markusbordihn.easynpc.network.message.client.WorldDialogStateMessage;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Server-owned session for dialogue rendered directly over the game world. */
public final class WorldDialogSessionManager {

  public static final double AUTO_OPEN_RANGE = 6.0D;

  /** Range the client keeps a dialogue open in (it closes after a short grace outside it). */
  public static final double SESSION_RANGE = 8.0D;

  /**
   * Extra distance the server tolerates on top of {@link #SESSION_RANGE}, so a click sent right at
   * the edge (latency, knockback) is still honoured. Quest choices must use the same total.
   */
  public static final double SESSION_RANGE_SLACK = 1.5D;

  /** Shared rate limit for {@code BeginWorldDialogMessage}. */
  public static final WorldDialogRequestLimiter REQUEST_LIMITER = new WorldDialogRequestLimiter();

  private static final int VALIDATE_INTERVAL_TICKS = 10;
  private static final int LINE_OF_SIGHT_GRACE_TICKS = 60;
  private static int validateCounter;
  private static final ConcurrentHashMap<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<UUID, UUID> REQUESTED_OPENS = new ConcurrentHashMap<>();

  private WorldDialogSessionManager() {}

  /**
   * Opens (or re-pages) a session for a dialogue the player asked for: a click on the NPC, a choice
   * or an action. Only {@link #openTeaser} opens one that does not count as talking yet.
   */
  public static void open(
      ServerPlayer serverPlayer, EasyNPC<?> easyNPC, UUID dialogId, int pageIndex) {
    open(serverPlayer, easyNPC, dialogId, pageIndex, true);
  }

  /**
   * Shows the short read-only line the player gets by walking up to an NPC. It is not a
   * conversation yet: no execution is recorded, no sound plays and no dialog event fires until the
   * player engages (see {@link #engage}).
   */
  public static void openTeaser(ServerPlayer serverPlayer, EasyNPC<?> easyNPC, UUID dialogId) {
    open(serverPlayer, easyNPC, dialogId, 0, false);
  }

  private static void open(
      ServerPlayer serverPlayer,
      EasyNPC<?> easyNPC,
      UUID dialogId,
      int pageIndex,
      boolean engaged) {
    if (serverPlayer == null || easyNPC == null || dialogId == null) {
      return;
    }
    DialogDataCapable<?> dialogData = easyNPC.getEasyNPCDialogData();
    if (dialogData == null || (!dialogData.hasDialog(dialogId)
        && !(QuestDialogBridge.DIALOG_ID.equals(dialogId) && QuestDialogBridge.available(serverPlayer, easyNPC.getEntityUUID())))) {
      return;
    }

    Session previous = SESSIONS.get(serverPlayer.getUUID());
    boolean keepEngaged =
        previous != null && previous.engaged && easyNPC.getEntityUUID().equals(previous.npcUUID);
    SESSIONS.put(
        serverPlayer.getUUID(),
        new Session(easyNPC.getEntityUUID(), dialogId, engaged || keepEngaged));
    NetworkHandlerManager.sendMessageToPlayer(
        WorldDialogStateMessage.open(
            DialogMenuHandler.getScreenData(easyNPC, dialogId, pageIndex, serverPlayer).encode()),
        serverPlayer);
  }

  /**
   * Marks the player's session with this NPC as a real conversation. Returns {@code true} only the
   * first time, so the caller records the execution and fires the open events exactly once.
   */
  public static boolean engage(ServerPlayer serverPlayer, UUID npcUUID) {
    if (serverPlayer == null || npcUUID == null) {
      return false;
    }
    Session session = SESSIONS.get(serverPlayer.getUUID());
    if (session == null || !npcUUID.equals(session.npcUUID()) || session.engaged) {
      return false;
    }
    session.engaged = true;
    return true;
  }

  public static void requestOpen(ServerPlayer serverPlayer, UUID npcUUID) {
    if (serverPlayer != null && npcUUID != null) {
      REQUESTED_OPENS.put(serverPlayer.getUUID(), npcUUID);
    }
  }

  public static boolean consumeRequestedOpen(ServerPlayer serverPlayer, UUID npcUUID) {
    if (serverPlayer == null || npcUUID == null) {
      return false;
    }
    return REQUESTED_OPENS.remove(serverPlayer.getUUID(), npcUUID);
  }

  public static void clearRequestedOpen(ServerPlayer serverPlayer) {
    if (serverPlayer != null) {
      REQUESTED_OPENS.remove(serverPlayer.getUUID());
    }
  }

  public static void close(ServerPlayer serverPlayer, boolean notifyClient) {
    if (serverPlayer == null) {
      return;
    }
    SESSIONS.remove(serverPlayer.getUUID());
    REQUESTED_OPENS.remove(serverPlayer.getUUID());
    if (notifyClient) {
      NetworkHandlerManager.sendMessageToPlayer(WorldDialogStateMessage.close(), serverPlayer);
    }
  }

  public static void close(ServerPlayer serverPlayer, UUID npcUUID, boolean notifyClient) {
    if (serverPlayer == null || npcUUID == null) {
      return;
    }
    Session session = SESSIONS.get(serverPlayer.getUUID());
    if (session != null && npcUUID.equals(session.npcUUID())) {
      close(serverPlayer, notifyClient);
    }
  }

  /** The player left the server: drop the session and the rate limit entry. */
  public static void playerLeft(ServerPlayer serverPlayer) {
    if (serverPlayer == null) {
      return;
    }
    close(serverPlayer, false);
    REQUEST_LIMITER.forget(serverPlayer.getUUID());
  }

  /** Server stop: nothing may survive into the next server of this JVM (singleplayer worlds). */
  public static void reset() {
    SESSIONS.clear();
    REQUESTED_OPENS.clear();
    REQUEST_LIMITER.clear();
    validateCounter = 0;
  }

  /** Dialogue currently shown to the player, or {@code null} without a session. */
  public static UUID dialogId(ServerPlayer serverPlayer) {
    Session session = serverPlayer != null ? SESSIONS.get(serverPlayer.getUUID()) : null;
    return session != null ? session.dialogId() : null;
  }

  public static boolean hasSession(ServerPlayer serverPlayer) {
    return serverPlayer != null && SESSIONS.containsKey(serverPlayer.getUUID());
  }

  public static boolean hasSession(ServerPlayer serverPlayer, UUID npcUUID) {
    if (serverPlayer == null || npcUUID == null) {
      return false;
    }
    Session session = SESSIONS.get(serverPlayer.getUUID());
    return session != null && npcUUID.equals(session.npcUUID());
  }

  public static boolean isValid(ServerPlayer serverPlayer, UUID npcUUID, UUID dialogId) {
    if (serverPlayer == null || npcUUID == null) {
      return false;
    }
    Session session = SESSIONS.get(serverPlayer.getUUID());
    if (session == null
        || !npcUUID.equals(session.npcUUID())
        || (dialogId != null && !dialogId.equals(session.dialogId()))) {
      return false;
    }

    EasyNPC<?> easyNPC = LivingEntityManager.getServerEasyNPCEntityByUUID(npcUUID, serverPlayer);
    LivingEntity npc = easyNPC != null ? easyNPC.getLivingEntity() : null;
    // Line of sight is checked with a grace period in tick(); a click is aimed at the panel anyway.
    boolean valid = inRange(serverPlayer, npc) && canInteract(easyNPC, serverPlayer);
    if (!valid) {
      close(serverPlayer, true);
    }
    return valid;
  }

  /**
   * Whether a dialogue for this player can be presented over the world. Farther away (an
   * ON_DISTANCE action, a command, a delayed action chain) the client would close it at once, so
   * callers fall back to the classic dialogue screen instead.
   */
  public static boolean canPresentInWorld(ServerPlayer serverPlayer, EasyNPC<?> easyNPC) {
    LivingEntity npc = easyNPC != null ? easyNPC.getLivingEntity() : null;
    if (serverPlayer == null || npc == null || serverPlayer.level() != npc.level()) {
      return false;
    }
    return fitsWorldPresentation(
        serverPlayer.distanceToSqr(npc), serverPlayer.hasLineOfSight(npc));
  }

  /** Same limits the client uses to keep a session open without its closing grace. */
  public static boolean fitsWorldPresentation(double distanceSqr, boolean lineOfSight) {
    return lineOfSight && distanceSqr <= SESSION_RANGE * SESSION_RANGE;
  }

  /** The NPC is shown to this player (7.17 per-player visibility conditions). */
  public static boolean isVisibleTo(EasyNPC<?> easyNPC, ServerPlayer serverPlayer) {
    return easyNPC != null
        && serverPlayer != null
        && !VisibilityHandler.handleIsInvisibleToPlayer(easyNPC, serverPlayer, false);
  }

  /**
   * Same rule as {@code InteractionHandler}: an NPC hidden from the player only refuses to talk
   * when its "interaction when invisible" display attribute is set to false.
   */
  public static boolean canInteract(EasyNPC<?> easyNPC, ServerPlayer serverPlayer) {
    if (easyNPC == null || serverPlayer == null) {
      return false;
    }
    DisplayAttributeDataCapable<?> displayAttributeData = easyNPC.getEasyNPCDisplayAttributeData();
    boolean blockWhenInvisible =
        displayAttributeData != null
            && displayAttributeData.hasDisplayAttribute(
                DisplayAttributeType.INTERACTION_WHEN_INVISIBLE)
            && !displayAttributeData.getDisplayBooleanAttribute(
                DisplayAttributeType.INTERACTION_WHEN_INVISIBLE);
    return !blockWhenInvisible || isVisibleTo(easyNPC, serverPlayer);
  }

  /**
   * Closes sessions the client never closed (lost packet, teleport, NPC removed, hidden or out of
   * sight for a few seconds). Without this a stale session blocks every new auto-open for that
   * player.
   */
  public static void tick(MinecraftServer minecraftServer) {
    if (minecraftServer == null
        || SESSIONS.isEmpty()
        || ++validateCounter % VALIDATE_INTERVAL_TICKS != 0) {
      return;
    }
    for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
      ServerPlayer serverPlayer = minecraftServer.getPlayerList().getPlayer(entry.getKey());
      if (serverPlayer == null) {
        SESSIONS.remove(entry.getKey());
        REQUESTED_OPENS.remove(entry.getKey());
        continue;
      }
      Session session = entry.getValue();
      EasyNPC<?> easyNPC =
          LivingEntityManager.getServerEasyNPCEntityByUUID(session.npcUUID(), serverPlayer);
      LivingEntity npc = easyNPC != null ? easyNPC.getLivingEntity() : null;
      // A teaser is only a line over a visible NPC; a conversation follows the interaction rule.
      boolean allowed =
          session.engaged ? canInteract(easyNPC, serverPlayer) : isVisibleTo(easyNPC, serverPlayer);
      if (!inRange(serverPlayer, npc) || !allowed) {
        close(serverPlayer, true);
        continue;
      }
      if (serverPlayer.hasLineOfSight(npc)) {
        session.lostSightTicks = 0;
      } else if ((session.lostSightTicks += VALIDATE_INTERVAL_TICKS)
          >= LINE_OF_SIGHT_GRACE_TICKS) {
        close(serverPlayer, true);
      }
    }
  }

  private static boolean inRange(ServerPlayer serverPlayer, LivingEntity npc) {
    double range = SESSION_RANGE + SESSION_RANGE_SLACK;
    return npc != null
        && npc.isAlive()
        && serverPlayer.isAlive()
        && serverPlayer.level() == npc.level()
        && serverPlayer.distanceToSqr(npc) <= range * range;
  }

  private static final class Session {
    private final UUID npcUUID;
    private final UUID dialogId;
    private volatile boolean engaged;
    private int lostSightTicks;

    private Session(UUID npcUUID, UUID dialogId, boolean engaged) {
      this.npcUUID = npcUUID;
      this.dialogId = dialogId;
      this.engaged = engaged;
    }

    UUID npcUUID() {
      return this.npcUUID;
    }

    UUID dialogId() {
      return this.dialogId;
    }
  }
}
