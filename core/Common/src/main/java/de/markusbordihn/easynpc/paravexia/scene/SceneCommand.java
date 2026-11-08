package de.markusbordihn.easynpc.paravexia.scene;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.markusbordihn.easynpc.api.handler.EasyNPCEntityHandler;
import de.markusbordihn.easynpc.commands.Command;
import de.markusbordihn.easynpc.data.attribute.InteractionAttributeType;
import de.markusbordihn.easynpc.data.attribute.MovementAttributeType;
import de.markusbordihn.easynpc.handler.AttributeHandler;
import de.markusbordihn.easynpc.handler.OwnerHandler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * /easy_npc scene list|place|remove|rotate — staff tools for scenes. Skins, names and dialogs of
 * each actor are set in the normal Easy NPC editor (sneak + click with the wand / editor item).
 */
public final class SceneCommand extends Command {

  private static final String SCENE_ARG = "scene";
  private static final String YAW_ARG = "yaw";
  private static final String RADIUS_ARG = "radius";
  private static final int DEFAULT_RADIUS = 4;

  private static final SuggestionProvider<CommandSourceStack> SCENES =
      (context, builder) ->
          SharedSuggestionProvider.suggestResource(
              SceneRegistry.all().stream().map(SceneDefinition::id), builder);

  private SceneCommand() {}

  public static ArgumentBuilder<CommandSourceStack, ?> register() {
    return Commands.literal("scene")
        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
        .then(Commands.literal("list").executes(SceneCommand::list))
        .then(
            Commands.literal("place")
                .then(
                    Commands.argument(SCENE_ARG, ResourceLocationArgument.id())
                        .suggests(SCENES)
                        .executes(context -> place(context, Optional.empty()))
                        .then(
                            Commands.argument(YAW_ARG, FloatArgumentType.floatArg(-180F, 180F))
                                .executes(
                                    context ->
                                        place(
                                            context,
                                            Optional.of(FloatArgumentType.getFloat(context, YAW_ARG)))))))
        .then(
            Commands.literal("remove")
                .executes(context -> remove(context, DEFAULT_RADIUS))
                .then(
                    radiusArgument()
                        .executes(
                            context -> remove(context, IntegerArgumentType.getInteger(context, RADIUS_ARG)))))
        .then(
            Commands.literal("rotate")
                .then(
                    Commands.argument(YAW_ARG, FloatArgumentType.floatArg(-180F, 180F))
                        .executes(context -> rotate(context, DEFAULT_RADIUS))
                        .then(
                            radiusArgument()
                                .executes(
                                    context ->
                                        rotate(context, IntegerArgumentType.getInteger(context, RADIUS_ARG))))));
  }

  private static ArgumentBuilder<CommandSourceStack, ?> radiusArgument() {
    return Commands.argument(RADIUS_ARG, IntegerArgumentType.integer(1, SceneConstants.MAX_COMMAND_RADIUS));
  }

  private static int list(CommandContext<CommandSourceStack> context) {
    List<SceneDefinition> scenes =
        SceneRegistry.all().stream().sorted(Comparator.comparing(scene -> scene.id().toString())).toList();
    if (scenes.isEmpty()) {
      context.getSource().sendFailure(Component.literal("Nenhuma cena carregada (data/<ns>/easy_npc_scenes/)."));
      return FAILURE;
    }
    context.getSource().sendSuccess(() -> Component.literal(scenes.size() + " cenas:"), false);
    for (SceneDefinition scene : scenes) {
      context
          .getSource()
          .sendSuccess(
              () ->
                  Component.literal(" " + scene.id() + " ")
                      .withStyle(ChatFormatting.YELLOW)
                      .append(
                          Component.literal(
                                  scene.title()
                                      + " · "
                                      + scene.actors().size()
                                      + " ator(es) · "
                                      + String.join(", ", scene.tags()))
                              .withStyle(ChatFormatting.GRAY)),
              false);
    }
    return scenes.size();
  }

  private static int place(CommandContext<CommandSourceStack> context, Optional<Float> yawArgument) {
    CommandSourceStack source = context.getSource();
    Optional<EntityType<? extends SceneNPC>> type = SceneEntityTypes.sceneType();
    if (type.isEmpty()) {
      source.sendFailure(Component.literal("Cenas precisam do GeckoLib instalado."));
      return FAILURE;
    }
    ResourceLocation id = ResourceLocationArgument.getId(context, SCENE_ARG);
    Optional<SceneDefinition> definition = SceneRegistry.get(id);
    if (definition.isEmpty()) {
      source.sendFailure(Component.literal("Cena desconhecida: " + id));
      return FAILURE;
    }
    ServerLevel level = source.getLevel();
    Vec3 position = source.getPosition();
    Vec3 origin = new Vec3(Mth.floor(position.x) + 0.5D, Mth.floor(position.y), Mth.floor(position.z) + 0.5D);
    // Default: the scene faces whoever placed it, snapped to the 4 block directions.
    float yaw =
        yawArgument.orElseGet(() -> Mth.wrapDegrees(Math.round((source.getRotation().y + 180F) / 90F) * 90F));

    List<SceneNPC> actors = new ArrayList<>();
    UUID group = UUID.randomUUID();
    for (SceneDefinition.Actor actor : definition.get().actors()) {
      SceneNPC npc = type.get().create(level);
      if (npc == null) {
        actors.forEach(SceneNPC::discard);
        return FAILURE;
      }
      npc.setupScene(definition.get(), actor, origin, yaw, group);
      applySceneAttributes(npc);
      if (source.getEntity() instanceof ServerPlayer player) {
        OwnerHandler.setOwner(npc, player);
      }
      actors.add(npc);
    }
    for (SceneNPC npc : actors) {
      level.addFreshEntity(npc);
    }
    source.sendSuccess(
        () ->
            Component.literal(
                "Cena " + id + " colocada (" + actors.size() + " ator(es), yaw " + Math.round(yaw)
                    + "). Edite skin/nome/diálogo de cada ator no editor do Easy NPC."),
        true);
    return actors.size();
  }

  /** Scene actors never push or get pushed and never walk away from the scene. */
  private static void applySceneAttributes(SceneNPC npc) {
    AttributeHandler.setInteractionAttribute(npc, InteractionAttributeType.IS_PUSHABLE, false);
    AttributeHandler.setInteractionAttribute(npc, InteractionAttributeType.PUSH_ENTITIES, false);
    AttributeHandler.setMovementAttribute(npc, MovementAttributeType.IS_IMMOVABLE, true);
  }

  private static int remove(CommandContext<CommandSourceStack> context, int radius) {
    List<SceneNPC> group = nearestGroup(context.getSource(), radius);
    if (group.isEmpty()) {
      context.getSource().sendFailure(Component.literal("Nenhuma cena num raio de " + radius + " blocos."));
      return FAILURE;
    }
    String name = group.get(0).getSceneId().map(ResourceLocation::toString).orElse("?");
    // Through the Easy NPC handler so the NPC storage marks them deleted instead of restoring them.
    group.forEach(EasyNPCEntityHandler::delete);
    context
        .getSource()
        .sendSuccess(() -> Component.literal("Cena " + name + " removida (" + group.size() + " ator(es))."), true);
    return group.size();
  }

  private static int rotate(CommandContext<CommandSourceStack> context, int radius) {
    List<SceneNPC> group = nearestGroup(context.getSource(), radius);
    if (group.isEmpty()) {
      context.getSource().sendFailure(Component.literal("Nenhuma cena num raio de " + radius + " blocos."));
      return FAILURE;
    }
    float yaw = FloatArgumentType.getFloat(context, YAW_ARG);
    // All actors turn around the shared scene origin.
    Vec3 origin = group.get(0).sceneOrigin();
    group.forEach(npc -> npc.placeAt(origin, yaw));
    context.getSource().sendSuccess(() -> Component.literal("Cena girada para " + Math.round(yaw) + "."), true);
    return group.size();
  }

  /** The actors of the scene closest to the source (same group id, or just the actor when legacy). */
  private static List<SceneNPC> nearestGroup(CommandSourceStack source, int radius) {
    Vec3 position = source.getPosition();
    List<SceneNPC> nearby =
        source
            .getLevel()
            .getEntitiesOfClass(
                SceneNPC.class, AABB.ofSize(position, radius * 2D, radius * 2D, radius * 2D));
    Optional<SceneNPC> nearest =
        nearby.stream().min(Comparator.comparingDouble(npc -> npc.distanceToSqr(position)));
    if (nearest.isEmpty()) {
      return List.of();
    }
    UUID group = nearest.get().getGroupId();
    if (group == null) {
      return List.of(nearest.get());
    }
    // Actors of one scene stand within a few blocks of each other.
    return source
        .getLevel()
        .getEntitiesOfClass(SceneNPC.class, nearest.get().getBoundingBox().inflate(8D))
        .stream()
        .filter(npc -> group.equals(npc.getGroupId()))
        .toList();
  }
}
