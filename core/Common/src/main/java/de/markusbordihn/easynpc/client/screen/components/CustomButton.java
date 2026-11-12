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

import com.mojang.blaze3d.systems.RenderSystem;
import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.network.components.TextComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class CustomButton extends Button {

  protected static final WidgetSprites SPRITES =
      new WidgetSprites(
          ConfigUi.BUTTON,
          ConfigUi.BUTTON_DISABLED,
          ConfigUi.BUTTON_HOVER);

  private int accentColor = 0;
  private boolean blocked = false;
  private boolean tabButton = false;

  public CustomButton(int left, int top, int width, int height) {
    this(left, top, width, height, null, unused -> {});
  }

  public CustomButton(int left, int top, int width, int height, OnPress onPress) {
    this(left, top, width, height, null, onPress);
  }

  public CustomButton(
      int left, int top, int width, int height, Component component, OnPress onPress) {
    super(
        left,
        top,
        width,
        height,
        component != null ? component : TextComponent.getBlankText(),
        onPress,
        Button.DEFAULT_NARRATION);
  }

  public CustomButton setAccentColor(int accentColor) {
    this.accentColor = accentColor;
    return this;
  }

  public int getAccentColor() {
    return this.accentColor;
  }

  public CustomButton setBlocked(boolean blocked) {
    this.blocked = blocked;
    return this;
  }

  public boolean isBlocked() {
    return this.blocked;
  }

  public CustomButton setTabButton(boolean tabButton) {
    this.tabButton = tabButton;
    return this;
  }

  public boolean isTabButton() {
    return this.tabButton;
  }

  public boolean isSelectedTab() {
    return this.tabButton && !this.active && !this.blocked;
  }

  public int getButtonTextColor() {
    if (this.isSelectedTab()) {
      return ConfigUi.GOLD;
    }
    if (!this.active) {
      return ConfigUi.DIM;
    }
    return this.isHoveredOrFocused() ? ConfigUi.TEXT_BRIGHT : ConfigUi.TEXT;
  }

  public boolean isHovered() {
    return this.isHovered;
  }

  public void renderButtonText(
      GuiGraphics guiGraphics, Font font, Component component, int x, int y) {
    if (component != null && !component.getString().isEmpty()) {
      int foregroundColor = this.getButtonTextColor() & 0x00FFFFFF;
      guiGraphics.drawCenteredString(
          font,
          component,
          this.getX() + (this.width) / 2,
          this.getY() + (this.height - 8) / 2,
          foregroundColor | Mth.ceil(this.alpha * 255.0F) << 24);
    }
  }

  @Override
  public void onPress() {
    if (this.onPress != null) {
      super.onPress();
    }
  }

  @Override
  public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
    this.renderButton(guiGraphics, mouseX, mouseY, partialTicks);
  }

  public void renderButton(GuiGraphics guiGraphics, int left, int top, float partialTicks) {
    Minecraft minecraft = Minecraft.getInstance();
    Font font = minecraft.font;
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.enableDepthTest();

    // Background (Paravexia dark stone nine-slice with optional accent stripe / active tab bar)
    ConfigUi.drawButton(
        guiGraphics,
        this.getX(),
        this.getY(),
        this.getWidth(),
        this.getHeight(),
        this.active,
        this.isHoveredOrFocused(),
        this.isSelectedTab(),
        this.accentColor);

    this.renderButtonText(guiGraphics, font, this.getMessage(), this.getX(), this.getY());
  }
}
