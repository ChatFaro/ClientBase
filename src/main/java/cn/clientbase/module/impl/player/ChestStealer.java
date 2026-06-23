package cn.clientbase.module.impl.player;

import cn.clientbase.event.base.annotation.EventTarget;
import cn.clientbase.event.impl.TickEvent;
import cn.clientbase.module.Category;
import cn.clientbase.module.Module;
import cn.clientbase.module.impl.combat.KillAura;
import cn.clientbase.module.impl.movement.Scaffold;
import cn.clientbase.module.value.impl.BoolValue;
import cn.clientbase.module.value.impl.NumberValue;
import cn.clientbase.util.misc.TimerUtil;
import cn.clientbase.util.player.BlockUtil;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.SwordItem;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class ChestStealer extends Module {
    private final NumberValue delay = new NumberValue("Delay", 200, 0, 1000, 10);
    private final NumberValue openDelay = new NumberValue("Open Delay", 2, 0, 10, 1);
    private final BoolValue onlyBest = new BoolValue("Only Best", true);
    private final BoolValue pickTrash = new BoolValue("Pick Trash", false);
    private final BoolValue randomClick = new BoolValue("Random Click", false);
    private final BoolValue smartStealing = new BoolValue("Smart Stealing", true);

    private static final TimerUtil stealTimer = new TimerUtil();
    private static long lastClickDelayMs = 0L;

    private final Random random = new Random();
    private final List<StealTarget> stealQueue = new ArrayList<>();
    private GenericContainerScreen lastScreen = null;
    private int openTicks = 0;
    private int stealIndex = 0;
    private boolean queueBuilt = false;

    // OpenZen-style deferred click: a slot is scheduled one tick, executed the next.
    private GenericContainerScreenHandler pendingMenu = null;
    private int pendingSlot = -1;
    private boolean hasPendingClick = false;
    private int ticksSinceMenu = 0;

    public ChestStealer() {
        super("ChestStealer", Category.Player);
        setDescription("Automatically steals useful items from containers");
    }

    public static boolean isRateLimited() {
        return !stealTimer.hasTimeElapsed(Math.max(100L, lastClickDelayMs));
    }

    @Override
    public void onDisable() {
        resetQueue();
        resetState();
        lastScreen = null;
        openTicks = 0;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.interactionManager == null) return;

        // OpenZen onGameTick — execute a scheduled click exactly one tick after it was queued,
        // so we never click in the same tick we read the container (avoids slot desync).
        if (hasPendingClick && pendingMenu != null && pendingSlot >= 0) {
            ticksSinceMenu++;
            if (ticksSinceMenu >= 1) {
                executePendingClick();
                resetState();
            }
            return;
        }

        KillAura aura = getModule(KillAura.class);
        Scaffold scaffold = getModule(Scaffold.class);
        if ((aura != null && aura.isEnabled() && aura.getTarget() != null)
                || (scaffold != null && scaffold.isEnabled())) {
            return;
        }

        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            resetQueue();
            lastScreen = null;
            openTicks = 0;
            return;
        }

        if (screen != lastScreen) {
            resetQueue();
            lastScreen = screen;
            openTicks = 0;
            return;
        }

        openTicks++;
        if (openTicks < openDelay.getValue().intValue()) return;
        if (!stealTimer.hasTimeElapsed(delay.getValue().longValue())) return;

        GenericContainerScreenHandler handler = screen.getScreenHandler();
        if (isChestDone(handler)) {
            mc.player.closeHandledScreen();
            resetQueue();
            return;
        }

        if (smartStealing.getValue()) {
            stealSmart(handler);
        } else {
            stealLinear(handler);
        }
    }

    private void stealSmart(GenericContainerScreenHandler handler) {
        if (!queueBuilt) {
            buildQueue(handler);
            queueBuilt = true;
            stealIndex = 0;
        }

        while (stealIndex < stealQueue.size()) {
            StealTarget target = stealQueue.get(stealIndex++);
            if (!handler.getSlot(target.slot()).getStack().isEmpty()) {
                schedulePendingClick(handler, target.slot());
                return;
            }
        }
        resetQueue();
    }

    private void stealLinear(GenericContainerScreenHandler handler) {
        List<Integer> slots = getStealableSlots(handler);
        if (slots.isEmpty()) return;
        int slot = randomClick.getValue() ? slots.get(random.nextInt(slots.size())) : slots.get(0);
        schedulePendingClick(handler, slot);
    }

    private void buildQueue(GenericContainerScreenHandler handler) {
        stealQueue.clear();
        int chestSize = handler.getRows() * 9;
        for (int slot = 0; slot < chestSize; slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack.isEmpty() || !shouldSteal(stack)) continue;
            stealQueue.add(new StealTarget(slot, category(stack), score(stack)));
        }
        stealQueue.sort(Comparator
                .comparingInt(StealTarget::category)
                .thenComparing(Comparator.comparingDouble(StealTarget::score).reversed()));
    }

    private List<Integer> getStealableSlots(GenericContainerScreenHandler handler) {
        List<Integer> slots = new ArrayList<>();
        int chestSize = handler.getRows() * 9;
        for (int slot = 0; slot < chestSize; slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (!stack.isEmpty() && shouldSteal(stack)) slots.add(slot);
        }
        return slots;
    }

    private boolean isChestDone(GenericContainerScreenHandler handler) {
        return getStealableSlots(handler).isEmpty();
    }

    private boolean shouldSteal(ItemStack stack) {
        if (pickTrash.getValue()) return true;
        if (!isWorthStealing(stack)) return false;
        return !onlyBest.getValue() || isBetterThanCurrent(stack);
    }

    static boolean isWorthStealing(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof ArmorItem || item instanceof SwordItem || item instanceof MiningToolItem
                || item instanceof BowItem || item instanceof CrossbowItem) return true;
        if (item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE || item == Items.ENDER_PEARL) return true;
        if (item == Items.WATER_BUCKET || item == Items.LAVA_BUCKET || item == Items.COBWEB || item == Items.ARROW) return true;
        if (item == Items.SNOWBALL || item == Items.EGG || item instanceof FishingRodItem) return true;
        return item instanceof BlockItem && BlockUtil.isPlaceable(stack);
    }

    private boolean isBetterThanCurrent(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ArmorItem armor) {
            return score(stack) > bestArmorScore(armorSlot(stack)) + 0.1;
        }
        if (item instanceof SwordItem) return score(stack) > bestInventoryScore(SwordItem.class);
        if (item instanceof AxeItem) return score(stack) > bestInventoryScore(AxeItem.class);
        if (item instanceof MiningToolItem) return score(stack) > bestInventoryScore(item.getClass());
        if (item instanceof BowItem) return score(stack) > bestInventoryScore(BowItem.class);
        if (item instanceof CrossbowItem) return score(stack) > bestInventoryScore(CrossbowItem.class);
        if (item == Items.WATER_BUCKET) return countItem(Items.WATER_BUCKET) < InventoryManager.getMaxWaterBuckets();
        if (item == Items.LAVA_BUCKET) return countItem(Items.LAVA_BUCKET) < InventoryManager.getMaxLavaBuckets();
        if (item == Items.ARROW) return countItem(Items.ARROW) < InventoryManager.getMaxArrows();
        if (item instanceof BlockItem && item != Items.COBWEB) {
            return BlockUtil.countBlocks() + stack.getCount() <= InventoryManager.getMaxBlockSize();
        }
        if (item instanceof FishingRodItem) return countItem(Items.FISHING_ROD) < 1;
        return true;
    }

    private double bestInventoryScore(Class<?> type) {
        double best = 0.0;
        if (mc.player == null) return best;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && type.isInstance(stack.getItem())) {
                best = Math.max(best, score(stack));
            }
        }
        return best;
    }

    private double bestArmorScore(int armorSlot) {
        double best = 0.0;
        if (mc.player == null) return best;
        ItemStack equipped = mc.player.getInventory().armor.get(armorSlot);
        if (!equipped.isEmpty()) best = score(equipped);
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof ArmorItem && armorSlot(stack) == armorSlot) {
                best = Math.max(best, score(stack));
            }
        }
        return best;
    }

    static double score(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ArmorItem) return armorBaseScore(stack) + stack.getEnchantments().getSize() * 0.25;
        if (item instanceof SwordItem) return materialTierScore(item) + 4.0 + stack.getEnchantments().getSize() * 0.5;
        if (item instanceof AxeItem) return materialTierScore(item) + 5.0 + stack.getEnchantments().getSize() * 0.5;
        if (item instanceof MiningToolItem) return materialTierScore(item) + stack.getEnchantments().getSize() * 0.25;
        if (item instanceof BowItem || item instanceof CrossbowItem) return 1.0 + stack.getEnchantments().getSize();
        if (item == Items.ENCHANTED_GOLDEN_APPLE) return 100.0 + stack.getCount();
        if (item == Items.GOLDEN_APPLE) return 90.0 + stack.getCount();
        if (item == Items.ENDER_PEARL) return 80.0 + stack.getCount();
        if (item == Items.COBWEB) return 70.0 + stack.getCount();
        if (item instanceof BlockItem) return 60.0 + stack.getCount() * 0.05;
        return stack.getCount();
    }

    private static int armorSlot(ItemStack stack) {
        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        if (equippable == null) return -1;
        EquipmentSlot slot = equippable.slot();
        if (slot == EquipmentSlot.FEET) return 0;
        if (slot == EquipmentSlot.LEGS) return 1;
        if (slot == EquipmentSlot.CHEST) return 2;
        if (slot == EquipmentSlot.HEAD) return 3;
        return -1;
    }

    private static double armorBaseScore(ItemStack stack) {
        Item item = stack.getItem();
        int slot = armorSlot(stack);
        double slotWeight = slot == 2 ? 6.0 : slot == 1 ? 5.0 : slot == 3 ? 3.0 : 2.0;
        return slotWeight + materialTierScore(item);
    }

    private static double materialTierScore(Item item) {
        String name = item.toString();
        if (name.contains("netherite")) return 6.0;
        if (name.contains("diamond")) return 5.0;
        if (name.contains("iron")) return 4.0;
        if (name.contains("chainmail")) return 3.5;
        if (name.contains("stone")) return 3.0;
        if (name.contains("golden") || name.contains("gold")) return 2.5;
        if (name.contains("leather") || name.contains("wooden") || name.contains("wood")) return 2.0;
        return 1.0;
    }

    private int category(ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.ENCHANTED_GOLDEN_APPLE || item == Items.GOLDEN_APPLE) return 0;
        if (item instanceof ArmorItem) return 1;
        if (item instanceof SwordItem || item instanceof AxeItem) return 2;
        if (item instanceof BowItem || item instanceof CrossbowItem) return 3;
        if (item instanceof MiningToolItem) return 4;
        if (item == Items.ENDER_PEARL || item == Items.COBWEB || item == Items.WATER_BUCKET || item == Items.LAVA_BUCKET) return 5;
        if (item instanceof BlockItem) return 6;
        return 7;
    }

    private int countItem(Item item) {
        if (mc.player == null) return 0;
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == item) total += stack.getCount();
        }
        return total;
    }

    /** OpenZen schedulePendingClick — queue a click; only one may be pending at a time. */
    private void schedulePendingClick(GenericContainerScreenHandler handler, int slot) {
        if (!hasPendingClick) {
            pendingMenu = handler;
            pendingSlot = slot;
            hasPendingClick = true;
            ticksSinceMenu = 0;
        }
    }

    /** OpenZen executePendingClick — perform the deferred shift-click. */
    private void executePendingClick() {
        if (pendingMenu != null && pendingSlot >= 0) {
            lastClickDelayMs = delay.getValue().longValue();
            mc.interactionManager.clickSlot(pendingMenu.syncId, pendingSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
            stealTimer.reset();
        }
    }

    private void resetState() {
        hasPendingClick = false;
        pendingSlot = -1;
        pendingMenu = null;
        ticksSinceMenu = 0;
    }

    private void resetQueue() {
        stealQueue.clear();
        queueBuilt = false;
        stealIndex = 0;
    }

    private record StealTarget(int slot, int category, double score) {
    }
}
