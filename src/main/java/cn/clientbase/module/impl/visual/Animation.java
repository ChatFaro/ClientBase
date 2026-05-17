package cn.clientbase.module.impl.visual;

import cn.clientbase.event.base.annotation.EventTarget;
import cn.clientbase.event.impl.UpdateEvent;
import cn.clientbase.module.Category;
import cn.clientbase.module.Module;
import cn.clientbase.module.value.impl.BoolValue;
import cn.clientbase.module.value.impl.ModeValue;
import cn.clientbase.module.value.impl.NumberValue;

public class Animation extends Module {
    public final NumberValue swingSpeed = new NumberValue("Swing Speed", 0, -4, 20, 1);
    public final ModeValue swingMode = new ModeValue("Swing Mode", "Vanilla", "Vanilla", "Smooth");
    public final ModeValue blockMode = new ModeValue("Block Mode", "1.7", "1.7", "SideDown");
    public final BoolValue equipProgress = new BoolValue("Equip Progress",true);

    public Animation() {
        super("Animation", Category.Visual);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        setSuffix(blockMode.getValue());
    }
}
