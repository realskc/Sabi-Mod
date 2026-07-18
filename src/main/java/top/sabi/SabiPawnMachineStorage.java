package top.sabi;

import com.mojang.serialization.Codec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.util.datafix.DataFixTypes;

public class SabiPawnMachineStorage extends SavedData {
    private static final int MAX_SERIALIZED_STACK_SIZE = 99;
    private static final Codec<SabiPawnMachineStorage> CODEC = ItemStack.CODEC.listOf()
            .fieldOf("stored_items")
            .codec()
            .xmap(SabiPawnMachineStorage::new, SabiPawnMachineStorage::serializedStacks);
    private static final SavedDataType<SabiPawnMachineStorage> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Sabi.MOD_ID, "sabi_machine_storage"),
            SabiPawnMachineStorage::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    private final Map<Item, Deque<ItemStack>> storedItemsByItem = new HashMap<>();

    public SabiPawnMachineStorage() {
    }

    private SabiPawnMachineStorage(List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()) != null) {
                this.store(stack, false);
            }
        }
    }

    public static SabiPawnMachineStorage get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public long storedCount(Item item) {
        long count = 0;
        Deque<ItemStack> stack = this.storedItemsByItem.get(item);
        if (stack != null) {
            for (ItemStack itemStack : stack) {
                count = saturatingAdd(count, itemStack.getCount());
            }
        }
        return count;
    }

    public void store(ItemStack stack) {
        this.store(stack, true);
    }

    private void store(ItemStack stack, boolean markDirty) {
        if (stack.isEmpty()) {
            return;
        }

        Deque<ItemStack> itemStacks = this.storedItemsByItem.computeIfAbsent(stack.getItem(), ignored -> new ArrayDeque<>());
        int remaining = stack.getCount();
        ItemStack top = itemStacks.peek();
        if (top != null && ItemStack.isSameItemSameComponents(top, stack) && top.getCount() < MAX_SERIALIZED_STACK_SIZE) {
            int merged = Math.min(remaining, MAX_SERIALIZED_STACK_SIZE - top.getCount());
            top.grow(merged);
            remaining -= merged;
        }
        while (remaining > 0) {
            int count = Math.min(remaining, MAX_SERIALIZED_STACK_SIZE);
            itemStacks.push(stack.copyWithCount(count));
            remaining -= count;
        }
        if (markDirty) {
            this.setDirty();
        }
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
        this.setDirty();
        return result;
    }

    private List<ItemStack> serializedStacks() {
        List<ItemStack> result = new ArrayList<>();
        for (Deque<ItemStack> stack : this.storedItemsByItem.values()) {
            for (ItemStack itemStack : stack) {
                addSerializedStack(result, itemStack);
            }
        }
        return result;
    }

    private static void addSerializedStack(List<ItemStack> result, ItemStack stack) {
        int remaining = stack.getCount();
        while (remaining > 0) {
            int count = Math.min(MAX_SERIALIZED_STACK_SIZE, remaining);
            result.add(stack.copyWithCount(count));
            remaining -= count;
        }
    }

    private static long saturatingAdd(long left, int right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
