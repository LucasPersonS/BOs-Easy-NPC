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

import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.data.dialog.WorldDialogSessionManager;
import de.markusbordihn.easynpc.data.dialog.QuestDialogBridge;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.entity.easynpc.data.DialogDataCapable;
import de.markusbordihn.easynpc.network.message.NetworkMessageRecord;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Asks the server to show an NPC's dialogue in the world.
 *
 * @param uuid NPC
 * @param alternate {@code false}: open (quests first) when the player has no session yet. {@code
 *     true}: the player already talks to this NPC, switch between its quest list and its own
 *     dialogue.
 */
public record BeginWorldDialogMessage(UUID uuid, boolean alternate) implements NetworkMessageRecord {

  public static final ResourceLocation MESSAGE_ID =
      ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "begin_world_dialog");
  public static final CustomPacketPayload.Type<BeginWorldDialogMessage> PAYLOAD_TYPE =
      new Type<>(MESSAGE_ID);
  public static final StreamCodec<RegistryFriendlyByteBuf, BeginWorldDialogMessage> STREAM_CODEC =
      StreamCodec.of((buffer, message) -> message.write(buffer), BeginWorldDialogMessage::create);

  public BeginWorldDialogMessage(UUID uuid) {
    this(uuid, false);
  }

  public static BeginWorldDialogMessage create(FriendlyByteBuf buffer) {
    return new BeginWorldDialogMessage(buffer.readUUID(), buffer.readBoolean());
  }

  @Override
  public void write(FriendlyByteBuf buffer) {
    buffer.writeUUID(this.uuid);
    buffer.writeBoolean(this.alternate);
  }

  @Override
  public ResourceLocation id() {
    return MESSAGE_ID;
  }

  @Override
  public Type<BeginWorldDialogMessage> type() {
    return PAYLOAD_TYPE;
  }

  @Override
  public void handleServer(ServerPlayer serverPlayer) {
    if (this.alternate) {
      switchDialog(serverPlayer);
      return;
    }
    if (WorldDialogSessionManager.hasSession(serverPlayer)) {
      return;
    }
    EasyNPC<?> easyNPC = getEasyNPC(this.uuid, serverPlayer);
    DialogDataCapable<?> dialogData =
        easyNPC != null ? easyNPC.getEasyNPCDialogData() : null;
    LivingEntity npc = easyNPC != null ? easyNPC.getLivingEntity() : null;
    if (dialogData == null
        || (!dialogData.hasDialog() && !QuestDialogBridge.available(serverPlayer, this.uuid))
        || npc == null
        || !npc.isAlive()
        || serverPlayer.isSpectator()
        || serverPlayer.distanceToSqr(npc)
            > WorldDialogSessionManager.AUTO_OPEN_RANGE
                * WorldDialogSessionManager.AUTO_OPEN_RANGE
        || !serverPlayer.hasLineOfSight(npc)) {
      return;
    }
    if (QuestDialogBridge.available(serverPlayer, this.uuid)) {
      WorldDialogSessionManager.open(serverPlayer, easyNPC, QuestDialogBridge.DIALOG_ID, 0);
      return;
    }
    WorldDialogSessionManager.requestOpen(serverPlayer, this.uuid);
    try {
      dialogData.openDefaultDialog(serverPlayer);
    } finally {
      WorldDialogSessionManager.clearRequestedOpen(serverPlayer);
    }
  }

  /** Quest list -> the NPC's own dialogue, or the other way round. Needs an open session. */
  private void switchDialog(ServerPlayer serverPlayer) {
    if (!WorldDialogSessionManager.hasSession(serverPlayer, this.uuid)) {
      return;
    }
    EasyNPC<?> easyNPC = getEasyNPC(this.uuid, serverPlayer);
    DialogDataCapable<?> dialogData = easyNPC != null ? easyNPC.getEasyNPCDialogData() : null;
    if (dialogData == null || !WorldDialogSessionManager.isValid(serverPlayer, this.uuid, null)) {
      return;
    }
    if (QuestDialogBridge.DIALOG_ID.equals(WorldDialogSessionManager.dialogId(serverPlayer))) {
      if (dialogData.hasDialog()) {
        // openDialog sees the session and keeps the presentation in the world.
        dialogData.openDefaultDialog(serverPlayer);
      }
    } else if (QuestDialogBridge.available(serverPlayer, this.uuid)) {
      WorldDialogSessionManager.open(serverPlayer, easyNPC, QuestDialogBridge.DIALOG_ID, 0);
    }
  }
}
