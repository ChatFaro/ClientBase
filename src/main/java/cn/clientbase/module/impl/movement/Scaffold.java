package cn.clientbase.module.impl.movement;

import cn.clientbase.Client;
import cn.clientbase.event.base.annotation.EventPriority;
import cn.clientbase.event.base.annotation.EventTarget;
import cn.clientbase.event.impl.*;
import cn.clientbase.manager.RotationManager;
import cn.clientbase.module.Category;
import cn.clientbase.module.Module;
import cn.clientbase.module.value.impl.BoolValue;
import cn.clientbase.module.value.impl.ModeValue;
import cn.clientbase.module.value.impl.NumberValue;
import cn.clientbase.util.misc.MathUtil;
import cn.clientbase.util.misc.ReflectionUtil;
import cn.clientbase.util.network.PacketUtil;
import cn.clientbase.util.player.*;
import cn.clientbase.util.render.RenderUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

@Getter
public class Scaffold extends Module {

    public final ModeValue   mode            = new ModeValue("Mode", "Normal", "Normal", "Telly Bridge", "Old Telly", "Keep Y");
    public final BoolValue   eagle           = new BoolValue("Eagle",           true, () -> mode.is("Normal"));
    public final BoolValue   sneak           = new BoolValue("Sneak",           true);
    public final BoolValue   snap            = new BoolValue("Snap",            true, () -> mode.is("Normal"));
    public final BoolValue   renderItemSpoof = new BoolValue("Render Item Spoof", true);
    public final NumberValue rotationTick    = new NumberValue("Rotation Tick", 3, 1, 6, 1);
    public final BoolValue   clutch          = new BoolValue("Clutch",          true);

    public Rotation correctRotation = new Rotation();
    public Rotation rots            = new Rotation();
    public Rotation lastRots        = new Rotation();

    private int     oldSlot;
    private int     groundTicks, airTicks;
    private int     eagleTimer;
    private boolean canBuildNow = true;
    private int     velocityDelay;
    private int     rotationDelay;
    private int     targetYLevel = -1;

    private BlockPos  targetSupport;
    private Direction targetFace;
    private boolean   haveTarget;

    private double lastYawDiff   = Double.NaN;
    private double lastPitchDiff = Double.NaN;
    private int    jitterCounter;

    public Scaffold() {
        super("Scaffold", Category.Movement);
    }

    // =========================================================================
    // Enable / Disable
    // =========================================================================

    @Override
    public void onEnable() {
        if (mc.player == null) return;
        oldSlot = mc.player.getInventory().selectedSlot;
        rots.setYawPitch(mc.player.getYaw(), mc.player.getPitch());
        lastRots.setYawPitch(mc.player.getYaw(), mc.player.getPitch());
        targetYLevel  = 10000;
        velocityDelay = 0;
        rotationDelay = 0;
        jitterCounter = 0;
        lastYawDiff   = Double.NaN;
        lastPitchDiff = Double.NaN;
        canBuildNow   = true;
        Client.delayPackets.clear();
    }

    @Override
    public void onDisable() {
        Runnable r;
        while ((r = Client.delayPackets.poll()) != null) {
            try { r.run(); } catch (Throwable ignored) {}
        }
        Client.delayPackets.clear();
        if (mc.options != null) {
            mc.options.sneakKey.setPressed(false);
            mc.options.useKey.setPressed(false);
        }
        if (mc.player != null)
            mc.player.getInventory().selectedSlot = oldSlot;
        haveTarget  = false;
        canBuildNow = true;
    }

    // =========================================================================
    // Events
    // =========================================================================

    @EventTarget
    public void onUpdateHeldItem(UpdateHeldItemEvent event) {
        if (renderItemSpoof.getValue() && event.getHand() == Hand.MAIN_HAND && mc.player != null)
            event.setItemStack(mc.player.getInventory().getStack(oldSlot));
    }

    /** Mirror of OpenZen onMotion: only increment counters here. */
    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null || !event.isPost()) return;
        if (mc.player.isOnGround()) { groundTicks++; airTicks = 0; }
        else                        { groundTicks = 0; airTicks++; }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null || event.getType() != PacketEvent.Type.Received) return;
        if (event.getPacket() instanceof net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket vel
                && vel.getEntityId() == mc.player.getId()) {
            double len = Math.hypot(vel.getVelocityX(), vel.getVelocityZ()) / 8000.0;
            if (len >= 1.5) velocityDelay = 60;
        }
    }

    @EventTarget
    public void onJump(JumpEvent event) {
        if (!canBuildNow && haveTarget && rotationDelay > 0) event.setCancelled(true);
    }

    // =========================================================================
    // Main tick  (mirrors OpenZen onTick priority=1)
    // =========================================================================

    @EventTarget
    @EventPriority(1)
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        // --- velocity delay ---
        if (velocityDelay > 0) velocityDelay--;
        if (mc.player.isOnGround() && velocityDelay <= 30) velocityDelay = 0;

        // --- slot ---
        int slot = BlockUtil.findBlockSlot();
        if (slot != -1 && mc.player.getInventory().selectedSlot != slot)
            mc.player.getInventory().selectedSlot = slot;

        // Read physical key state like OpenZen's InputConstants.isKeyDown — so our own
        // setPressed(true) for TellyBridge auto-jump does NOT pollute this flag.
        net.minecraft.client.util.InputUtil.Key boundKey =
                net.minecraft.client.util.InputUtil.fromTranslationKey(
                        mc.options.jumpKey.getBoundKeyTranslationKey());
        boolean jumpHeld = boundKey.getCategory() == net.minecraft.client.util.InputUtil.Type.KEYSYM
                && GLFW.glfwGetKey(mc.getWindow().getHandle(), boundKey.getCode()) == GLFW.GLFW_PRESS;

        // --- targetYLevel ---
        if (targetYLevel == -1
                || targetYLevel > (int) Math.floor(mc.player.getY()) - 1
                || mc.player.isOnGround()
                || !MovementUtil.isMoving()
                || jumpHeld
                || mode.is("Normal")) {
            targetYLevel = (int) Math.floor(mc.player.getY()) - 1;
        }

        // --- target resolution (OpenZen applyRotations) ---
        resolveTarget();

        // --- clutch ---
        boolean firstGroundTick = false;
        canBuildNow = true;
        if (haveTarget && slot != -1) {
            if (groundTicks == 1 && jumpHeld) firstGroundTick = true;
            if (clutch.getValue() && mc.player.getVelocity().y < -0.1) {
                MotionSimulator sim = new MotionSimulator(mc.player);
                sim.simulateWithFriction(2);
                if (targetSupport != null && targetSupport.getY() > sim.y) canBuildNow = false;
            }
        }
        if (mc.player.isOnGround()) canBuildNow = true;
        // TellyBridge/OldTelly never need clutch protection — they auto-jump
        if (mode.is("Telly Bridge") || mode.is("Old Telly")) canBuildNow = true;

        // --- reference rotation ---
        float refYaw   = RotationManager.prevRotation != null ? RotationManager.prevRotation.getYaw()   : mc.player.getYaw();
        float refPitch = RotationManager.prevRotation != null ? RotationManager.prevRotation.getPitch() : mc.player.getPitch();

        // --- correctRotation ---
        correctRotation = (mode.is("Telly Bridge") && canBuildNow)
                ? getTargetRotation(firstGroundTick, refYaw, refPitch)
                : getPlayerYawRotation();
        if (correctRotation == null) correctRotation = getPlayerYawRotation();

        if (!haveTarget) {
            Client.delayPackets.clear();
            lastRots.setYawPitch(rots.getYaw(), rots.getPitch());
            return;
        }

        // --- clutch delayPackets path (OpenZen verbatim, not used in TellyBridge) ---
        if (clutch.getValue() && !mode.is("Telly Bridge") && !mode.is("Old Telly")
                && (!canBuildNow || velocityDelay > 0) && rotationDelay <= 8) {
            Rotation toBlock        = RotationUtil.rotationToBlock(targetSupport, 1.0f);
            Rotation previousTarget = RotationManager.targetRotation;
            rots.setYawPitch(toBlock.getYaw(), toBlock.getPitch());
            RotationManager.targetRotation = rots;
            rotationDelay++;
            Client.delayPackets.add(() -> {});
            Client.delayPackets.add(() -> {
                if (mc.player == null) return;
                if (RotationManager.prevSentRotation == null) RotationManager.prevSentRotation = new Rotation();
                RotationManager.prevSentRotation.setYawPitch(toBlock.getYaw(), toBlock.getPitch());
                // OpenZen: add 720 to yaw to flag as clutch packet
                float sendYaw = toBlock.getYaw();
                if (sendYaw > -360.0f && sendYaw < 360.0f) sendYaw += 720.0f;
                boolean shouldSend = previousTarget == null || previousTarget.getYaw() != toBlock.getYaw();
                if (shouldSend)
                    PacketUtil.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                            sendYaw, toBlock.getPitch(), mc.player.isOnGround(), mc.player.horizontalCollision));
                doSnap();
                // NOTE: no recursive onTick call — MixinClientWorld drains one packet per entity tick,
                // which naturally re-invokes the normal tick flow next frame.
            });
            return;
        }

        // --- normal path ---
        canBuildNow = true;
        Client.delayPackets.clear();
        rotationDelay = 0;

        if (mode.is("Normal") && snap.getValue()) {
            rots.setYaw(correctRotation.getYaw());
        } else {
            rots.setYaw(RotationUtil.moveTowards((float) getBlockDistance(), rots.getYaw(), correctRotation.getYaw()));
        }
        rots.setPitch(correctRotation.getPitch());

        // sneak timer — Normal mode only (OpenZen: not used in Telly/KeepY)
        if (sneak.getValue() && mode.is("Normal")) {
            eagleTimer++;
            if (eagleTimer == 18) {
                if (mc.player.isSprinting()) { mc.options.sprintKey.setPressed(false); mc.player.setSprinting(false); }
                mc.options.sneakKey.setPressed(true);
            } else if (eagleTimer >= 21) {
                mc.options.sneakKey.setPressed(false);
                eagleTimer = 0;
            }
        }

        // --- mode-specific (OpenZen verbatim) ---
        if (mode.is("Telly Bridge") || mode.is("Old Telly")) {
            mc.options.jumpKey.setPressed(MovementUtil.isMoving() || jumpHeld);
            if (mode.is("Old Telly")) rots.setYaw(mc.player.getYaw());
            tryPlace();
        } else if (mode.is("Keep Y")) {
            mc.options.jumpKey.setPressed(MovementUtil.isMoving() || jumpHeld);
            tryPlace();
        } else {
            if (eagle.getValue())
                mc.options.sneakKey.setPressed(mc.player.isOnGround() && isOnBlockEdge(0.3f));
            if (snap.getValue() && !jumpHeld) resetSnap();
            tryPlace();
        }

        lastRots.setYawPitch(rots.getYaw(), rots.getPitch());
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) { setSuffix(mode.getValue()); }

    // =========================================================================
    // Placement
    // =========================================================================

    /**
     * tryPlace — mirrors OpenZen onPreMotion gate logic.
     * Called AFTER rots has been fully set for this tick.
     */
    private void tryPlace() {
        if (mc.currentScreen != null || !haveTarget) return;
        boolean canRayTrace = RayTraceUtil.canRayTrace(rots, targetFace, targetSupport, false);
        if (!canBuildNow && !isPlacementReachable()) return;
        if (rotationDelay <= 0 && !mode.is("Old Telly") && !mode.is("Telly Bridge") && !canRayTrace) return;
        doSnap();
    }

    private void doSnap() {
        if (!haveTarget || mc.player == null || mc.interactionManager == null) return;
        if (!BlockUtil.isPlaceable(mc.player.getMainHandStack())) return;
        if (targetFace == null) return;
        if (targetFace == Direction.UP && !mc.player.isOnGround()
                && MovementUtil.isMoving() && !mc.options.jumpKey.isPressed()
                && !mode.is("Normal")) return;

        BlockHitResult hit = new BlockHitResult(
                getHitVec(targetSupport, targetFace), targetFace, targetSupport, false);
        if (mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit).isAccepted())
            mc.player.swingHand(Hand.MAIN_HAND);
    }

    // =========================================================================
    // Target resolution  (OpenZen applyRotations / isAbovePlaceable)
    // =========================================================================

    private void resolveTarget() {
        haveTarget = false;
        Vec3d eye = mc.player.getEyePos();

        if (!canBuildNow)
            eye = eye.add(mc.player.getVelocity().multiply(2));
        if (clutch.getValue() && mc.player.getVelocity().y < 0.01) {
            MotionSimulator sim = new MotionSimulator(mc.player);
            sim.simulateWithFriction(2);
            double simEyeY = sim.y + mc.player.getEyeHeight(mc.player.getPose());
            eye = new Vec3d(eye.x, Math.max(simEyeY, eye.y), eye.z);
        }

        BlockPos feet = BlockPos.ofFloored(eye.x, targetYLevel + 0.1, eye.z);
        int fx = feet.getX(), fz = feet.getZ();

        if (mc.world.getBlockState(feet).hasSolidTopSurface(mc.world, feet, mc.player)) return;

        if (isAbovePlaceable(eye, feet)) return;
        for (int r = 1; r <= 6; r++) {
            if (isAbovePlaceable(eye, new BlockPos(fx, targetYLevel - r, fz))) return;
            for (int x = 1; x <= r; x++) {
                for (int z = 0; z <= r - x; z++) {
                    int yOff = r - x - z;
                    for (int sx = 0; sx <= 1; sx++) for (int sz = 0; sz <= 1; sz++) {
                        if (isAbovePlaceable(eye, new BlockPos(
                                fx + (sx == 0 ? x : -x),
                                targetYLevel - yOff,
                                fz + (sz == 0 ? z : -z)))) return;
                    }
                }
            }
        }
    }

    private boolean isAbovePlaceable(Vec3d eye, BlockPos pos) {
        if (mc.world == null || mc.player == null) return false;
        if (!mc.world.getBlockState(pos).isAir()) return false;
        Vec3d center = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        for (Direction dir : Direction.values()) {
            Vec3d offsetCenter = center.add(Vec3d.of(dir.getVector()).multiply(0.5));
            BlockPos offset = pos.offset(dir);
            if (mc.world.getBlockState(offset).isSolidSurface(mc.world, offset, mc.player, dir)) {
                Vec3d delta = offsetCenter.subtract(eye);
                if (delta.lengthSquared() <= 20.25
                        && delta.normalize().dotProduct(Vec3d.of(dir.getVector()).normalize()) >= 0.0) {
                    targetSupport = offset;
                    targetFace    = dir.getOpposite();
                    haveTarget    = true;
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isPlacementReachable() {
        if (!haveTarget || mc.player == null) return false;
        Vec3d hitPoint = new Vec3d(targetSupport.getX() + 0.5, targetSupport.getY() + 0.5, targetSupport.getZ() + 0.5)
                .add(Vec3d.of(targetFace.getVector()).multiply(0.5));
        Vec3d toBlock = hitPoint.subtract(mc.player.getEyePos());
        return toBlock.lengthSquared() <= 20.25
                && toBlock.normalize().dotProduct(Vec3d.of(targetFace.getVector()).multiply(-1).normalize()) >= 0.0;
    }

    // =========================================================================
    // Rotation  (OpenZen getTargetRotation → findValidRotation → getSnappedRotation)
    // =========================================================================

    private Rotation getTargetRotation(boolean firstGround, float refYaw, float refPitch) {
        if (!MovementUtil.isMoving()) return getPlayerYawRotation();
        if (!haveTarget) return new Rotation(mc.player.getYaw(), mc.player.getPitch());

        Vec3d    hitVec   = getHitVec(targetSupport, targetFace);
        Rotation rot      = RotationUtil.rotationFromVec(hitVec);
        double   yawDelta = RotationUtil.angleDiffDouble(rot.getYaw(), refYaw);

        if (groundTicks > 0) {
            if (!mc.options.jumpKey.isPressed())
                return new Rotation(mc.player.getYaw(), 75.5f);
            if (groundTicks == 1) {
                if (!firstGround) {
                    rot.setYaw(refYaw + RotationUtil.clampAngle((float) yawDelta, (float)(yawDelta / 2.0)));
                    rot.setPitch(75.5f);
                } else {
                    rot = RotationUtil.rotationFromVec(hitVec);
                }
                ReflectionUtil.setJumpDelay(2);
            } else if (groundTicks == 2) {
                return new Rotation(mc.player.getYaw(), 75.5f);
            }
        } else {
            float clampLimit = airTicks == 1 ? 90.0f : 50.0f;
            clampLimit -= MathUtil.getRandomInRange(0.001f, 0.006f);
            rot.setYaw(refYaw + RotationUtil.clampAngle((float) yawDelta, clampLimit));
        }

        rot = findValidRotation(rot, firstGround, refYaw, refPitch);
        return getSnappedRotation(rot, refYaw, refPitch);
    }

    private Rotation findValidRotation(Rotation rot, boolean firstGround, float refYaw, float refPitch) {
        if (firstGround) return rot;
        double delta = MathHelper.wrapDegrees(rot.getYaw() - refYaw);
        if (Math.abs(delta) > 90.0)
            rot.setYaw((float)(refYaw + Math.copySign(90.0, delta)));
        double maxStep = Math.max(45.0, 180.0 / Math.max(1.0, rotationTick.getValue().doubleValue()));
        if (mode.is("Telly Bridge")) maxStep = Math.max(maxStep, 75.0);
        return RotationUtil.smoothRotation(new Rotation(refYaw, refPitch), rot, maxStep);
    }

    private Rotation getSnappedRotation(Rotation rot, float refYaw, float refPitch) {
        double yd = Math.abs(MathHelper.wrapDegrees(rot.getYaw() - refYaw));
        double pd = Math.abs(rot.getPitch() - refPitch);
        boolean stuckY = yd > 2.0 && !Double.isNaN(lastYawDiff)   && Math.abs(yd - lastYawDiff)   < 1e-4;
        boolean stuckP = pd > 2.0 && !Double.isNaN(lastPitchDiff) && Math.abs(pd - lastPitchDiff) < 1e-4;
        if (stuckY || stuckP) {
            float jy = MathUtil.getRandomInRange(0.095f, 0.19f);
            float jp = MathUtil.getRandomInRange(0.016f, 0.055f);
            if ((jitterCounter++ & 1) == 0) jy = -jy;
            rot = rot.clone();
            rot.setYaw(rot.getYaw() + jy);
            rot.setPitch(MathHelper.clamp(rot.getPitch() + jp, -89.5f, 89.5f));
            yd = Math.abs(MathHelper.wrapDegrees(rot.getYaw() - refYaw));
            pd = Math.abs(rot.getPitch() - refPitch);
        }
        lastYawDiff   = yd;
        lastPitchDiff = pd;
        return rot;
    }

    private Rotation getPlayerYawRotation() {
        if (!haveTarget || mc.player == null) return new Rotation(mc.player != null ? mc.player.getYaw() : 0, 0);
        return RotationUtil.rotationToBlock(targetSupport, 0.0f);
    }

    private double getBlockDistance() {
        if (mode.is("Old Telly")) return 180.0;
        double base = Math.max(60.0, 360.0 / rotationTick.getValue().doubleValue());
        return Math.max(base, 180.0);
    }

    // =========================================================================
    // Render
    // =========================================================================

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!haveTarget || mc.player == null || mc.gameRenderer == null) return;
        MatrixStack matrices = event.getMatrices();
        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        Box box = new Box(targetSupport.offset(targetFace));
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        Color c = new Color(74, 144, 226);
        RenderSystem.setShaderColor(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 0.25f);
        RenderUtil.drawSolidBox(box, matrices);
        RenderSystem.setShaderColor(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 0.75f);
        RenderUtil.drawOutlineBox(box, matrices);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        matrices.pop();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() instanceof BlockItem && BlockUtil.isPlaceable(s)) total += s.getCount();
        }
        if (total == 0) return;
        String count = String.valueOf(total);
        String suffix = " Blocks";
        DrawContext ctx = event.getContext();
        int cx = mc.getWindow().getScaledWidth() / 2;
        int y  = mc.getWindow().getScaledHeight() / 2 - 20;
        int tw = mc.textRenderer.getWidth(count + suffix);
        int x  = cx - tw / 2;
        ctx.drawText(mc.textRenderer, count,  x, y, 0xF4555562, false);
        ctx.drawText(mc.textRenderer, suffix, x + mc.textRenderer.getWidth(count), y, 0xFFFFFFFF, false);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void resetSnap() {
        if (!haveTarget) return;
        HitResult r = RayTraceUtil.rayTrace(1.0f, rots);
        boolean looking = r instanceof BlockHitResult bhr
                && bhr.getBlockPos().equals(targetSupport)
                && bhr.getSide() != Direction.UP;
        if (!looking && mc.player.age % 4 == 0)
            rots.setYaw(mc.player.getYaw() + MathUtil.getRandomInRange(-0.25f, 0.25f));
    }

    public static Vec3d getHitVec(BlockPos pos, Direction d) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        if (d != Direction.UP && d != Direction.DOWN) y += rand(0.3); else { x += rand(0.3); z += rand(0.3); }
        if (d == Direction.WEST  || d == Direction.EAST)  z += rand(0.3);
        if (d == Direction.SOUTH || d == Direction.NORTH) x += rand(0.3);
        return new Vec3d(x, y, z);
    }

    private static double rand(double r) { return (Math.random() * 2 - 1) * r; }

    public static boolean isOnBlockEdge(float inflate) {
        if (mc.world == null || mc.player == null) return false;
        return !mc.world.getBlockCollisions(mc.player,
                mc.player.getBoundingBox().offset(0, -0.5, 0).contract(inflate, 0, inflate))
                .iterator().hasNext();
    }
}
