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

import de.markusbordihn.easynpc.entity.LivingEntityManager;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.entity.easynpc.data.DialogDataCapable;
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

  private static final int VALIDATE_INTERVAL_TICKS = 10;
  private static final int LINE_OF_SIGHT_GRACE_TICKS = 60;
  private static int validateCounter;
  private static final ConcurrentHashMap<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<UUID, UUID> REQUESTED_OPENS = new ConcurrentHashMap<>();

  private WorldDialogSessionManager() {}

  public static void open(
      ServerPlayer serverPlayer, EasyNPC<?> easyNPC, UUID dialogId, int pageIndex) {
    if (serverPlayer == null || easyNPC == null || dialogId == null) {
      return;
    }
    DialogDataCapable<?> dialogData = easyNPC.getEasyNPCDialogData();
    if (dialogData == null || (!dialogData.hasDialog(dialogId)
        && !(QuestDialogBridge.DIALOG_ID.equals(dialogId) && QuestDialogBridge.available(serverPlayer, easyNPC.getEntityUUID())))) {
      return;
    }

    SESSIONS.put(serverPlayer.getUUID(), new Session(easyNPC.getEntityUUID(), dialogId));
    NetworkHandlerManager.sendMessageToPlayer(
        WorldDialogStateMessage.open(
            DialogMenuHandler.getScreenData(easyNPC, dialogId, pageIndex, serverPlayer).encode()),
        serverPlayer);
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
    boolean valid = inRange(serverPlayer, npc);
    if (!valid) {
      close(serverPlayer, true);
    }
    return valid;
  }

  /**
   * Closes sessions the client never closed (lost packet, teleport, NPC removed or out of sight for
   * a few seconds). Without this a stale session blocks every new auto-open for that player.
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
      if (!inRange(serverPlayer, npc)) {
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
    private int lostSightTicks;

    private Session(UUID npcUUID, UUID dialogId) {
      this.npcUUID = npcUUID;
      this.dialogId = dialogId;
    }

    UUID npcUUID() {
      return this.npcUUID;
    }

    UUID dialogId() {
      return this.dialogId;
    }
  }
}
