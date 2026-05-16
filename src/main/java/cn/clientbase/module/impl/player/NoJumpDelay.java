package cn.clientbase.module.impl.player;

import cn.clientbase.event.base.annotation.EventTarget;
import cn.clientbase.event.impl.UpdateEvent;
import cn.clientbase.module.Category;
import cn.clientbase.module.Module;
import cn.injection.accessor.LivingEntityAccessor;

public class NoJumpDelay extends Module {

    public NoJumpDelay() {
        super("NoJumpDelay", Category.Player);
        setDescription("No jump delay");
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;

        ((LivingEntityAccessor)mc.player).setJumpCooldown(0);
    }
}
