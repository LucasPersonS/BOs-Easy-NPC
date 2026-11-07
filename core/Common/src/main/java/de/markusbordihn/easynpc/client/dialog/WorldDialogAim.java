package de.markusbordihn.easynpc.client.dialog;

import de.markusbordihn.easynpc.entity.LivingEntityManager;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import java.util.Iterator;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** What the crosshair points at, and where an NPC's head lands on screen. */
final class WorldDialogAim {

  /** Extra hitbox margin, so small or thin NPCs are easy to aim at. */
  private static final double PICK_MARGIN = 0.25D;

  private WorldDialogAim() {}

  /**
   * EasyNPC under the crosshair, at most {@code range} blocks from the player and not behind a
   * block. Casts from the camera, so it also works with third-person / shoulder cameras.
   */
  static EasyNPC<?> pick(Minecraft minecraft, double range) {
    if (minecraft.player == null || minecraft.level == null) {
      return null;
    }
    Camera camera = minecraft.gameRenderer.getMainCamera();
    if (!camera.isInitialized()) {
      return null;
    }
    Vec3 from = camera.getPosition();
    Vector3f look = camera.getLookVector();
    double reach = range + from.distanceTo(minecraft.player.getEyePosition()) + 1.0D;
    Vec3 to = from.add(look.x() * reach, look.y() * reach, look.z() * reach);

    HitResult block =
        minecraft.level.clip(
            new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player));
    double limit =
        block.getType() == HitResult.Type.MISS
            ? reach * reach
            : from.distanceToSqr(block.getLocation());

    EasyNPC<?> best = null;
    double bestDistance = limit;
    Iterator<EasyNPC<?>> iterator = LivingEntityManager.getClientEasyNPCEntities().iterator();
    while (iterator.hasNext()) {
      EasyNPC<?> easyNPC = iterator.next();
      LivingEntity npc = easyNPC.getLivingEntity();
      if (npc == null
          || !npc.isAlive()
          || npc.level() != minecraft.level
          || npc.isInvisibleTo(minecraft.player)
          || minecraft.player.distanceToSqr(npc) > range * range) {
        continue;
      }
      AABB box = npc.getBoundingBox().inflate(PICK_MARGIN);
      Optional<Vec3> hit = box.contains(from) ? Optional.of(from) : box.clip(from, to);
      if (hit.isPresent()) {
        double distance = from.distanceToSqr(hit.get());
        if (distance < bestDistance) {
          bestDistance = distance;
          best = easyNPC;
        }
      }
    }
    return best;
  }

  /** World point a little above the NPC's head (its name tag attachment when it has one). */
  static Vec3 headAnchor(LivingEntity npc, float partialTick) {
    Vec3 offset =
        npc.getAttachments()
            .getNullable(EntityAttachment.NAME_TAG, 0, npc.getViewYRot(partialTick));
    if (offset == null) {
      offset = new Vec3(0.0D, npc.getBbHeight() + 0.5D, 0.0D);
    }
    return npc.getPosition(partialTick).add(offset.x, offset.y + 0.1D, offset.z);
  }

  /**
   * Projects a world point to normalized device coordinates ({@code [-1, 1]}, y up). Returns {@code
   * null} when the point is behind the camera.
   */
  static float[] project(Vec3 world, Camera camera, Matrix4f modelView, Matrix4f projection) {
    Vec3 rel = world.subtract(camera.getPosition());
    Vector4f clip = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0F);
    clip.mul(modelView);
    clip.mul(projection);
    if (clip.w() <= 0.05F) {
      return null;
    }
    return new float[] {clip.x() / clip.w(), clip.y() / clip.w()};
  }
}
