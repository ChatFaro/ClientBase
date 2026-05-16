package cn.clientbase.module.impl.misc;

import cn.clientbase.event.base.annotation.EventTarget;
import cn.clientbase.event.impl.UpdateEvent;
import cn.clientbase.module.Category;
import cn.clientbase.module.Module;
import cn.clientbase.module.value.impl.NumberValue;
import cn.clientbase.util.Util;

public class Timer extends Module {
    private final NumberValue speed = new NumberValue("Speed", 1, .1f, 10, .1f);

    public Timer() {
        super("Timer", Category.Client);
        setDescription("Modify game speed");
    }

    @Override
    public void onDisable() {
        Util.timer = 1f;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        setSuffix(speed.getValue().toString());

        Util.timer = speed.getValue();
    }
}
