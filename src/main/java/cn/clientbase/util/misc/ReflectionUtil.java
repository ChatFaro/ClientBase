package cn.clientbase.util.misc;

import cn.clientbase.util.IMinecraft;
import cn.injection.accessor.LivingEntityAccessor;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ReflectionUtil implements IMinecraft {
    /** Sets the jump cooldown (noJumpDelay) on the local player. */
    public void setJumpDelay(int delay) {
        if (mc.player == null) return;
        ((LivingEntityAccessor) mc.player).setJumpCooldown(delay);
    }
}
