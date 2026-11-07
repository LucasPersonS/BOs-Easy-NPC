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
import de.markusbordihn.easynpc.data.action.ActionContext;
import de.markusbordihn.easynpc.data.action.ActionDataEntry;
import de.markusbordihn.easynpc.data.action.ActionDataSet;
import de.markusbordihn.easynpc.data.action.ActionDataType;
import de.markusbordihn.easynpc.data.action.ActionEventType;
import de.markusbordihn.easynpc.data.dialog.DialogButtonEntry;
import de.markusbordihn.easynpc.data.dialog.DialogDataEntry;
import de.markusbordihn.easynpc.data.dialog.DialogDataSet;
import de.markusbordihn.easynpc.data.dialog.QuestDialogBridge;
import de.markusbordihn.easynpc.data.dialog.WorldDialogSessionManager;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.entity.easynpc.data.ActionEventDataCapable;
import de.markusbordihn.easynpc.entity.easynpc.data.DialogDataCapable;
import de.markusbordihn.easynpc.entity.easynpc.handlers.ActionHandler;
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
 * Client request for the world dialogue of one NPC.
 *
 * <ul>
 *   <li>{@link Kind#AUTO}: the player walked up to or looked at the NPC. Opens a teaser only; it
 *       does not count as talking (no execution recorded, no sound, no dialog event).
 *   <li>{@link Kind#ENGAGE}: the player clicked the NPC or expanded the teaser. Opens the dialogue
 *       if needed and, the first time per session, counts as talking and runs the NPC's
 *       interaction actions that the world dialogue does not already cover.
 *   <li>{@link Kind#ALTERNATE}: switch between the quest list and the NPC's own dialogue.
 * </ul>
 */
public record BeginWorldDialogMessage(UUID uuid, Kind kind) implements NetworkMessageRecord {

  public static final ResourceLocation MESSAGE_ID =
      ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "begin_world_dialog");
  public static final CustomPacketPayload.Type<BeginWorldDialogMessage> PAYLOAD_TYPE =
      new Type<>(MESSAGE_ID);
  public static final StreamCodec<RegistryFriendlyByteBuf, BeginWorldDialogMessage> STREAM_CODEC =
      StreamCodec.of((buffer, message) -> message.write(buffer), BeginWorldDialogMessage::create);

  public BeginWorldDialogMessage(UUID uuid) {
    this(uuid, Kind.AUTO);
  }

  public static BeginWorldDialogMessage create(FriendlyByteBuf buffer) {
    UUID uuid = buffer.readUUID();
    // Out of range ordinals decode to null and are ignored in handleServer.
    return new BeginWorldDialogMessage(uuid, NetworkMessageRecord.readEnum(buffer, Kind.class));
  }

  @Override
  public void write(FriendlyByteBuf buffer) {
    buffer.writeUUID(this.uuid);
    buffer.writeEnum(this.kind != null ? this.kind : Kind.AUTO);
  }

  public boolean alternate() {
    return this.kind == Kind.ALTERNATE;
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
    if (this.kind == null || this.uuid == null || serverPlayer.getServer() == null) {
      return;
    }
    long tick = serverPlayer.getServer().getTickCount();
    UUID playerUUID = serverPlayer.getUUID();
    switch (this.kind) {
      case AUTO -> {
        if (WorldDialogSessionManager.REQUEST_LIMITER.tryAuto(playerUUID, this.uuid, tick)) {
          this.openTeaser(serverPlayer);
        }
      }
      case ENGAGE -> {
        if (WorldDialogSessionManager.REQUEST_LIMITER.tryClick(playerUUID, tick)) {
          this.engage(serverPlayer);
        }
      }
      case ALTERNATE -> {
        if (WorldDialogSessionManager.REQUEST_LIMITER.tryClick(playerUUID, tick)) {
          this.switchDialog(serverPlayer);
        }
      }
    }
  }

  private void openTeaser(ServerPlayer serverPlayer) {
    if (WorldDialogSessionManager.hasSession(serverPlayer)) {
      return;
    }
    EasyNPC<?> easyNPC = this.getTalkableNPC(serverPlayer);
    // A teaser is a line over an NPC the player can see, whatever its interaction setting.
    if (easyNPC == null || !WorldDialogSessionManager.isVisibleTo(easyNPC, serverPlayer)) {
      return;
    }
    UUID dialogId = this.pickDialog(serverPlayer, easyNPC);
    if (dialogId != null) {
      WorldDialogSessionManager.openTeaser(serverPlayer, easyNPC, dialogId);
    }
  }

  private void engage(ServerPlayer serverPlayer) {
    EasyNPC<?> easyNPC;
    if (WorldDialogSessionManager.hasSession(serverPlayer, this.uuid)) {
      // Expanding an open teaser: the session range applies (it may be farther than auto-open).
      easyNPC = getEasyNPC(this.uuid, serverPlayer);
      if (easyNPC == null || !WorldDialogSessionManager.isValid(serverPlayer, this.uuid, null)) {
        return;
      }
    } else {
      // A click on an NPC without (or with another) session opens this one straight away.
      easyNPC = this.getTalkableNPC(serverPlayer);
      if (easyNPC == null || !WorldDialogSessionManager.canInteract(easyNPC, serverPlayer)) {
        return;
      }
      UUID dialogId = this.pickDialog(serverPlayer, easyNPC);
      if (dialogId == null) {
        return;
      }
      WorldDialogSessionManager.close(serverPlayer, false);
      WorldDialogSessionManager.openTeaser(serverPlayer, easyNPC, dialogId);
    }
    if (!WorldDialogSessionManager.engage(serverPlayer, this.uuid)) {
      return;
    }
    DialogDataCapable<?> dialogData = easyNPC.getEasyNPCDialogData();
    dialogData.engageWorldDialog(serverPlayer, WorldDialogSessionManager.dialogId(serverPlayer));
    runInteractionActions(easyNPC, serverPlayer);
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

  /** NPC within auto-open range, alive, in line of sight and with something to say. */
  private EasyNPC<?> getTalkableNPC(ServerPlayer serverPlayer) {
    EasyNPC<?> easyNPC = getEasyNPC(this.uuid, serverPlayer);
    DialogDataCapable<?> dialogData = easyNPC != null ? easyNPC.getEasyNPCDialogData() : null;
    LivingEntity npc = easyNPC != null ? easyNPC.getLivingEntity() : null;
    if (dialogData == null
        || npc == null
        || !npc.isAlive()
        || serverPlayer.isSpectator()
        || serverPlayer.level() != npc.level()
        || serverPlayer.distanceToSqr(npc)
            > WorldDialogSessionManager.AUTO_OPEN_RANGE
                * WorldDialogSessionManager.AUTO_OPEN_RANGE
        || !serverPlayer.hasLineOfSight(npc)) {
      return null;
    }
    if (!dialogData.hasDialog() && !QuestDialogBridge.available(serverPlayer, this.uuid)) {
      return null;
    }
    return easyNPC;
  }

  private UUID pickDialog(ServerPlayer serverPlayer, EasyNPC<?> easyNPC) {
    if (QuestDialogBridge.available(serverPlayer, this.uuid)) {
      return QuestDialogBridge.DIALOG_ID;
    }
    DialogDataEntry dialog =
        easyNPC
            .getEasyNPCDialogData()
            .getDialogDataSet()
            .getNextAvailableDialog(serverPlayer, easyNPC.getLivingEntity());
    return dialog != null ? dialog.getId() : null;
  }

  /**
   * The world dialogue swallows the right-click on a talking NPC, so the NPC's ON_INTERACTION
   * actions (trading, commands, quest triggers, ...) are run here instead. Opening a dialogue is
   * skipped because the world dialogue already shows it, and trading is left to the dialogue when
   * one of its buttons offers it, so a merchant still greets the player before the trade screen.
   */
  private static void runInteractionActions(EasyNPC<?> easyNPC, ServerPlayer serverPlayer) {
    ActionEventDataCapable<?> actionEventData = easyNPC.getEasyNPCActionEventData();
    ActionHandler<?> actionHandler = easyNPC.getEasyNPCActionHandler();
    if (actionEventData == null
        || actionHandler == null
        || !actionEventData.hasActionEvent(ActionEventType.ON_INTERACTION)) {
      return;
    }
    ActionDataSet interactionActions =
        filterInteractionActions(
            actionEventData.getActionDataSet(ActionEventType.ON_INTERACTION),
            dialogOffersTrading(easyNPC.getEasyNPCDialogData().getDialogDataSet()));
    if (!interactionActions.isEmpty()) {
      actionHandler.executeActions(
          interactionActions, ActionContext.of(ActionEventType.ON_INTERACTION, serverPlayer));
    }
  }

  public static ActionDataSet filterInteractionActions(
      ActionDataSet actionDataSet, boolean dialogOffersTrading) {
    ActionDataSet result = new ActionDataSet();
    if (actionDataSet == null) {
      return result;
    }
    for (ActionDataEntry actionDataEntry : actionDataSet.getOrderedEntries()) {
      ActionDataType type = actionDataEntry.actionDataType();
      boolean opensDialog =
          type == ActionDataType.OPEN_DEFAULT_DIALOG
              || type == ActionDataType.OPEN_NAMED_DIALOG
              || type == ActionDataType.OPEN_NAMED_DIALOG_CONDITIONAL;
      boolean tradingInDialog = dialogOffersTrading && type == ActionDataType.OPEN_TRADING_SCREEN;
      if (!opensDialog && !tradingInDialog) {
        result.add(actionDataEntry);
      }
    }
    return result;
  }

  private static boolean dialogOffersTrading(DialogDataSet dialogDataSet) {
    if (dialogDataSet == null) {
      return false;
    }
    for (DialogDataEntry dialog : dialogDataSet.getDialogsByLabel()) {
      for (DialogButtonEntry button : dialog.getDialogButtons()) {
        if (button.actionDataSet() != null
            && button.actionDataSet().hasActionDataType(ActionDataType.OPEN_TRADING_SCREEN)) {
          return true;
        }
      }
    }
    return false;
  }

  public enum Kind {
    AUTO,
    ENGAGE,
    ALTERNATE
  }
}
