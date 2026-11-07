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

import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorldDialogNetworkMessageRoundTripTest {

  @Test
  @DisplayName("A begin world dialog request keeps the NPC id")
  void testBeginMessage() {
    UUID npcId = UUID.randomUUID();
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    new BeginWorldDialogMessage(npcId).write(buffer);

    assertEquals(npcId, BeginWorldDialogMessage.create(buffer).uuid());
  }

  @Test
  @DisplayName("A switch request keeps the NPC id and the alternate flag")
  void testBeginAlternateMessage() {
    UUID npcId = UUID.randomUUID();
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    new BeginWorldDialogMessage(npcId, true).write(buffer);

    BeginWorldDialogMessage message = BeginWorldDialogMessage.create(buffer);
    assertEquals(npcId, message.uuid());
    assertEquals(true, message.alternate());
  }

  @Test
  @DisplayName("A close world dialog request keeps the NPC id")
  void testCloseMessage() {
    UUID npcId = UUID.randomUUID();
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    new CloseWorldDialogMessage(npcId).write(buffer);

    assertEquals(npcId, CloseWorldDialogMessage.create(buffer).uuid());
  }
}
