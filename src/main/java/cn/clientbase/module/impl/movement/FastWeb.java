package cn.clientbase.module.impl.movement;

import cn.clientbase.event.base.annotation.EventTarget;
import cn.clientbase.event.impl.CobwebEvent;
import cn.clientbase.event.impl.UpdateEvent;
import cn.clientbase.module.Category;
import cn.clientbase.module.Module;
import cn.clientbase.module.value.impl.ModeValue;
import cn.clientbase.module.value.impl.NumberValue;
import cn.clientbase.util.player.MovementUtil;

public class FastWeb extends Module {
    public final ModeValue mode = new ModeValue("Mode", "Vanilla", "Vanilla", "Motion");
    public final NumberValue motion = new NumberValue("Mode", 0.6f,0.1f, 1, 0.1f, () -> mode.is("Motion"));

    public FastWeb() {
        super("FastWeb", Category.Movement);
        setDescription("Move quickly through the web");
    }

    @EventTarget
    public void onCobweb(CobwebEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (mode.is("Vanilla")) {
            event.setCancelled(true);
        } else {
            MovementUtil.strafe(mode.is("GrimAC") ? 0.6f : motion.getValue());

            if (mc.options.jumpKey.isPressed()) {
                mc.player.setVelocity(mc.player.getVelocity().x, 1, mc.player.getVelocity().z);
            } else if (mc.options.sneakKey.isPressed()) {
                mc.player.setVelocity(mc.player.getVelocity().x, -1, mc.player.getVelocity().z);
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        setSuffix(mode.getValue());
    }
}