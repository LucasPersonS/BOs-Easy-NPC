package de.markusbordihn.easynpc.paravexia.scene;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.markusbordihn.easynpc.commands.Command;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** /easy_npc scene list|place|remove|rotate|reskin — staff tools for placing scenes by hand. */
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
                                        rotate(context, IntegerArgumentType.getInteger(context, RADIUS_ARG))))))
        .then(
            Commands.literal("reskin")
                .executes(context -> reskin(context, DEFAULT_RADIUS))
                .then(
                    radiusArgument()
                        .executes(
                            context -> reskin(context, IntegerArgumentType.getInteger(context, RADIUS_ARG)))));
  }

  private static ArgumentBuilder<CommandSourceStack, ?> radiusArgument() {
    return Commands.argument(
        RADIUS_ARG, IntegerArgumentType.integer(1, SceneConstants.MAX_SCENES_PER_COMMAND_RADIUS));
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
    Optional<EntityType<? extends SceneEntity>> type = SceneEntityTypes.sceneType();
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
    SceneEntity scene = type.get().create(level);
    if (scene == null) {
      return FAILURE;
    }
    Vec3 position = source.getPosition();
    // Default: the scene faces whoever placed it, snapped to the 4 block directions.
    float yaw =
        yawArgument.orElseGet(() -> Mth.wrapDegrees(Math.round((source.getRotation().y + 180F) / 90F) * 90F));
    scene.moveTo(Mth.floor(position.x) + 0.5D, Mth.floor(position.y), Mth.floor(position.z) + 0.5D, yaw, 0F);
    scene.applyDefinition(definition.get(), level.getRandom());
    if (!level.addFreshEntity(scene)) {
      source.sendFailure(Component.literal("Não foi possível colocar a cena aqui."));
      return FAILURE;
    }
    source.sendSuccess(
        () -> Component.literal("Cena " + id + " colocada (yaw " + Math.round(yaw) + ")."), true);
    return SINGLE_SUCCESS;
  }

  private static int remove(CommandContext<CommandSourceStack> context, int radius) {
    Optional<SceneEntity> scene = nearest(context.getSource(), radius);
    if (scene.isEmpty()) {
      context.getSource().sendFailure(Component.literal("Nenhuma cena num raio de " + radius + " blocos."));
      return FAILURE;
    }
    String name = scene.get().getSceneId().map(ResourceLocation::toString).orElse("?");
    scene.get().discard();
    context.getSource().sendSuccess(() -> Component.literal("Cena " + name + " removida."), true);
    return SINGLE_SUCCESS;
  }

  private static int rotate(CommandContext<CommandSourceStack> context, int radius) {
    Optional<SceneEntity> scene = nearest(context.getSource(), radius);
    if (scene.isEmpty()) {
      context.getSource().sendFailure(Component.literal("Nenhuma cena num raio de " + radius + " blocos."));
      return FAILURE;
    }
    float yaw = FloatArgumentType.getFloat(context, YAW_ARG);
    SceneEntity entity = scene.get();
    entity.moveTo(entity.getX(), entity.getY(), entity.getZ(), yaw, 0F);
    context.getSource().sendSuccess(() -> Component.literal("Cena girada para " + Math.round(yaw) + "."), true);
    return SINGLE_SUCCESS;
  }

  private static int reskin(CommandContext<CommandSourceStack> context, int radius) {
    Optional<SceneEntity> scene = nearest(context.getSource(), radius);
    Optional<SceneDefinition> definition = scene.flatMap(SceneEntity::getDefinition);
    if (definition.isEmpty()) {
      context.getSource().sendFailure(Component.literal("Nenhuma cena conhecida num raio de " + radius + " blocos."));
      return FAILURE;
    }
    scene.get().rollSkins(definition.get(), context.getSource().getLevel().getRandom());
    context.getSource().sendSuccess(() -> Component.literal("Skins sorteadas de novo."), true);
    return SINGLE_SUCCESS;
  }

  private static Optional<SceneEntity> nearest(CommandSourceStack source, int radius) {
    Vec3 position = source.getPosition();
    return source
        .getLevel()
        .getEntitiesOfClass(SceneEntity.class, AABB.ofSize(position, radius * 2D, radius * 2D, radius * 2D))
        .stream()
        .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(position)));
  }
}
