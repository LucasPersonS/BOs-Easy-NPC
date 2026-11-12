package de.markusbordihn.easynpc.data.dialog;

import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Optional server integration; EasyNPC continues to work without the quest mod. */
public final class QuestDialogBridge {
  public static final UUID DIALOG_ID = new DialogDataEntry("paravexia_quest", "Missões", "Missões").getId();
  private static final String CLASS = "com.luppz.paravexia.story.StoryQuestHadesBridge";
  private static Method available, decorate, choose;
  private static boolean resolved;

  private QuestDialogBridge() {}

  private static synchronized void resolve() {
    if (resolved) return;
    resolved = true;
    try {
      Class<?> bridge = Class.forName(CLASS);
      available = bridge.getMethod("available", ServerPlayer.class, UUID.class);
      decorate = bridge.getMethod("decorate", ServerPlayer.class, UUID.class, DialogDataSet.class);
      choose = bridge.getMethod("choose", ServerPlayer.class, UUID.class, UUID.class, UUID.class);
    } catch (ReflectiveOperationException ignored) {
      // The companion quest mod is optional.
    }
  }

  public static boolean available(ServerPlayer player, UUID npc) {
    resolve();
    try { return available != null && Boolean.TRUE.equals(available.invoke(null, player, npc)); }
    catch (ReflectiveOperationException ignored) { return false; }
  }

  public static DialogDataSet decorate(ServerPlayer player, UUID npc, DialogDataSet base) {
    resolve();
    try { return decorate != null ? (DialogDataSet) decorate.invoke(null, player, npc, base) : base; }
    catch (ReflectiveOperationException ignored) { return base; }
  }

  public static boolean choose(ServerPlayer player, UUID npc, UUID dialog, UUID button) {
    resolve();
    try { return choose != null && Boolean.TRUE.equals(choose.invoke(null, player, npc, dialog, button)); }
    catch (ReflectiveOperationException ignored) { return false; }
  }
}
