package org.bukkit.craftbukkit.inventory;

import com.google.common.base.Preconditions;
import net.ethylenemc.interfaces.world.inventory.EthyleneAbstractContainerMenu;
import org.bukkit.GameMode;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public class CraftInventoryView<T extends net.minecraft.world.inventory.AbstractContainerMenu, I extends Inventory> extends CraftAbstractInventoryView {
    protected final T container;
    private final CraftHumanEntity player;
    private final I viewing;
    private final String originalTitle;
    private String title;

    public CraftInventoryView(HumanEntity player, I viewing, T container) {
        // TODO: Should we make sure it really IS a CraftHumanEntity first? And a CraftInventory?
        this.player = (CraftHumanEntity) player;
        this.viewing = viewing;
        this.container = container;
        this.originalTitle = CraftChatMessage.fromComponent(((EthyleneAbstractContainerMenu) container).getTitle());
        this.title = originalTitle;
    }

    @Override
    public I getTopInventory() {
        return viewing;
    }

    @Override
    public Inventory getBottomInventory() {
        return player.getInventory();
    }

    @Override
    public HumanEntity getPlayer() {
        return player;
    }

    @Override
    public InventoryType getType() {
        InventoryType type = viewing.getType();
        if (type == InventoryType.CRAFTING && player.getGameMode() == GameMode.CREATIVE) {
            return InventoryType.CREATIVE;
        }
        return type;
    }

    @Override
    public void setItem(int slot, ItemStack item) {
        net.minecraft.world.item.ItemStack stack = CraftItemStack.asNMSCopy(item);
        if (slot >= 0) {
            container.getSlot(slot).set(stack);
        } else {
            player.getHandle().drop(stack, false);
        }
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0) {
            return null;
        }
        return CraftItemStack.asCraftMirror(container.getSlot(slot).getItem());
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getOriginalTitle() {
        return originalTitle;
    }

    @Override
    public void setTitle(String title) {
        sendInventoryTitleChange(this, title);
        this.title = title;
    }

    public boolean isInTop(int rawSlot) {
        return rawSlot < viewing.getSize();
    }

    public net.minecraft.world.inventory.AbstractContainerMenu getHandle() {
        return container;
    }

    public static void sendInventoryTitleChange(InventoryView view, String title) {
        Preconditions.checkArgument(view != null, "InventoryView cannot be null");
        Preconditions.checkArgument(title != null, "Title cannot be null");
        Preconditions.checkArgument(view.getPlayer() instanceof Player, "NPCs are not currently supported for this function");
        Preconditions.checkArgument(view.getTopInventory().getType().isCreatable(), "Only creatable inventories can have their title changed");

        final net.minecraft.server.level.ServerPlayer entityPlayer = (net.minecraft.server.level.ServerPlayer) ((CraftHumanEntity) view.getPlayer()).getHandle();
        final int containerId = entityPlayer.containerMenu.containerId;
        final net.minecraft.world.inventory.MenuType<?> windowType = CraftContainer.getNotchInventoryType(view.getTopInventory());
        entityPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundOpenScreenPacket(containerId, windowType, CraftChatMessage.fromString(title)[0]));
        ((Player) view.getPlayer()).updateInventory();
    }
}
