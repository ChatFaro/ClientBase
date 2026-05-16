package cn.clientbase.module.impl.movement;

import cn.clientbase.event.base.annotation.EventTarget;
import cn.clientbase.event.impl.MotionEvent;
import cn.clientbase.module.Category;
import cn.clientbase.module.Module;
import cn.clientbase.util.player.MovementUtil;

public class Strafe extends Module {

    public Strafe() {
        super("Strafe", Category.Movement);
        setDescription("Aerial agility");
    }


    @EventTarget
    public void onMotion(MotionEvent e) {
        MovementUtil.strafe();
    }
}