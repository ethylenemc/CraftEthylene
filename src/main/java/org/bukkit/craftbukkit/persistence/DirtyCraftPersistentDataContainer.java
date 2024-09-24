package org.bukkit.craftbukkit.persistence;

import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * A child class of the persistent data container that recalls if it has been
 * mutated from an external caller.
 */
public final class DirtyCraftPersistentDataContainer extends CraftPersistentDataContainer {

    private boolean dirty;

    public DirtyCraftPersistentDataContainer(Map<String, net.minecraft.nbt.Tag> customTags, CraftPersistentDataTypeRegistry registry) {
        super(customTags, registry);
    }

    public DirtyCraftPersistentDataContainer(CraftPersistentDataTypeRegistry registry) {
        super(registry);
    }

    public boolean dirty() {
        return this.dirty;
    }

    public void dirty(final boolean dirty) {
        this.dirty = dirty;
    }

    @Override
    public <T, Z> void set(@NotNull NamespacedKey key, @NotNull PersistentDataType<T, Z> type, @NotNull Z value) {
        super.set(key, type, value);
        this.dirty(true);
    }

    @Override
    public void remove(@NotNull NamespacedKey key) {
        super.remove(key);
        this.dirty(true);
    }

    @Override
    public void put(String key, net.minecraft.nbt.Tag base) {
        super.put(key, base);
        this.dirty(true);
    }

    @Override
    public void putAll(net.minecraft.nbt.CompoundTag compound) {
        super.putAll(compound);
        this.dirty(true);
    }

    @Override
    public void putAll(Map<String, net.minecraft.nbt.Tag> map) {
        super.putAll(map);
        this.dirty(true);
    }
}
