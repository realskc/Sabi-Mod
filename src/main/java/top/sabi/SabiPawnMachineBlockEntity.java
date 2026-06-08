package top.sabi;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class SabiPawnMachineBlockEntity extends BlockEntity {
    private final Map<Item, Deque<ItemStack>> storedItemsByItem = new HashMap<>();

    public SabiPawnMachineBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(Sabi.PAWN_MACHINE_BLOCK_ENTITY.get(), worldPosition, blockState);
    }

    public int storedCount(Item item) {
        int count = 0;
        Deque<ItemStack> stack = this.storedItemsByItem.get(item);
        if (stack != null) {
            for (ItemStack itemStack : stack) {
                count += itemStack.getCount();
            }
        }
        return count;
    }

    public void store(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        Deque<ItemStack> itemStack = this.storedItemsByItem.computeIfAbsent(stack.getItem(), ignored -> new ArrayDeque<>());
        ItemStack top = itemStack.peek();
        if (top != null && ItemStack.isSameItemSameComponents(top, stack)) {
            top.grow(stack.getCount());
        } else {
            itemStack.push(stack.copy());
        }
        this.setChanged();
    }

    public List<ItemStack> take(Item item, int amount) {
        int remaining = Math.max(0, amount);
        if (remaining == 0 || this.storedCount(item) < remaining) {
            return List.of();
        }

        Deque<ItemStack> stack = this.storedItemsByItem.get(item);
        if (stack == null) {
            return List.of();
        }

        List<ItemStack> result = new ArrayList<>();
        while (remaining > 0 && !stack.isEmpty()) {
            ItemStack top = stack.peek();
            int toTake = Math.min(remaining, top.getCount());
            result.add(top.copyWithCount(toTake));
            top.shrink(toTake);
            remaining -= toTake;
            if (top.isEmpty()) {
                stack.pop();
            }
        }

        if (stack.isEmpty()) {
            this.storedItemsByItem.remove(item);
        }
        this.setChanged();
        return result;
    }

    public void dropStoredItems(Level level, BlockPos pos) {
        for (Deque<ItemStack> stack : this.storedItemsByItem.values()) {
            for (ItemStack itemStack : stack) {
                dropStack(level, pos, itemStack);
            }
        }
        this.storedItemsByItem.clear();
        this.setChanged();
    }

    private static void dropStack(Level level, BlockPos pos, ItemStack stack) {
        int maxStackSize = stack.getMaxStackSize();
        int remaining = stack.getCount();
        while (remaining > 0) {
            int count = Math.min(maxStackSize, remaining);
            Block.popResource(level, pos, stack.copyWithCount(count));
            remaining -= count;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ValueOutput.ValueOutputList list = output.childrenList("stored_items");
        for (Deque<ItemStack> stack : this.storedItemsByItem.values()) {
            for (ItemStack itemStack : stack) {
                ValueOutput child = list.addChild();
                child.store("stack", ItemStack.CODEC, itemStack);
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.storedItemsByItem.clear();
        for (ValueInput child : input.childrenListOrEmpty("stored_items")) {
            ItemStack stack = child.read("stack", ItemStack.CODEC).orElse(ItemStack.EMPTY);
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()) != null) {
                this.store(stack);
            }
        }
    }

    public static Identifier itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }
}
