package de.markusbordihn.easynpc.paravexia.village;

import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.data.skin.SkinDataEntry;
import de.markusbordihn.easynpc.data.skin.SkinType;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.entity.easynpc.data.SkinDataCapable;
import de.markusbordihn.easynpc.handler.SkinHandler;
import de.markusbordihn.easynpc.utils.PlayersUtils;
import de.markusbordihn.easynpc.validator.NameValidator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Mob;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Applies the {@code skin} field of a village NPC: a texture shipped in a jar/resource pack
 * ({@code namespace:textures/....png}) or a Minecraft player name.
 */
public final class VillageSkins {

  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);
  private static final Set<UUID> PENDING_PLAYER_LOOKUPS = ConcurrentHashMap.newKeySet();

  private VillageSkins() {}

  public enum Kind {
    TEXTURE,
    PLAYER,
    INVALID
  }

  public record Parsed(Kind kind, ResourceLocation texture, String playerName) {}

  public static Parsed parse(String raw) {
    String value = raw == null ? "" : raw.trim();
    if (value.contains(":") || value.endsWith(".png")) {
      ResourceLocation texture = ResourceLocation.tryParse(value);
      if (texture != null
          && texture.getPath().startsWith("textures/")
          && texture.getPath().endsWith(".png")) {
        return new Parsed(Kind.TEXTURE, texture, "");
      }
      return new Parsed(Kind.INVALID, null, "");
    }
    if (NameValidator.isValidPlayerName(value)) {
      return new Parsed(Kind.PLAYER, null, value);
    }
    return new Parsed(Kind.INVALID, null, "");
  }

  /**
   * Applies the skin when it differs from the current one. Player names are resolved off the server
   * thread (the profile lookup may hit the network) and applied back on it.
   *
   * @return true when a texture skin was changed right away
   */
  public static boolean apply(EasyNPC<?> easyNPC, Optional<String> skin, MinecraftServer server) {
    if (easyNPC == null || skin.isEmpty() || easyNPC.isClientSideInstance()) {
      return false;
    }
    SkinDataCapable<?> skinData = easyNPC.getEasyNPCSkinData();
    if (skinData == null) {
      return false;
    }
    Parsed parsed = parse(skin.get());
    SkinDataEntry current = skinData.getSkinDataEntry();
    switch (parsed.kind()) {
      case TEXTURE -> {
        if (current.type() == SkinType.RESOURCE_LOCATION && parsed.texture().equals(current.texture())) {
          return false;
        }
        return SkinHandler.setSkin(easyNPC, SkinDataEntry.createResourceLocationSkin(parsed.texture()));
      }
      case PLAYER -> {
        if (current.type() == SkinType.PLAYER_SKIN && parsed.playerName().equalsIgnoreCase(current.name())) {
          return false;
        }
        requestPlayerSkin(easyNPC, parsed.playerName(), server);
        return false;
      }
      default -> {
        return false;
      }
    }
  }

  private static void requestPlayerSkin(EasyNPC<?> easyNPC, String playerName, MinecraftServer server) {
    UUID npcUUID = easyNPC.getEntityUUID();
    if (server == null || !PENDING_PLAYER_LOOKUPS.add(npcUUID)) {
      return;
    }
    CompletableFuture.supplyAsync(() -> PlayersUtils.getUserUUID(server, playerName), Util.backgroundExecutor())
        .whenCompleteAsync(
            (playerUUID, error) -> {
              PENDING_PLAYER_LOOKUPS.remove(npcUUID);
              Mob mob = easyNPC.getMob();
              if (error != null || playerUUID == null || mob == null || mob.isRemoved()) {
                log.debug("[Villages] Unable to resolve player skin {} for {}.", playerName, npcUUID);
                return;
              }
              SkinHandler.setSkin(easyNPC, SkinDataEntry.createPlayerSkin(playerName, playerUUID));
            },
            server);
  }
}
