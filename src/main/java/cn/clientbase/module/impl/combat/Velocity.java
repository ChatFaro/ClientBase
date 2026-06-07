package cn.clientbase.module.impl.combat;

import cn.clientbase.event.base.annotation.EventTarget;
import cn.clientbase.event.impl.*;
import cn.clientbase.module.Category;
import cn.clientbase.module.Module;
import cn.clientbase.module.value.impl.BoolValue;
import cn.clientbase.module.value.impl.ModeValue;
import cn.clientbase.module.value.impl.NumberValue;
import cn.clientbase.util.network.PacketUtil;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.Hand;

import java.util.concurrent.LinkedBlockingDeque;

public class Velocity extends Module {

    public final ModeValue   mode         = new ModeValue("Mode", "NoXZ", "Vanilla", "Legit", "NoXZ");
    public final NumberValue timeout      = new NumberValue("Timeout",       12, 1, 40, 1, () -> mode.is("NoXZ"));
    public final NumberValue attackAmount = new NumberValue("Attack Amount",  5, 0, 20, 1, () -> mode.is("NoXZ"));
    public final BoolValue   sprintCheck  = new BoolValue("Sprint Check", true,    () -> mode.is("NoXZ"));

    public static boolean isAttacking = false;
    public static int     attackCount = 0;

    // Legit
    private boolean jump = false;

    // NoXZ
    private final LinkedBlockingDeque<Packet<?>> packetQueue   = new LinkedBlockingDeque<>();
    private final LinkedBlockingDeque<Packet<?>> moveQueue     = new LinkedBlockingDeque<>();
    private EntityVelocityUpdateS2CPacket knockbackPacket = null;
    private boolean isSuspending = false;
    private volatile boolean isFlushing = false;
    private int suspendTicks = 0;
    private int flagCooldown = 0;
    private boolean shouldFlushMotion = false;
    // ground-hit burst attack (OpenZen: attackTarget / attacksRemaining)
    private LivingEntity scheduledAttackTarget = null;
    private int scheduledAttackAmount = 0;

    public Velocity() {
        super("Velocity", Category.Combat);
    }

    @Override
    public void onDisable() {
        resetAll();
    }

    // =========================================================================
    // Packet receive/send
    // =========================================================================

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null) return;

        if (event.getType() == PacketEvent.Type.Received) {
            switch (mode.getValue()) {
                case "Vanilla" -> {
                    if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket v
                            && v.getEntityId() == mc.player.getId())
                        event.setCancelled(true);
                }
                case "Legit" -> {
                    if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket v
                            && v.getEntityId() == mc.player.getId())
                        jump = true;
                }
                case "NoXZ" -> handleReceive(event);
            }
        } else if (event.getType() == PacketEvent.Type.Send
                && mode.is("NoXZ") && isSuspending && !isFlushing
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

        if (!(packet instanceof EntityVelocityUpdateS2CPacket v)) return;
        if (v.getEntityId() != mc.player.getId()) return;

        // OpenZen: only act on packets with upward Y velocity (actual knockback hit)
        if (v.getVelocityY() <= 0) return;

        // Always suspend and cancel — we replay after landing / burst-attacking
        isSuspending    = true;
        suspendTicks    = 0;
        knockbackPacket = v;
        event.setCancelled(true);

        // If already on the ground and sprinting, schedule the burst immediately
        if (mc.player.isOnGround()) {
            LivingEntity target = getKATarget();
            if (target != null && mc.player.isSprinting()) {
                isFlushing = true;
                sendMovePackets();
                applyKnockback();
                shouldFlushMotion = true;
                isFlushing = false;
                scheduledAttackTarget = target;
                scheduledAttackAmount = attackAmount.getValue().intValue();
                resetSuspension();
            }
        }
    }

    // =========================================================================
    // Tick
    // =========================================================================

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || !mode.is("NoXZ")) {
            isAttacking = false; attackCount = 0;
            return;
        }

        if (flagCooldown > 0) flagCooldown--;

        // ground-hit instant attack (OpenZen: attacksRemaining path)
        if (scheduledAttackTarget != null) {
            doScheduledAttack();
            return;
        }

        if (shouldIgnore()) {
            if (isSuspending) release();
            return;
        }

        if (!isSuspending) return;

        suspendTicks++;
        boolean grounded = mc.player.isOnGround();
        boolean timedOut = suspendTicks >= timeout.getValue().intValue();

        // While suspended, maintain sprint so the burst attack is valid
        if (sprintCheck.getValue() && !mc.player.isSprinting())
            mc.player.setSprinting(true);

        if (grounded) {
            LivingEntity target = getKATarget();
            boolean canAttack   = target != null && mc.player.isSprinting();
            if (canAttack) {
                isFlushing = true;
                sendMovePackets();
                applyKnockback();
                shouldFlushMotion = true;
                isFlushing = false;
                scheduledAttackTarget = target;
                scheduledAttackAmount = attackAmount.getValue().intValue();
                resetSuspension();
            } else if (timedOut) {
                release();
            }
            // else: still on ground but no target yet — keep waiting until timeout
        } else if (timedOut) {
            release();
        }
    }

    private void doScheduledAttack() {
        if (scheduledAttackTarget == null) return;
        if (!scheduledAttackTarget.isAlive() || mc.player.distanceTo(scheduledAttackTarget) > 4.0f) {
            scheduledAttackTarget = null; scheduledAttackAmount = 0;
            isAttacking = false; attackCount = 0;
            return;
        }
        isAttacking = true;
        attackCount = scheduledAttackAmount--;
        mc.interactionManager.attackEntity(mc.player, scheduledAttackTarget);
        mc.player.swingHand(Hand.MAIN_HAND);
        if (scheduledAttackAmount <= 0) {
            scheduledAttackTarget = null;
            isAttacking = false; attackCount = 0;
        }
    }

    // =========================================================================
    // Motion: flush held server packets on pre (mirrors OpenZen onMotion)
    // =========================================================================

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!event.isPre() || !shouldFlushMotion) return;
        shouldFlushMotion = false;
        while (!packetQueue.isEmpty()) {
            Packet<?> p = packetQueue.poll();
            if (p != null) {
                try { PacketUtil.receivePacket(p); } catch (Exception ignored) {}
            }
        }
    }

    // =========================================================================
    // MoveInput: Legit jump
    // =========================================================================

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (jump) { event.setJumping(true); jump = false; }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        setSuffix(mode.getValue());
        if (!mode.is("NoXZ") && isSuspending) release();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private LivingEntity getKATarget() {
        KillAura ka = getModule(KillAura.class);
        if (ka == null || !ka.isEnabled()) return null;
        return ka.getTarget();
    }

    private void sendMovePackets() {
        while (!moveQueue.isEmpty()) {
            Packet<?> p = moveQueue.poll();
            if (p != null) PacketUtil.sendPacketNoEvent(p);
        }
    }

    private void applyKnockback() {
        if (knockbackPacket != null) {
            try { PacketUtil.receivePacket(knockbackPacket); } catch (Exception ignored) {}
            knockbackPacket = null;
        }
    }

    private void release() {
        isFlushing = true;
        sendMovePackets();
        applyKnockback();
        shouldFlushMotion = true;
        isFlushing = false;
        resetSuspension();
    }

    private void resetSuspension() {
        isSuspending          = false;
        suspendTicks          = 0;
        knockbackPacket       = null;
        packetQueue.clear();
        moveQueue.clear();
        scheduledAttackTarget = null;
        scheduledAttackAmount = 0;
        isAttacking = false;
        attackCount = 0;
    }

    private void resetAll() {
        resetSuspension();
        isFlushing        = false;
        shouldFlushMotion = false;
        flagCooldown      = 0;
        jump              = false;
    }

    private boolean shouldIgnore() {
        if (mc.player == null || mc.world == null) return true;
        if (mc.player.isDead() || !mc.player.isAlive() || mc.player.getHealth() <= 0f) return true;
        if (mc.player.isSpectator() || mc.player.getAbilities().flying) return true;
        return mc.player.isTouchingWater() || mc.player.isInLava() || mc.player.isClimbing();
    }

    private boolean isAllowedPacket(Packet<?> p) {
        return p instanceof EntityVelocityUpdateS2CPacket
            || p instanceof HealthUpdateS2CPacket
            || p instanceof EntityDamageS2CPacket
            || p instanceof GameMessageS2CPacket
            || p instanceof TitleS2CPacket
            || p instanceof TeamS2CPacket;
    }
}
