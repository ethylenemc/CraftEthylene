package org.bukkit.craftbukkit.inventory.view;

import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
import org.bukkit.craftbukkit.potion.CraftPotionEffectType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.BeaconInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.view.BeaconView;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

public class CraftBeaconView extends CraftInventoryView<net.minecraft.world.inventory.BeaconMenu, BeaconInventory> implements BeaconView {

    public CraftBeaconView(final HumanEntity player, final BeaconInventory viewing, final net.minecraft.world.inventory.BeaconMenu container) {
        super(player, viewing, container);
    }

    @Override
    public int getTier() {
        return container.getLevels();
    }

    @Nullable
    @Override
    public PotionEffectType getPrimaryEffect() {
        return container.getPrimaryEffect() != null ? CraftPotionEffectType.minecraftHolderToBukkit(container.getPrimaryEffect()) : null;
    }

    @Nullable
    @Override
    public PotionEffectType getSecondaryEffect() {
        return container.getSecondaryEffect() != null ? CraftPotionEffectType.minecraftHolderToBukkit(container.getSecondaryEffect()) : null;
    }

    @Override
    public void setPrimaryEffect(@Nullable final PotionEffectType effectType) {
        container.setData(BeaconBlockEntity.DATA_PRIMARY, BeaconMenu.encodeEffect((effectType == null) ? null : CraftPotionEffectType.bukkitToMinecraftHolder(effectType)));
    }

    @Override
    public void setSecondaryEffect(@Nullable final PotionEffectType effectType) {
        container.setData(BeaconBlockEntity.DATA_SECONDARY, BeaconMenu.encodeEffect((effectType == null) ? null : CraftPotionEffectType.bukkitToMinecraftHolder(effectType)));
    }
}
