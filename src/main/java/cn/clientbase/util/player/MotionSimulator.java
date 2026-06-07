package cn.clientbase.util.player;

import cn.clientbase.util.IMinecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Predictive player-physics simulator. Faithful port of OpenZen's
 * {@code shit.zen.utils.game.MotionSimulator} (object-based), Yarn-mapped for 1.21.4.
 *
 * Scaffold's clutch path constructs one from the local player, calls
 * {@link #simulateWithFriction(int)} and reads {@link #y}.
 */
public class MotionSimulator implements IMinecraft {
    public double x;
    public double y;
    public double z;
    private double motionX;
    private double motionY;
    private double motionZ;
    private final float yaw;
    private final float strafeSpeed;
    private final float forwardSpeed;
    private float jumpPower;

    public MotionSimulator(double x, double y, double z, double motionX, double motionY, double motionZ, float yaw, float strafeSpeed, float forwardSpeed) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.motionX = motionX;
        this.motionY = motionY;
        this.motionZ = motionZ;
        this.yaw = yaw;
        this.strafeSpeed = strafeSpeed;
        this.forwardSpeed = forwardSpeed;
    }

    public MotionSimulator(PlayerEntity player) {
        this(player.getX(), player.getY(), player.getZ(),
                player.getVelocity().x, player.getVelocity().y, player.getVelocity().z,
                player.getYaw(), player.sidewaysSpeed, player.forwardSpeed);
        float currentJumpFactor = player.getWorld().getBlockState(player.getBlockPos()).getBlock().getJumpVelocityMultiplier();
        float belowJumpFactor = player.getWorld().getBlockState(player.getSteppingPos()).getBlock().getJumpVelocityMultiplier();
        this.jumpPower = 0.42f * ((double) currentJumpFactor == 1.0 ? belowJumpFactor : currentJumpFactor) + player.getJumpBoostVelocityModifier();
    }

    private void tick() {
        float strafe = this.strafeSpeed;
        float forward = this.forwardSpeed;
        float magSqr = strafe * strafe + forward * forward;
        if (magSqr >= 1.0E-4f) {
            magSqr = MathHelper.sqrt(magSqr);
            if (magSqr < 1.0f) {
                magSqr = 1.0f;
            }
            float speed = this.jumpPower;
            if (mc.player != null && mc.player.isSprinting()) {
                speed *= 1.3f;
            }
            magSqr = speed / magSqr;
            float sinYaw = MathHelper.sin(this.yaw * (float) Math.PI / 180.0f);
            float cosYaw = MathHelper.cos(this.yaw * (float) Math.PI / 180.0f);
            this.motionX += (strafe *= magSqr) * cosYaw - (forward *= magSqr) * sinYaw;
            this.motionZ += forward * cosYaw + strafe * sinYaw;
        }
        this.motionY -= 0.08;
        this.motionY *= 0.98f;
        this.x += this.motionX;
        this.y += this.motionY;
        this.z += this.motionZ;
    }

    private void tickWithFriction() {
        float strafe = this.strafeSpeed * 0.98f;
        float forward = this.forwardSpeed * 0.98f;
        float magSqr = strafe * strafe + forward * forward;
        if (magSqr >= 1.0E-4f) {
            magSqr = MathHelper.sqrt(magSqr);
            if (magSqr < 1.0f) {
                magSqr = 1.0f;
            }
            float speed = this.jumpPower;
            if (mc.player != null && mc.player.isSprinting()) {
                speed *= 1.3f;
            }
            magSqr = speed / magSqr;
            float sinYaw = MathHelper.sin(this.yaw * (float) Math.PI / 180.0f);
            float cosYaw = MathHelper.cos(this.yaw * (float) Math.PI / 180.0f);
            this.motionX += (strafe *= magSqr) * cosYaw - (forward *= magSqr) * sinYaw;
            this.motionZ += forward * cosYaw + strafe * sinYaw;
        }
        this.motionY -= 0.08;
        this.motionY *= 0.98f;
        this.x += this.motionX;
        this.y += this.motionY;
        this.z += this.motionZ;
        this.motionX *= 0.91;
        this.motionZ *= 0.91;
    }

    public BlockPos findLandingBlock(int maxTicks) {
        if (mc.player == null) return null;
        for (int i = 0; i < maxTicks; ++i) {
            Vec3d fromPos = new Vec3d(this.x, this.y, this.z);
            this.tickWithFriction();
            Vec3d toPos = new Vec3d(this.x, this.y, this.z);
            float halfWidth = mc.player.getWidth() / 2.0f;
            BlockPos hit;
            if ((hit = this.rayTraceBlock(fromPos, toPos)) != null) return hit;
            if ((hit = this.rayTraceBlock(fromPos.add(halfWidth, 0.0, halfWidth), toPos)) != null) return hit;
            if ((hit = this.rayTraceBlock(fromPos.add(-halfWidth, 0.0, halfWidth), toPos)) != null) return hit;
            if ((hit = this.rayTraceBlock(fromPos.add(halfWidth, 0.0, -halfWidth), toPos)) != null) return hit;
            if ((hit = this.rayTraceBlock(fromPos.add(-halfWidth, 0.0, -halfWidth), toPos)) != null) return hit;
            if ((hit = this.rayTraceBlock(fromPos.add(halfWidth, 0.0, halfWidth / 2.0f), toPos)) != null) return hit;
            if ((hit = this.rayTraceBlock(fromPos.add(-halfWidth, 0.0, halfWidth / 2.0f), toPos)) != null) return hit;
            if ((hit = this.rayTraceBlock(fromPos.add(halfWidth / 2.0f, 0.0, halfWidth), toPos)) != null) return hit;
            if ((hit = this.rayTraceBlock(fromPos.add(halfWidth / 2.0f, 0.0, -halfWidth), toPos)) != null) return hit;
        }
        return null;
    }

    private BlockPos rayTraceBlock(Vec3d fromPos, Vec3d toPos) {
        if (mc.world == null || mc.player == null) return null;
        HitResult hitResult = mc.world.raycast(new RaycastContext(fromPos, toPos,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK
                && hitResult instanceof BlockHitResult blockHitResult
                && blockHitResult.getSide() == Direction.UP) {
            return blockHitResult.getBlockPos();
        }
        return null;
    }

    public void simulate(int ticks) {
        for (int i = 0; i < ticks; ++i) {
            this.tick();
        }
    }

    public void simulateWithFriction(int ticks) {
        for (int i = 0; i < ticks; ++i) {
            this.tickWithFriction();
        }
    }
}
