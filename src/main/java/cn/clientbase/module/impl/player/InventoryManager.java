package cn.clientbase.module.impl.player;

import cn.clientbase.event.base.annotation.EventTarget;
import cn.clientbase.event.impl.TickEvent;
import cn.clientbase.module.Category;
import cn.clientbase.module.Module;
import cn.clientbase.module.value.impl.NumberValue;
import cn.clientbase.util.misc.TimerUtil;
import net.minecraft.item.*;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class InventoryManager extends Module {
    private final NumberValue delay = new NumberValue("Delay", 100, 0, 500, 10);
    private final TimerUtil timer = new TimerUtil();

    public InventoryManager() {
        super("InventoryManager", Category.Player);
        setDescription("Automatically manages inventory by dropping junk");
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.currentScreen != null) return;
        if (!timer.hasTimeElapsed(delay.getValue().longValue())) return;

        PlayerScreenHandler handler = mc.player.playerScreenHandler;

        // slots 9-35 = main inventory, 36-44 = hotbar (in player screen handler)
        for (int i = 9; i < 45; i++) {
            var stack = handler.getSlot(i).getStack();
            if (!stack.isEmpty() && isJunk(stack.getItem())) {
                mc.interactionManager.clickSlot(handler.syncId, i, 1, SlotActionType.THROW, mc.player);
                timer.reset();
                return;
            }
        }
    }

    private boolean isJunk(Item item) {
        return item instanceof ArrowItem
            || item instanceof BoneMealItem
            || item == Items.ROTTEN_FLESH
            || item == Items.SPIDER_EYE
            || item == Items.STRING
            || item == Items.GUNPOWDER
            || item == Items.GRAVEL
            || item == Items.SAND
            || item == Items.DIRT;
    }
}
