package org.bukkit.craftbukkit.entity;

import com.google.common.base.Preconditions;
import net.ethylenemc.interfaces.world.entity.EthyleneEntity;
import net.ethylenemc.interfaces.world.entity.monster.EthyleneCreeper;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreeperPowerEvent;
import org.jetbrains.annotations.NotNull;

public class CraftCreeper extends CraftMonster implements Creeper {

    public CraftCreeper(CraftServer server, net.minecraft.world.entity.monster.Creeper entity) {
        super(server, entity);
    }

    @Override
    public boolean isPowered() {
        return getHandle().isPowered();
    }

    @Override
    public void setPowered(boolean powered) {
        CreeperPowerEvent.PowerCause cause = powered ? CreeperPowerEvent.PowerCause.SET_ON : CreeperPowerEvent.PowerCause.SET_OFF;

        // only call event when we are not in world generation
        if (((EthyleneEntity) getHandle()).getGeneration() || !callPowerEvent(cause)) {
            ((EthyleneCreeper) getHandle()).setPowered(powered);
        }
    }

    private boolean callPowerEvent(CreeperPowerEvent.PowerCause cause) {
        CreeperPowerEvent event = new CreeperPowerEvent((Creeper) ((EthyleneEntity) getHandle()).getBukkitEntity(), cause);
        server.getPluginManager().callEvent(event);
        return event.isCancelled();
    }

    @Override
    public void setMaxFuseTicks(int ticks) {
        Preconditions.checkArgument(ticks >= 0, "ticks < 0");

        getHandle().maxSwell = ticks;
    }

    @Override
    public int getMaxFuseTicks() {
        return getHandle().maxSwell;
    }

    @Override
    public void setFuseTicks(int ticks) {
        Preconditions.checkArgument(ticks >= 0, "ticks < 0");
        Preconditions.checkArgument(ticks <= getMaxFuseTicks(), "ticks > maxFuseTicks");

        getHandle().swell = ticks;
    }

    @Override
    public int getFuseTicks() {
        return getHandle().swell;
    }

    @Override
    public void setExplosionRadius(int radius) {
        Preconditions.checkArgument(radius >= 0, "radius < 0");

        getHandle().explosionRadius = radius;
    }

    @Override
    public int getExplosionRadius() {
        return getHandle().explosionRadius;
    }

    @Override
    public void explode() {
        getHandle().explodeCreeper();
    }

    @Override
    public void ignite(@NotNull Entity entity) {
        Preconditions.checkNotNull(entity, "entity cannot be null");
        ((EthyleneCreeper) getHandle()).entityIgniter(((CraftEntity) entity).getHandle());
        getHandle().ignite();
    }

    @Override
    public void ignite() {
        getHandle().ignite();
    }

    @Override
    public Entity getIgniter() {
        return (((EthyleneCreeper) getHandle()).entityIgniter() != null) ? ((EthyleneEntity) ((EthyleneCreeper) getHandle()).entityIgniter()).getBukkitEntity() : null;
    }

    @Override
    public net.minecraft.world.entity.monster.Creeper getHandle() {
        return (net.minecraft.world.entity.monster.Creeper) entity;
    }

    @Override
    public String toString() {
        return "CraftCreeper";
    }
}
