package cn.clientbase.util.player;

import cn.clientbase.util.IMinecraft;
import cn.injection.accessor.EntityAccessor;
import lombok.experimental.UtilityClass;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.entity.EntityPose;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class RotationUtil implements IMinecraft {

    public float[] nearestRotation(final Box box) {
        if (mc.player == null) return null;

        final Vec3d targetPoint = getNearestPointBB(box);
        return getRotations(targetPoint);
    }

    public Vec3d getNearestPointBB(Box box) {
        if (mc.player == null) return null;

        Vec3d eyePos = mc.player.getEyePos();

        double x = MathHelper.clamp(eyePos.x, box.minX, box.maxX);
        double y = MathHelper.clamp(eyePos.y, box.minY, box.maxY);
        double z = MathHelper.clamp(eyePos.z, box.minZ, box.maxZ);
        Vec3d nearestPoint = new Vec3d(x, y, z);

        if (isVisible(nearestPoint)) {
            return nearestPoint;
        }

        Box scanBox = box.expand(-0.002);

        double stepX = scanBox.getLengthX();
        double stepY = scanBox.getLengthY();
        double stepZ = scanBox.getLengthZ();

        Vec3d bestPoint = null;
        double minDistanceSq = Double.MAX_VALUE;

        for (double sX = scanBox.minX; sX <= scanBox.maxX; sX += stepX / 2.0) {
            for (double sY = scanBox.minY; sY <= scanBox.maxY; sY += stepY / 2.0) {
                for (double sZ = scanBox.minZ; sZ <= scanBox.maxZ; sZ += stepZ / 2.0) {
                    Vec3d currentPoint = new Vec3d(sX, sY, sZ);

                    if (isVisible(currentPoint)) {
                        double distSq = eyePos.squaredDistanceTo(currentPoint);
                        if (distSq < minDistanceSq) {
                            minDistanceSq = distSq;
                            bestPoint = currentPoint;
                        }
                    }
                }
            }
        }

        return (bestPoint != null) ? bestPoint : nearestPoint;
    }

    public boolean isVisible(Vec3d targetVec) {
        if (mc.player == null || mc.world == null) return false;

        Vec3d eyePos = mc.player.getEyePos();
        if (eyePos.squaredDistanceTo(targetVec) > 4096.0) return false;
        Vec3d lookVec = mc.player.getRotationVec(1);
        Vec3d toTarget = targetVec.subtract(eyePos).normalize();
        if (lookVec.dotProduct(toTarget) < 0) return false;
        RaycastContext context = new RaycastContext(eyePos, targetVec, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        return mc.world.raycast(context).getType() == HitResult.Type.MISS;
    }

    public float getDistanceToEntity(LivingEntity target) {
        if (mc.player == null || target == null) return 0.0f;

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d nearestPoint = getNearestPointBB(target.getBoundingBox());
        return (float) eyePos.distanceTo(nearestPoint);
    }

    public double getRotationDifference(LivingEntity entity) {
        if (mc.player == null || entity == null) return 0;

        Vec3d targetPos = entity.getPos();
        float[] neededRotations = RotationUtil.getRotations(targetPos);
        if (neededRotations == null) return 180.0;

        float yawDiff = Math.abs(MathHelper.wrapDegrees(mc.player.getYaw() - neededRotations[0]));
        float pitchDiff = Math.abs(MathHelper.wrapDegrees(mc.player.getPitch() - neededRotations[1]));

        return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
    }

    public float[] getRotations(Vec3d target) {
        if (mc.player == null) return null;
        return getRotations(mc.player.getEyePos(), target);
    }

    public float[] getRotations(Vec3d origin, Vec3d target) {
        return getRotations(target.x, target.y, target.z, origin.x, origin.y, origin.z);
    }

    public float[] getRotations(double rotX, double rotY, double rotZ, double startX, double startY, double startZ) {
        double diffX = rotX - startX;
        double diffY = rotY - startY;
        double diffZ = rotZ - startZ;
        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, dist));
        return new float[]{yaw, pitch};
    }

    public float[] getSmoothRotation(float[] lastRotation, float[] targetRotation, double speed) {
        float yaw = targetRotation[0];
        float pitch = targetRotation[1];
        float lastYaw = lastRotation[0];
        float lastPitch = lastRotation[1];

        if (speed != 0) {
            float rotationSpeed = (float) speed;

            double deltaYaw = MathHelper.wrapDegrees(targetRotation[0] - lastRotation[0]);
            double deltaPitch = pitch - lastPitch;

            double distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
            double distributionYaw = Math.abs(deltaYaw / distance);
            double distributionPitch = Math.abs(deltaPitch / distance);

            double maxYaw = rotationSpeed * distributionYaw;
            double maxPitch = rotationSpeed * distributionPitch;

            float moveYaw = (float) Math.max(Math.min(deltaYaw, maxYaw), -maxYaw);
            float movePitch = (float) Math.max(Math.min(deltaPitch, maxPitch), -maxPitch);

            yaw = lastYaw + moveYaw;
            pitch = lastPitch + movePitch;
        }

        boolean randomise = Math.random() > 0.8;

        for (int i = 1; i <= (int) (2 + Math.random() * 2); ++i) {
            if (randomise) {
                yaw += (float) ((Math.random() - 0.5) / 100000000);
                pitch -= (float) (Math.random() / 200000000);
            }

            // Fixing GCD
            float[] rotations = new float[]{yaw, pitch};
            float[] fixedRotations = applySensitivityPatch(rotations);

            // Setting rotations
            if (fixedRotations != null) {
                yaw = fixedRotations[0];
                pitch = MathHelper.clamp(fixedRotations[1], -90.0F, 90.0F);
            }
        }

        return new float[]{yaw, pitch};
    }

    public float[] applySensitivityPatch(float[] rotations) {
        if (mc.player == null) return null;
        EntityAccessor accessor = (EntityAccessor) mc.player;
        float[] prevRotations = new float[]{accessor.getPrevYaw(), accessor.getPrevPitch()};
        return applySensitivityPatch(rotations, prevRotations);
    }

    public float[] applySensitivityPatch(float[] rotation, float[] previousRotation) {
        float mouseSensitivity = (float) (mc.options.getMouseSensitivity().getValue() * (1 + Math.random() / 10000000) * 0.6F + 0.2F);
        double multiplier = mouseSensitivity * mouseSensitivity * mouseSensitivity * 8.0F * 0.15D;
        float yaw = previousRotation[0] + (float) (Math.round((rotation[0] - previousRotation[0]) / multiplier) * multiplier);
        float pitch = previousRotation[1] + (float) (Math.round((rotation[1] - previousRotation[1]) / multiplier) * multiplier);
        return new float[]{yaw, MathHelper.clamp(pitch, -90.0F, 90.0F)};
    }

    // -----------------------------------------------------------------------
    // Methods ported VERBATIM from OpenZen RotationUtil (Scaffold rotation math)
    // -----------------------------------------------------------------------

    /** OpenZen addNoise: value + rand(0.05,0.08) * (rand(0,1)*2 - 1). */
    private double addNoise(double value) {
        double a = Math.random() * (0.08 - 0.05) + 0.05;
        return value + a * (Math.random() * 2.0 - 1.0);
    }

    /** OpenZen rotationFromDeltas: yaw/pitch from world deltas, wrapped. */
    public Rotation rotationFromDeltas(double dx, double dy, double dz) {
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        float yaw   = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontalDist)));
        return new Rotation(MathHelper.wrapDegrees(yaw), MathHelper.wrapDegrees(pitch));
    }

    /** OpenZen rotationFromVec: from player eye to a world Vec3d, with noise + wrap. */
    public Rotation rotationFromVec(Vec3d target) {
        if (mc.player == null) return new Rotation(0, 0);
        return rotationFromCoords(target.x, target.y, target.z);
    }

    public Rotation rotationFromCoords(double x, double y, double z) {
        if (mc.player == null) return new Rotation(0, 0);
        double fromX = mc.player.getX();
        double fromY = mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose());
        double fromZ = mc.player.getZ();
        double dx = addNoise(x - fromX);
        double dy = addNoise(y - fromY);
        double dz = addNoise(z - fromZ);
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        float yaw   = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) (-(Math.atan2(dy, horizontalDist) * 180.0 / Math.PI));
        return new Rotation(yaw, pitch);
    }

    /** OpenZen rotationToBlock: eye→block centre with partialTicks motion prediction + noise. */
    public Rotation rotationToBlock(BlockPos blockPos, float partialTicks) {
        if (mc.player == null) return new Rotation(0, 0);
        double px = mc.player.getX() + mc.player.getVelocity().x * partialTicks;
        double py = mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()) + mc.player.getVelocity().y * partialTicks;
        double pz = mc.player.getZ() + mc.player.getVelocity().z * partialTicks;
        double dx = (double) blockPos.getX() - px + 0.5;
        double dy = (double) blockPos.getY() - py + 0.5;
        double dz = (double) blockPos.getZ() - pz + 0.5;
        return rotationFromDeltas(addNoise(dx), addNoise(dy), addNoise(dz));
    }

    /**
     * Smooth rotation from {@code current} toward {@code target} with a per-tick step budget.
     * Faithful port of OpenZen RotationUtil.smoothRotation.
     */
    public Rotation smoothRotation(Rotation current, Rotation target, double speed) {
        float targetYaw   = target.getYaw();
        float targetPitch = target.getPitch();
        float currYaw     = current.getYaw();
        float currPitch   = current.getPitch();
        if (speed != 0.0) {
            float maxStep = (float) speed;
            double yawDiff   = MathHelper.wrapDegrees(target.getYaw() - current.getYaw());
            double pitchDiff = targetPitch - currPitch;
            double dist = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
            if (dist > 0) {
                double yawRatio   = Math.abs(yawDiff   / dist);
                double pitchRatio = Math.abs(pitchDiff / dist);
                double maxYawStep   = (double) maxStep * yawRatio;
                double maxPitchStep = (double) maxStep * pitchRatio;
                float yawStep   = (float) Math.max(Math.min(yawDiff,   maxYawStep),   -maxYawStep);
                float pitchStep = (float) Math.max(Math.min(pitchDiff, maxPitchStep), -maxPitchStep);
                targetYaw   = currYaw   + yawStep;
                targetPitch = currPitch + pitchStep;
            }
        }
        // GCD / sensitivity snap (OpenZen uses snapToSensitivity loop; reuse our patch)
        float[] snapped = applySensitivityPatch(new float[]{targetYaw, targetPitch});
        if (snapped != null) {
            targetYaw   = snapped[0];
            targetPitch = MathHelper.clamp(snapped[1], -90f, 90f);
        }
        return new Rotation(targetYaw, targetPitch);
    }

    /** Clamp {@code angle} to ±{@code max} (OpenZen clampAngle). */
    public float clampAngle(float angle, float max) {
        if (Math.abs(angle) < max) return angle;
        if (angle > 0.0f) return max;
        if (angle < 0.0f) return -max;
        return 0.0f;
    }

    /** Signed angular difference (degrees) from b to a, normalised to (−180, 180] (OpenZen angleDiffDouble). */
    public double angleDiffDouble(float a, float b) {
        double diff = a - b;
        return ((diff + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
    }

    /** Move {@code current} toward {@code target} by at most {@code maxStep} degrees (OpenZen moveTowards/rotateTowards). */
    public float moveTowards(float maxStep, float current, float target) {
        float diff = MathHelper.wrapDegrees(target - current);
        if (diff > maxStep)  diff = maxStep;
        if (diff < -maxStep) diff = -maxStep;
        return current + diff;
    }
}
