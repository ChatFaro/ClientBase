package cn.clientbase.util.player;

import cn.clientbase.util.IMinecraft;
import lombok.experimental.UtilityClass;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

@UtilityClass
public class BlockUtil implements IMinecraft {

    /** A stack is "placeable" if it holds a normal full-cube block we can bridge on. */
    public boolean isPlaceable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        try {
            // Reject non-full blocks (slabs, stairs, torches, etc.) so we only bridge with safe blocks.
            return blockItem.getBlock().getDefaultState().isFullCube(mc.world, BlockPos.ORIGIN);
        } catch (Throwable t) {
            return true;
        }
    }

    /** True if the block at pos is a solid support we can click a face against. */
    public boolean isSolid(BlockPos pos) {
        if (mc.world == null) return false;
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return false;
        return !state.getCollisionShape(mc.world, pos).isEmpty();
    }

    public boolean isReplaceable(BlockPos pos) {
        if (mc.world == null) return false;
        return mc.world.getBlockState(pos).isReplaceable();
    }

    /** Counts every placeable block in the inventory (hotbar + main). */
    public int countBlocks() {
        if (mc.player == null) return 0;
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isPlaceable(stack)) total += stack.getCount();
        }
        return total;
    }

    /** First hotbar slot (0-8) holding a placeable block, or -1. */
    public int findBlockSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (isPlaceable(mc.player.getInventory().getStack(i))) return i;
        }
        return -1;
    }

    /** The face of `support` that points toward `air` (i.e. the face we click to fill `air`). */
    public Direction faceToward(BlockPos support, BlockPos air) {
        BlockPos diff = air.subtract(support);
        for (Direction dir : Direction.values()) {
            if (dir.getVector().equals(diff)) return dir;
        }
        return null;
    }
}
