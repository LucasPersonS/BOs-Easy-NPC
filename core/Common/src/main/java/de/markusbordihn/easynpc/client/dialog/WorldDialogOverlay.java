/*
 * Copyright 2026 Markus Bordihn
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

package de.markusbordihn.easynpc.client.dialog;

import static de.markusbordihn.easynpc.client.dialog.WorldDialogStyle.*;

import de.markusbordihn.easynpc.client.dialog.DialogUi.ReplyState;
import de.markusbordihn.easynpc.condition.ClientConditionEvaluator;
import de.markusbordihn.easynpc.data.action.ActionDataType;
import de.markusbordihn.easynpc.data.action.ActionEventType;
import de.markusbordihn.easynpc.data.dialog.DialogButtonConditionMode;
import de.markusbordihn.easynpc.data.dialog.DialogButtonEntry;
import de.markusbordihn.easynpc.data.dialog.DialogDataEntry;
import de.markusbordihn.easynpc.data.dialog.DialogMetaData;
import de.markusbordihn.easynpc.data.dialog.DialogOptionsData;
import de.markusbordihn.easynpc.data.dialog.QuestDialogBridge;
import de.markusbordihn.easynpc.data.dialog.WorldDialogSessionManager;
import de.markusbordihn.easynpc.data.highlight.NPCHighlightManager;
import de.markusbordihn.easynpc.data.screen.AdditionalScreenData;
import de.markusbordihn.easynpc.data.screen.ScreenData;
import de.markusbordihn.easynpc.entity.LivingEntityManager;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.network.NetworkHandlerManager;
import de.markusbordihn.easynpc.network.NetworkMessageHandlerManager;
import de.markusbordihn.easynpc.network.components.TextComponent;
import de.markusbordihn.easynpc.network.message.server.BeginWorldDialogMessage;
import de.markusbordihn.easynpc.network.message.server.CloseWorldDialogMessage;
import de.markusbordihn.easynpc.utils.ItemUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * NPC dialogue shown over the game world:
 *
 * <ul>
 *   <li><b>Teaser</b>: walking up to an NPC (or looking at one) shows a compact preview box over
 *       its head in the website's style.
 *   <li><b>Prompt</b>: looking at an NPC that can talk outlines it and shows a right-mouse sprite
 *       next to the crosshair.
 *   <li><b>Full</b>: right-click expands into the fixed bottom dialogue panel matching the website's
 *       dialogue library ({@code Website/admin/dialogues.css}): 2D NPC face in a stone frame, gold
 *       speaker name, dark inset text box, and stacked stone reply buttons with coloured stripes.
 * </ul>
 */
public final class WorldDialogOverlay {

  private static DialogDataEntry cachedTextDialog;
  private static UUID cachedTextNpc;
  private static String cachedText = "";

  private enum Mode {
    TEASER,
    FULL
  }

  private static final int REQUEST_INTERVAL_TICKS = 30;
  private static final int CLOSE_GRACE_TICKS = 10;
  private static final int AIM_DWELL_TICKS = 4;
  private static final int CLICK_COOLDOWN_TICKS = 5;
  private static final int OUTLINE_TICKS = 2;

  private static final int PANEL_MAX_WIDTH = 300;
  private static final int PANEL_MIN_WIDTH = 200;
  private static final int PANEL_BOTTOM_CLEARANCE = 52;
  private static final int FULL_TEXT_MAX_LINES = 6;

  /** An NPC that did not answer an automatic request is not asked again for this long. */
  private static final long QUIET_NPC_MS = 30_000L;

  private static final long OPEN_ANIM_MS = 160L;
  private static final long PRESS_FLASH_MS = 320L;
  private static final long DENY_MS = 320L;
  private static final long PENDING_TIMEOUT_MS = 1500L;
  private static final long HOVER_SOUND_GAP_MS = 70L;
  private static final long REOPEN_GUARD_MS = 750L;
  private static final long PROMPT_CLICK_MS = 220L;
  private static final long PROMPT_PULSE_PERIOD_MS = 1400L;
  private static final long PROMPT_PULSE_MS = 140L;
  private static final float HOVER_RATE_MS = 70.0F;
  private static final float PROMPT_RATE_MS = 90.0F;

  // Session (mirrors the server's).
  private static ScreenData screenData;
  private static AdditionalScreenData additionalData;
  private static DialogDataEntry dialog;
  private static Mode mode = Mode.TEASER;
  private static boolean alternateAvailable;
  private static boolean npcDialogPresented;
  private static List<ChoiceView> choices = List.of();
  private static int choiceOffset;
  private static int selectedIndex;
  private static int textPage;
  private static int textPageCount = 1;
  private static int outOfRangeTicks;

  // Opening / closing bookkeeping.
  private static UUID suppressedNpc;
  private static UUID requestedNpc;
  private static UUID expandOnArrival;
  private static long expandOnArrivalUntilMs;
  private static final long EXPAND_WAIT_MS = 1500L;
  private static long nextRequestTick;
  private static long nextClickTick;
  private static UUID lastClosedNpc;
  private static long lastClosedAtMs;
  private static final Set<UUID> talkativeNpcs = new HashSet<>();
  // NPCs that had nothing to say (no dialogue, conditions, hidden ...), with the time to ask again.
  private static final Map<UUID, Long> quietNpcs = new HashMap<>();
  private static UUID awaitedNpc;
  private static long awaitedUntilTick;
  private static boolean engagedSent;

  // Aim.
  private static EasyNPC<?> aimedNpc;
  private static UUID aimedNpcId;
  private static int aimTicks;
  private static UUID promptNpc;
  private static Component promptLabel;
  private static float promptAlpha;
  private static long promptClickAtMs;
  private static boolean useHeld;
  private static boolean attackHeld;

  // Projection of the NPC's head, captured during world rendering.
  private static boolean anchorVisible;
  private static float anchorX;
  private static float anchorY;

  // Choice feedback.
  private static ChoiceView aimedChoice;
  private static final Map<UUID, Float> hoverProgress = new HashMap<>();
  private static UUID lastHoverId;
  private static long lastHoverSoundMs;
  private static UUID pressedId;
  private static long pressedAtMs;
  private static boolean pending;
  private static UUID deniedId;
  private static long deniedAtMs;
  private static long openedAtMs;
  private static long lastFrameMs;

  private WorldDialogOverlay() {}

  // ---------------------------------------------------------------------------------------------
  // Ticking
  // ---------------------------------------------------------------------------------------------

  /** Start of the client tick, before vanilla consumes key presses. */
  public static void preTick() {
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft.player == null) {
      return;
    }
    if (!minecraft.options.keyUse.isDown()) useHeld = false;
    if (!minecraft.options.keyAttack.isDown()) attackHeld = false;
    if (!isOpen() || mode != Mode.FULL || minecraft.screen != null || pending) {
      return;
    }
    if (hasMoreTextPages()) {
      return;
    }
    // Number keys pick choices while talking, instead of switching the hotbar slot.
    KeyMapping[] slots = minecraft.options.keyHotbarSlots;
    for (int index = 0; index < slots.length && index < choices.size(); index++) {
      boolean pressed = false;
      while (slots[index].consumeClick()) {
        pressed = true;
      }
      if (pressed) {
        selectedIndex = index;
        ensureVisible(index);
        choose(choices.get(index));
        return;
      }
    }
  }

  public static void tick() {
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft.player == null || minecraft.level == null) {
      clear();
      return;
    }

    // Editing and other screens own input. The chat may stay open over a conversation.
    boolean otherScreen = minecraft.screen != null && !(minecraft.screen instanceof ChatScreen);
    if (otherScreen || ItemUtils.isPlayerHoldingEasyNPCWand(minecraft.player)) {
      if (screenData != null) requestClose(false);
      suppressedNpc = null;
      setAim(null);
      promptNpc = null;
      return;
    }

    if (pending && Util.getMillis() - pressedAtMs > PENDING_TIMEOUT_MS) {
      pending = false;
    }
    setAim(
        minecraft.screen == null
            ? WorldDialogAim.pick(minecraft, WorldDialogSessionManager.AUTO_OPEN_RANGE)
            : null);
    long gameTime = minecraft.level.getGameTime();

    if (screenData != null) {
      if (tickOpenSession(minecraft)) {
        EasyNPC<?> active = LivingEntityManager.getClientEasyNPCEntityByUUID(screenData.uuid());
        choices = active != null ? collectChoices(minecraft, active) : List.of();
        selectedIndex = clampIndex(selectedIndex);
        choiceOffset = clampOffset(choiceOffset);
        // A teaser follows the player's gaze: looking at another NPC lets that one speak.
        if (mode == Mode.TEASER
            && aimedNpcId != null
            && aimTicks >= AIM_DWELL_TICKS
            && !aimedNpcId.equals(screenData.uuid())
            && !aimedNpcId.equals(suppressedNpc)) {
          closeQuietly();
          sendBegin(aimedNpcId, gameTime);
        }
      }
    } else {
      expireAwaitedRequest(gameTime);
      requestNearby(minecraft, gameTime);
    }

    updatePrompt();
    if (promptNpc != null) {
      NPCHighlightManager.highlight(promptNpc, OUTLINE_TICKS);
    }
  }

  /** Returns {@code false} when the session was closed. */
  private static boolean tickOpenSession(Minecraft minecraft) {
    EasyNPC<?> active = LivingEntityManager.getClientEasyNPCEntityByUUID(screenData.uuid());
    LivingEntity npc = active != null ? active.getLivingEntity() : null;
    if (npc == null || !npc.isAlive() || minecraft.player.level() != npc.level()) {
      requestClose(true);
      return false;
    }

    // Past the server's hard limit there is no point in waiting: the server closes it anyway.
    double distanceSqr = minecraft.player.distanceToSqr(npc);
    double hardRange =
        WorldDialogSessionManager.SESSION_RANGE + WorldDialogSessionManager.SESSION_RANGE_SLACK;
    if (distanceSqr > hardRange * hardRange) {
      requestClose(true);
      return false;
    }

    // Inside the slack band, or briefly behind a post: give it a moment before closing.
    double range = WorldDialogSessionManager.SESSION_RANGE;
    boolean comfortable = distanceSqr <= range * range && minecraft.player.hasLineOfSight(npc);
    outOfRangeTicks = comfortable ? 0 : outOfRangeTicks + 1;
    if (outOfRangeTicks >= CLOSE_GRACE_TICKS) {
      requestClose(true);
      return false;
    }
    return true;
  }

  /**
   * No session: ask for the aimed NPC, otherwise the nearest one. Only one request is sent at a
   * time; an NPC that does not answer is skipped for {@link #QUIET_NPC_MS} so the next one gets its
   * turn and the server is not asked about every NPC around the player every 30 ticks.
   */
  private static void requestNearby(Minecraft minecraft, long gameTime) {
    List<EasyNPC<?>> nearby = findNearby(minecraft);
    if (suppressedNpc != null
        && nearby.stream().noneMatch(npc -> suppressedNpc.equals(npc.getEntityUUID()))) {
      suppressedNpc = null;
    }

    long now = Util.getMillis();
    quietNpcs.values().removeIf(until -> until <= now);
    UUID target = null;
    if (aimedNpcId != null
        && aimTicks >= AIM_DWELL_TICKS
        && !aimedNpcId.equals(suppressedNpc)
        && !quietNpcs.containsKey(aimedNpcId)) {
      target = aimedNpcId;
    }
    for (int index = 0; target == null && index < nearby.size(); index++) {
      UUID id = nearby.get(index).getEntityUUID();
      if (!id.equals(suppressedNpc) && !quietNpcs.containsKey(id)) {
        target = id;
      }
    }
    if (target == null) {
      return;
    }
    if (!target.equals(requestedNpc) || gameTime >= nextRequestTick) {
      sendBegin(target, gameTime);
    }
  }

  private static void sendBegin(UUID target, long gameTime) {
    requestedNpc = target;
    nextRequestTick = gameTime + REQUEST_INTERVAL_TICKS;
    awaitedNpc = target;
    awaitedUntilTick = gameTime + REQUEST_INTERVAL_TICKS;
    NetworkHandlerManager.sendMessageToServer(
        new BeginWorldDialogMessage(target, BeginWorldDialogMessage.Kind.AUTO));
  }

  /** The awaited NPC did not open a teaser in time: it has nothing to say to us right now. */
  private static void expireAwaitedRequest(long gameTime) {
    if (awaitedNpc != null && gameTime >= awaitedUntilTick) {
      quietNpcs.put(awaitedNpc, Util.getMillis() + QUIET_NPC_MS);
      awaitedNpc = null;
    }
  }

  private static void setAim(EasyNPC<?> npc) {
    UUID id = npc != null ? npc.getEntityUUID() : null;
    aimTicks = id != null && id.equals(aimedNpcId) ? aimTicks + 1 : 0;
    aimedNpc = npc;
    aimedNpcId = id;
  }

  /** Outline + right-mouse prompt for the NPC under the crosshair, when right-click does something. */
  private static void updatePrompt() {
    promptNpc = null;
    promptLabel = null;
    if (aimedNpcId == null) {
      return;
    }
    boolean sessionNpc = isOpen() && aimedNpcId.equals(screenData.uuid());
    if (sessionNpc && mode == Mode.FULL) {
      if (alternateAvailable) {
        promptNpc = aimedNpcId;
        promptLabel = alternateLabel();
      }
      return;
    }
    if (sessionNpc || talkativeNpcs.contains(aimedNpcId)) {
      promptNpc = aimedNpcId;
      promptLabel = Component.translatable("easy_npc.world_dialog.talk");
    }
  }

  private static Component alternateLabel() {
    return Component.translatable(
        isQuestView() ? "easy_npc.world_dialog.show_dialog" : "easy_npc.world_dialog.show_quests");
  }

  // ---------------------------------------------------------------------------------------------
  // Session events from the server
  // ---------------------------------------------------------------------------------------------

  public static void open(ScreenData newScreenData) {
    if (newScreenData == null || newScreenData.uuid() == null || newScreenData.dialogId() == null) {
      return;
    }

    // The server may have sent this before it processed our close. Re-affirm the close.
    long now = Util.getMillis();
    if (expandOnArrival != null && now > expandOnArrivalUntilMs) {
      expandOnArrival = null;
    }
    if (screenData == null
        && newScreenData.uuid().equals(lastClosedNpc)
        && !newScreenData.uuid().equals(expandOnArrival)
        && now - lastClosedAtMs < REOPEN_GUARD_MS) {
      NetworkHandlerManager.sendMessageToServer(new CloseWorldDialogMessage(newScreenData.uuid()));
      return;
    }

    AdditionalScreenData newAdditionalData =
        new AdditionalScreenData(newScreenData.additionalData());
    DialogDataEntry newDialog =
        newAdditionalData.getDialogDataSet().getDialog(newScreenData.dialogId());
    if (newDialog == null) {
      return;
    }
    boolean newSession = screenData == null || !newScreenData.uuid().equals(screenData.uuid());
    boolean pageChanged =
        !newSession && (pending || !newScreenData.dialogId().equals(screenData.dialogId()));

    screenData = newScreenData;
    additionalData = newAdditionalData;
    dialog = newDialog;
    alternateAvailable =
        newAdditionalData.getData().getBoolean(AdditionalScreenData.WORLD_DIALOG_ALTERNATE_TAG);
    talkativeNpcs.add(newScreenData.uuid());
    quietNpcs.remove(newScreenData.uuid());
    if (newScreenData.uuid().equals(awaitedNpc)) {
      awaitedNpc = null;
    }
    requestedNpc = newScreenData.uuid();
    suppressedNpc = null;
    outOfRangeTicks = 0;
    pending = false;
    pressedId = null;
    deniedId = null;
    aimedChoice = null;

    if (newSession) {
      mode = newScreenData.uuid().equals(expandOnArrival) ? Mode.FULL : Mode.TEASER;
      npcDialogPresented = false;
      // A session opened by our click was requested with ENGAGE already.
      engagedSent = mode == Mode.FULL;
    }
    if (newScreenData.uuid().equals(expandOnArrival)) {
      expandOnArrival = null;
    }
    if (newSession || pageChanged) {
      openedAtMs = now;
      choiceOffset = 0;
      selectedIndex = 0;
      textPage = 0;
      textPageCount = 1;
      hoverProgress.clear();
      lastHoverId = null;
    }
    if (pageChanged && mode == Mode.FULL) {
      playSound(SoundEvents.BOOK_PAGE_TURN, 1.25F, 0.4F);
    }
    if (mode == Mode.FULL) {
      presentNpcDialog();
    }
  }

  public static void closeFromServer() {
    if (screenData != null) {
      suppressedNpc = screenData.uuid();
    }
    clearSession();
  }

  public static void clear() {
    clearSession();
    suppressedNpc = null;
    requestedNpc = null;
    expandOnArrival = null;
    nextRequestTick = 0L;
    lastClosedNpc = null;
    talkativeNpcs.clear();
    quietNpcs.clear();
    awaitedNpc = null;
    engagedSent = false;
    setAim(null);
    promptNpc = null;
    promptAlpha = 0.0F;
  }

  public static boolean isOpen() {
    return screenData != null && dialog != null;
  }

  private static boolean isQuestView() {
    return screenData != null && QuestDialogBridge.DIALOG_ID.equals(screenData.dialogId());
  }

  /** ON_OPEN_DIALOG fires once per session, when the NPC's own dialogue is first shown in full. */
  private static void presentNpcDialog() {
    if (npcDialogPresented || isQuestView()) {
      return;
    }
    npcDialogPresented = true;
    if (additionalData.getActionEventSet().hasActionEvent(ActionEventType.ON_OPEN_DIALOG)) {
      NetworkMessageHandlerManager.getServerHandler()
          .executeActionEvent(screenData.uuid(), ActionEventType.ON_OPEN_DIALOG);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Input
  // ---------------------------------------------------------------------------------------------

  /** Kept for callers that do not tell the buttons apart. */
  public static boolean click() {
    return click(false);
  }

  /**
   * Attack / use key. Returns {@code true} when the dialogue used the click (cancel vanilla).
   *
   * @param useKey {@code true} for right-click (use), {@code false} for left-click (attack)
   */
  public static boolean click(boolean useKey) {
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft.player == null || minecraft.level == null) {
      return false;
    }
    if (ItemUtils.isPlayerHoldingEasyNPCWand(minecraft.player)) {
      if (screenData != null) requestClose(false);
      return false;
    }
    long gameTime = minecraft.level.getGameTime();
    boolean freshPress = useKey ? !useHeld : !attackHeld;

    // 1) While there are more text pages in FULL mode, any click advances to the next text bubble.
    if (isOpen() && mode == Mode.FULL && hasMoreTextPages()) {
      markHeld(useKey);
      if (freshPress && gameTime >= nextClickTick) {
        nextClickTick = gameTime + CLICK_COOLDOWN_TICKS;
        advanceTextPage();
      }
      return true;
    }

    // 2) A choice directly under the crosshair takes either button.
    if (isOpen() && mode == Mode.FULL && aimedChoice != null) {
      markHeld(useKey);
      if (freshPress && !pending && gameTime >= nextClickTick) {
        nextClickTick = gameTime + CLICK_COOLDOWN_TICKS;
        choose(aimedChoice);
      }
      return true;
    }

    // 3) Right-click on an NPC.
    if (useKey && aimedNpc != null) {
      UUID target = aimedNpcId;
      boolean sessionNpc = isOpen() && target.equals(screenData.uuid());
      boolean talkative = sessionNpc || talkativeNpcs.contains(target);
      boolean act = freshPress && gameTime >= nextClickTick;

      if (sessionNpc) {
        if (mode == Mode.TEASER) {
          markHeld(true);
          if (act) {
            nextClickTick = gameTime + CLICK_COOLDOWN_TICKS;
            promptClickAtMs = Util.getMillis();
            expand();
          }
          return true;
        }
        if (alternateAvailable) {
          markHeld(true);
          if (act) {
            nextClickTick = gameTime + CLICK_COOLDOWN_TICKS;
            promptClickAtMs = Util.getMillis();
            playSound(SoundEvents.BOOK_PAGE_TURN, 0.95F, 0.45F);
            NetworkHandlerManager.sendMessageToServer(
                new BeginWorldDialogMessage(target, BeginWorldDialogMessage.Kind.ALTERNATE));
          }
          return true;
        }
        // In FULL mode without alternate view, right-clicking the NPC confirms the selected reply.
        if (!choices.isEmpty()) {
          markHeld(true);
          if (act && !pending) {
            nextClickTick = gameTime + CLICK_COOLDOWN_TICKS;
            choose(choices.get(clampIndex(selectedIndex)));
          }
          return true;
        }
        return true;
      }

      // Another NPC: leave the current one (if any) and open this one straight in full.
      markHeld(true);
      if (act) {
        nextClickTick = gameTime + CLICK_COOLDOWN_TICKS;
        promptClickAtMs = Util.getMillis();
        if (screenData != null) {
          if (mode == Mode.FULL) requestClose(true);
          else closeQuietly();
        }
        if (target.equals(suppressedNpc)) suppressedNpc = null;
        quietNpcs.remove(target);
        expandOnArrival = target;
        expandOnArrivalUntilMs = Util.getMillis() + EXPAND_WAIT_MS;
        requestedNpc = target;
        nextRequestTick = gameTime + REQUEST_INTERVAL_TICKS;
        // A click is talking: the server opens the dialogue and runs the NPC's interaction actions.
        NetworkHandlerManager.sendMessageToServer(
            new BeginWorldDialogMessage(target, BeginWorldDialogMessage.Kind.ENGAGE));
      }
      // An NPC we never heard talk keeps its vanilla interaction (trading, commands, ...).
      return talkative;
    }

    // 4) While FULL dialogue is open at the bottom, clicking confirms the selected reply (or closes if none).
    if (isOpen() && mode == Mode.FULL) {
      if (!choices.isEmpty()) {
        markHeld(useKey);
        if (freshPress && !pending && gameTime >= nextClickTick) {
          nextClickTick = gameTime + CLICK_COOLDOWN_TICKS;
          choose(choices.get(clampIndex(selectedIndex)));
        }
        return true;
      }
    }

    return false;
  }

  private static boolean hasMoreTextPages() {
    return textPage < textPageCount - 1;
  }

  private static void advanceTextPage() {
    if (textPage < textPageCount - 1) {
      textPage++;
      openedAtMs = Util.getMillis();
      playSound(SoundEvents.BOOK_PAGE_TURN, 1.2F, 0.4F);
    }
  }

  public static boolean scroll(double delta) {
    if (!isOpen() || mode != Mode.FULL || delta == 0.0D) {
      return false;
    }
    if (hasMoreTextPages()) {
      if (delta < 0.0D) {
        advanceTextPage();
        return true;
      }
      if (delta > 0.0D && textPage > 0) {
        textPage--;
        playSound(SoundEvents.BOOK_PAGE_TURN, 1.05F, 0.35F);
        return true;
      }
      return false;
    }
    if (choices.size() <= 1) {
      return false;
    }
    int step = delta < 0.0D ? 1 : -1;
    int next = clampIndex(selectedIndex + step);
    if (next != selectedIndex) {
      selectedIndex = next;
      ensureVisible(selectedIndex);
      playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 1.55F, 0.16F);
    }
    return true;
  }

  /** Up / Down arrows select replies; Enter / Space / Right advances text pages or confirms. */
  public static boolean onKey(int key, int action) {
    if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) {
      return false;
    }
    Minecraft minecraft = Minecraft.getInstance();
    if (!isOpen() || mode != Mode.FULL || minecraft.screen != null || pending) {
      return false;
    }
    if (key == GLFW.GLFW_KEY_LEFT && textPage > 0) {
      textPage--;
      playSound(SoundEvents.BOOK_PAGE_TURN, 1.05F, 0.35F);
      return true;
    }
    if (hasMoreTextPages()) {
      if (action == GLFW.GLFW_PRESS
          && (key == GLFW.GLFW_KEY_ENTER
              || key == GLFW.GLFW_KEY_KP_ENTER
              || key == GLFW.GLFW_KEY_SPACE
              || key == GLFW.GLFW_KEY_RIGHT
              || key == GLFW.GLFW_KEY_DOWN)) {
        advanceTextPage();
        return true;
      }
      return false;
    }
    if (choices.isEmpty()) {
      return false;
    }
    if (key == GLFW.GLFW_KEY_UP) {
      return scroll(1.0D);
    }
    if (key == GLFW.GLFW_KEY_DOWN) {
      return scroll(-1.0D);
    }
    if (action == GLFW.GLFW_PRESS
        && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
      choose(choices.get(clampIndex(selectedIndex)));
      return true;
    }
    return false;
  }

  private static void markHeld(boolean useKey) {
    if (useKey) useHeld = true;
    else attackHeld = true;
  }

  private static void expand() {
    mode = Mode.FULL;
    if (!engagedSent && screenData != null) {
      engagedSent = true;
      // The teaser becomes a conversation now; until here it did not count as talking.
      NetworkHandlerManager.sendMessageToServer(
          new BeginWorldDialogMessage(screenData.uuid(), BeginWorldDialogMessage.Kind.ENGAGE));
    }
    openedAtMs = Util.getMillis();
    choiceOffset = 0;
    selectedIndex = 0;
    textPage = 0;
    textPageCount = 1;
    hoverProgress.clear();
    lastHoverId = null;
    playSound(SoundEvents.BOOK_PAGE_TURN, 1.1F, 0.5F);
    presentNpcDialog();
  }

  private static void choose(ChoiceView choice) {
    long now = Util.getMillis();
    if (!choice.active()) {
      deniedId = choice.entry().id();
      deniedAtMs = now;
      playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.45F);
      return;
    }
    DialogButtonEntry entry = choice.entry();
    boolean questChoice = entry.label().startsWith("pq_");
    pressedId = entry.id();
    pressedAtMs = now;
    playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.3F, 0.35F);
    playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), questChoice ? 1.6F : 1.35F, 0.3F);

    if (!questChoice
        && additionalData.getActionEventSet().hasActionEvent(ActionEventType.ON_BUTTON_CLICK)) {
      NetworkMessageHandlerManager.getServerHandler()
          .executeActionEvent(screenData.uuid(), ActionEventType.ON_BUTTON_CLICK);
    }
    if (entry.hasActionData() || questChoice) {
      pending = true;
      NetworkMessageHandlerManager.getServerHandler()
          .executeDialogButtonAction(screenData.uuid(), screenData.dialogId(), entry.id());
    } else {
      requestClose(true);
    }
  }

  private static void ensureVisible(int index) {
    if (index < choiceOffset) {
      choiceOffset = index;
    } else if (index >= choiceOffset + MAX_VISIBLE_CHOICES) {
      choiceOffset = clampOffset(index - MAX_VISIBLE_CHOICES + 1);
    }
  }

  private static int clampIndex(int index) {
    return choices.isEmpty() ? 0 : Math.max(0, Math.min(index, choices.size() - 1));
  }

  private static int clampOffset(int offset) {
    return Math.max(0, Math.min(offset, Math.max(0, choices.size() - MAX_VISIBLE_CHOICES)));
  }

  /** Ends the conversation: the NPC stays quiet until the player walks away or asks again. */
  private static void requestClose(boolean fireEvent) {
    if (screenData == null) {
      return;
    }
    UUID npcUUID = screenData.uuid();
    if (fireEvent
        && npcDialogPresented
        && additionalData != null
        && additionalData.getActionEventSet().hasActionEvent(ActionEventType.ON_CLOSE_DIALOG)) {
      NetworkMessageHandlerManager.getServerHandler()
          .executeActionEvent(npcUUID, ActionEventType.ON_CLOSE_DIALOG);
    }
    sendClose(npcUUID);
    suppressedNpc = npcUUID;
    clearSession();
  }

  /** Drops a teaser without muting the NPC (the player just looked at someone else). */
  private static void closeQuietly() {
    if (screenData == null) {
      return;
    }
    sendClose(screenData.uuid());
    clearSession();
  }

  private static void sendClose(UUID npcUUID) {
    NetworkHandlerManager.sendMessageToServer(new CloseWorldDialogMessage(npcUUID));
    lastClosedNpc = npcUUID;
    lastClosedAtMs = Util.getMillis();
  }

  private static void clearSession() {
    screenData = null;
    additionalData = null;
    dialog = null;
    mode = Mode.TEASER;
    alternateAvailable = false;
    npcDialogPresented = false;
    choices = List.of();
    choiceOffset = 0;
    selectedIndex = 0;
    textPage = 0;
    textPageCount = 1;
    aimedChoice = null;
    outOfRangeTicks = 0;
    pending = false;
    pressedId = null;
    deniedId = null;
    lastHoverId = null;
    anchorVisible = false;
    hoverProgress.clear();
  }

  // ---------------------------------------------------------------------------------------------
  // Rendering
  // ---------------------------------------------------------------------------------------------

  /**
   * Called during world rendering (any stage after the camera is set up) with the frame's matrices.
   */
  public static void captureView(
      Camera camera, Matrix4f modelView, Matrix4f projection, float partialTick) {
    anchorVisible = false;
    if (!isOpen()) {
      return;
    }
    EasyNPC<?> easyNPC = LivingEntityManager.getClientEasyNPCEntityByUUID(screenData.uuid());
    LivingEntity npc = easyNPC != null ? easyNPC.getLivingEntity() : null;
    if (npc == null || !npc.isAlive()) {
      return;
    }
    float[] ndc =
        WorldDialogAim.project(
            WorldDialogAim.headAnchor(npc, partialTick), camera, modelView, projection);
    if (ndc != null) {
      anchorX = ndc[0];
      anchorY = ndc[1];
      anchorVisible = true;
    }
  }

  /** GUI layer: teaser over the NPC's head, full dialogue panel at the bottom, prompt at crosshair. */
  public static void renderHud(GuiGraphics graphics, DeltaTracker deltaTracker) {
    Minecraft minecraft = Minecraft.getInstance();
    aimedChoice = null;
    if (minecraft.player == null || minecraft.options.hideGui) {
      return;
    }
    if (minecraft.screen != null && !(minecraft.screen instanceof ChatScreen)) {
      return;
    }
    long now = Util.getMillis();
    float frameMs = lastFrameMs == 0L ? 16.0F : Math.min(100.0F, now - lastFrameMs);
    lastFrameMs = now;

    Font font = minecraft.font;
    int screenWidth = graphics.guiWidth();
    int screenHeight = graphics.guiHeight();
    int crosshairX = screenWidth / 2;
    int crosshairY = screenHeight / 2;
    boolean canAim = minecraft.screen == null;

    if (isOpen()) {
      EasyNPC<?> easyNPC = LivingEntityManager.getClientEasyNPCEntityByUUID(screenData.uuid());
      if (easyNPC != null && easyNPC.getLivingEntity() != null) {
        float open = WorldDialogHud.clamp01((now - openedAtMs) / (float) OPEN_ANIM_MS);
        float eased = 1.0F - (1.0F - open) * (1.0F - open) * (1.0F - open);
        int rise = Math.round(3.0F * (1.0F - eased));
        if (mode == Mode.TEASER) {
          if (anchorVisible) {
            int headX = Math.round((anchorX * 0.5F + 0.5F) * screenWidth);
            int headY = Math.round((0.5F - anchorY * 0.5F) * screenHeight);
            renderTeaser(graphics, font, minecraft, easyNPC, headX, headY + rise, screenWidth,
                screenHeight, eased);
          }
        } else {
          renderFull(graphics, font, minecraft, easyNPC, rise, screenWidth, screenHeight,
              canAim ? crosshairX : Integer.MIN_VALUE, crosshairY, eased, now, frameMs);
        }
      }
    }

    // Prompt next to the crosshair; hidden while a choice is aimed (the click picks the choice).
    boolean showPrompt = canAim && promptNpc != null && promptLabel != null && aimedChoice == null;
    float approach = Math.min(1.0F, frameMs / PROMPT_RATE_MS);
    promptAlpha += ((showPrompt ? 1.0F : 0.0F) - promptAlpha) * approach;
    if (showPrompt && promptAlpha > 0.02F) {
      boolean pressed =
          now - promptClickAtMs < PROMPT_CLICK_MS
              || (now % PROMPT_PULSE_PERIOD_MS) < PROMPT_PULSE_MS;
      WorldDialogHud.prompt(graphics, font, crosshairX + 6, crosshairY + 4, promptLabel, pressed,
          true, promptAlpha);
    }
  }

  private static void renderTeaser(
      GuiGraphics graphics, Font font, Minecraft minecraft, EasyNPC<?> easyNPC, int headX,
      int headY, int screenWidth, int screenHeight, float alpha) {
    if (headX < -40 || headX > screenWidth + 40 || headY < 0 || headY > screenHeight + 60) {
      return;
    }
    String name = WorldDialogHud.ellipsize(font, easyNPC.getLivingEntity().getName().getString(),
        TEASER_WIDTH - 2 * PAD, false);
    List<String> lines =
        WorldDialogHud.wrap(font, dialogText(easyNPC, minecraft), TEASER_WIDTH - 2 * PAD,
            TEASER_LINES);
    int width = Math.max(WorldDialogHud.maxWidth(font, lines), DialogUi.width(font, name)) + 2 * PAD;
    int height = PAD + 10 + lines.size() * LINE + PAD - 3;
    int x = clamp(headX - width / 2, SCREEN_MARGIN, screenWidth - width - SCREEN_MARGIN);
    int y = Math.max(SCREEN_MARGIN, headY - DialogUi.TAIL_HEIGHT - height);

    DialogUi.textBox(graphics, x, y, width, height, alpha);
    int tailX = clamp(headX, x + 6, x + width - 6);
    DialogUi.tail(graphics, tailX, y + height, alpha);

    WorldDialogHud.textShadow(graphics, font, name, x + PAD, y + PAD - 2, DialogUi.GOLD, alpha);
    for (int line = 0; line < lines.size(); line++) {
      WorldDialogHud.text(graphics, font, lines.get(line), x + PAD, y + PAD + 9 + line * LINE,
          DialogUi.TEXT, alpha);
    }
  }

  private static void renderFull(
      GuiGraphics graphics, Font font, Minecraft minecraft, EasyNPC<?> easyNPC, int rise,
      int screenWidth, int screenHeight, int crosshairX, int crosshairY, float alpha, long now,
      float frameMs) {
    int panelWidth =
        Math.max(PANEL_MIN_WIDTH, Math.min(PANEL_MAX_WIDTH, screenWidth - 2 * SCREEN_MARGIN - 16));
    int innerWidth = panelWidth - 2 * DialogUi.PAD;
    int textBoxPad = 5;
    List<FormattedCharSequence> lines =
        DialogUi.split(font, dialogText(easyNPC, minecraft), innerWidth - 2 * textBoxPad);
    textPageCount = Math.max(1, (lines.size() + FULL_TEXT_MAX_LINES - 1) / FULL_TEXT_MAX_LINES);
    textPage = Math.max(0, Math.min(textPage, textPageCount - 1));
    boolean moreTextPages = textPage < textPageCount - 1;
    int lineStart = textPage * FULL_TEXT_MAX_LINES;
    int lineEnd = Math.min(lines.size(), lineStart + FULL_TEXT_MAX_LINES);
    int shownLines = Math.max(1, lineEnd - lineStart);
    int textBoxHeight = 4 + shownLines * DialogUi.LINE + 4 + (textPageCount > 1 ? 8 : 0);

    List<ChoiceLayout> layouts = new ArrayList<>();
    int end = Math.min(choices.size(), choiceOffset + MAX_VISIBLE_CHOICES);
    int repliesHeight = 0;
    if (!moreTextPages) {
      for (int index = choiceOffset; index < end; index++) {
        ChoiceView choice = choices.get(index);
        Component label =
            TextComponent.getTextComponentRaw(choice.entry().name(), choice.entry().isTranslationKey());
        boolean merchant =
            choice.entry().actionDataSet().hasActionDataType(ActionDataType.OPEN_TRADING_SCREEN);
        int tagSpace = merchant ? 48 : 6;
        List<String> labelLines =
            DialogUi.wrap(font, label.getString(), innerWidth - DialogUi.LABEL_X - tagSpace, 4);
        int rowH = DialogUi.rowHeight(labelLines.size());
        layouts.add(new ChoiceLayout(choice, index, labelLines, merchant, 0, repliesHeight, rowH));
        repliesHeight += rowH + DialogUi.ROW_GAP;
      }
    }
    boolean hasChoices = !layouts.isEmpty();
    if (hasChoices) {
      repliesHeight -= DialogUi.ROW_GAP;
    } else if (!moreTextPages) {
      repliesHeight = 12;
    }

    int headerHeight = DialogUi.FACE_SIZE;
    boolean hasOverflow = !moreTextPages && choices.size() > MAX_VISIBLE_CHOICES;
    int footerHeight = hasOverflow ? 9 : 0;
    int panelHeight =
        DialogUi.PAD
            + headerHeight
            + 4
            + textBoxHeight
            + (repliesHeight > 0 ? 4 + repliesHeight : 0)
            + (hasOverflow ? 2 + footerHeight : 0)
            + DialogUi.PAD;

    int panelX = (screenWidth - panelWidth) / 2;
    int maxBottom = screenHeight - PANEL_BOTTOM_CLEARANCE;
    int panelY = Math.max(SCREEN_MARGIN, maxBottom - panelHeight) + rise;

    int headerX = panelX + DialogUi.PAD;
    int headerY = panelY + DialogUi.PAD;
    int textBoxX = headerX;
    int textBoxY = headerY + headerHeight + 4;
    int repliesX = headerX;
    int repliesY = textBoxY + textBoxHeight + 4;

    ChoiceLayout hovered = null;
    for (ChoiceLayout layout : layouts) {
      int x = repliesX;
      int y = repliesY + layout.y();
      if (crosshairX >= x - 1 && crosshairX <= x + innerWidth + 1
          && crosshairY >= y - 1 && crosshairY <= y + layout.height() + 1) {
        hovered = layout;
        break;
      }
    }
    if (hovered != null) {
      selectedIndex = hovered.index();
    }
    selectedIndex = clampIndex(selectedIndex);
    aimedChoice = hovered != null ? hovered.choice() : null;

    ChoiceView activeFocus =
        hovered != null
            ? hovered.choice()
            : (!choices.isEmpty() ? choices.get(selectedIndex) : null);
    UUID focusId = activeFocus != null ? activeFocus.entry().id() : null;
    if (focusId != null && !focusId.equals(lastHoverId) && activeFocus.active() && !pending
        && now - lastHoverSoundMs > HOVER_SOUND_GAP_MS) {
      playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 1.8F, 0.18F);
      lastHoverSoundMs = now;
    }
    lastHoverId = focusId;

    DialogUi.panel(graphics, panelX, panelY, panelWidth, panelHeight, alpha);
    LivingEntity npcEntity = easyNPC.getLivingEntity();
    DialogUi.face(graphics, npcEntity, headerX, headerY, alpha);

    int alternateWidth = 0;
    if (alternateAvailable) {
      Component alt = alternateLabel();
      alternateWidth = 18 + DialogUi.width(font, alt) + 4;
      WorldDialogHud.prompt(
          graphics,
          font,
          panelX + panelWidth - DialogUi.PAD - alternateWidth + 2,
          headerY + 2,
          alt,
          now - promptClickAtMs < PROMPT_CLICK_MS,
          false,
          alpha);
    }

    String speakerName =
        DialogUi.ellipsize(
            font,
            npcEntity.getName().getString(),
            innerWidth - DialogUi.FACE_SIZE - 8 - alternateWidth,
            false);
    DialogUi.draw(
        graphics,
        font,
        speakerName,
        headerX + DialogUi.FACE_SIZE + 6,
        headerY + 6,
        DialogUi.GOLD,
        alpha,
        true);

    DialogUi.textBox(graphics, textBoxX, textBoxY, innerWidth, textBoxHeight, alpha);
    for (int line = 0; line < shownLines && (lineStart + line) < lines.size(); line++) {
      FormattedCharSequence text = lines.get(lineStart + line);
      int lineY = textBoxY + 4 + line * DialogUi.LINE;
      DialogUi.draw(graphics, font, text, textBoxX + textBoxPad, lineY, DialogUi.TEXT, alpha);
    }
    if (textPageCount > 1) {
      int pageY = textBoxY + textBoxHeight - 10;
      String pageNum = (textPage + 1) + "/" + textPageCount;
      DialogUi.draw(
          graphics, font, pageNum, textBoxX + textBoxPad, pageY, DialogUi.DIM, alpha, false);
      if (moreTextPages) {
        int bounce = (now / 350L) % 2L == 0L ? 0 : 1;
        DialogUi.draw(
            graphics,
            font,
            "▸",
            textBoxX + innerWidth - textBoxPad - 5 + bounce,
            pageY,
            DialogUi.GOLD,
            alpha,
            false);
      }
    }

    if (moreTextPages) {
      return;
    }

    if (!hasChoices) {
      String endText = "— fim da conversa —";
      DialogUi.draw(
          graphics,
          font,
          endText,
          panelX + (panelWidth - DialogUi.width(font, endText)) / 2,
          repliesY + 2,
          DialogUi.DIM,
          alpha,
          false);
      return;
    }

    float approach = Math.min(1.0F, frameMs / HOVER_RATE_MS);
    for (ChoiceLayout layout : layouts) {
      ChoiceView choice = layout.choice();
      UUID id = choice.entry().id();
      boolean pressed = id.equals(pressedId);
      boolean confirming = pressed && pending;
      boolean dimmed = pending && !pressed;
      boolean locked = !choice.active();
      boolean isSelected = layout.index() == selectedIndex && !dimmed;
      float target = isSelected && choice.active() && !pending ? 1.0F : 0.0F;
      float hover = hoverProgress.getOrDefault(id, 0.0F);
      hover += (target - hover) * approach;
      hoverProgress.put(id, hover);
      if (confirming) hover = 1.0F;
      float deny =
          id.equals(deniedId) ? 1.0F - WorldDialogHud.clamp01((now - deniedAtMs) / (float) DENY_MS)
              : 0.0F;
      int shake =
          deny > 0.0F ? Math.round((float) Math.sin((now - deniedAtMs) / 18.0D) * 3.0F * deny) : 0;

      int x = repliesX + Math.round(2.0F * hover) + shake;
      int y = repliesY + layout.y();
      int h = layout.height();

      ReplyState state =
          locked || dimmed
              ? ReplyState.LOCKED
              : pressed
                  ? ReplyState.PRESSED
                  : isSelected
                      ? ReplyState.HOVER
                      : ReplyState.IDLE;
      int stripe =
          deny > 0.0F
              ? WorldDialogHud.lerp(DialogUi.segment(layout.index()), DialogUi.DENY, deny)
              : DialogUi.segment(layout.index());
      DialogUi.reply(graphics, x, y, innerWidth, h, state, stripe, alpha);

      boolean usable = !locked && !dimmed;
      String number = Integer.toString(layout.index() + 1);
      DialogUi.keycap(graphics, font, x + 5, y + 2, number, isSelected && usable, usable, alpha);

      int labelColor =
          !usable
              ? DialogUi.DIM
              : isSelected
                  ? DialogUi.GOLD
                  : DialogUi.TEXT;
      for (int line = 0; line < layout.lines().size(); line++) {
        String prefix = line == 0 ? "▸ " : "  ";
        DialogUi.draw(
            graphics,
            font,
            prefix + layout.lines().get(line),
            x + DialogUi.LABEL_X,
            y + 3 + line * DialogUi.LINE,
            labelColor,
            alpha,
            false);
      }

      if (layout.merchant()) {
        String tag = "comércio";
        DialogUi.draw(
            graphics,
            font,
            tag,
            x + innerWidth - DialogUi.width(font, tag) - 5,
            y + 3,
            DialogUi.DIM,
            alpha,
            false);
      }
    }

    if (hasOverflow) {
      int footerY = repliesY + repliesHeight + 2;
      String arrows = (choiceOffset > 0 ? "▲" : "") + (choiceOffset > 0 && end < choices.size() ? " " : "") + (end < choices.size() ? "▼" : "");
      DialogUi.draw(
          graphics,
          font,
          arrows,
          panelX + (panelWidth - DialogUi.width(font, arrows)) / 2,
          footerY,
          DialogUi.GOLD,
          alpha,
          false);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Data
  // ---------------------------------------------------------------------------------------------

  private static List<EasyNPC<?>> findNearby(Minecraft minecraft) {
    double range = WorldDialogSessionManager.AUTO_OPEN_RANGE;
    return LivingEntityManager.getClientEasyNPCEntities()
        .filter(
            easyNPC -> {
              LivingEntity npc = easyNPC.getLivingEntity();
              return npc != null
                  && npc.isAlive()
                  && minecraft.player.level() == npc.level()
                  && minecraft.player.distanceToSqr(npc) <= range * range
                  && !npc.isInvisibleTo(minecraft.player)
                  && minecraft.player.hasLineOfSight(npc);
            })
        .sorted(
            Comparator.comparingDouble(
                easyNPC -> minecraft.player.distanceToSqr(easyNPC.getLivingEntity())))
        .toList();
  }

  private static String dialogText(EasyNPC<?> easyNPC, Minecraft minecraft) {
    if (dialog == cachedTextDialog && easyNPC.getEntityUUID().equals(cachedTextNpc)) {
      return cachedText;
    }
    DialogMetaData metadata =
        new DialogMetaData(
            easyNPC.getLivingEntity(), minecraft.player, additionalData.getScoreboardData());
    String text = dialog.getDialogText(metadata);
    cachedTextDialog = dialog;
    cachedTextNpc = easyNPC.getEntityUUID();
    cachedText = text != null && !text.isBlank() ? text : "…";
    return cachedText;
  }

  private static List<ChoiceView> collectChoices(Minecraft minecraft, EasyNPC<?> easyNPC) {
    List<ChoiceView> visible = new ArrayList<>();
    DialogOptionsData options = dialog.getDialogOptions();
    for (DialogButtonEntry entry : dialog.getDialogButtons()) {
      if (entry == null || entry.name() == null || entry.name().isBlank()) {
        continue;
      }
      boolean unavailable =
          (entry.hasConditions()
                  && !ClientConditionEvaluator.evaluateAll(
                      entry.conditions(), minecraft.player, easyNPC.getLivingEntity()))
              || additionalData.isExecutionLimitReached(dialog.getId(), entry.id())
              || additionalData.isDialogButtonLocked(dialog.getId(), entry.id());
      if (unavailable && options.buttonConditionMode() == DialogButtonConditionMode.HIDE) {
        continue;
      }
      visible.add(new ChoiceView(entry, !unavailable));
    }
    return visible;
  }

  private static int clamp(int value, int min, int max) {
    return max < min ? min : Math.max(min, Math.min(max, value));
  }

  private static void playSound(SoundEvent sound, float pitch, float volume) {
    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
  }

  private record ChoiceView(DialogButtonEntry entry, boolean active) {}

  private record ChoiceLayout(
      ChoiceView choice,
      int index,
      List<String> lines,
      boolean merchant,
      int x,
      int y,
      int height) {}
}
