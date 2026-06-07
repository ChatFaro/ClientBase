package cn.clientbase.manager;

import cn.clientbase.event.base.annotation.EventPriority;
import cn.clientbase.event.base.annotation.EventTarget;
import cn.clientbase.event.impl.*;
import cn.clientbase.manager.movement.MovementCorrection;
import cn.clientbase.module.impl.combat.KillAura;
import cn.clientbase.module.impl.movement.Scaffold;
import cn.clientbase.util.IMinecraft;
import cn.clientbase.util.player.MovementUtil;
import cn.clientbase.util.player.Rotation;
import cn.clientbase.util.player.RotationUtil;

/**
 * Rotation Manager — drives the outgoing movement packet yaw/pitch.
 * Priority order (low→high, executed last):
 *   KillAura  >  Scaffold  >  none
 */
public class RotationManager implements IMinecraft {
    public static float[] currentRotations;
    public static float[] targetRotations;
    public static float[] lastRotations;

    // ---- OpenZen RotationHandler state model (Rotation-typed) ----
    // prevRotation     = last rotation actually written to an outgoing packet
    // sentRotation     = current frame's sent rotation
    // prevSentRotation = previous frame's sentRotation (for head-turn animation)
    // targetRotation   = rotation a module currently wants
    public static Rotation prevRotation;
    public static Rotation sentRotation;
    public static Rotation prevSentRotation;
    public static Rotation targetRotation;

    public static MovementCorrection correctMovement;
    private static double rotationSpeed;
    private static boolean enabled;

    public RotationManager() {
        instance.getEventManager().register(this);
    }

    public static void setRotations(float[] rotations, double speed, MovementCorrection correction) {
        targetRotations  = rotations;
        rotationSpeed    = speed;
        correctMovement  = correction;
        enabled          = true;
    }

    @EventTarget
    @EventPriority(999)
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (mc.player == null) return;

        KillAura  aura     = instance.getModuleManager().getModule(KillAura.class);
        Scaffold  scaffold = instance.getModuleManager().getModule(Scaffold.class);

        if (aura.isEnabled() && aura.getTarget() != null && aura.getRotations() != null) {
            setRotations(aura.getRotations(), aura.getRotationSpeed().getValue(),
                    aura.getMovementFixMode().is("None") ? MovementCorrection.None
                    : aura.getMovementFixMode().is("Silent") ? MovementCorrection.Silent
                    : MovementCorrection.Strict);
        } else if (scaffold.isEnabled() && scaffold.rots != null) {
            // Scaffold's rots is already moved-towards each tick — inject directly, no smoothing.
            // IMPORTANT: set ALL three arrays (incl. targetRotations) so the onMotion pre-phase
            // null-guard does not reset our rotation back to the player's real view.
            float[] r = new float[]{scaffold.rots.getYaw(), scaffold.rots.getPitch()};
            lastRotations    = currentRotations != null ? currentRotations : r;
            currentRotations = r;
            targetRotations  = r;
            correctMovement  = MovementCorrection.Silent;
            enabled = true;
            mc.gameRenderer.updateCrosshairTarget(1.0f);
            return;
        } else {
            enabled = false;
        }

        lastRotations = currentRotations;
        if (enabled && targetRotations != null) {
            if (lastRotations == null) lastRotations = new float[]{mc.player.getYaw(), mc.player.getPitch()};
            currentRotations = RotationUtil.getSmoothRotation(lastRotations, targetRotations, rotationSpeed + Math.random());
        } else {
            currentRotations = lastRotations = new float[]{mc.player.getYaw(), mc.player.getPitch()};
        }
        mc.gameRenderer.updateCrosshairTarget(1.0f);
    }

    @EventTarget
    @EventPriority(999)
    public void onLook(LookEvent e) {
        if (mc.player == null) return;
        if (enabled) { e.setRotation(currentRotations); e.setLastRotation(lastRotations); }
    }

    @EventTarget
    @EventPriority(999)
    public void onStrafe(StrafeEvent e) {
        if (mc.player == null) return;
        if (enabled && correctMovement != MovementCorrection.None)
            e.setYaw(currentRotations[0]);
    }

    @EventTarget
    @EventPriority(999)
    public void onJump(JumpEvent e) {
        if (mc.player == null) return;
        if (enabled && correctMovement != MovementCorrection.None)
            e.setYaw(currentRotations[0]);
    }

    @EventTarget
    @EventPriority(999)
    public void onMotion(MotionEvent e) {
        if (mc.player == null) return;
        if (e.isPre()) {
            if (!enabled || currentRotations == null || lastRotations == null || targetRotations == null)
                currentRotations = targetRotations = lastRotations = new float[]{mc.player.getYaw(), mc.player.getPitch()};
            if (enabled) {
                e.setYaw(currentRotations[0]);
                e.setPitch(currentRotations[1]);
                targetRotation = new Rotation(currentRotations[0], currentRotations[1]);
            }
        } else {
            // Post-phase: capture what was actually written to the packet, mirroring
            // OpenZen RotationHandler.onMotion (prevSent <- sent, sent/prev <- current sent).
            if (prevRotation == null) prevRotation = new Rotation(mc.player.getYaw(), mc.player.getPitch());
            prevSentRotation = sentRotation;
            sentRotation = new Rotation(e.getYaw(), e.getPitch());
            prevRotation = new Rotation(e.getYaw(), e.getPitch());
        }
    }

    @EventTarget
    @EventPriority(999)
    public void onMoveInput(MoveInputEvent e) {
        if (enabled && correctMovement == MovementCorrection.Silent)
            MovementUtil.fixMovement(e, currentRotations[0]);
    }

    @EventTarget
    @EventPriority(999)
    public void onRotation(RotationEvent e) {
        if (mc.player == null) return;
        if (enabled) { e.setRotation(currentRotations); e.setLastRotation(lastRotations); }
    }

    /** Reset the Rotation-state model on world join (OpenZen RotationHandler.onWorldChange). */
    @EventTarget
    public void onWorld(WorldEvent e) {
        prevRotation = null;
        sentRotation = null;
        prevSentRotation = null;
        targetRotation = null;
        cn.clientbase.Client.delayPackets.clear();
    }
}
