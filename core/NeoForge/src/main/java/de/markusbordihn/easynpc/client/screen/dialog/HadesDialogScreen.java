package de.markusbordihn.easynpc.client.screen.dialog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import de.markusbordihn.easynpc.client.renderer.screen.EntityScreenRenderer;
import de.markusbordihn.easynpc.client.screen.Screen;
import de.markusbordihn.easynpc.client.screen.components.Text;
import de.markusbordihn.easynpc.compat.IntegrationRegistry;
import de.markusbordihn.easynpc.condition.ClientConditionEvaluator;
import de.markusbordihn.easynpc.config.ClientDialogConfig;
import de.markusbordihn.easynpc.data.action.ActionEventType;
import de.markusbordihn.easynpc.data.dialog.DialogButtonConditionMode;
import de.markusbordihn.easynpc.data.dialog.DialogButtonEntry;
import de.markusbordihn.easynpc.data.dialog.DialogMetaData;
import de.markusbordihn.easynpc.data.dialog.DialogOptionsData;
import de.markusbordihn.easynpc.data.render.EntityRenderConfig;
import de.markusbordihn.easynpc.data.render.EntityRenderOverrides;
import de.markusbordihn.easynpc.data.screen.AdditionalScreenData;
import de.markusbordihn.easynpc.menu.dialog.DialogMenu;
import de.markusbordihn.easynpc.network.NetworkMessageHandlerManager;
import de.markusbordihn.easynpc.network.components.TextComponent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

/** Cinematic interaction UI; server-owned dialog actions and locks remain authoritative. */
public class HadesDialogScreen<T extends DialogMenu> extends Screen<T, AdditionalScreenData> {
  private static final ResourceLocation DISPLAY_FONT =
      ResourceLocation.fromNamespaceAndPath("easy_npc", "hades_display");
  private static final ResourceLocation BODY_FONT =
      ResourceLocation.fromNamespaceAndPath("easy_npc", "hades_body");
  private static final SoundEvent SPEAK = SoundEvent.createVariableRangeEvent(
      ResourceLocation.fromNamespaceAndPath("easy_npc", "gui.speak"));
  private final List<Choice> choices = new ArrayList<>();
  private final List<Button> controls = new ArrayList<>();
  private List<FormattedCharSequence> lines = List.of();
  private DialogOptionsData options = DialogOptionsData.DEFAULT;
  private int panelX, panelY, panelWidth, panelHeight, page, linesPerPage, choiceOffset;
  private long revealStart;
  private int lastRevealed;
  private boolean revealed, opened, closed;
  private Button next, previous, more, less;
  private ResourceLocation portrait;
  private int portraitWidth = 512, portraitHeight = 768;
  private String subtitle = "";
  private SimpleSoundInstance speechSound;

  public HadesDialogScreen(T menu, Inventory inventory, Component title) {
    super(menu, inventory, title, 280, 200);
    showCloseButton = false;
  }

  @Override
  public void init() {
    super.init();
    if (getEasyNPC() == null) return;
    options = hasDialogData() ? getDialogData().getDialogOptions() : DialogOptionsData.DEFAULT;
    HadesLayout geometry = HadesLayout.fit(width, height);
    panelX = geometry.x();
    panelWidth = geometry.width();
    linesPerPage = geometry.linesPerPage();
    panelHeight = geometry.height();
    panelY = geometry.y();
    Component text = Component.empty();
    if (hasDialogData()) {
      var metadata = new DialogMetaData(getEasyNPC().getLivingEntity(), minecraft.player,
          getAdditionalScreenData() != null ? getAdditionalScreenData().getScoreboardData() : null);
      String raw = getDialogData().getDialogText(metadata);
      if (raw != null) text = TextComponent.getText(raw).copy()
          .withStyle(style -> style.withFont(BODY_FONT));
    }
    lines = font.split(text, Math.max(40, panelWidth - 36));
    page = Math.min(page, lastPage());
    choices.clear();
    controls.clear();
    if (hasDialogData()) {
      for (DialogButtonEntry entry : getDialogData().getDialogButtons()) {
        if (entry == null || entry.name() == null || entry.name().isBlank()) continue;
        Choice choice = new Choice(entry);
        choices.add(choice);
        addRenderableWidget(choice.button);
      }
    }
    previous = control("‹", b -> changePage(-1), 25);
    next = control("›", b -> advance(), 25);
    less = control("↑", b -> { choiceOffset = Math.max(0, choiceOffset - 2); layout(); }, 22);
    more = control("↓", b -> { choiceOffset += 2; layout(); }, 22);
    if (options.showCloseButton()) {
      Button close = control("×", b -> onClose(), 22);
      close.setX(width - 30);
      close.setY(10);
    }
    loadPortrait();
    beginReveal();
    layout();
    if (!opened) {
      opened = true;
      action(ActionEventType.ON_OPEN_DIALOG);
    }
  }

  private Button control(String label, Button.OnPress action, int size) {
    Button button = new CinematicButton(0, 0, size, body(Component.literal(label)), action);
    controls.add(button);
    return addRenderableWidget(button);
  }

  private void loadPortrait() {
    portrait = null;
    subtitle = "";
    ResourceLocation mapping = ResourceLocation.fromNamespaceAndPath("easy_npc", "hades/portraits.json");
    var resource = minecraft.getResourceManager().getResource(mapping);
    if (resource.isEmpty()) return;
    try (var reader = resource.get().openAsReader()) {
      JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
      String uuid = getEasyNPCUUID().toString();
      String name = getEasyNPC().getLivingEntity().getName().getString();
      String library = getEasyNPC().getEasyNPCDialogData().getDialogDataSet().getLibraryId();
      var value = root.has(uuid) ? root.get(uuid)
          : root.has(name) ? root.get(name)
          : library != null && !library.isBlank() ? root.get(library) : null;
      if (value == null || !value.isJsonObject()) return;
      JsonObject data = value.getAsJsonObject();
      if (data.has("subtitle")) subtitle = data.get("subtitle").getAsString();
      if (!data.has("texture")) return;
      ResourceLocation texture = ResourceLocation.parse(data.get("texture").getAsString());
      if (minecraft.getResourceManager().getResource(texture).isEmpty()) return;
      portraitWidth = Math.max(1, data.has("width") ? data.get("width").getAsInt() : 512);
      portraitHeight = Math.max(1, data.has("height") ? data.get("height").getAsInt() : 768);
      portrait = texture;
    } catch (Exception exception) {
      log.warn("Unable to read Hades NPC portrait mapping; using NPC model", exception);
    }
  }

  private void layout() {
    List<Choice> visible = new ArrayList<>();
    for (Choice choice : choices) {
      boolean locked = unavailable(choice.entry);
      choice.button.active = !locked;
      choice.button.visible = false;
      if (!locked || options.buttonConditionMode() != DialogButtonConditionMode.HIDE) visible.add(choice);
    }
    choiceOffset = Math.max(0, Math.min(choiceOffset, Math.max(0, visible.size() - 2)));
    boolean done = !typing() && page == lastPage();
    for (int i = choiceOffset; i < Math.min(visible.size(), choiceOffset + 2); i++) {
      Button button = visible.get(i).button;
      button.visible = done;
      button.setX(panelX + 10);
      button.setY(panelY + panelHeight + 8 + (i - choiceOffset) * 23);
      button.setWidth(panelWidth - 42);
    }
    previous.setX(panelX + panelWidth - 65);
    previous.setY(panelY + panelHeight - 23);
    previous.visible = page > 0;
    next.setX(panelX + panelWidth - 35);
    next.setY(panelY + panelHeight - 23);
    next.visible = typing() || page < lastPage() || (visible.isEmpty() && options.allowEscClose());
    less.setX(panelX + panelWidth - 26);
    less.setY(panelY + panelHeight + 8);
    less.visible = done && choiceOffset > 0;
    more.setX(panelX + panelWidth - 26);
    more.setY(panelY + panelHeight + 31);
    more.visible = done && choiceOffset + 2 < visible.size();
  }

  private boolean unavailable(DialogButtonEntry entry) {
    var data = getAdditionalScreenData();
    return (entry.hasConditions() && !ClientConditionEvaluator.evaluateAll(entry.conditions(),
        minecraft.player, getEasyNPC().getLivingEntity()))
        || (data != null && (data.isExecutionLimitReached(entry.id()) || data.isDialogButtonLocked(entry.id())));
  }

  private int lastPage() { return HadesLayout.lastPage(lines.size(), linesPerPage); }
  private int pageCharacters() {
    int total = 0;
    for (int i = page * linesPerPage; i < Math.min(lines.size(), (page + 1) * linesPerPage); i++) total += Text.length(lines.get(i));
    return total;
  }
  private int revealedCharacters() {
    if (revealed || !ClientDialogConfig.TYPEWRITER_ENABLED) return pageCharacters();
    return (int) Math.min(pageCharacters(), (Util.getMillis() - revealStart)
        * Math.max(1, ClientDialogConfig.TYPEWRITER_CHARS_PER_SECOND) / 1000L);
  }
  private boolean typing() { return revealedCharacters() < pageCharacters(); }
  private void beginReveal() {
    stopSpeech();
    revealStart = Util.getMillis();
    lastRevealed = 0;
    revealed = false;
  }
  private void changePage(int delta) {
    page = Math.max(0, Math.min(lastPage(), page + delta));
    beginReveal();
    layout();
  }
  private void advance() {
    if (typing()) { revealed = true; stopSpeech(); layout(); }
    else if (page < lastPage()) changePage(1);
    else if (choices.isEmpty() && options.allowEscClose()) onClose();
  }
  private void action(ActionEventType event) {
    if (getActionEventSet().hasActionEvent(event))
      NetworkMessageHandlerManager.getServerHandler().executeActionEvent(getEasyNPCUUID(), event);
  }

  @Override
  protected void updateTick() {
    if (getEasyNPC() != null && next != null) layout();
  }

  @Override
  public void renderBackground(GuiGraphics g, int x, int y, float delta) {
    // Keep the world sharp behind the cinematic scrim; no vanilla blur or inventory panel.
  }

  @Override
  public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
    if (getEasyNPC() == null || next == null) return;
    g.fill(0, 0, width, height, 0x65050810);
    g.fillGradient(0, height / 3, width, height, 0x00050810, 0xd9070c14);
    g.fill(0, 0, width, 5, 0xd9000000);
    g.fill(0, height - 5, width, height, 0xd9000000);
    if (options.displayAvatar() && width >= 420) renderPortrait(g);
    HadesFrame.panel(g, panelX, panelY, panelWidth, panelHeight);
    Component styledTitle = display(Component.literal(title.getString()));
    int nameWidth = Math.min(panelWidth - 20, Math.max(126, font.width(styledTitle) + 34));
    int nameX = panelX + 12;
    HadesFrame.nameplate(g, nameX, panelY - 37, nameWidth);
    g.drawCenteredString(font, fit(styledTitle, nameWidth - 20), nameX + nameWidth / 2,
        panelY - 29, 0xffeee6d4);
    if (!subtitle.isEmpty()) {
      g.pose().pushPose();
      g.pose().translate(nameX + nameWidth / 2.0f, panelY - 16, 0);
      g.pose().scale(.75f, .75f, 1);
      g.drawCenteredString(font, fit(body(Component.literal(subtitle)),
          (int) ((nameWidth - 16) / .75)), 0, 0, HadesFrame.JADE);
      g.pose().popPose();
    } else HadesFrame.diamond(g, nameX + nameWidth / 2, panelY - 14, 2, HadesFrame.JADE);
    int count = revealedCharacters();
    int remaining = count;
    for (int i = page * linesPerPage; i < Math.min(lines.size(), (page + 1) * linesPerPage); i++) {
      int length = Text.length(lines.get(i));
      g.drawString(font, Text.limit(lines.get(i), Math.max(0, Math.min(length, remaining))),
          panelX + 18, panelY + 15 + (i - page * linesPerPage) * 12, 0xffe3e3df, false);
      remaining -= length;
    }
    if (count > lastRevealed && typing() && ClientDialogConfig.TYPEWRITER_ENABLED
        && ClientDialogConfig.HADES_SPEAK_ENABLED
        && (speechSound == null || !minecraft.getSoundManager().isActive(speechSound))) {
      speechSound = SimpleSoundInstance.forUI(SPEAK, 1.0f, .35f);
      minecraft.getSoundManager().play(speechSound);
    }
    lastRevealed = count;
    if (!typing()) stopSpeech();
    layout();
    if (lastPage() > 0) g.drawString(font, body(Component.literal(
            (page + 1) + " / " + (lastPage() + 1))),
        panelX + 18, panelY + panelHeight - 17, 0xff789790, false);
    for (Choice choice : choices) {
      choice.button.render(g, mouseX, mouseY, delta);
      if (choice.button.visible && choice.button.isHoveredOrFocused())
        g.renderTooltip(font, choice.button.active ? choice.button.getMessage()
            : body(Component.translatable("hades.easy_npc.locked")), mouseX, mouseY);
    }
    for (Button button : controls) button.render(g, mouseX, mouseY, delta);
    g.drawString(font, body(Component.translatable(
            typing() ? "hades.easy_npc.reveal" : "hades.easy_npc.controls")),
        panelX + 8, height - 15, 0xff8fa39f, false);
  }

  private Component display(Component component) {
    return component.copy().withStyle(style -> style.withFont(DISPLAY_FONT));
  }

  private Component body(Component component) {
    return component.copy().withStyle(style -> style.withFont(BODY_FONT));
  }

  private Component fit(Component component, int maxWidth) {
    if (font.width(component) <= maxWidth) return component;
    String value = component.getString();
    while (value.length() > 1 && font.width(component) > maxWidth) {
      value = value.substring(0, value.length() - 1);
      component = Component.literal(value + "…").withStyle(component.getStyle());
    }
    return component;
  }

  private void renderPortrait(GuiGraphics g) {
    if (portrait != null) {
      int h = height - 12;
      int w = (int) ((long) h * portraitWidth / portraitHeight);
      if (w > width * .56) { w = (int) (width * .56); h = (int) ((long) w * portraitHeight / portraitWidth); }
      RenderSystem.enableBlend();
      g.blit(portrait, Math.max(-12, panelX / 2 - w / 2), height - h, w, h,
          0, 0, portraitWidth, portraitHeight, portraitWidth, portraitHeight);
      RenderSystem.disableBlend();
      return;
    }
    int right = Math.min(width / 2, panelX + 30);
    int scale = Math.max(25, (int) ((height - 24) / Math.max(1.8, getEasyNPC().getLivingEntity().getBbHeight())));
    IntegrationRegistry.setGuiPreviewMode(true);
    g.pose().pushPose();
    try {
      EntityScreenRenderer.renderEntity(g, getEasyNPC(), new EntityRenderConfig(
          right / 2, height / 2, scale, 0, 0, 0, 8, right, height - 5, 0,
          EntityRenderOverrides.HIDE_NAME_TAG_RESET_ROOT), right / 2.0f, height / 2.0f);
    } finally {
      g.pose().popPose();
      IntegrationRegistry.setGuiPreviewMode(false);
    }
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    if (button == 0 && typing()) { revealed = true; stopSpeech(); layout(); return true; }
    return super.mouseClicked(x, y, button);
  }

  @Override
  public boolean keyPressed(int key, int scan, int mods) {
    if (key == GLFW.GLFW_KEY_ESCAPE) {
      if (options.allowEscClose()) onClose();
      return true;
    }
    if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
      if (typing() || page < lastPage()) advance();
      else if (getFocused() instanceof Button button && button.visible && button.active) button.onPress();
      else advance();
      return true;
    }
    if (key == GLFW.GLFW_KEY_RIGHT) { advance(); return true; }
    if (key == GLFW.GLFW_KEY_LEFT) { if (page > 0) changePage(-1); return true; }
    return super.keyPressed(key, scan, mods);
  }

  private void stopSpeech() {
    if (speechSound != null) { minecraft.getSoundManager().stop(speechSound); speechSound = null; }
  }
  @Override
  public void onClose() {
    if (closed) return;
    closed = true;
    stopSpeech();
    action(ActionEventType.ON_CLOSE_DIALOG);
    super.onClose();
  }
  @Override
  public void removed() { stopSpeech(); super.removed(); }

  private final class Choice {
    final DialogButtonEntry entry;
    final Button button;
    Choice(DialogButtonEntry entry) {
      this.entry = entry;
      this.button = new CinematicButton(0, 0, 100,
          body(TextComponent.getTextComponentRaw(entry.name(), entry.isTranslationKey())), b -> {
            if (typing() || page != lastPage() || unavailable(entry)) return;
            action(ActionEventType.ON_BUTTON_CLICK);
            if (entry.hasActionData()) NetworkMessageHandlerManager.getServerHandler()
                .executeDialogButtonAction(getEasyNPCUUID(), getDialogUUID(), entry.id());
            else onClose();
          });
    }
  }

  private final class CinematicButton extends Button {
    CinematicButton(int x, int y, int w, Component message, OnPress onPress) {
      super(x, y, w, 20, message, onPress, DEFAULT_NARRATION);
    }
    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
      boolean focus = isHoveredOrFocused();
      g.fill(getX(), getY(), getX() + getWidth(), getY() + height, focus ? 0xe52b4947 : 0xc80a1419);
      HadesFrame.line(g, getX() + 5, getY() + height - 1, getX() + getWidth() - 5,
          getY() + height - 1, focus ? HadesFrame.JADE : 0xff365453);
      if (getWidth() > 30) HadesFrame.diamond(g, getX() + 8, getY() + 9, 2,
          active ? HadesFrame.JADE : 0xff657476);
      int inset = getWidth() > 30 ? 17 : 4;
      Component label = fit(getMessage(), Math.max(1, getWidth() - inset - 5));
      g.drawString(font, label, getX() + inset, getY() + 6,
          active ? 0xffe5e3d6 : 0xff718080, false);
    }
  }
}
