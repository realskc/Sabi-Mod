package top.sabi;

import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class CurrencyStackUpgradeRecipe extends CustomRecipe {
    private final Supplier<? extends Item> input;
    private final Supplier<? extends Item> output;

    private CurrencyStackUpgradeRecipe(Supplier<? extends Item> input, Supplier<? extends Item> output) {
        this.input = input;
        this.output = output;
    }

    public static RecipeSerializer<CurrencyStackUpgradeRecipe> serializer(Supplier<? extends Item> input, Supplier<? extends Item> output) {
        CurrencyStackUpgradeRecipe recipe = new CurrencyStackUpgradeRecipe(input, output);
        return new RecipeSerializer<>(MapCodec.unit(() -> recipe), StreamCodec.unit(recipe));
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() != 1) {
            return false;
        }

        Item requiredItem = this.input.get();
        for (ItemStack stack : input.items()) {
            if (!stack.isEmpty()) {
                return stack.is(requiredItem) && stack.getCount() >= Sabi.CURRENCY_EXCHANGE_RATE;
            }
        }
        return false;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return new ItemStack(this.output.get());
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return NonNullList.withSize(input.size(), ItemStack.EMPTY);
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        Item inputItem = this.input.get();
        if (inputItem == Sabi.SMALL_SABI.get()) {
            return Sabi.SMALL_TO_MEDIUM_SABI_RECIPE.get();
        }
        if (inputItem == Sabi.MEDIUM_SABI.get()) {
            return Sabi.MEDIUM_TO_BIG_SABI_RECIPE.get();
        }
        return Sabi.BIG_TO_GIANT_SABI_RECIPE.get();
    }
}
