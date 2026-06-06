package cn.clientbase.module.impl.combat;

import cn.clientbase.module.Module;
import cn.clientbase.event.base.annotation.EventTarget;
import cn.clientbase.event.impl.MoveInputEvent;
import cn.clientbase.event.impl.PacketEvent;
import cn.clientbase.event.impl.TickEvent;
import cn.clientbase.event.impl.UpdateEvent;
import cn.clientbase.module.Category;
import cn.clientbase.module.value.impl.ModeValue;
import cn.clientbase.module.value.impl.NumberValue;
import cn.clientbase.util.network.PacketUtil;
import lombok.Getter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.TeamS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;

import java.util.concurrent.LinkedBlockingDeque;

@Getter
public class Velocity extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Vanilla", "Vanilla", "Legit", "NoXZ");
    private final NumberValue timeout = new NumberValue("Timeout", 12, 1, 40, 1, () -> mode.is("NoXZ"));
    private final NumberValue attackAmount = new NumberValue("Attack Amount", 5, 0, 20, 1, () -> mode.is("NoXZ"));
    private final cn.clientbase.module.value.impl.BoolValue sprintCheck =
            new cn.clientbase.module.value.impl.BoolValue("Sprint Check", true, () -> mode.is("NoXZ"));

    // Exposed so KillAura can back off its own CPS while NoXZ is mid burst-attack.
    public static boolean isAttacking = false;
    public static int attackCount = 0;

    private boolean jump = false;

    // NoXZ state: hold the knockback in air, freeze outgoing moves, flush once grounded or timed out.
    private final LinkedBlockingDeque<Packet<?>> packetQueue = new LinkedBlockingDeque<>();
    private final LinkedBlockingDeque<Packet<?>> movePacketQueue = new LinkedBlockingDeque<>();
    private EntityVelocityUpdateS2CPacket knockbackPacket = null;
    private boolean suspending = false;
    private boolean flushing = false;
    private int suspendTicks = 0;

    public Velocity() {
        super("Velocity", Category.Combat);
        setDescription("Modify the velocity of the player.");
    }

    @Override
    public void onDisable() {
        resetNoXZ();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null) return;

        Packet<?> packet = event.getPacket();

        if (event.getType() == PacketEvent.Type.Received) {
            switch (mode.getValue()) {
                case "Vanilla" -> {
                    if (packet instanceof EntityVelocityUpdateS2CPacket velocity) {
                        if (velocity.getEntityId() == mc.player.getId()) {
                            event.setCancelled(true);
                        }
                    }
                }

                case "Legit" -> {
                    if (packet instanceof EntityVelocityUpdateS2CPacket velocity) {
                        if (velocity.getEntityId() == mc.player.getId()) {
                            jump = true;
                        }
                    }
                }

                case "NoXZ" -> handleNoXZReceive(event, packet);
            }
        } else if (event.getType() == PacketEvent.Type.Send) {
            // While suspended, hold our own movement so the server keeps us in place.
            if (mode.is("NoXZ") && suspending && !flushing && packet instanceof PlayerMoveC2SPacket) {
                movePacketQueue.add(packet);
                event.setCancelled(true);
            }
        }
    }

    private void handleNoXZReceive(PacketEvent event, Packet<?> packet) {
        if (flushing) return;
        if (shouldIgnore()) return;

        // A position correction means the server reset us; release and resync immediately.
        if (packet instanceof PlayerPositionLookS2CPacket) {
            if (suspending) release();
            resetNoXZ();
            return;
        }

        if (suspending) {
            if (!isAllowedPacket(packet)) {
                packetQueue.add(packet);
                event.setCancelled(true);
            }
            return;
        }

        if (packet instanceof EntityVelocityUpdateS2CPacket motionPacket) {
            if (motionPacket.getEntityId() != mc.player.getId()) return;

            // Only worth suspending while airborne (where X/Z knockback actually carries).
            if (!mc.player.isOnGround()) {
                suspending = true;
                suspendTicks = 0;
                knockbackPacket = motionPacket;
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || !mode.is("NoXZ")) {
            isAttacking = false;
            attackCount = 0;
            return;
        }

        // Decay the burst-attack flag a tick after the flush so KillAura's throttle window closes.
        if (isAttacking && !suspending) {
            isAttacking = false;
            attackCount = 0;
        }

        if (!suspending) return;

        if (shouldIgnore()) {
            release();
            return;
        }

        suspendTicks++;

        boolean grounded = mc.player.isOnGround();
        boolean timedOut = suspendTicks >= timeout.getValue().intValue();
        if (grounded || timedOut) {
            release();
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (mc.player == null) return;

        if (jump) {
            event.setJumping(true);
            jump = false;
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        setSuffix(mode.getValue());

        if (!mode.is("NoXZ") && (suspending || !packetQueue.isEmpty() || !movePacketQueue.isEmpty())) {
            release();
        }
    }

    /**
     * Flush everything we held back: resend our queued movement, apply the stored
     * knockback (now harmless because we are on ground), then re-inject held packets in order.
     * On a clean grounded release with a live KillAura target, burst-attack it.
     */
    private void release() {
        boolean grounded = mc.player != null && mc.player.isOnGround();
        flushing = true;
        try {
            while (!movePacketQueue.isEmpty()) {
                Packet<?> packet = movePacketQueue.poll();
                if (packet == null) continue;
                PacketUtil.sendPacketNoEvent(packet);
            }

            if (knockbackPacket != null) {
                PacketUtil.receivePacket(knockbackPacket);
                knockbackPacket = null;
            }

            while (!packetQueue.isEmpty()) {
                Packet<?> packet = packetQueue.poll();
                if (packet == null) continue;
                PacketUtil.receivePacket(packet);
            }

            if (grounded) {
                burstAttack();
            }
        } catch (Exception ignored) {
        } finally {
            suspending = false;
            suspendTicks = 0;
            flushing = false;
        }
    }

    /** Pull the current KillAura target and hit it attackAmount times, exposing state for CPS sync. */
    private void burstAttack() {
        int amount = attackAmount.getValue().intValue();
        if (amount <= 0) return;
        if (mc.player == null || mc.interactionManager == null) return;
        if (sprintCheck.getValue() && !mc.player.isSprinting()) return;

        LivingEntity target = getSyncTarget();
        if (target == null || !target.isAlive()) return;
        if (mc.player.distanceTo(target) > 4.0f) return;

        isAttacking = true;
        for (int i = 0; i < amount; i++) {
            attackCount = amount - i;
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            if (!target.isAlive()) break;
        }
    }

    /** Read KillAura's current target, if KillAura is on. */
    private LivingEntity getSyncTarget() {
        KillAura killAura = getModule(KillAura.class);
        if (killAura == null || !killAura.isEnabled()) return null;
        return killAura.getTarget();
    }

    private void resetNoXZ() {
        suspending = false;
        flushing = false;
        suspendTicks = 0;
        knockbackPacket = null;
        packetQueue.clear();
        movePacketQueue.clear();
        isAttacking = false;
        attackCount = 0;
    }

    private boolean shouldIgnore() {
        if (mc.player == null || mc.world == null) return true;
        if (mc.player.isDead() || !mc.player.isAlive() || mc.player.getHealth() <= 0.0f) return true;
        if (mc.player.isSpectator() || mc.player.getAbilities().flying) return true;
        return mc.player.isTouchingWater() || mc.player.isInLava() || mc.player.isClimbing();
    }

    /** Packets safe to process during suspension without desyncing the held movement. */
    private boolean isAllowedPacket(Packet<?> packet) {
        return packet instanceof EntityVelocityUpdateS2CPacket
                || packet instanceof HealthUpdateS2CPacket
                || packet instanceof EntityDamageS2CPacket
                || packet instanceof GameMessageS2CPacket
                || packet instanceof TitleS2CPacket
                || packet instanceof TeamS2CPacket
                || packet instanceof DisconnectS2CPacket;
    }
}
