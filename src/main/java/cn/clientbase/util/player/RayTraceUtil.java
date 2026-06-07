package cn.clientbase.util.player;

import cn.clientbase.util.IMinecraft;
import lombok.experimental.UtilityClass;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Ray-trace helpers. Faithful port of OpenZen's {@code RayTraceUtil}, Yarn-mapped.
 */
@UtilityClass
public class RayTraceUtil implements IMinecraft {

    /** Returns true if looking at the given face of blockPos with the given rotation. */
    public boolean canRayTrace(Rotation rotation, Direction facing, BlockPos blockPos) {
        return canRayTrace(rotation, facing, blockPos, true);
    }

    /**
     * Faithful port of OpenZen RayTraceUtil.canRayTrace: clips from the eye along the
     * given rotation; matches block position, and (when {@code checkFace}) the hit face.
     */
    public boolean canRayTrace(Rotation rotation, Direction facing, BlockPos blockPos, boolean checkFace) {
        if (mc.player == null || mc.world == null) return false;
        Vec3d eye = mc.player.getEyePos();
        Vec3d dir = Vec3d.fromPolar(rotation.getPitch(), rotation.getYaw());
        Vec3d end = eye.add(dir.multiply(5.0));
        BlockHitResult result = mc.world.raycast(new RaycastContext(eye, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        if (result.getType() != HitResult.Type.BLOCK) return false;
        boolean samePos = result.getBlockPos().equals(blockPos);
        boolean sameFace = !checkFace || result.getSide() == facing;
        return samePos && sameFace;
    }

    /** Ray-trace from player eye using the given rotation (OUTLINE shape, no fluids). */
    public HitResult rayTrace(float partialTicks, Rotation rotation) {
        if (mc.player == null || mc.world == null) return null;
        Vec3d eye = mc.player.getEyePos();
        Vec3d dir = Vec3d.fromPolar(rotation.getPitch(), rotation.getYaw());
        double range = 4.5;
        Vec3d end = eye.add(dir.multiply(range));
        return mc.world.raycast(new RaycastContext(eye, end,
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
    }
}
