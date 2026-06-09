package cn.clientbase.module.impl.combat;

import cn.clientbase.event.base.annotation.EventTarget;
import cn.clientbase.event.impl.MotionEvent;
import cn.clientbase.event.impl.MoveInputEvent;
import cn.clientbase.event.impl.PacketEvent;
import cn.clientbase.event.impl.TickEvent;
import cn.clientbase.event.impl.UpdateEvent;
import cn.clientbase.module.Category;
import cn.clientbase.module.Module;
import cn.clientbase.module.value.impl.BoolValue;
import cn.clientbase.module.value.impl.ModeValue;
import cn.clientbase.module.value.impl.NumberValue;
import cn.clientbase.util.network.PacketUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.TeamS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.LinkedBlockingDeque;

public class Velocity extends Module {
    public final ModeValue mode = new ModeValue("Mode", "NoXZ", "Vanilla", "Legit", "NoXZ");
    public final NumberValue timeout = new NumberValue("Timeout", 12, 1, 40, 1, () -> mode.is("NoXZ"));
    public final NumberValue attackAmount = new NumberValue("Attack Amount", 5, 0, 20, 1, () -> mode.is("NoXZ"));
    public final BoolValue sprintCheck = new BoolValue("Sprint Check", true, () -> mode.is("NoXZ"));

    public static boolean isAttacking = false;
    public static int attackCount = 0;

    private boolean jump = false;

    private final LinkedBlockingDeque<Packet<?>> packetQueue = new LinkedBlockingDeque<>();
    private final LinkedBlockingDeque<Packet<?>> moveQueue = new LinkedBlockingDeque<>();
    private EntityVelocityUpdateS2CPacket knockbackPacket = null;
    private boolean isSuspending = false;
    private volatile boolean isFlushing = false;
    private int suspendTicks = 0;
    private int flagCooldown = 0;
    private boolean shouldFlushMotion = false;

    private int attackCooldown = 0;
    private int hitCounter = 0;
    private Entity attackTarget = null;
    private int attacksRemaining = 0;

    public Velocity() {
        super("Velocity", Category.Combat);
    }

    @Override
    public void onEnable() {
        resetAll();
    }

    @Override
    public void onDisable() {
        resetAll();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null) return;

        if (event.getType() == PacketEvent.Type.Received) {
            switch (mode.getValue()) {
                case "Vanilla" -> {
                    if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket velocity
                            && velocity.getEntityId() == mc.player.getId()) {
                        event.setCancelled(true);
                    }
                }
                case "Legit" -> {
                    if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket velocity
                            && velocity.getEntityId() == mc.player.getId()) {
                        jump = true;
                    }
                }
                case "NoXZ" -> handleReceive(event);
            }
        } else if (event.getType() == PacketEvent.Type.Send
                && mode.is("NoXZ")
                && isSuspending
                && !isFlushing
                && event.getPacket() instanceof PlayerMoveC2SPacket) {
            moveQueue.add(event.getPacket());
            event.setCancelled(true);
        }
    }

    private void handleReceive(PacketEvent event) {
        if (isFlushing || shouldIgnore()) return;

        Packet<?> packet = event.getPacket();

        if (packet instanceof PlayerPositionLookS2CPacket) {
            if (isSuspending) release();
            resetSuspension();
            flagCooldown = 2;
            return;
        }

        if (flagCooldown > 0) return;

        if (isSuspending) {
            if (!isAllowedPacket(packet)) {
                packetQueue.add(packet);
                event.setCancelled(true);
            }
            return;
        }

        if (!(packet instanceof EntityVelocityUpdateS2CPacket velocity)) return;
        if (velocity.getEntityId() != mc.player.getId()) return;

        if (Math.abs(velocity.getVelocityX()) > 0.01 || Math.abs(velocity.getVelocityZ()) > 0.01) {
            hitCounter = 1;
        }

        if (velocity.getVelocityY() <= 0) return;

        Entity target = getAttackTarget();
        boolean canAttack = isValidTarget(target) && mc.player.isSprinting();
        if (!isValidTarget(target)) {
            event.setCancelled(true);
            scheduleMotionFlush();
            return;
        }

        if (!mc.player.isOnGround() || !canAttack) {
            isSuspending = true;
            suspendTicks = 0;
            knockbackPacket = velocity;
            event.setCancelled(true);
        } else {
            attackTarget = target;
            attacksRemaining = attackAmount.getValue().intValue();
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || !mode.is("NoXZ")) {
            isAttacking = false;
            attackCount = 0;
            return;
        }

        if (attackCooldown > 0 && --attackCooldown <= 0) {
            isAttacking = false;
            attackCount = 0;
        }
        if (hitCounter > 0 && ++hitCounter > 2) hitCounter = 0;

        if (shouldIgnore()) {
            clearAttackTarget();
            if (isSuspending) release();
            return;
        }

        if (flagCooldown > 0) {
            flagCooldown--;
            clearAttackTarget();
        }

        if (isSuspending) {
            suspendTicks++;
            boolean grounded = mc.player.isOnGround();
            boolean timedOut = suspendTicks >= timeout.getValue().intValue();
            if (grounded || timedOut) {
                Entity target = getAttackTarget();
                boolean canAttack = grounded && isValidTarget(target) && mc.player.isSprinting();
                if (canAttack) {
                    isFlushing = true;
                    attackTarget = target;
                    attacksRemaining = attackAmount.getValue().intValue();
                    sendMovePackets();
                    applyKnockback();
                    doAttackSequence();
                    scheduleMotionFlush();
                    isSuspending = false;
                    suspendTicks = 0;
                    isFlushing = false;
                } else {
                    if (!isValidTarget(target) || !mc.player.isSprinting() || timedOut) {
                        discardKnockback();
                    } else {
                        release();
                    }
                    if (grounded && mc.player.isSprinting()) {
                        mc.player.setSprinting(false);
                    }
                }
            }
            return;
        }

        if (attacksRemaining > 0 && attackTarget != null) {
            doAttackSequence();
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre() || !shouldFlushMotion) return;
        while (!packetQueue.isEmpty()) {
            Packet<?> packet = packetQueue.poll();
            if (packet != null) {
                try {
                    PacketUtil.receivePacket(packet);
                } catch (Exception ignored) {
                }
            }
        }
        shouldFlushMotion = false;
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (jump) {
            event.setJumping(true);
            jump = false;
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        setSuffix(mode.getValue());
        if (!mode.is("NoXZ") && isSuspending) release();
    }

    private void doAttackSequence() {
        if (!isValidTarget(attackTarget)) {
            clearAttackTarget();
            return;
        }
        isAttacking = true;
        attackCount = attacksRemaining--;
        attackCooldown = 2;
        doAttack(attackTarget);
        if (attacksRemaining <= 0) {
            clearAttackTarget();
        }
    }

    private boolean doAttack(Entity entity) {
        if (mc.player == null || mc.interactionManager == null) return false;
        if (sprintCheck.getValue() && !mc.player.isSprinting()) return false;
        boolean wasSprinting = mc.player.isSprinting();
        if (wasSprinting) mc.player.setSprinting(false);
        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);
        if (wasSprinting) {
            Vec3d velocity = mc.player.getVelocity();
            mc.player.setVelocity(velocity.x * 0.6, velocity.y, velocity.z * 0.6);
        }
        return true;
    }

    private Entity getAttackTarget() {
        KillAura killAura = getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null) {
            return killAura.getTarget();
        }
        return getHitResultEntity();
    }

    private Entity getHitResultEntity() {
        if (mc.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity living
                && hit.getEntity() != mc.player
                && hit.getEntity().isAlive()
                && !hit.getEntity().isSpectator()
                && !living.isDead()
                && living.getHealth() > 0f) {
            return hit.getEntity();
        }
        return null;
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == null || !entity.isAlive() || entity.isSpectator()) return false;
        if (entity instanceof LivingEntity living && (living.isDead() || living.getHealth() <= 0f)) {
            return false;
        }
        return getAABBDistance(entity) <= 3.7;
    }

    private double getAABBDistance(Entity entity) {
        if (mc.player == null) return Double.MAX_VALUE;
        Vec3d eye = mc.player.getEyePos();
        Box box = entity.getBoundingBox();
        double x = Math.max(box.minX, Math.min(eye.x, box.maxX));
        double y = Math.max(box.minY, Math.min(eye.y, box.maxY));
        double z = Math.max(box.minZ, Math.min(eye.z, box.maxZ));
        return eye.distanceTo(new Vec3d(x, y, z));
    }

    private void sendMovePackets() {
        while (!moveQueue.isEmpty()) {
            Packet<?> packet = moveQueue.poll();
            if (packet != null) PacketUtil.sendPacketNoEvent(packet);
        }
    }

    private void applyKnockback() {
        if (knockbackPacket != null) {
            try {
                PacketUtil.receivePacket(knockbackPacket);
            } catch (Exception ignored) {
            }
            knockbackPacket = null;
        }
    }

    private void scheduleMotionFlush() {
        shouldFlushMotion = true;
    }

    private void release() {
        isFlushing = true;
        sendMovePackets();
        applyKnockback();
        scheduleMotionFlush();
        isFlushing = false;
        isSuspending = false;
        suspendTicks = 0;
    }

    private void discardKnockback() {
        isFlushing = true;
        sendMovePackets();
        knockbackPacket = null;
        scheduleMotionFlush();
        isFlushing = false;
        isSuspending = false;
        suspendTicks = 0;
    }

    private void resetSuspension() {
        isSuspending = false;
        suspendTicks = 0;
        knockbackPacket = null;
        moveQueue.clear();
    }

    private void resetAll() {
        clearAttackTarget();
        resetSuspension();
        packetQueue.clear();
        isFlushing = false;
        shouldFlushMotion = false;
        flagCooldown = 0;
        hitCounter = 0;
        attackCooldown = 0;
        jump = false;
        isAttacking = false;
        attackCount = 0;
    }

    private void clearAttackTarget() {
        attackTarget = null;
        attacksRemaining = 0;
    }

    private boolean shouldIgnore() {
        if (mc.player == null || mc.world == null) return true;
        if (mc.player.isDead() || !mc.player.isAlive() || mc.player.getHealth() <= 0f) return true;
        if (mc.player.isSpectator() || mc.player.getAbilities().flying || mc.player.isSleeping()) return true;
        return mc.player.isTouchingWater() || mc.player.isInLava() || mc.player.isClimbing();
    }

    private boolean isAllowedPacket(Packet<?> packet) {
        return packet instanceof EntityVelocityUpdateS2CPacket
                || packet instanceof HealthUpdateS2CPacket
                || packet instanceof PlayerPositionLookS2CPacket
                || packet instanceof PlaySoundS2CPacket
                || packet instanceof EntityDamageS2CPacket
                || packet instanceof GameMessageS2CPacket
                || packet instanceof TitleS2CPacket
                || packet instanceof TeamS2CPacket
                || packet instanceof EntityAnimationS2CPacket animation
                && animation.getEntityId() != mc.player.getId();
    }
}
