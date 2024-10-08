package org.bukkit.craftbukkit.entity;

import com.google.common.base.Preconditions;
import net.ethylenemc.interfaces.world.entity.EthyleneEntity;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

public class CraftWarden extends CraftMonster implements org.bukkit.entity.Warden {

    public CraftWarden(CraftServer server, net.minecraft.world.entity.monster.warden.Warden entity) {
        super(server, entity);
    }

    @Override
    public net.minecraft.world.entity.monster.warden.Warden getHandle() {
        return (net.minecraft.world.entity.monster.warden.Warden) entity;
    }

    @Override
    public String toString() {
        return "CraftWarden";
    }

    @Override
    public int getAnger() {
        return getHandle().getAngerManagement().getActiveAnger(getHandle().getTarget());
    }

    @Override
    public int getAnger(Entity entity) {
        Preconditions.checkArgument(entity != null, "Entity cannot be null");

        return getHandle().getAngerManagement().getActiveAnger(((CraftEntity) entity).getHandle());
    }

    @Override
    public void increaseAnger(Entity entity, int increase) {
        Preconditions.checkArgument(entity != null, "Entity cannot be null");

        getHandle().getAngerManagement().increaseAnger(((CraftEntity) entity).getHandle(), increase);
    }

    @Override
    public void setAnger(Entity entity, int anger) {
        Preconditions.checkArgument(entity != null, "Entity cannot be null");

        getHandle().clearAnger(((CraftEntity) entity).getHandle());
        getHandle().getAngerManagement().increaseAnger(((CraftEntity) entity).getHandle(), anger);
    }

    @Override
    public void clearAnger(Entity entity) {
        Preconditions.checkArgument(entity != null, "Entity cannot be null");

        getHandle().clearAnger(((CraftEntity) entity).getHandle());
    }

    @Override
    public LivingEntity getEntityAngryAt() {
        return (LivingEntity) getHandle().getEntityAngryAt().map(entity -> ((EthyleneEntity) entity).getBukkitEntity()).orElse(null);
    }

    @Override
    public void setDisturbanceLocation(Location location) {
        Preconditions.checkArgument(location != null, "Location cannot be null");

        net.minecraft.world.entity.monster.warden.WardenAi.setDisturbanceLocation(getHandle(), net.minecraft.core.BlockPos.containing(location.getX(), location.getY(), location.getZ()));
    }

    @Override
    public AngerLevel getAngerLevel() {
        return switch (getHandle().getAngerLevel()) {
            case CALM -> AngerLevel.CALM;
            case AGITATED -> AngerLevel.AGITATED;
            case ANGRY -> AngerLevel.ANGRY;
        };
    }
}
