package cn.clientbase.event.impl;

import cn.clientbase.event.base.Event;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

/**
 * Fired from {@code MixinHeldItemRenderer} just before the first-person item is
 * rendered, allowing modules to spoof the displayed {@link ItemStack}.
 * Faithful port of OpenZen's {@code UpdateHeldItemEvent}.
 */
@Getter
@Setter
public class UpdateHeldItemEvent extends Event {
    private final Hand hand;
    private ItemStack itemStack;

    public UpdateHeldItemEvent(Hand hand, ItemStack itemStack) {
        this.hand = hand;
        this.itemStack = itemStack;
    }
}
