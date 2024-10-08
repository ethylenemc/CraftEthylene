package org.bukkit.craftbukkit.inventory;

import net.ethylenemc.EthyleneStatic;
import net.ethylenemc.interfaces.world.item.crafting.EthyleneRecipeManager;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.inventory.ComplexRecipe;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.ItemStack;

public class CraftComplexRecipe extends CraftingRecipe implements CraftRecipe, ComplexRecipe {

    private final net.minecraft.world.item.crafting.CustomRecipe recipe;

    public CraftComplexRecipe(NamespacedKey key, ItemStack result, net.minecraft.world.item.crafting.CustomRecipe recipe) {
        super(key, result);
        this.recipe = recipe;
    }

    @Override
    public void addToCraftingManager() {
        ((EthyleneRecipeManager) EthyleneStatic.getServer().getRecipeManager()).addRecipe(new net.minecraft.world.item.crafting.RecipeHolder<>(CraftNamespacedKey.toMinecraft(this.getKey()), recipe));
    }
}
