package cn.clientbase.module.impl.movement;

import cn.clientbase.event.base.annotation.EventTarget;
import cn.clientbase.event.impl.MotionEvent;
import cn.clientbase.event.impl.MoveInputEvent;
import cn.clientbase.event.impl.UpdateEvent;
import cn.clientbase.module.Category;
import cn.clientbase.module.Module;
import cn.clientbase.module.value.impl.BoolValue;
import cn.clientbase.module.value.impl.ModeValue;
import cn.clientbase.module.value.impl.NumberValue;
import cn.clientbase.util.player.BlockUtil;
import cn.clientbase.util.player.MovementUtil;
import lombok.Getter;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Auto block-bridge. The "Telly Bridge" mode bridges while bunny-hopping: it holds jump
 * whenever moving, and during the airborne window snaps the rotation toward the block below
 * and places it. Rotation is clamped per tick and given a tiny alternating jitter whenever it
 * would otherwise sit perfectly still, which keeps the look motion from reading as a static bot.
 *
 * Anti-detection improvements (借鉴 OpenZen):
 * 1. Dual-axis jitter: triggers on both yaw AND pitch diff stagnation (not just yaw delta magnitude).
 * 2. groundTicks rotation smoothing: on the 1st ground tick, halve yaw step; on the 2nd, freeze yaw.
 *    This mimics the visual jolt of landing.
 * 3. Hard 90° per-tick yaw cap: prevents single-frame snaps >90° regardless of rotateStep.
 * 4. Random clamp noise: subtract a tiny random from the step each tick so the clamp limit is never
 *    the same value twice in a row.
 */
@Getter
public class Scaffold extends Module {
    public final ModeValue mode = new ModeValue("Mode", "Telly", "Telly", "Normal");
    public final BoolValue sneak = new BoolValue("Eagle Sneak", true, () -> mode.is("Normal"));
    public final BoolValue keepRotation = new BoolValue("Keep Rotation", true);
    public final NumberValue rotateStep = new NumberValue("Rotate Step", 75, 20, 180, 5);
    public final BoolValue jitter = new BoolValue("Anti-Stuck Jitter", true);

    // Placement target resolved each tick.
    private BlockPos targetSupport;
    private Direction targetFace;
    private BlockPos targetGap;

    // Rotation state (degrees).
    private float wantYaw, wantPitch;
    private boolean haveTarget;
    private float lastSentYaw = Float.NaN;
    private float lastSentPitch = Float.NaN;

    // Jitter: track per-axis diff across ticks (mirrors OpenZen's yawDiff/pitchDiff pattern).
    private double lastYawDiff = Double.NaN;
    private double lastPitchDiff = Double.NaN;
    private int jitterCounter;

    private boolean jitterFlip;

    private int airTicks, groundTicks;

    public Scaffold() {
        super("Scaffold", Category.Movement);
        setDescription("Auto-bridge below your feet. Telly mode hops while bridging.");
    }

    @Override
    public void onDisable() {
        if (mc.options != null) {
            mc.options.jumpKey.setPressed(mc.options.jumpKey.isPressed());
            mc.options.sneakKey.setPressed(false);
        }
        resetState();
    }

    private void resetState() {
        targetSupport = null;
        targetFace = null;
        targetGap = null;
        haveTarget = false;
        lastSentYaw = lastSentPitch = Float.NaN;
        lastYawDiff = lastPitchDiff = Double.NaN;
        jitterCounter = 0;
        airTicks = groundTicks = 0;
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (event.isPost()) {
            if (mc.player.isOnGround()) { groundTicks++; airTicks = 0; }
            else                        { airTicks++; groundTicks = 0; }
            return;
        }

        int slot = BlockUtil.findBlockSlot();
        if (slot == -1) { haveTarget = false; return; }
        if (mc.player.getInventory().selectedSlot != slot)
            mc.player.getInventory().selectedSlot = slot;

        resolveTarget();
        if (!haveTarget) return;

        computeRotation();

        event.setYaw(wantYaw);
        event.setPitch(wantPitch);
        if (!keepRotation.getValue()) {
            mc.player.setYaw(wantYaw);
            mc.player.setPitch(wantPitch);
        }

        place();
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (mc.player == null) return;

        if (mode.is("Telly")) {
            if (MovementUtil.isMoving() || event.isJumping())
                event.setJumping(true);
        } else if (sneak.getValue()) {
            event.setSneaking(event.isSneaking() || (mc.player.isOnGround() && isOnEdge()));
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        setSuffix(mode.getValue());
    }

    // --- target resolution ---------------------------------------------------

    private void resolveTarget() {
        haveTarget = false;
        Vec3d base = mc.player.getPos();

        BlockPos feet = BlockPos.ofFloored(base.x, base.y - 0.5, base.z);
        if (tryGap(feet)) return;

        if (MovementUtil.isMoving()) {
            double dir = MovementUtil.getDirection();
            BlockPos behind = BlockPos.ofFloored(
                    base.x - MathHelper.sin((float) dir) * 0.6,
                    base.y - 0.5,
                    base.z + MathHelper.cos((float) dir) * 0.6);
            tryGap(behind);
        }
    }

    private boolean tryGap(BlockPos gap) {
        if (!BlockUtil.isReplaceable(gap)) return false;
        for (Direction dir : Direction.values()) {
            BlockPos support = gap.offset(dir);
            if (BlockUtil.isSolid(support)) {
                this.targetGap = gap;
                this.targetSupport = support;
                this.targetFace = dir.getOpposite();
                this.haveTarget = true;
                return true;
            }
        }
        return false;
    }

    // --- rotation ------------------------------------------------------------

    private void computeRotation() {
        Vec3d hit = hitVec(targetSupport, targetFace);
        float[] raw = rotationsTo(hit);
        float targetYaw   = raw[0];
        float targetPitch = MathHelper.clamp(raw[1], -90f, 90f);

        float refYaw   = Float.isNaN(lastSentYaw)   ? mc.player.getYaw()   : lastSentYaw;
        float refPitch = Float.isNaN(lastSentPitch)  ? mc.player.getPitch() : lastSentPitch;

        // --- [1] per-tick clamp with tiny random noise (OpenZen: clampLimit -= random(0.001,0.006)) ---
        float step = rotateStep.getValue();
        if (airTicks >= 1) step = Math.max(step, 90f);
        step -= (float)(Math.random() * 0.005 + 0.001); // noise so clamp is never identical

        // --- [2] Hard 90° cap (OpenZen: getOptimalRotation) ---
        step = Math.min(step, 90f);

        float delta = MathHelper.wrapDegrees(targetYaw - refYaw);

        // --- [3] groundTicks landing smoothing (OpenZen: case 1 → half step, case 2 → freeze) ---
        if (groundTicks == 1) {
            delta *= 0.5f;          // 1st ground tick: halve yaw travel, simulate landing jolt
            targetPitch = 75.5f;
        } else if (groundTicks == 2) {
            delta = 0f;             // 2nd ground tick: don't rotate at all
            targetPitch = 75.5f;
        }

        delta = MathHelper.clamp(delta, -step, step);
        float yaw = refYaw + delta;

        // --- [4] Dual-axis jitter (OpenZen: stuckYaw || stuckPitch) ---
        if (jitter.getValue()) {
            double yawDiff   = Math.abs(MathHelper.wrapDegrees(yaw - refYaw));
            double pitchDiff = Math.abs(targetPitch - refPitch);

            boolean stuckYaw   = yawDiff   > 2.0 && !Double.isNaN(lastYawDiff)
                               && Math.abs(yawDiff   - lastYawDiff)   < 1e-4;
            boolean stuckPitch = pitchDiff > 2.0 && !Double.isNaN(lastPitchDiff)
                               && Math.abs(pitchDiff - lastPitchDiff) < 1e-4;

            if (stuckYaw || stuckPitch) {
                float jitterYaw   = 0.095f + (float) Math.random() * 0.095f;  // 0.095–0.19
                float jitterPitch = 0.016f + (float) Math.random() * 0.039f;  // 0.016–0.055
                if ((jitterCounter++ & 1) == 0) jitterYaw = -jitterYaw;
                yaw          += jitterYaw;
                targetPitch   = MathHelper.clamp(targetPitch + jitterPitch, -89.5f, 89.5f);
                yawDiff   = Math.abs(MathHelper.wrapDegrees(yaw - refYaw));
                pitchDiff = Math.abs(targetPitch - refPitch);
            }

            lastYawDiff   = yawDiff;
            lastPitchDiff = pitchDiff;
        }

        this.wantYaw     = yaw;
        this.wantPitch   = targetPitch;
        this.lastSentYaw = yaw;
        this.lastSentPitch = targetPitch;
    }

    private float[] rotationsTo(Vec3d target) {
        Vec3d eye  = mc.player.getEyePos();
        double dx  = target.x - eye.x;
        double dy  = target.y - eye.y;
        double dz  = target.z - eye.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw   = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        return new float[]{yaw, pitch};
    }

    private Vec3d hitVec(BlockPos support, Direction face) {
        double x = support.getX() + 0.5;
        double y = support.getY() + 0.5;
        double z = support.getZ() + 0.5;
        double r = 0.3;
        if (face.getAxis() == Direction.Axis.Y) {
            x += rand(r); z += rand(r);
        } else {
            y += rand(r);
            if (face.getAxis() == Direction.Axis.X) z += rand(r); else x += rand(r);
        }
        Vec3d n = Vec3d.of(face.getVector());
        return new Vec3d(x, y, z).add(n.multiply(0.5));
    }

    private double rand(double range) {
        return (Math.random() * 2 - 1) * range;
    }

    // --- placement -----------------------------------------------------------

    private void place() {
        if (mc.interactionManager == null || mc.player == null) return;
        if (!BlockUtil.isPlaceable(mc.player.getMainHandStack())) return;
        if (!BlockUtil.isReplaceable(targetGap)) return;

        BlockHitResult hit = new BlockHitResult(
                hitVec(targetSupport, targetFace), targetFace, targetSupport, false);

        if (mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit).isAccepted()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private boolean isOnEdge() {
        if (mc.world == null || mc.player == null) return false;
        return !mc.world.getBlockCollisions(mc.player,
                mc.player.getBoundingBox().offset(0, -0.5, 0).contract(0.3, 0, 0.3))
                .iterator().hasNext();
    }
}
