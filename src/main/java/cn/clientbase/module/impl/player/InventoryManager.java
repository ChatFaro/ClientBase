package cn.clientbase.module.impl.player;

import cn.clientbase.event.base.annotation.EventTarget;
import cn.clientbase.event.impl.TickEvent;
import cn.clientbase.module.Category;
import cn.clientbase.module.Module;
import cn.clientbase.module.impl.movement.Scaffold;
import cn.clientbase.module.value.impl.BoolValue;
import cn.clientbase.module.value.impl.NumberValue;
import cn.clientbase.util.misc.TimerUtil;
import cn.clientbase.util.player.BlockUtil;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
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
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InventoryManager extends Module {
    private final NumberValue actionDelay = new NumberValue("Delay", 200, 0, 500, 10);
    private final NumberValue dropDelay = new NumberValue("Drop Delay", 200, 0, 500, 10);
    private final BoolValue autoArmor = new BoolValue("Auto Armor", true);
    private final BoolValue throwItems = new BoolValue("Throw Items", true);
    private final BoolValue inventoryOnly = new BoolValue("Inventory Only", true);
    private final BoolValue fastThrow = new BoolValue("Fast Throw", false);
    private final NumberValue maxBlocks = new NumberValue("Max Block Size", 256, 64, 512, 64);
    private final NumberValue maxFood = new NumberValue("Max Food Size", 128, 32, 256, 32);
    private final NumberValue maxProjectiles = new NumberValue("Max Eggs & Snowballs Size", 64, 16, 256, 16);
    private final NumberValue swordSlot = new NumberValue("Sword Slot", 1, 0, 9, 1);
    private final NumberValue blockSlot = new NumberValue("Block Slot", 2, 0, 9, 1);
    private final NumberValue pickaxeSlot = new NumberValue("Pickaxe Slot", 3, 0, 9, 1);
    private final NumberValue axeSlot = new NumberValue("Axe Slot", 4, 0, 9, 1);
    private final NumberValue bowSlot = new NumberValue("Bow Slot", 5, 0, 9, 1);
    private final NumberValue pearlSlot = new NumberValue("Ender Pearl Slot", 6, 0, 9, 1);
    private final NumberValue waterSlot = new NumberValue("Water Bucket Slot", 7, 0, 9, 1);
    private final NumberValue gappleSlot = new NumberValue("Golden Apple Slot", 8, 0, 9, 1);
    private final NumberValue projectileSlot = new NumberValue("Eggs & Snowballs Slot", 9, 0, 9, 1);

    private static InventoryManager instanceRef;
    private final TimerUtil actionTimer = new TimerUtil();
    private final TimerUtil throwTimer = new TimerUtil();
    private int swapCooldownTicks = 0;

    public InventoryManager() {
        super("InventoryManager", Category.Player);
        setDescription("Automatically equips armor, sorts the hotbar and drops junk");
        instanceRef = this;
    }

    @Override
    public void onEnable() {
        swapCooldownTicks = 0;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.interactionManager == null) return;
        Scaffold scaffold = getModule(Scaffold.class);
        if (scaffold != null && scaffold.isEnabled()) return;
        if (ChestStealer.isRateLimited()) return;
        if (inventoryOnly.getValue() && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (!validateSlotConfig()) return;
        if (swapCooldownTicks > 0) {
            swapCooldownTicks--;
            return;
        }

        if (performInventoryAction()) {
            actionTimer.reset();
        }
    }

    private boolean performInventoryAction() {
        if (autoArmor.getValue() && actionTimer.hasTimeElapsed(actionDelay.getValue().longValue())) {
            if (equipBestArmor()) return true;
        }

        if (actionTimer.hasTimeElapsed(actionDelay.getValue().longValue())) {
            if (moveBestToHotbar(swordSlot, this::isWeapon)) return true;
            if (moveBestToHotbar(blockSlot, stack -> stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack))) return true;
            if (moveBestToHotbar(pickaxeSlot, stack -> stack.getItem() instanceof PickaxeItem)) return true;
            if (moveBestToHotbar(axeSlot, stack -> stack.getItem() instanceof AxeItem)) return true;
            if (moveBestToHotbar(bowSlot, stack -> stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem)) return true;
            if (moveItemToHotbar(pearlSlot, Items.ENDER_PEARL)) return true;
            if (moveItemToHotbar(waterSlot, Items.WATER_BUCKET)) return true;
            if (moveItemToHotbar(gappleSlot, Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE)) return true;
            if (moveBestToHotbar(projectileSlot, stack -> stack.getItem() == Items.EGG || stack.getItem() == Items.SNOWBALL)) return true;
        }

        if (throwItems.getValue() && (fastThrow.getValue() || throwTimer.hasTimeElapsed(dropDelay.getValue().longValue()))) {
            ItemStack drop = findDropStack();
            if (!drop.isEmpty() && throwItem(drop)) {
                throwTimer.reset();
                return true;
            }
        }
        return false;
    }

    private boolean equipBestArmor() {
        for (int armorSlot = 0; armorSlot < 4; armorSlot++) {
            ItemStack equipped = mc.player.getInventory().armor.get(armorSlot);
            double bestScore = armorScore(mc.player.getInventory().armor.get(armorSlot));
            int bestSlot = -1;
            for (int i = 0; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.getItem() instanceof ArmorItem
                        && armorSlot(stack) == armorSlot
                        && armorScore(stack) > bestScore + 0.01) {
                    bestScore = armorScore(stack);
                    bestSlot = i;
                }
            }

            if (bestSlot != -1 && !equipped.isEmpty()) {
                click(armorSlotId(armorSlot), 1, SlotActionType.THROW);
                swapCooldownTicks = 4;
                return true;
            }

            if (bestSlot != -1) {
                click(slotId(bestSlot), 0, SlotActionType.QUICK_MOVE);
                swapCooldownTicks = 4;
                return true;
            }
        }
        return false;
    }

    private boolean moveBestToHotbar(NumberValue slotSetting, StackPredicate predicate) {
        int target = slotSetting.getValue().intValue() - 1;
        if (target < 0) return false;
        ItemStack current = mc.player.getInventory().getStack(target);
        ItemStack best = current;
        double bestScore = predicate.test(current) ? itemScore(current) : -1.0;
        int bestSlot = -1;

        for (int i = 0; i < 36; i++) {
            if (i == target || isProtectedHotbarSlot(i, target)) continue;
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || !predicate.test(stack)) continue;
            double score = itemScore(stack);
            if (score > bestScore) {
                best = stack;
                bestScore = score;
                bestSlot = i;
            }
        }

        if (!best.isEmpty() && bestSlot != -1 && bestSlot != target) {
            click(slotId(bestSlot), target, SlotActionType.SWAP);
            return true;
        }
        return false;
    }

    private boolean moveItemToHotbar(NumberValue slotSetting, Item... items) {
        int target = slotSetting.getValue().intValue() - 1;
        if (target < 0) return false;
        ItemStack current = mc.player.getInventory().getStack(target);
        int bestSlot = -1;
        int bestCount = matches(current, items) ? current.getCount() : -1;
        for (int i = 0; i < 36; i++) {
            if (i == target || isProtectedHotbarSlot(i, target)) continue;
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (matches(stack, items) && stack.getCount() > bestCount) {
                bestSlot = i;
                bestCount = stack.getCount();
            }
        }
        if (bestSlot != -1 && bestSlot != target) {
            click(slotId(bestSlot), target, SlotActionType.SWAP);
            return true;
        }
        return false;
    }

    private ItemStack findDropStack() {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < 36; i++) order.add(i);
        Collections.shuffle(order);

        for (int i : order) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && !isUsefulItem(stack)) return stack;
        }
        if (BlockUtil.countBlocks() > maxBlocks.getValue().intValue()) return worst(stack -> stack.getItem() instanceof BlockItem);
        if (countFood() > maxFood.getValue().intValue()) return worst(this::isFood);
        if (countItem(Items.EGG) + countItem(Items.SNOWBALL) > maxProjectiles.getValue().intValue()) {
            return worst(stack -> stack.getItem() == Items.EGG || stack.getItem() == Items.SNOWBALL);
        }
        if (countItem(Items.FISHING_ROD) > 1) return worst(stack -> stack.getItem() instanceof FishingRodItem);
        return ItemStack.EMPTY;
    }

    private ItemStack worst(StackPredicate predicate) {
        ItemStack worst = ItemStack.EMPTY;
        double worstScore = Double.MAX_VALUE;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && predicate.test(stack) && itemScore(stack) < worstScore) {
                worst = stack;
                worstScore = itemScore(stack);
            }
        }
        return worst;
    }

    private boolean throwItem(ItemStack stack) {
        int slot = getSlot(stack);
        if (slot == -1) return false;
        click(slotId(slot), 1, SlotActionType.THROW);
        return true;
    }

    public boolean isUsefulItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item == Items.COBWEB || item == Items.ENDER_PEARL || item == Items.ENCHANTED_GOLDEN_APPLE || item == Items.GOLDEN_APPLE) return true;
        if (item == Items.WATER_BUCKET) return countItem(Items.WATER_BUCKET) <= getMaxWaterBuckets();
        if (item == Items.LAVA_BUCKET) return countItem(Items.LAVA_BUCKET) <= getMaxLavaBuckets();
        if (item == Items.ARROW) return countItem(Items.ARROW) <= getMaxArrows();
        if (item instanceof BlockItem) return BlockUtil.isPlaceable(stack) && BlockUtil.countBlocks() <= getMaxBlockSize();
        if (item instanceof FishingRodItem) return countItem(Items.FISHING_ROD) <= 1;
        if (item == Items.EGG || item == Items.SNOWBALL) return countItem(Items.EGG) + countItem(Items.SNOWBALL) <= getMaxEggsSnowballsSize();
        if (item instanceof ArmorItem) return armorScore(stack) >= bestArmorScore(armorSlot(stack));
        if (isWeapon(stack)) return itemScore(stack) >= bestScore(this::isWeapon);
        if (item instanceof PickaxeItem) return itemScore(stack) >= bestScore(s -> s.getItem() instanceof PickaxeItem);
        if (item instanceof AxeItem) return itemScore(stack) >= bestScore(s -> s.getItem() instanceof AxeItem);
        if (item instanceof ShovelItem) return itemScore(stack) >= bestScore(s -> s.getItem() instanceof ShovelItem);
        if (item instanceof BowItem || item instanceof CrossbowItem) {
            return itemScore(stack) >= bestScore(s -> s.getItem() instanceof BowItem || s.getItem() instanceof CrossbowItem);
        }
        return isFood(stack);
    }

    private boolean isWeapon(ItemStack stack) {
        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem;
    }

    private double bestArmorScore(int armorSlot) {
        double best = armorScore(mc.player.getInventory().armor.get(armorSlot));
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof ArmorItem && armorSlot(stack) == armorSlot) {
                best = Math.max(best, armorScore(stack));
            }
        }
        return best;
    }

    private double bestScore(StackPredicate predicate) {
        double best = 0.0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && predicate.test(stack)) best = Math.max(best, itemScore(stack));
        }
        return best;
    }

    private double armorScore(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorItem armor)) return 0.0;
        return armorBaseScore(stack) + stack.getEnchantments().getSize() * 0.25;
    }

    private double itemScore(ItemStack stack) {
        return ChestStealer.score(stack);
    }

    private boolean matches(ItemStack stack, Item... items) {
        if (stack.isEmpty()) return false;
        for (Item item : items) {
            if (stack.getItem() == item) return true;
        }
        return false;
    }

    private int countFood() {
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isFood(stack)) total += stack.getCount();
        }
        return total;
    }

    private int countItem(Item item) {
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == item) total += stack.getCount();
        }
        return total;
    }

    private int getSlot(ItemStack target) {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i) == target) return i;
        }
        return -1;
    }

    private int slotId(int inventorySlot) {
        return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
    }

    private int armorSlotId(int armorSlot) {
        return 8 - armorSlot;
    }

    private void click(int slot, int button, SlotActionType action) {
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, button, action, mc.player);
        if (action == SlotActionType.SWAP || action == SlotActionType.QUICK_MOVE || action == SlotActionType.THROW) {
            swapCooldownTicks = 2;
        }
    }

    private boolean validateSlotConfig() {
        Set<Integer> usedSlots = new HashSet<>();
        NumberValue[] slots = {
                swordSlot, blockSlot, pickaxeSlot, axeSlot, bowSlot,
                pearlSlot, waterSlot, gappleSlot, projectileSlot
        };
        for (NumberValue slot : slots) {
            int target = slot.getValue().intValue() - 1;
            if (target < 0) continue;
            if (!usedSlots.add(target)) return false;
        }
        return true;
    }

    private boolean isProtectedHotbarSlot(int slot, int target) {
        if (slot < 0 || slot >= 9 || slot == target) return false;
        ItemStack stack = mc.player.getInventory().getStack(slot);
        return isAssignedToConfiguredSlot(slot, stack);
    }

    private boolean isAssignedToConfiguredSlot(int slot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (configuredSlot(swordSlot) == slot && isWeapon(stack)) return true;
        if (configuredSlot(blockSlot) == slot && stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack)) return true;
        if (configuredSlot(pickaxeSlot) == slot && stack.getItem() instanceof PickaxeItem) return true;
        if (configuredSlot(axeSlot) == slot && stack.getItem() instanceof AxeItem) return true;
        if (configuredSlot(bowSlot) == slot && (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem)) return true;
        if (configuredSlot(pearlSlot) == slot && stack.getItem() == Items.ENDER_PEARL) return true;
        if (configuredSlot(waterSlot) == slot && stack.getItem() == Items.WATER_BUCKET) return true;
        if (configuredSlot(gappleSlot) == slot && (stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE || stack.getItem() == Items.GOLDEN_APPLE)) return true;
        return configuredSlot(projectileSlot) == slot && (stack.getItem() == Items.EGG || stack.getItem() == Items.SNOWBALL);
    }

    private int configuredSlot(NumberValue setting) {
        return setting.getValue().intValue() - 1;
    }

    private boolean isFood(ItemStack stack) {
        return stack.contains(DataComponentTypes.FOOD);
    }

    private int armorSlot(ItemStack stack) {
        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        if (equippable == null) return -1;
        EquipmentSlot slot = equippable.slot();
        if (slot == EquipmentSlot.FEET) return 0;
        if (slot == EquipmentSlot.LEGS) return 1;
        if (slot == EquipmentSlot.CHEST) return 2;
        if (slot == EquipmentSlot.HEAD) return 3;
        return -1;
    }

    private double armorBaseScore(ItemStack stack) {
        int slot = armorSlot(stack);
        double slotWeight = slot == 2 ? 6.0 : slot == 1 ? 5.0 : slot == 3 ? 3.0 : 2.0;
        return slotWeight + materialTierScore(stack.getItem());
    }

    private double materialTierScore(Item item) {
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

    public static int getMaxBlockSize() {
        return instanceRef != null ? instanceRef.maxBlocks.getValue().intValue() : 256;
    }

    public static int getMaxEggsSnowballsSize() {
        return instanceRef != null ? instanceRef.maxProjectiles.getValue().intValue() : 64;
    }

    public static int getMaxArrows() {
        return 256;
    }

    public static int getMaxWaterBuckets() {
        return 1;
    }

    public static int getMaxLavaBuckets() {
        return 1;
    }

    private interface StackPredicate {
        boolean test(ItemStack stack);
    }
}
