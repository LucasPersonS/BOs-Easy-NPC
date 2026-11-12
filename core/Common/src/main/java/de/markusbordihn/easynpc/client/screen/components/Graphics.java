/*
 * Copyright 2023 Markus Bordihn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package de.markusbordihn.easynpc.client.screen.components;

import de.markusbordihn.easynpc.Constants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public class Graphics {

  private Graphics() {}

  public static void blit(
      GuiGraphics guiGraphics,
      ResourceLocation texture,
      int x,
      int y,
      int width,
      int height,
      int textureX,
      int textureY) {
    blit(guiGraphics, texture, x, y, width, height, textureX, textureY, 256, 256);
  }

  public static void blit(
      GuiGraphics guiGraphics,
      ResourceLocation texture,
      int x,
      int y,
      int width,
      int height,
      int textureX,
      int textureY,
      int textureWidth,
      int textureHeight) {
    if (Constants.TEXTURE_INVENTORY.equals(texture)) {
      if (textureX > 0 && textureY > 0 && textureX % 18 == 0 && textureY % 18 == 0) {
        ConfigUi.drawSlotRegion(guiGraphics, x, y, textureX, textureY);
        return;
      }
      if (width == 212 && height == 0 && textureX == 16 && textureY == 16) {
        // Trading arrow (replace vanilla grey arrow with Paravexia gold/stone chevron)
        int cy = y + 7;
        guiGraphics.fill(x + 2, cy, x + 11, cy + 2, ConfigUi.GOLD_DARK);
        guiGraphics.fill(x + 9, cy - 2, x + 11, cy + 4, ConfigUi.GOLD);
        guiGraphics.fill(x + 11, cy - 1, x + 13, cy + 3, ConfigUi.GOLD);
        guiGraphics.fill(x + 13, cy, x + 14, cy + 2, ConfigUi.TEXT_BRIGHT);
        return;
      }
    }
    guiGraphics.blit(texture, x, y, width, height, textureX, textureY, textureWidth, textureHeight);
  }

  public static void blitStretched(
      GuiGraphics guiGraphics,
      ResourceLocation texture,
      int destX,
      int destY,
      int destWidth,
      int destHeight,
      int srcX,
      int srcY,
      int srcWidth,
      int srcHeight,
      int textureWidth,
      int textureHeight) {
    guiGraphics.pose().pushPose();
    guiGraphics.pose().translate(destX, destY, 0);
    guiGraphics.pose().scale((float) destWidth / srcWidth, (float) destHeight / srcHeight, 1f);
    guiGraphics.blit(
        texture,
        0,
        0,
        srcWidth,
        srcHeight,
        srcX,
        srcY,
        srcWidth,
        srcHeight,
        textureWidth,
        textureHeight);
    guiGraphics.pose().popPose();
  }
}
