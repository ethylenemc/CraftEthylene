package org.bukkit.craftbukkit.entity;

import com.google.common.base.Preconditions;
import net.ethylenemc.interfaces.world.entity.EthyleneEntity;
import net.ethylenemc.interfaces.world.entity.EthyleneMob;
import net.minecraft.world.entity.animal.AbstractGolem;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.projectiles.ProjectileSource;

public class CraftShulkerBullet extends AbstractProjectile implements ShulkerBullet {

    public CraftShulkerBullet(CraftServer server, net.minecraft.world.entity.projectile.ShulkerBullet entity) {
        super(server, entity);
    }

    @Override
    public ProjectileSource getShooter() {
        return ((EthyleneEntity) getHandle()).getProjectileSource();
    }

    @Override
    public void setShooter(ProjectileSource shooter) {
        if (shooter instanceof Entity) {
            getHandle().setOwner(((CraftEntity) shooter).getHandle());
        } else {
            getHandle().setOwner(null);
        }
        ((EthyleneEntity) getHandle()).setProjectileSource(shooter);
    }

    @Override
    public org.bukkit.entity.Entity getTarget() {
        return getHandle().getTarget() != null ? getHandle().getTarget().getBukkitEntity() : null;
    }

    @Override
    public void setTarget(org.bukkit.entity.Entity target) {
        Preconditions.checkState(!((EthyleneEntity) getHandle()).getGeneration(), "Cannot set target during world generation");

        getHandle().setTarget(target == null ? null : ((CraftEntity) target).getHandle());
    }

    AbstractGolem

    @Override
    public String toString() {
        return "CraftShulkerBullet";
    }

    @Override
    public net.minecraft.world.entity.projectile.ShulkerBullet getHandle() {
        return (net.minecraft.world.entity.projectile.ShulkerBullet) entity;
    }
}
