package org.bukkit.craftbukkit.inventory;

import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.StonecuttingRecipe;

public class CraftStonecuttingRecipe extends StonecuttingRecipe implements CraftRecipe {
    public CraftStonecuttingRecipe(NamespacedKey key, ItemStack result, RecipeChoice source) {
        super(key, result, source);
    }

    public static CraftStonecuttingRecipe fromBukkitRecipe(StonecuttingRecipe recipe) {
        if (recipe instanceof CraftStonecuttingRecipe) {
            return (CraftStonecuttingRecipe) recipe;
        }
        CraftStonecuttingRecipe ret = new CraftStonecuttingRecipe(recipe.getKey(), recipe.getResult(), recipe.getInputChoice());
        ret.setGroup(recipe.getGroup());
        return ret;
    }

    @Override
    public void addToCraftingManager() {
        ItemStack result = this.getResult();

        net.minecraft.server.MinecraftServer.getServer().getRecipeManager().addRecipe(new net.minecraft.world.item.crafting.RecipeHolder<>(CraftNamespacedKey.toMinecraft(this.getKey()), new net.minecraft.world.item.crafting.StonecutterRecipe(this.getGroup(), toNMS(this.getInputChoice(), true), CraftItemStack.asNMSCopy(result))));
    }
}
