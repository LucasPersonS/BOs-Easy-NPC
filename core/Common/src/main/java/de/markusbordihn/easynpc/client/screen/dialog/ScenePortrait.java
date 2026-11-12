/*
 * Copyright 2026 Paravexia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.markusbordihn.easynpc.client.screen.dialog;

import com.mojang.blaze3d.systems.RenderSystem;
import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.client.renderer.entity.EasyNPCEntityRenderer;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.paravexia.scene.SceneNPC;
import de.markusbordihn.easynpc.paravexia.scene.SceneSkinVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Dialog avatar of a scene actor: the face of its skin.
 *
 * <p>A scene actor is drawn by the scene renderer, which paints the whole diorama (props, other
 * actors) around the scene origin instead of a standalone body. Through the regular avatar path
 * that showed whatever the actor holds, or nothing at all (hidden scenes, missing model). The
 * portrait therefore draws the head layer and the hat layer of the actor skin as a flat face.
 *
 * <p>The skin comes from the same Easy NPC skin managers the scene uses, so default skins, player
 * names, URLs, uploaded files and server textures all work. UVs are normalized to the 64x64 skin
 * layout, so 128x128 skins render correctly too.
 */
final class ScenePortrait {

  private static final int MIN_SIZE = 32;
  private static final int MAX_SIZE = 64;
  private static final int FRAME_DARK = 0xFF120A06;
  private static final int FRAME_LIGHT = 0xFFC9AE85;
  private static final int BACKDROP = 0xFF2B1C11;

  private ScenePortrait() {}

  /** True for the actors of a Paravexia scene. */
  static boolean applies(final EasyNPC<?> easyNPC) {
    return easyNPC != null && easyNPC.getEntity() instanceof SceneNPC;
  }

  /**
   * Draws the face centred on {@code centerX}, with its bottom edge {@code bottomY - lift}.
   *
   * @param scale avatar scale of the dialog (50 by default); the face is drawn as a multiple of
   *     8 px so every skin pixel stays crisp.
   */
  static void render(
      final GuiGraphics guiGraphics,
      final EasyNPC<?> easyNPC,
      final int centerX,
      final int bottomY,
      final int scale) {
    int size = Math.max(MIN_SIZE, Math.min(MAX_SIZE, Math.round(scale / 8.0f) * 8));
    int left = centerX - size / 2;
    // (centerX, bottomY) is where the standing avatar puts its feet: 80 px under the window top.
    // The face sits 14 px under the window top (plus the NPC's own avatar offset), fully inside
    // the window, in the area the body used to occupy.
    int top = bottomY - 66;

    // Frame: dark outline, light rim, dark backdrop so the face reads on any dialog background.
    guiGraphics.fill(left - 3, top - 3, left + size + 3, top + size + 3, FRAME_DARK);
    guiGraphics.fill(left - 2, top - 2, left + size + 2, top + size + 2, FRAME_LIGHT);
    guiGraphics.fill(left - 1, top - 1, left + size + 1, top + size + 1, BACKDROP);

    ResourceLocation skin = skinOf(easyNPC);
    RenderSystem.enableBlend();
    // Head front (8,8) and hat layer front (40,8), 8x8 px each, normalized against a 64x64 skin.
    guiGraphics.blit(skin, left, top, size, size, 8.0f, 8.0f, 8, 8, 64, 64);
    guiGraphics.blit(skin, left, top, size, size, 40.0f, 8.0f, 8, 8, 64, 64);
    RenderSystem.disableBlend();
  }

  /** Skin of the actor through the renderer the scene uses; the neutral NPC skin as fallback. */
  private static ResourceLocation skinOf(final EasyNPC<?> easyNPC) {
    ResourceLocation fallback = SceneSkinVariant.NPC_SKIN.getTextureLocation();
    Entity entity = easyNPC.getEntity();
    if (entity == null) {
      return fallback;
    }
    try {
      Object renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
      if (renderer instanceof EasyNPCEntityRenderer easyRenderer) {
        ResourceLocation skin = easyRenderer.getEntityPlayerTexture(easyNPC);
        if (skin != null && !Constants.BLANK_ENTITY_TEXTURE.equals(skin)) {
          return skin;
        }
      }
    } catch (RuntimeException exception) {
      // A skin that is still downloading must never break the dialog: use the neutral skin.
    }
    return fallback;
  }
}
