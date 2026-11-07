package de.markusbordihn.easynpc.paravexia.village;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.serialization.DataResult;
import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.commands.Command;
import de.markusbordihn.easynpc.commands.arguments.EasyNPCArgument;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.core.BlockPos;

/** /easy_npc village … — staff tools for the Paravexia village runtime (permission level 2). */
public final class VillageCommand extends Command {

  public static final Path VILLAGE_DIRECTORY =
      Constants.CONFIG_DIR.resolve(Constants.MOD_ID).resolve("villages");

  private static final Pattern FILE_PATTERN = Pattern.compile("[a-z0-9_\\-]{1,64}");
  private static final String VILLAGE_ARG = "village";
  private static final String ID_ARG = "id";
  private static final String RADIUS_ARG = "radius";
  private static final String POI_ARG = "poi";
  private static final String PROFILE_ARG = "profile";
  private static final String START_ARG = "dayTimeStart";
  private static final String FILE_ARG = "file";
  private static final String NPC_KEY_ARG = "npc";

  private static final SuggestionProvider<CommandSourceStack> VILLAGES =
      (context, builder) ->
          SharedSuggestionProvider.suggest(
              VillageRuntime.data()
                  .map(data -> data.villages().stream().map(village -> village.spec().id()).toList())
                  .orElse(java.util.List.of()),
              builder);
  // Lists config/easy_npc/villages/*.json so staff can Tab-complete the file to import.
  private static final SuggestionProvider<CommandSourceStack> FILES =
      (context, builder) -> SharedSuggestionProvider.suggest(listVillageFiles(), builder);
  private static final SuggestionProvider<CommandSourceStack> POIS =
      (context, builder) ->
          SharedSuggestionProvider.suggest(
              village(context).map(village -> village.spec().pois().stream().map(VillageSpec.Poi::id).toList())
                  .orElse(java.util.List.of()),
              builder);
  private static final SuggestionProvider<CommandSourceStack> NPC_KEYS =
      (context, builder) -> {
        java.util.List<String> keys = new java.util.ArrayList<>();
        keys.add("all");
        village(context).ifPresent(village -> village.spec().npcs().forEach(npc -> keys.add(npc.key())));
        return SharedSuggestionProvider.suggest(keys, builder);
      };
  private static final SuggestionProvider<CommandSourceStack> PROFILES =
      (context, builder) ->
          SharedSuggestionProvider.suggest(
              Arrays.stream(VillageProfile.values()).map(VillageProfile::serializedName), builder);
  private static final SuggestionProvider<CommandSourceStack> POI_TYPES =
      (context, builder) ->
          SharedSuggestionProvider.suggest(
              Arrays.stream(PoiType.values()).map(PoiType::serializedName), builder);

  private VillageCommand() {}

  public static ArgumentBuilder<CommandSourceStack, ?> register() {
    return Commands.literal("village")
        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
        .then(
            Commands.literal("create")
                .then(
                    Commands.argument(ID_ARG, StringArgumentType.word())
                        .then(
                            Commands.argument(RADIUS_ARG, IntegerArgumentType.integer(4, 128))
                                .executes(VillageCommand::create))))
        .then(
            Commands.literal("remove")
                .then(
                    villageArgument()
                        .executes(context -> remove(context, false))
                        .then(Commands.literal("delete_npcs").executes(context -> remove(context, true)))))
        .then(
            Commands.literal("poi")
                .then(
                    Commands.literal("add")
                        .then(
                            villageArgument()
                                .then(
                                    Commands.argument(POI_ARG, StringArgumentType.word())
                                        .then(
                                            Commands.argument("type", StringArgumentType.word())
                                                .suggests(POI_TYPES)
                                                .executes(VillageCommand::addPoi)))))
                .then(
                    Commands.literal("set")
                        .then(
                            villageArgument()
                                .then(
                                    Commands.argument(POI_ARG, StringArgumentType.word())
                                        .suggests(POIS)
                                        .executes(VillageCommand::setPoi))))
                .then(Commands.literal("list").then(villageArgument().executes(VillageCommand::listPois))))
        .then(
            Commands.literal("respawn")
                .then(
                    villageArgument()
                        .then(
                            Commands.argument(NPC_KEY_ARG, StringArgumentType.word())
                                .suggests(NPC_KEYS)
                                .executes(VillageCommand::respawn))))
        .then(
            Commands.literal("npc")
                .then(
                    Commands.literal("add")
                        .then(
                            villageArgument()
                                .then(
                                    Commands.argument(NPC_TARGET_ARG, EasyNPCArgument.npc())
                                        .then(
                                            Commands.argument(PROFILE_ARG, StringArgumentType.word())
                                                .suggests(PROFILES)
                                                .then(
                                                    Commands.argument(POI_ARG, StringArgumentType.word())
                                                        .executes(VillageCommand::addNpc))))))
                .then(
                    Commands.literal("remove")
                        .then(
                            villageArgument()
                                .then(
                                    Commands.argument(NPC_TARGET_ARG, EasyNPCArgument.npc())
                                        .executes(VillageCommand::removeNpc)))))
        .then(
            Commands.literal("schedule")
                .then(
                    Commands.literal("set")
                        .then(
                            villageArgument()
                                .then(
                                    Commands.argument(NPC_TARGET_ARG, EasyNPCArgument.npc())
                                        .then(
                                            Commands.argument(START_ARG, IntegerArgumentType.integer(0, 23999))
                                                .then(
                                                    Commands.argument(POI_ARG, StringArgumentType.word())
                                                        .executes(VillageCommand::setSchedule)))))))
        .then(Commands.literal("wake").then(villageArgument().executes(context -> force(context, true))))
        .then(Commands.literal("sleep").then(villageArgument().executes(context -> force(context, false))))
        .then(Commands.literal("auto").then(villageArgument().executes(context -> force(context, null))))
        .then(
            Commands.literal("status")
                .executes(context -> status(context, null))
                .then(
                    villageArgument()
                        .executes(context -> status(context, StringArgumentType.getString(context, VILLAGE_ARG)))))
        .then(
            Commands.literal("import")
                .then(
                    Commands.argument(FILE_ARG, StringArgumentType.word())
                        .suggests(FILES)
                        .executes(context -> importFile(context, false))
                        .then(Commands.literal("here").executes(context -> importFile(context, true)))));
  }

  private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>
      villageArgument() {
    return Commands.argument(VILLAGE_ARG, StringArgumentType.word()).suggests(VILLAGES);
  }

  private static int create(CommandContext<CommandSourceStack> context) {
    CommandSourceStack source = context.getSource();
    return feedback(
        source,
        VillageService.create(
            StringArgumentType.getString(context, ID_ARG),
            source.getLevel(),
            BlockPos.containing(source.getPosition()),
            IntegerArgumentType.getInteger(context, RADIUS_ARG)));
  }

  private static int remove(CommandContext<CommandSourceStack> context, boolean deleteNpcs) {
    return feedback(
        context.getSource(),
        VillageService.remove(StringArgumentType.getString(context, VILLAGE_ARG), deleteNpcs));
  }

  private static int addPoi(CommandContext<CommandSourceStack> context) {
    DataResult<PoiType> type = PoiType.parse(StringArgumentType.getString(context, "type"));
    if (type.error().isPresent()) {
      return sendFailureMessage(context.getSource(), type.error().get().message());
    }
    return feedback(
        context.getSource(),
        VillageService.addPoi(
            StringArgumentType.getString(context, VILLAGE_ARG),
            StringArgumentType.getString(context, POI_ARG),
            type.getOrThrow(),
            BlockPos.containing(context.getSource().getPosition())));
  }

  private static int addNpc(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    DataResult<VillageProfile> profile =
        VillageProfile.parse(StringArgumentType.getString(context, PROFILE_ARG));
    if (profile.error().isPresent()) {
      return sendFailureMessage(context.getSource(), profile.error().get().message());
    }
    EasyNPC<?> easyNPC = EasyNPCArgument.getEntityWithAccess(context, NPC_TARGET_ARG);
    return feedback(
        context.getSource(),
        VillageService.addNpc(
            StringArgumentType.getString(context, VILLAGE_ARG),
            easyNPC,
            profile.getOrThrow(),
            StringArgumentType.getString(context, POI_ARG)));
  }

  private static int removeNpc(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    EasyNPC<?> easyNPC = EasyNPCArgument.getEntityWithAccess(context, NPC_TARGET_ARG);
    return feedback(
        context.getSource(),
        VillageService.removeNpc(
            StringArgumentType.getString(context, VILLAGE_ARG), easyNPC.getEntityUUID()));
  }

  private static int setSchedule(CommandContext<CommandSourceStack> context)
      throws CommandSyntaxException {
    EasyNPC<?> easyNPC = EasyNPCArgument.getEntityWithAccess(context, NPC_TARGET_ARG);
    return feedback(
        context.getSource(),
        VillageService.setSchedule(
            StringArgumentType.getString(context, VILLAGE_ARG),
            easyNPC.getEntityUUID(),
            IntegerArgumentType.getInteger(context, START_ARG),
            StringArgumentType.getString(context, POI_ARG)));
  }

  private static int force(CommandContext<CommandSourceStack> context, Boolean awake) {
    String villageId = StringArgumentType.getString(context, VILLAGE_ARG);
    if (VillageRuntime.data().flatMap(data -> data.village(villageId)).isEmpty()) {
      return sendFailureMessage(context.getSource(), "Unknown village " + villageId + ".");
    }
    VillageRuntime.force(villageId, awake);
    return sendSuccessMessage(
        context.getSource(),
        "Village "
            + villageId
            + (awake == null ? " follows players again." : awake ? " forced awake." : " forced asleep."));
  }

  @SuppressWarnings("unchecked")
  private static int status(CommandContext<CommandSourceStack> context, String villageId) {
    Map<String, Object> status = VillageApi.statusMap();
    StringBuilder text =
        new StringBuilder()
            .append("Villages: budget level ")
            .append(status.get("budgetLevel"))
            .append(", Easy NPC ")
            .append(status.get("npcMicrosPerTick"))
            .append(" us/tick of ")
            .append(status.get("budgetMicros"))
            .append(" us, ")
            .append(status.get("queuedSpawns"))
            .append(" queued spawns");
    for (Map<String, Object> village : (java.util.List<Map<String, Object>>) status.get("villages")) {
      if (villageId != null && !villageId.equals(village.get("id"))) {
        continue;
      }
      text.append("\n- ")
          .append(village.get("id"))
          .append(Boolean.TRUE.equals(village.get("awake")) ? " awake" : " asleep")
          .append(", ")
          .append(village.get("spawned"))
          .append('/')
          .append(village.get("npcs"))
          .append(" NPCs ")
          .append(village.get("profiles"))
          .append(", walkers ")
          .append(village.get("walkers"))
          .append(", forced ")
          .append(village.get("forced"));
    }
    return sendSuccessMessage(context.getSource(), text.toString());
  }

  private static Optional<VillageSavedData.Village> village(CommandContext<CommandSourceStack> context) {
    String villageId;
    try {
      villageId = StringArgumentType.getString(context, VILLAGE_ARG);
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
    return VillageRuntime.data().flatMap(data -> data.village(villageId));
  }

  private static int setPoi(CommandContext<CommandSourceStack> context) {
    return feedback(
        context.getSource(),
        VillageService.setPoi(
            StringArgumentType.getString(context, VILLAGE_ARG),
            StringArgumentType.getString(context, POI_ARG),
            BlockPos.containing(context.getSource().getPosition())));
  }

  private static int listPois(CommandContext<CommandSourceStack> context) {
    String villageId = StringArgumentType.getString(context, VILLAGE_ARG);
    if (VillageRuntime.data().flatMap(data -> data.village(villageId)).isEmpty()) {
      return sendFailureMessage(context.getSource(), "Unknown village " + villageId + ".");
    }
    MutableComponent text = Component.literal("POIs of " + villageId + ":");
    int missing = 0;
    for (VillageRuntime.PoiReport report : VillageRuntime.poiReport(villageId)) {
      BlockPos pos = report.pos();
      MutableComponent line =
          Component.literal("\n- " + report.id() + " (" + report.type().serializedName() + ") "
              + pos.getX() + " " + pos.getY() + " " + pos.getZ() + ": ");
      if (!report.loaded()) {
        line.append(Component.literal("not loaded").withStyle(ChatFormatting.GRAY));
      } else if (report.spot().isPresent()) {
        BlockPos spot = report.spot().get();
        line.append(
            Component.literal(
                    spot.equals(pos)
                        ? "ok"
                        : "ok, NPCs stand at " + spot.getX() + " " + spot.getY() + " " + spot.getZ())
                .withStyle(ChatFormatting.GREEN));
      } else {
        missing++;
        line.append(Component.literal("no free spot within 6 blocks, use poi set").withStyle(ChatFormatting.RED));
      }
      text.append(line);
    }
    final MutableComponent message = text;
    context.getSource().sendSuccess(() -> message, false);
    return missing == 0 ? 1 : 0;
  }

  private static int respawn(CommandContext<CommandSourceStack> context) {
    String villageId = StringArgumentType.getString(context, VILLAGE_ARG);
    String key = StringArgumentType.getString(context, NPC_KEY_ARG);
    Optional<VillageSavedData.Village> village = VillageRuntime.data().flatMap(data -> data.village(villageId));
    if (village.isEmpty()) {
      return sendFailureMessage(context.getSource(), "Unknown village " + villageId + ".");
    }
    boolean all = "all".equals(key);
    if (!all && village.get().spec().npc(key).isEmpty()) {
      return sendFailureMessage(context.getSource(), "Unknown NPC " + key + " in village " + villageId + ".");
    }
    int moved = VillageRuntime.respawn(villageId, all ? null : key);
    return sendSuccessMessage(
        context.getSource(), "Moved " + moved + " loaded NPC" + (moved == 1 ? "" : "s") + " of " + villageId + " to their spots.");
  }

  private static int importFile(CommandContext<CommandSourceStack> context, boolean here) {
    String file = StringArgumentType.getString(context, FILE_ARG);
    if (!FILE_PATTERN.matcher(file).matches()) {
      return sendFailureMessage(context.getSource(), "File name must match " + FILE_PATTERN.pattern());
    }
    Path path = VILLAGE_DIRECTORY.resolve(file + ".json");
    if (!Files.isRegularFile(path)) {
      java.util.List<String> available = listVillageFiles();
      return sendFailureMessage(
          context.getSource(),
          "File not found: "
              + path.toAbsolutePath().normalize()
              + (available.isEmpty() ? " (folder is empty)" : " (available: " + String.join(", ", available) + ")"));
    }
    String json;
    try {
      json = Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException exception) {
      return sendFailureMessage(context.getSource(), "Unable to read " + path + ": " + exception.getMessage());
    }
    DataResult<VillageSpec> spec = VillageSpec.parseJson(json);
    if (spec.error().isPresent()) {
      return sendFailureMessage(context.getSource(), spec.error().get().message());
    }
    VillageSpec village = spec.getOrThrow();
    if (here) {
      // Same as tools/mover-vila.js: the preset keeps its layout, centered on the executor.
      village =
          village.shiftedTo(
              BlockPos.containing(context.getSource().getPosition()),
              context.getSource().getLevel().dimension().location());
    }
    return feedback(context.getSource(), VillageService.apply(village));
  }

  /** Creates config/easy_npc/villages so staff know where to drop village files. */
  public static void ensureVillageDirectory() {
    try {
      Files.createDirectories(VILLAGE_DIRECTORY);
    } catch (IOException exception) {
      log.warn("Unable to create village folder {}: {}", VILLAGE_DIRECTORY, exception.getMessage());
    }
  }

  static java.util.List<String> listVillageFiles() {
    if (!Files.isDirectory(VILLAGE_DIRECTORY)) {
      return java.util.List.of();
    }
    try (java.util.stream.Stream<Path> files = Files.list(VILLAGE_DIRECTORY)) {
      return files
          .map(file -> file.getFileName().toString())
          .filter(name -> name.endsWith(".json"))
          .map(name -> name.substring(0, name.length() - ".json".length()))
          .filter(name -> FILE_PATTERN.matcher(name).matches())
          .sorted()
          .toList();
    } catch (IOException exception) {
      return java.util.List.of();
    }
  }

  private static int feedback(CommandSourceStack source, VillageService.Result result) {
    String message = String.join("\n", result.messages());
    if (!result.ok()) {
      return sendFailureMessage(source, message);
    }
    Optional<String> counts =
        result.queuedSpawns() + result.updatedNpcs() + result.removedNpcs() > 0
            ? Optional.of(
                " (" + result.queuedSpawns() + " queued, " + result.updatedNpcs() + " updated, "
                    + result.removedNpcs() + " removed)")
            : Optional.empty();
    return sendSuccessMessage(source, message + counts.orElse(""));
  }
}
