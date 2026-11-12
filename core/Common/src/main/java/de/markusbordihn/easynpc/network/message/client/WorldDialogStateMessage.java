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

package de.markusbordihn.easynpc.network.message.client;

import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.client.dialog.WorldDialogOverlay;
import de.markusbordihn.easynpc.data.screen.ScreenData;
import de.markusbordihn.easynpc.network.message.NetworkMessageRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WorldDialogStateMessage(boolean open, CompoundTag data)
    implements NetworkMessageRecord {

  public static final ResourceLocation MESSAGE_ID =
      ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "world_dialog_state");
  public static final CustomPacketPayload.Type<WorldDialogStateMessage> PAYLOAD_TYPE =
      new Type<>(MESSAGE_ID);
  public static final StreamCodec<RegistryFriendlyByteBuf, WorldDialogStateMessage> STREAM_CODEC =
      StreamCodec.of((buffer, message) -> message.write(buffer), WorldDialogStateMessage::create);

  public static WorldDialogStateMessage create(FriendlyByteBuf buffer) {
    boolean open = buffer.readBoolean();
    return new WorldDialogStateMessage(open, open ? buffer.readNbt() : null);
  }

  public static WorldDialogStateMessage open(CompoundTag data) {
    return new WorldDialogStateMessage(true, data);
  }

  public static WorldDialogStateMessage close() {
    return new WorldDialogStateMessage(false, null);
  }

  @Override
  public void write(FriendlyByteBuf buffer) {
    buffer.writeBoolean(this.open);
    if (this.open) {
      buffer.writeNbt(this.data);
    }
  }

  @Override
  public ResourceLocation id() {
    return MESSAGE_ID;
  }

  @Override
  public Type<WorldDialogStateMessage> type() {
    return PAYLOAD_TYPE;
  }

  @Override
  public void handleClient() {
    if (!this.open) {
      WorldDialogOverlay.closeFromServer();
      return;
    }
    ScreenData screenData = ScreenData.decode(this.data);
    if (screenData != null) {
      WorldDialogOverlay.open(screenData);
    }
  }
}
