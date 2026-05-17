package cn.clientbase.module.impl.combat;

import cn.clientbase.event.base.annotation.EventTarget;
import cn.clientbase.event.impl.LivingUpdateEvent;
import cn.clientbase.event.impl.MotionEvent;
import cn.clientbase.event.impl.UpdateEvent;
import cn.clientbase.manager.RotationManager;
import cn.clientbase.module.Category;
import cn.clientbase.module.Module;
import cn.clientbase.module.value.impl.ModeValue;
import cn.clientbase.module.value.impl.NumberValue;
import cn.clientbase.util.misc.MathUtil;
import cn.clientbase.util.misc.TimerUtil;
import cn.clientbase.util.network.PacketUtil;
import cn.clientbase.util.player.EntityUtil;
import cn.clientbase.util.player.RotationUtil;
import lombok.Getter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter
public class KillAura extends Module {
    private final ModeValue targetMode = new ModeValue("Target Mode", "Single", "Single", "Switch");
    private final NumberValue switchDelay = new NumberValue("Switch Delay", 200, 0, 1000, 50, () -> targetMode.is("Switch"));

    private final ModeValue attackMode = new ModeValue("Attack Mode", "Legit", "Legit", "Packet");
    private final ModeValue priority = new ModeValue("Priority", "Distance", "Health", "Fov", "LivingTime", "Armor");

    private final NumberValue maxCps = new NumberValue("Max CPS", 10, 1, 20, 1);
    private final NumberValue minCps = new NumberValue("Min CPS", 7, 1, 20, 1);

    private final NumberValue attackRange = new NumberValue("Range", 3.0f, 1.0f, 6.0f, .1f);
    private final NumberValue blockRange = new NumberValue("Block Reach", 4.0f, 1.0f, 6.0f, .1f);
    private final NumberValue wallRange = new NumberValue("Wall Range", 0.0f, 0.0f, 6.0f, .1f);
    private final NumberValue rotationRange = new NumberValue("Rotation Range", 4.0f, 0.0f, 6.0f, .1f);
    private final ModeValue autoBlockMode = new ModeValue("AutoBlock Mode", "None", "None", "Fake", "Vanilla");
    private final NumberValue rotationSpeed = new NumberValue("Rotation Speed", 180, 0, 180, 5);
    private final ModeValue movementFixMode = new ModeValue("MovementFix Mode", "None", "None", "Silent", "Strict");

    private final List<LivingEntity> targets = new ArrayList<>();
    private final TimerUtil switchTimer = new TimerUtil();
    private final TimerUtil attackTimer = new TimerUtil();
    private LivingEntity target = null;
    private float[] rotations = null;

    private boolean renderBlock = false;
    private boolean blocking = false;

    public KillAura() {
        super("KillAura", Category.Combat);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    public void reset() {
        switchTimer.reset();
        attackTimer.reset();
        rotations = null;
        targets.clear();
        target = null;

        unBlock();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || !shouldAura()) return;

        updateTargets();
        if (targets.isEmpty()) {
            reset();
            return;
        }

        selectTarget();

        if (target != null) {
            if (canAttack(target)) {
                if (attackTimer.hasTimeElapsed(700L / getCps())) {
                    attack(target);
                    attackTimer.reset();
                }
            }
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (event.isPost()) {
            if (canBlock()) {
                block();
            } else {
                unBlock();
            }
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (target != null) {
            if (RotationUtil.getDistanceToEntity(target) <= rotationRange.getValue()) {
                rotations = RotationUtil.nearestRotation(target.getBoundingBox());
            }
        }
    }

    private void selectTarget() {
        if (targetMode.is("Switch")) {
            if (switchTimer.hasTimeElapsed(switchDelay.getValue().longValue())) {
                int index = 0;
                if (targets.size() > 1) {
                    index = (int) (Math.random() * targets.size());
                }
                target = targets.get(index);
                switchTimer.reset();
            }
        } else {
            target = targets.getFirst();
        }

        setSuffix(targetMode.getValue());
    }

    private boolean canAttack(LivingEntity target) {
        Vec3d bestPoint = RotationUtil.getNearestPointBB(target.getBoundingBox());
        float range = RotationUtil.isVisible(bestPoint) ? attackRange.getValue() : wallRange.getValue();
        return RotationUtil.getDistanceToEntity(target) <= range;
    }

    public boolean canBlock() {
        if (autoBlockMode.is("None") || !isHoldingSword() || target == null) return false;
        return RotationUtil.getDistanceToEntity(target) <= blockRange.getValue();
    }

    private void attack(LivingEntity entity) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (attackMode.is("Packet")) {
            PacketUtil.sendPacket(PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking()));
        } else {
            mc.interactionManager.attackEntity(mc.player, entity);
        }

        mc.player.swingHand(Hand.MAIN_HAND);
    }

    public void block() {
        if (mc.player == null || mc.world == null || autoBlockMode.is("None")) return;

        switch (autoBlockMode.getValue()) {
            case "Use Item":
                mc.options.useKey.setPressed(true);
                blocking = true;
                break;

            case "Vanilla":
                PacketUtil.sendSequencedPacketNoEvent(sequence -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, RotationManager.currentRotations[0], RotationManager.currentRotations[1]));
                blocking = true;
                break;
        }

        renderBlock = true;
    }

    private void unBlock() {
        if (autoBlockMode.is("None")) return;

        if (blocking) {
            switch (autoBlockMode.getValue()) {
                case "Use Item":
                    mc.options.useKey.setPressed(false);
                    blocking = false;
                    break;

                case "Vanilla":
                    PacketUtil.sendPacketNoEvent(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));
                    blocking = false;
                    break;
            }
        }

        renderBlock = false;
    }

    private void updateTargets() {
        if (mc.player == null || mc.world == null) return;

        targets.clear();
        for (LivingEntity entity : instance.getTargetManager().getTargets()) {
            if (filter(entity)) {
                targets.add(entity);
            }
        }

        if (!targets.isEmpty()) {
            targets.sort(sortTargets(priority.getValue()));
        }

        setSuffix(targetMode.getValue());
    }

    public Comparator<LivingEntity> sortTargets(final String priority) {
        return switch (priority) {
            case "Health" -> Comparator.comparingDouble(entity -> entity.getHealth() + entity.getAbsorptionAmount());
            case "Fov" -> Comparator.comparingDouble(RotationUtil::getRotationDifference);
            case "LivingTime" -> Comparator.comparingInt((LivingEntity entity) -> entity.age).reversed();
            case "Armor" -> Comparator.comparingInt(LivingEntity::getArmor);
            default -> Comparator.comparingDouble(RotationUtil::getDistanceToEntity);
        };
    }

    public boolean filter(LivingEntity entity) {
        if (mc.player == null || mc.world == null) return false;
        if (!EntityUtil.isSelected(entity)) return false;
        if (RotationUtil.getDistanceToEntity(entity) > rotationRange.getValue()) return false;
        return !entity.isDead() && entity.isAlive() && entity.getHealth() > 0;
    }

    private boolean shouldAura() {
        if (mc.player == null || mc.world == null) return false;
        return mc.player.isAlive() && !mc.player.isSpectator();
    }

    private long getCps() {
        long min = minCps.getValue().longValue();
        long max = maxCps.getValue().longValue();
        return MathUtil.getRandomInRange(min, max);
    }

    private boolean isHoldingSword() {
        if (mc.player == null) return false;
        return mc.player.getMainHandStack().getItem() instanceof SwordItem;
    }
}