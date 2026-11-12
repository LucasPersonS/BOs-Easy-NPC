package de.markusbordihn.easynpc.client.screen.dialog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.markusbordihn.easynpc.client.dialog.DialogUi;
import de.markusbordihn.easynpc.client.dialog.DialogUi.ReplyState;
import de.markusbordihn.easynpc.client.screen.Screen;
import de.markusbordihn.easynpc.client.screen.components.Text;
import de.markusbordihn.easynpc.condition.ClientConditionEvaluator;
import de.markusbordihn.easynpc.config.ClientDialogConfig;
import de.markusbordihn.easynpc.data.action.ActionDataType;
import de.markusbordihn.easynpc.data.action.ActionEventType;
import de.markusbordihn.easynpc.data.dialog.DialogButtonConditionMode;
import de.markusbordihn.easynpc.data.dialog.DialogButtonEntry;
import de.markusbordihn.easynpc.data.dialog.DialogMetaData;
import de.markusbordihn.easynpc.data.dialog.DialogOptionsData;
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

/**
 * Dialogue screen styled after the Paravexia website's dialogue library
 * ({@code Website/admin/dialogues.css}): 2D NPC skin face in a stone frame, gold speaker name,
 * inset dark text box, and stacked stone reply buttons with coloured left stripes.
 */
public class HadesDialogScreen<T extends DialogMenu> extends Screen<T, AdditionalScreenData> {
  private static final SoundEvent SPEAK = SoundEvent.createVariableRangeEvent(
      ResourceLocation.fromNamespaceAndPath("easy_npc", "gui.speak"));
  private final List<Choice> choices = new ArrayList<>();
  private final List<Button> controls = new ArrayList<>();
  private List<FormattedCharSequence> lines = List.of();
  private DialogOptionsData options = DialogOptionsData.DEFAULT;
  private int panelX, panelY, panelWidth, panelHeight, page, linesPerPage, choicesPerPage, choiceOffset;
  private int visibleChoicesCount;
  private long revealStart;
  private int lastRevealed;
  private boolean revealed, opened, closed;
  private Button next, previous, more, less;
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
    panelWidth = Math.min(340, geometry.width());
    panelX = (width - panelWidth) / 2;
    linesPerPage = geometry.linesPerPage();
    choicesPerPage = geometry.choicesPerPage();
    panelHeight = geometry.height();
    panelY = geometry.y();
    Component text = Component.empty();
    if (hasDialogData()) {
      var metadata = new DialogMetaData(getEasyNPC().getLivingEntity(), minecraft.player,
          getAdditionalScreenData() != null ? getAdditionalScreenData().getScoreboardData() : null);
      String raw = getDialogData().getDialogText(metadata);
      if (raw != null) text = TextComponent.getText(raw).copy()
          .withStyle(style -> style.withFont(DialogUi.FONT));
    }
    lines = font.split(text, Math.max(40, panelWidth - 24));
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
    previous = control("‹", b -> changePage(-1), 20);
    next = control("›", b -> advance(), 20);
    less = control("▲", b -> { choiceOffset = Math.max(0, choiceOffset - 1); layout(); }, 18);
    more = control("▼", b -> { choiceOffset += 1; layout(); }, 18);
    if (options.showCloseButton()) {
      Button close = control("×", b -> onClose(), 18);
      close.setX(panelX + panelWidth - 18);
      close.setY(panelY - 26);
    }
    loadSubtitle();
    beginReveal();
    layout();
    if (!opened) {
      opened = true;
      action(ActionEventType.ON_OPEN_DIALOG);
    }
  }

  private Button control(String label, Button.OnPress action, int size) {
    Button button = new SiteReplyButton(0, 0, size, 16, body(Component.literal(label)), action);
    controls.add(button);
    return addRenderableWidget(button);
  }

  private void loadSubtitle() {
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
      if (value != null && value.isJsonObject()) {
        JsonObject data = value.getAsJsonObject();
        if (data.has("subtitle")) subtitle = data.get("subtitle").getAsString();
      }
    } catch (Exception exception) {
      log.warn("Unable to read NPC subtitle mapping", exception);
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
    choiceOffset = Math.max(0, Math.min(choiceOffset, Math.max(0, visible.size() - choicesPerPage)));
    boolean done = !typing() && page == lastPage();
    boolean needsScroll = visible.size() > choicesPerPage;
    int replyWidth = needsScroll ? panelWidth - 22 : panelWidth;
    int shown = 0;
    for (int i = choiceOffset; i < Math.min(visible.size(), choiceOffset + choicesPerPage); i++) {
      SiteReplyButton button = (SiteReplyButton) visible.get(i).button;
      button.choiceNumber = i + 1;
      button.visible = done;
      button.setX(panelX);
      button.setY(panelY + panelHeight + 6 + (i - choiceOffset) * 19);
      button.setWidth(replyWidth);
      shown++;
    }
    visibleChoicesCount = done ? shown : 0;
    previous.setX(panelX + panelWidth - 46);
    previous.setY(panelY + panelHeight - 19);
    previous.visible = page > 0;
    next.setX(panelX + panelWidth - 23);
    next.setY(panelY + panelHeight - 19);
    next.visible = typing() || page < lastPage() || (visible.isEmpty() && options.allowEscClose());
    less.setX(panelX + panelWidth - 18);
    less.setY(panelY + panelHeight + 6);
    less.visible = done && choiceOffset > 0;
    more.setX(panelX + panelWidth - 18);
    more.setY(panelY + panelHeight + 6 + Math.max(0, shown - 1) * 19);
    more.visible = done && choiceOffset + choicesPerPage < visible.size();
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
    // Keep the world visible behind the website-style dialogue panel.
  }

  @Override
  public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
    if (getEasyNPC() == null || next == null) return;
    g.fill(0, 0, width, height, 0x55080605);

    int outerPad = 8;
    int headerTop = panelY - 26;
    int repliesBlock = visibleChoicesCount > 0 ? 6 + visibleChoicesCount * 19 - 3 : (!typing() && page == lastPage() ? 18 : 0);
    int outerX = panelX - outerPad;
    int outerY = headerTop - outerPad;
    int outerW = panelWidth + outerPad * 2;
    int outerH = (panelY + panelHeight + repliesBlock) - headerTop + outerPad * 2 + 14;

    // .d-screen outer frame
    DialogUi.panel(g, outerX, outerY, outerW, outerH, 1.0F);

    // .d-speaker: 2D face frame + gold speaker name + optional subtitle
    DialogUi.face(g, getEasyNPC().getLivingEntity(), panelX, headerTop, 1.0F);
    String speaker = title.getString();
    DialogUi.draw(g, font, speaker, panelX + DialogUi.FACE_SIZE + 6,
        subtitle.isEmpty() ? headerTop + 6 : headerTop + 2, DialogUi.GOLD, 1.0F, true);
    if (!subtitle.isEmpty()) {
      DialogUi.draw(g, font, subtitle, panelX + DialogUi.FACE_SIZE + 6, headerTop + 12,
          DialogUi.DIM, 1.0F, false);
    }

    // .d-text inset box
    DialogUi.textBox(g, panelX, panelY, panelWidth, panelHeight, 1.0F);

    int count = revealedCharacters();
    int remaining = count;
    for (int i = page * linesPerPage; i < Math.min(lines.size(), (page + 1) * linesPerPage); i++) {
      int length = Text.length(lines.get(i));
      g.drawString(font, Text.limit(lines.get(i), Math.max(0, Math.min(length, remaining))),
          panelX + 10, panelY + 10 + (i - page * linesPerPage) * 12, DialogUi.TEXT, false);
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
    if (lastPage() > 0) {
      DialogUi.draw(g, font, (page + 1) + " / " + (lastPage() + 1),
          panelX + 10, panelY + panelHeight - 14, DialogUi.DIM, 1.0F, false);
    }
    if (!typing() && page == lastPage() && visibleChoicesCount == 0) {
      String end = "— fim da conversa —";
      DialogUi.draw(g, font, end, panelX + (panelWidth - DialogUi.width(font, end)) / 2,
          panelY + panelHeight + 6, DialogUi.DIM, 1.0F, false);
    }
    for (Choice choice : choices) {
      choice.button.render(g, mouseX, mouseY, delta);
      if (choice.button.visible && choice.button.isHoveredOrFocused())
        g.renderTooltip(font, choice.button.active ? choice.button.getMessage()
            : body(Component.translatable("hades.easy_npc.locked")), mouseX, mouseY);
    }
    for (Button button : controls) button.render(g, mouseX, mouseY, delta);
    DialogUi.draw(g, font, Component.translatable(
            typing() ? "hades.easy_npc.reveal" : "hades.easy_npc.controls"),
        panelX, outerY + outerH - outerPad - 9, DialogUi.DIM, 1.0F, false);
  }

  private Component body(Component component) {
    return DialogUi.styled(component);
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

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    if (button == 0 && typing()) { revealed = true; stopSpeech(); layout(); return true; }
    return super.mouseClicked(x, y, button);
  }

  @Override
  public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
    if (!typing() && page == lastPage() && scrollY != 0.0D) {
      choiceOffset = Math.max(0, choiceOffset + (scrollY < 0.0D ? 1 : -1));
      layout();
      return true;
    }
    return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
    if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
      int choiceNumber = key - GLFW.GLFW_KEY_0;
      for (Choice choice : choices) {
        SiteReplyButton button = (SiteReplyButton) choice.button;
        if (button.visible && button.active && button.choiceNumber == choiceNumber) {
          button.onPress();
          return true;
        }
      }
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
      this.button = new SiteReplyButton(
          0,
          0,
          100,
          16,
          body(TextComponent.getTextComponentRaw(entry.name(), entry.isTranslationKey())),
          entry.actionDataSet().hasActionDataType(ActionDataType.OPEN_TRADING_SCREEN),
          b -> {
            if (typing() || page != lastPage() || unavailable(entry)) return;
            action(ActionEventType.ON_BUTTON_CLICK);
            if (entry.hasActionData()) NetworkMessageHandlerManager.getServerHandler()
                .executeDialogButtonAction(getEasyNPCUUID(), getDialogUUID(), entry.id());
            else onClose();
          });
    }
  }

  private final class SiteReplyButton extends Button {
    private final boolean merchantAction;
    private int choiceNumber;

    SiteReplyButton(int x, int y, int w, int h, Component message, OnPress onPress) {
      this(x, y, w, h, message, false, onPress);
    }

    SiteReplyButton(
        int x, int y, int w, int h, Component message, boolean merchantAction, OnPress onPress) {
      super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
      this.merchantAction = merchantAction;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
      boolean focus = isHoveredOrFocused();
      ReplyState state = !active ? ReplyState.LOCKED : focus ? ReplyState.HOVER : ReplyState.IDLE;
      if (choiceNumber > 0) {
        int stripe = DialogUi.segment(choiceNumber - 1);
        DialogUi.reply(g, getX(), getY(), getWidth(), height, state, stripe, 1.0F);
        DialogUi.keycap(
            g,
            font,
            getX() + 5,
            getY() + (height - DialogUi.KEY) / 2,
            Integer.toString(choiceNumber),
            focus && active,
            active,
            1.0F);
        int tagSpace = merchantAction ? 48 : 6;
        Component prefixed = body(Component.literal("▸ " + getMessage().getString()));
        Component label = fit(prefixed, Math.max(1, getWidth() - DialogUi.LABEL_X - tagSpace));
        int color = !active ? DialogUi.DIM : focus ? DialogUi.GOLD : DialogUi.TEXT;
        g.drawString(font, label, getX() + DialogUi.LABEL_X, getY() + (height - 8) / 2, color, false);
        if (merchantAction) {
          String tag = "comércio";
          DialogUi.draw(
              g,
              font,
              tag,
              getX() + getWidth() - DialogUi.width(font, tag) - 5,
              getY() + (height - 8) / 2,
              DialogUi.DIM,
              1.0F,
              false);
        }
      } else {
        DialogUi.reply(g, getX(), getY(), getWidth(), height, state, DialogUi.GOLD, 1.0F);
        int color = !active ? DialogUi.DIM : focus ? DialogUi.GOLD : DialogUi.TEXT;
        g.drawCenteredString(
            font, getMessage(), getX() + getWidth() / 2, getY() + (height - 8) / 2, color);
      }
    }
  }
}
