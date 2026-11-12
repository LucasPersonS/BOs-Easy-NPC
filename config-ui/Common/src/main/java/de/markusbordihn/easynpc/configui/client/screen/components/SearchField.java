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

package de.markusbordihn.easynpc.configui.client.screen.components;

import de.markusbordihn.easynpc.client.screen.components.TextField;
import de.markusbordihn.easynpc.configui.Constants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public class SearchField extends TextField {

  private static final ResourceLocation SPRITE = Constants.TEXTURE_CONFIGURATION;
  private static final int SPRITE_HEIGHT = 12;
  private static final int SPRITE_OFFSET_X = 93;
  private static final int SPRITE_OFFSET_Y = 30;
  private static final int SPRITE_WIDTH = 12;
  private static final int SPRITE_X = 1;
  private static final int SPRITE_Y = 2;

  public SearchField(Font font, int x, int y, int width, int height) {
    super(font, x + SPRITE_WIDTH, y - 1, width, height + 2);
    this.setCursorPosition(15);
    this.setHighlightPos(15);
  }

  @Override
  public void renderWidget(GuiGraphics guiGraphics, int left, int top, float partialTicks) {
    if (!this.isVisible()) {
      return;
    }
    int stylePositionX = this.getX() - SPRITE_WIDTH;
    int totalX = stylePositionX - 1;
    int totalW = this.getWidth() + SPRITE_WIDTH + 1;
    de.markusbordihn.easynpc.client.screen.components.ConfigUi.drawInput(
        guiGraphics, totalX, this.getY(), totalW, this.height, this.isFocused());
    guiGraphics.fill(
        this.getX() - 1,
        this.getY() + 2,
        this.getX(),
        this.getY() + this.height - 2,
        de.markusbordihn.easynpc.client.screen.components.ConfigUi.PANEL_IN);

    guiGraphics.blit(
        SPRITE,
        stylePositionX + SPRITE_X,
        this.getY() + SPRITE_Y + 1,
        SPRITE_OFFSET_X,
        this.isFocused() ? SPRITE_OFFSET_Y : SPRITE_OFFSET_Y + SPRITE_HEIGHT,
        SPRITE_WIDTH,
        SPRITE_HEIGHT,
        256,
        256);

    int origX = this.getX();
    int origY = this.getY();
    int origW = this.getWidth();
    boolean wasBordered = this.isBordered();
    this.setBordered(false);
    this.setX(origX + 3);
    this.setY(origY + (this.height - 8) / 2);
    this.setWidth(Math.max(4, origW - 6));
    try {
      super.renderWidget(guiGraphics, left, top, partialTicks);
    } finally {
      this.setBordered(wasBordered);
      this.setX(origX);
      this.setY(origY);
      this.setWidth(origW);
    }
  }
}
