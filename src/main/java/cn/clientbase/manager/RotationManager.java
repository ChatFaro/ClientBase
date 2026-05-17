package cn.clientbase.manager;

import cn.clientbase.event.base.annotation.EventPriority;
import cn.clientbase.event.base.annotation.EventTarget;
import cn.clientbase.event.impl.*;
import cn.clientbase.manager.movement.MovementCorrection;
import cn.clientbase.module.impl.combat.KillAura;
import cn.clientbase.util.IMinecraft;
import cn.clientbase.util.player.MovementUtil;
import cn.clientbase.util.player.RotationUtil;

/**
 * Rotation Manager
 * @author DSJ
 */
public class RotationManager implements IMinecraft {
    public static float[] currentRotations;
    public static float[] targetRotations;
    public static float[] lastRotations;

    public static MovementCorrection correctMovement;
    private static double rotationSpeed;
    private static boolean enabled;

    public RotationManager() {
        instance.getEventManager().register(this);
    }

    public static void setRotations(float[] rotations, double rotationSpeed, MovementCorrection correctMovement) {
        RotationManager.targetRotations = rotations;
        RotationManager.rotationSpeed = rotationSpeed;
        RotationManager.correctMovement = correctMovement;

        enabled = true;
    }

    @EventTarget
    @EventPriority(999)
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (mc.player == null) return;

        KillAura aura = instance.getModuleManager().getModule(KillAura.class);

        if (aura.isEnabled() && aura.getTarget() != null && aura.getRotations() != null) {
            setRotations(aura.getRotations(), aura.getRotationSpeed().getValue(), aura.getMovementFixMode().is("None") ? MovementCorrection.None : (aura.getMovementFixMode().is("Silent") ? MovementCorrection.Silent : MovementCorrection.Strict));
        } else {
            enabled = false;
        }

        lastRotations = currentRotations;
        currentRotations = RotationUtil.getSmoothRotation(lastRotations, targetRotations, rotationSpeed + Math.random());
        mc.gameRenderer.updateCrosshairTarget(1.0f);
    }

    @EventTarget
    @EventPriority(999)
    public void onLook(LookEvent e) {
        if (mc.player == null) return;

        if (enabled) {
            e.setRotation(currentRotations);
            e.setLastRotation(lastRotations);
        }
    }

    @EventTarget
    @EventPriority(999)
    public void onStrafe(StrafeEvent e) {
        if (mc.player == null) return;

        if (enabled && correctMovement != MovementCorrection.None) {
            e.setYaw(currentRotations[0]);
        }
    }

    @EventTarget
    @EventPriority(999)
    public void onJump(JumpEvent e) {
        if (mc.player == null) return;

        if (enabled && correctMovement != MovementCorrection.None) {
            e.setYaw(currentRotations[0]);
        }
    }

    @EventTarget
    @EventPriority(999)
    public void onMotion(MotionEvent e) {
        if (mc.player == null) return;

        if (e.isPre()) {
            if (!enabled || currentRotations == null || lastRotations == null || targetRotations == null) {
                currentRotations = targetRotations = lastRotations = new float[]{mc.player.getYaw(), mc.player.getPitch()};
            }

            if (enabled) {
                e.setYaw(currentRotations[0]);
                e.setPitch(currentRotations[1]);
            }
        }
    }

    @EventTarget
    @EventPriority(999)
    public void onMoveInput(MoveInputEvent e) {
        if (enabled && correctMovement == MovementCorrection.Silent) {
            MovementUtil.fixMovement(e, currentRotations[0]);
        }
    }

    @EventTarget
    @EventPriority(999)
    public void onRotation(RotationEvent e) {
        if (mc.player == null) return;

        if (enabled) {
            e.setRotation(currentRotations);
            e.setLastRotation(lastRotations);
        }
    }
}
