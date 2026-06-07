package cn.clientbase.module.impl.player;

import cn.clientbase.event.base.annotation.EventTarget;
import cn.clientbase.event.impl.TickEvent;
import cn.clientbase.module.Category;
import cn.clientbase.module.Module;
import cn.clientbase.module.value.impl.NumberValue;
import cn.clientbase.util.misc.TimerUtil;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class ChestStealer extends Module {
    private final NumberValue delay = new NumberValue("Delay", 50, 0, 500, 10);
    private final TimerUtil timer = new TimerUtil();

    public ChestStealer() {
        super("ChestStealer", Category.Player);
        setDescription("Automatically steals items from chests");
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || !(mc.currentScreen instanceof GenericContainerScreen screen)) return;
        if (!timer.hasTimeElapsed(delay.getValue().longValue())) return;

        GenericContainerScreenHandler handler = screen.getScreenHandler();
        int chestSize = handler.getRows() * 9;

        for (int i = 0; i < chestSize; i++) {
            if (!handler.getSlot(i).getStack().isEmpty()) {
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                timer.reset();
                return;
            }
        }
    }
}
