package cn.clientbase.module.impl.movement;

import cn.clientbase.event.base.annotation.EventTarget;
import cn.clientbase.event.impl.MotionEvent;
import cn.clientbase.module.Category;
import cn.clientbase.module.Module;

public class Sprint extends Module {

    public Sprint() {
        super("Sprint", Category.Movement);
        setDescription("Forced sprint");
        setEnabled(true);
    }

    @Override
    public void onDisable() {
        if (mc.player == null) return;

        mc.options.sprintKey.setPressed(false);
        mc.player.setSprinting(false);
    }

    @EventTarget
    public void onMotion(MotionEvent e) {
        if (mc.player == null) return;

        if (mc.player.forwardSpeed > 0 && !mc.player.horizontalCollision) {
            mc.options.sprintKey.setPressed(true);
        }
    }
}