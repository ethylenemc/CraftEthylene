package org.bukkit.craftbukkit.inventory;

import net.ethylenemc.EthyleneStatic;
import net.ethylenemc.interfaces.world.item.crafting.EthyleneRecipeManager;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmithingTransformRecipe;

public class CraftSmithingTransformRecipe extends SmithingTransformRecipe implements CraftRecipe {
    public CraftSmithingTransformRecipe(NamespacedKey key, ItemStack result, RecipeChoice template, RecipeChoice base, RecipeChoice addition) {
        super(key, result, template, base, addition);
    }

    public static CraftSmithingTransformRecipe fromBukkitRecipe(SmithingTransformRecipe recipe) {
        if (recipe instanceof CraftSmithingTransformRecipe) {
            return (CraftSmithingTransformRecipe) recipe;
        }
        CraftSmithingTransformRecipe ret = new CraftSmithingTransformRecipe(recipe.getKey(), recipe.getResult(), recipe.getTemplate(), recipe.getBase(), recipe.getAddition());
        return ret;
    }

    @Override
    public void addToCraftingManager() {
        ItemStack result = this.getResult();

        ((EthyleneRecipeManager) EthyleneStatic.getServer().getRecipeManager()).addRecipe(new net.minecraft.world.item.crafting.RecipeHolder<>(CraftNamespacedKey.toMinecraft(this.getKey()), new net.minecraft.world.item.crafting.SmithingTransformRecipe(toNMS(this.getTemplate(), false), toNMS(this.getBase(), false), toNMS(this.getAddition(), false), CraftItemStack.asNMSCopy(result))));
    }
}
