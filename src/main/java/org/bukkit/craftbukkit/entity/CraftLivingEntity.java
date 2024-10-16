package org.bukkit.craftbukkit.entity;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.ethylenemc.interfaces.world.entity.EthyleneEntity;
import net.ethylenemc.interfaces.world.entity.EthyleneLivingEntity;
import net.ethylenemc.interfaces.world.entity.EthyleneMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.windcharge.AbstractWindCharge;
import net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftSound;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.memory.CraftMemoryKey;
import org.bukkit.craftbukkit.entity.memory.CraftMemoryMapper;
import org.bukkit.craftbukkit.inventory.CraftEntityEquipment;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.potion.CraftPotionEffectType;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityCategory;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Firework;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LingeringPotion;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.LlamaSpit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.TippedArrow;
import org.bukkit.entity.Trident;
import org.bukkit.entity.WindCharge;
import org.bukkit.entity.WitherSkull;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public class CraftLivingEntity extends CraftEntity implements LivingEntity {
    private CraftEntityEquipment equipment;

    public CraftLivingEntity(final CraftServer server, final net.minecraft.world.entity.LivingEntity entity) {
        super(server, entity);

        if (entity instanceof net.minecraft.world.entity.Mob || entity instanceof net.minecraft.world.entity.decoration.ArmorStand) {
            equipment = new CraftEntityEquipment(this);
        }
    }

    @Override
    public double getHealth() {
        return Math.min(Math.max(0, getHandle().getHealth()), getMaxHealth());
    }

    @Override
    public void setHealth(double health) {
        health = (float) health;
        Preconditions.checkArgument(health >= 0 && health <= this.getMaxHealth(), "Health value (%s) must be between 0 and %s", health, this.getMaxHealth());

        // during world generation, we don't want to run logic for dropping items and xp
        if (((EthyleneEntity) getHandle()).getGeneration() && health == 0) {
            ((EthyleneEntity) getHandle()).discard(null); // Add Bukkit remove cause
            return;
        }

        getHandle().setHealth((float) health);

        if (health == 0) {
            getHandle().die(getHandle().damageSources().generic());
        }
    }

    @Override
    public double getAbsorptionAmount() {
        return getHandle().getAbsorptionAmount();
    }

    @Override
    public void setAbsorptionAmount(double amount) {
        Preconditions.checkArgument(amount >= 0 && Double.isFinite(amount), "amount < 0 or non-finite");

        getHandle().setAbsorptionAmount((float) amount);
    }

    @Override
    public double getMaxHealth() {
        return getHandle().getMaxHealth();
    }

    @Override
    public void setMaxHealth(double amount) {
        Preconditions.checkArgument(amount > 0, "Max health amount (%s) must be greater than 0", amount);

        getHandle().getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(amount);

        if (getHealth() > amount) {
            setHealth(amount);
        }
    }

    @Override
    public void resetMaxHealth() {
        setMaxHealth(getHandle().getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getAttribute().value().getDefaultValue());
    }

    @Override
    public double getEyeHeight() {
        return getHandle().getEyeHeight();
    }

    @Override
    public double getEyeHeight(boolean ignorePose) {
        return getEyeHeight();
    }

    private List<Block> getLineOfSight(Set<Material> transparent, int maxDistance, int maxLength) {
        Preconditions.checkState(!((EthyleneEntity) getHandle()).getGeneration(), "Cannot get line of sight during world generation");

        if (transparent == null) {
            transparent = Sets.newHashSet(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR);
        }
        if (maxDistance > 120) {
            maxDistance = 120;
        }
        ArrayList<Block> blocks = new ArrayList<Block>();
        Iterator<Block> itr = new BlockIterator(this, maxDistance);
        while (itr.hasNext()) {
            Block block = itr.next();
            blocks.add(block);
            if (maxLength != 0 && blocks.size() > maxLength) {
                blocks.remove(0);
            }
            Material material = block.getType();
            if (!transparent.contains(material)) {
                break;
            }
        }
        return blocks;
    }

    @Override
    public List<Block> getLineOfSight(Set<Material> transparent, int maxDistance) {
        return getLineOfSight(transparent, maxDistance, 0);
    }

    @Override
    public Block getTargetBlock(Set<Material> transparent, int maxDistance) {
        List<Block> blocks = getLineOfSight(transparent, maxDistance, 1);
        return blocks.get(0);
    }

    @Override
    public List<Block> getLastTwoTargetBlocks(Set<Material> transparent, int maxDistance) {
        return getLineOfSight(transparent, maxDistance, 2);
    }

    @Override
    public Block getTargetBlockExact(int maxDistance) {
        return this.getTargetBlockExact(maxDistance, FluidCollisionMode.NEVER);
    }

    @Override
    public Block getTargetBlockExact(int maxDistance, FluidCollisionMode fluidCollisionMode) {
        RayTraceResult hitResult = this.rayTraceBlocks(maxDistance, fluidCollisionMode);
        return (hitResult != null ? hitResult.getHitBlock() : null);
    }

    @Override
    public RayTraceResult rayTraceBlocks(double maxDistance) {
        return this.rayTraceBlocks(maxDistance, FluidCollisionMode.NEVER);
    }

    @Override
    public RayTraceResult rayTraceBlocks(double maxDistance, FluidCollisionMode fluidCollisionMode) {
        Preconditions.checkState(!((EthyleneEntity) getHandle()).getGeneration(), "Cannot ray tray blocks during world generation");

        Location eyeLocation = this.getEyeLocation();
        Vector direction = eyeLocation.getDirection();
        return this.getWorld().rayTraceBlocks(eyeLocation, direction, maxDistance, fluidCollisionMode, false);
    }

    @Override
    public int getRemainingAir() {
        return getHandle().getAirSupply();
    }

    @Override
    public void setRemainingAir(int ticks) {
        getHandle().setAirSupply(ticks);
    }

    @Override
    public int getMaximumAir() {
        return getHandle().maxAirTicks;
    }

    @Override
    public void setMaximumAir(int ticks) {
        getHandle().maxAirTicks = ticks;
    }

    @Override
    public ItemStack getItemInUse() {
        net.minecraft.world.item.ItemStack item = getHandle().getUseItem();
        return item.isEmpty() ? null : CraftItemStack.asCraftMirror(item);
    }

    @Override
    public int getItemInUseTicks() {
        return getHandle().getUseItemRemainingTicks();
    }

    @Override
    public void setItemInUseTicks(int ticks) {
        getHandle().useItemRemaining = ticks;
    }

    @Override
    public int getArrowCooldown() {
        return getHandle().removeArrowTime;
    }

    @Override
    public void setArrowCooldown(int ticks) {
        getHandle().removeArrowTime = ticks;
    }

    @Override
    public int getArrowsInBody() {
        return getHandle().getArrowCount();
    }

    @Override
    public void setArrowsInBody(int count) {
        Preconditions.checkArgument(count >= 0, "New arrow amount must be >= 0");
        getHandle().getEntityData().set(net.minecraft.world.entity.LivingEntity.DATA_ARROW_COUNT_ID, count);
    }

    @Override
    public void damage(double amount) {
        damage(amount, getHandle().damageSources().generic());
    }

    @Override
    public void damage(double amount, org.bukkit.entity.Entity source) {
        net.minecraft.world.damagesource.DamageSource reason = getHandle().damageSources().generic();

        if (source instanceof HumanEntity) {
            reason = getHandle().damageSources().playerAttack(((CraftHumanEntity) source).getHandle());
        } else if (source instanceof LivingEntity) {
            reason = getHandle().damageSources().mobAttack(((CraftLivingEntity) source).getHandle());
        }

        damage(amount, reason);
    }

    @Override
    public void damage(double amount, org.bukkit.damage.DamageSource damageSource) {
        Preconditions.checkArgument(damageSource != null, "damageSource cannot be null");

        damage(amount, ((CraftDamageSource) damageSource).getHandle());
    }

    private void damage(double amount, net.minecraft.world.damagesource.DamageSource damageSource) {
        Preconditions.checkArgument(damageSource != null, "damageSource cannot be null");
        Preconditions.checkState(!((EthyleneEntity) getHandle()).getGeneration(), "Cannot damage entity during world generation");

        entity.hurt(damageSource, (float) amount);
    }

    @Override
    public Location getEyeLocation() {
        Location loc = getLocation();
        loc.setY(loc.getY() + getEyeHeight());
        return loc;
    }

    @Override
    public int getMaximumNoDamageTicks() {
        return getHandle().invulnerableDuration;
    }

    @Override
    public void setMaximumNoDamageTicks(int ticks) {
        getHandle().invulnerableDuration = ticks;
    }

    @Override
    public double getLastDamage() {
        return getHandle().lastHurt;
    }

    @Override
    public void setLastDamage(double damage) {
        getHandle().lastHurt = (float) damage;
    }

    @Override
    public int getNoDamageTicks() {
        return getHandle().invulnerableTime;
    }

    @Override
    public void setNoDamageTicks(int ticks) {
        getHandle().invulnerableTime = ticks;
    }

    @Override
    public int getNoActionTicks() {
        return getHandle().getNoActionTime();
    }

    @Override
    public void setNoActionTicks(int ticks) {
        Preconditions.checkArgument(ticks >= 0, "ticks must be >= 0");
        getHandle().setNoActionTime(ticks);
    }

    @Override
    public net.minecraft.world.entity.LivingEntity getHandle() {
        return (net.minecraft.world.entity.LivingEntity) entity;
    }

    public void setHandle(final net.minecraft.world.entity.LivingEntity entity) {
        super.setHandle(entity);
    }

    @Override
    public String toString() {
        return "CraftLivingEntity{" + "id=" + getEntityId() + '}';
    }

    @Override
    public Player getKiller() {
        return getHandle().lastHurtByPlayer == null ? null : (Player) ((EthyleneEntity) getHandle().lastHurtByPlayer).getBukkitEntity();
    }

    @Override
    public boolean addPotionEffect(PotionEffect effect) {
        return addPotionEffect(effect, false);
    }

    @Override
    public boolean addPotionEffect(PotionEffect effect, boolean force) {
        ((EthyleneLivingEntity) getHandle()).addEffect(new net.minecraft.world.effect.MobEffectInstance(CraftPotionEffectType.bukkitToMinecraftHolder(effect.getType()), effect.getDuration(), effect.getAmplifier(), effect.isAmbient(), effect.hasParticles()), EntityPotionEffectEvent.Cause.PLUGIN);
        return true;
    }

    @Override
    public boolean addPotionEffects(Collection<PotionEffect> effects) {
        boolean success = true;
        for (PotionEffect effect : effects) {
            success &= addPotionEffect(effect);
        }
        return success;
    }

    @Override
    public boolean hasPotionEffect(PotionEffectType type) {
        return getHandle().hasEffect(CraftPotionEffectType.bukkitToMinecraftHolder(type));
    }

    @Override
    public PotionEffect getPotionEffect(PotionEffectType type) {
        net.minecraft.world.effect.MobEffectInstance handle = getHandle().getEffect(CraftPotionEffectType.bukkitToMinecraftHolder(type));
        return (handle == null) ? null : new PotionEffect(CraftPotionEffectType.minecraftHolderToBukkit(handle.getEffect()), handle.getDuration(), handle.getAmplifier(), handle.isAmbient(), handle.isVisible());
    }

    @Override
    public void removePotionEffect(PotionEffectType type) {
        ((EthyleneLivingEntity) getHandle()).removeEffect(CraftPotionEffectType.bukkitToMinecraftHolder(type), EntityPotionEffectEvent.Cause.PLUGIN);
    }

    @Override
    public Collection<PotionEffect> getActivePotionEffects() {
        List<PotionEffect> effects = new ArrayList<PotionEffect>();
        for (net.minecraft.world.effect.MobEffectInstance handle : getHandle().activeEffects.values()) {
            effects.add(new PotionEffect(CraftPotionEffectType.minecraftHolderToBukkit(handle.getEffect()), handle.getDuration(), handle.getAmplifier(), handle.isAmbient(), handle.isVisible()));
        }
        return effects;
    }

    @Override
    public <T extends Projectile> T launchProjectile(Class<? extends T> projectile) {
        return launchProjectile(projectile, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Projectile> T launchProjectile(Class<? extends T> projectile, Vector velocity) {
        Preconditions.checkState(!((EthyleneEntity) getHandle()).getGeneration(), "Cannot launch projectile during world generation");

        net.minecraft.world.level.Level world = ((CraftWorld) getWorld()).getHandle();
        net.minecraft.world.entity.Entity launch = null;

        if (Snowball.class.isAssignableFrom(projectile)) {
            launch = new net.minecraft.world.entity.projectile.Snowball(world, getHandle());
            ((net.minecraft.world.entity.projectile.ThrowableProjectile) launch).shootFromRotation(getHandle(), getHandle().getXRot(), getHandle().getYRot(), 0.0F, 1.5F, 1.0F); // ItemSnowball
        } else if (Egg.class.isAssignableFrom(projectile)) {
            launch = new net.minecraft.world.entity.projectile.ThrownEgg(world, getHandle());
            ((net.minecraft.world.entity.projectile.ThrowableProjectile) launch).shootFromRotation(getHandle(), getHandle().getXRot(), getHandle().getYRot(), 0.0F, 1.5F, 1.0F); // ItemEgg
        } else if (EnderPearl.class.isAssignableFrom(projectile)) {
            launch = new net.minecraft.world.entity.projectile.ThrownEnderpearl(world, getHandle());
            ((net.minecraft.world.entity.projectile.ThrowableProjectile) launch).shootFromRotation(getHandle(), getHandle().getXRot(), getHandle().getYRot(), 0.0F, 1.5F, 1.0F); // ItemEnderPearl
        } else if (AbstractArrow.class.isAssignableFrom(projectile)) {
            if (TippedArrow.class.isAssignableFrom(projectile)) {
                launch = new net.minecraft.world.entity.projectile.Arrow(world, getHandle(), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
                ((Arrow) ((EthyleneEntity) launch).getBukkitEntity()).setBasePotionType(PotionType.WATER);
            } else if (SpectralArrow.class.isAssignableFrom(projectile)) {
                launch = new net.minecraft.world.entity.projectile.SpectralArrow(world, getHandle(), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SPECTRAL_ARROW), null);
            } else if (Trident.class.isAssignableFrom(projectile)) {
                launch = new net.minecraft.world.entity.projectile.ThrownTrident(world, getHandle(), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TRIDENT));
            } else {
                launch = new net.minecraft.world.entity.projectile.Arrow(world, getHandle(), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
            }
            ((net.minecraft.world.entity.projectile.AbstractArrow) launch).shootFromRotation(getHandle(), getHandle().getXRot(), getHandle().getYRot(), 0.0F, 3.0F, 1.0F); // ItemBow
        } else if (ThrownPotion.class.isAssignableFrom(projectile)) {
            if (LingeringPotion.class.isAssignableFrom(projectile)) {
                launch = new net.minecraft.world.entity.projectile.ThrownPotion(world, getHandle());
                ((net.minecraft.world.entity.projectile.ThrownPotion) launch).setItem(CraftItemStack.asNMSCopy(new ItemStack(org.bukkit.Material.LINGERING_POTION, 1)));
            } else {
                launch = new net.minecraft.world.entity.projectile.ThrownPotion(world, getHandle());
                ((net.minecraft.world.entity.projectile.ThrownPotion) launch).setItem(CraftItemStack.asNMSCopy(new ItemStack(org.bukkit.Material.SPLASH_POTION, 1)));
            }
            ((net.minecraft.world.entity.projectile.ThrowableProjectile) launch).shootFromRotation(getHandle(), getHandle().getXRot(), getHandle().getYRot(), -20.0F, 0.5F, 1.0F); // ItemSplashPotion
        } else if (ThrownExpBottle.class.isAssignableFrom(projectile)) {
            launch = new net.minecraft.world.entity.projectile.ThrownExperienceBottle(world, getHandle());
            ((net.minecraft.world.entity.projectile.ThrowableProjectile) launch).shootFromRotation(getHandle(), getHandle().getXRot(), getHandle().getYRot(), -20.0F, 0.7F, 1.0F); // ItemExpBottle
        } else if (FishHook.class.isAssignableFrom(projectile) && getHandle() instanceof net.minecraft.world.entity.player.Player) {
            launch = new net.minecraft.world.entity.projectile.FishingHook((net.minecraft.world.entity.player.Player) getHandle(), world, 0, 0);
        } else if (Fireball.class.isAssignableFrom(projectile)) {
            Location location = getEyeLocation();
            Vector direction = location.getDirection().multiply(10);
            net.minecraft.world.phys.Vec3 vec = new net.minecraft.world.phys.Vec3(direction.getX(), direction.getY(), direction.getZ());

            if (SmallFireball.class.isAssignableFrom(projectile)) {
                launch = new net.minecraft.world.entity.projectile.SmallFireball(world, getHandle(), vec);
            } else if (WitherSkull.class.isAssignableFrom(projectile)) {
                launch = new net.minecraft.world.entity.projectile.WitherSkull(world, getHandle(), vec);
            } else if (DragonFireball.class.isAssignableFrom(projectile)) {
                launch = new net.minecraft.world.entity.projectile.DragonFireball(world, getHandle(), vec);
            }  else if (AbstractWindCharge.class.isAssignableFrom(projectile)) {
                if (BreezeWindCharge.class.isAssignableFrom(projectile)) {
                    launch = EntityType.BREEZE_WIND_CHARGE.create(world);
                } else {
                    launch = EntityType.WIND_CHARGE.create(world);
                }

                ((net.minecraft.world.entity.projectile.windcharge.AbstractWindCharge) launch).setOwner(getHandle());
                ((net.minecraft.world.entity.projectile.windcharge.AbstractWindCharge) launch).shootFromRotation(getHandle(), getHandle().getXRot(), getHandle().getYRot(), 0.0F, 1.5F, 1.0F); // WindChargeItem
            } else {
                launch = new net.minecraft.world.entity.projectile.LargeFireball(world, getHandle(), vec, 1);
            }

            ((EthyleneEntity) ((net.minecraft.world.entity.projectile.AbstractHurtingProjectile) launch)).setProjectileSource(this);
            launch.moveTo(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        } else if (LlamaSpit.class.isAssignableFrom(projectile)) {
            Location location = getEyeLocation();
            Vector direction = location.getDirection();

            launch = net.minecraft.world.entity.EntityType.LLAMA_SPIT.create(world);

            ((net.minecraft.world.entity.projectile.LlamaSpit) launch).setOwner(getHandle());
            ((net.minecraft.world.entity.projectile.LlamaSpit) launch).shoot(direction.getX(), direction.getY(), direction.getZ(), 1.5F, 10.0F); // EntityLlama
            launch.moveTo(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        } else if (ShulkerBullet.class.isAssignableFrom(projectile)) {
            Location location = getEyeLocation();

            launch = new net.minecraft.world.entity.projectile.ShulkerBullet(world, getHandle(), null, null);
            launch.moveTo(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        } else if (Firework.class.isAssignableFrom(projectile)) {
            Location location = getEyeLocation();

            launch = new net.minecraft.world.entity.projectile.FireworkRocketEntity(world, net.minecraft.world.item.ItemStack.EMPTY, getHandle());
            launch.moveTo(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        }

        Preconditions.checkArgument(launch != null, "Projectile (%s) not supported", projectile.getName());

        if (velocity != null) {
            ((T) ((EthyleneEntity) launch).getBukkitEntity()).setVelocity(velocity);
        }

        world.addFreshEntity(launch);
        return (T) ((EthyleneEntity) launch).getBukkitEntity();
    }

    @Override
    public boolean hasLineOfSight(Entity other) {
        Preconditions.checkState(!((EthyleneEntity) getHandle()).getGeneration(), "Cannot check line of sight during world generation");

        return getHandle().hasLineOfSight(((CraftEntity) other).getHandle());
    }

    @Override
    public boolean getRemoveWhenFarAway() {
        return getHandle() instanceof net.minecraft.world.entity.Mob && !((net.minecraft.world.entity.Mob) getHandle()).isPersistenceRequired();
    }

    @Override
    public void setRemoveWhenFarAway(boolean remove) {
        if (getHandle() instanceof net.minecraft.world.entity.Mob) {
            ((EthyleneMob) ((net.minecraft.world.entity.Mob) getHandle())).setPersistenceRequired(!remove);
        }
    }

    @Override
    public EntityEquipment getEquipment() {
        return equipment;
    }

    @Override
    public void setCanPickupItems(boolean pickup) {
        if (getHandle() instanceof net.minecraft.world.entity.Mob) {
            ((net.minecraft.world.entity.Mob) getHandle()).setCanPickUpLoot(pickup);
        } else {
            ((EthyleneLivingEntity) getHandle()).bukkitPickUpLoot(pickup);
        }
    }

    @Override
    public boolean getCanPickupItems() {
        if (getHandle() instanceof net.minecraft.world.entity.Mob) {
            return ((net.minecraft.world.entity.Mob) getHandle()).canPickUpLoot();
        } else {
            return ((EthyleneLivingEntity) getHandle()).bukkitPickUpLoot();
        }
    }

    @Override
    public boolean teleport(Location location, PlayerTeleportEvent.TeleportCause cause) {
        if (getHealth() == 0) {
            return false;
        }

        return super.teleport(location, cause);
    }

    @Override
    public boolean isLeashed() {
        if (!(getHandle() instanceof net.minecraft.world.entity.Mob)) {
            return false;
        }
        return ((net.minecraft.world.entity.Mob) getHandle()).getLeashHolder() != null;
    }

    @Override
    public Entity getLeashHolder() throws IllegalStateException {
        Preconditions.checkState(isLeashed(), "Entity not leashed");
        return ((EthyleneEntity) ((net.minecraft.world.entity.Mob) getHandle()).getLeashHolder()).getBukkitEntity();
    }

    private boolean unleash() {
        if (!isLeashed()) {
            return false;
        }
        ((net.minecraft.world.entity.Mob) getHandle()).dropLeash(true, false);
        return true;
    }

    @Override
    public boolean setLeashHolder(Entity holder) {
        if (((EthyleneEntity) getHandle()).getGeneration() || (getHandle() instanceof net.minecraft.world.entity.boss.wither.WitherBoss) || !(getHandle() instanceof net.minecraft.world.entity.Mob)) {
            return false;
        }

        if (holder == null) {
            return unleash();
        }

        if (holder.isDead()) {
            return false;
        }

        unleash();
        ((net.minecraft.world.entity.Mob) getHandle()).setLeashedTo(((CraftEntity) holder).getHandle(), true);
        return true;
    }

    @Override
    public boolean isGliding() {
        return getHandle().getSharedFlag(7);
    }

    @Override
    public void setGliding(boolean gliding) {
        getHandle().setSharedFlag(7, gliding);
    }

    @Override
    public boolean isSwimming() {
        return getHandle().isSwimming();
    }

    @Override
    public void setSwimming(boolean swimming) {
        getHandle().setSwimming(swimming);
    }

    @Override
    public boolean isRiptiding() {
        return getHandle().isAutoSpinAttack();
    }

    @Override
    public void setRiptiding(boolean riptiding) {
        getHandle().setLivingEntityFlag(net.minecraft.world.entity.LivingEntity.LIVING_ENTITY_FLAG_SPIN_ATTACK, riptiding);
    }

    @Override
    public boolean isSleeping() {
        return getHandle().isSleeping();
    }

    @Override
    public boolean isClimbing() {
        Preconditions.checkState(!((EthyleneEntity) getHandle()).getGeneration(), "Cannot check if climbing during world generation");

        return getHandle().onClimbable();
    }

    @Override
    public AttributeInstance getAttribute(Attribute attribute) {
        return ((EthyleneLivingEntity) getHandle()).craftAttributes().getAttribute(attribute);
    }

    @Override
    public void setAI(boolean ai) {
        if (this.getHandle() instanceof net.minecraft.world.entity.Mob) {
            ((net.minecraft.world.entity.Mob) this.getHandle()).setNoAi(!ai);
        }
    }

    @Override
    public boolean hasAI() {
        return (this.getHandle() instanceof net.minecraft.world.entity.Mob) ? !((net.minecraft.world.entity.Mob) this.getHandle()).isNoAi() : false;
    }

    @Override
    public void attack(Entity target) {
        Preconditions.checkArgument(target != null, "target == null");
        Preconditions.checkState(!((EthyleneEntity) getHandle()).getGeneration(), "Cannot attack during world generation");

        if (getHandle() instanceof net.minecraft.world.entity.player.Player) {
            ((net.minecraft.world.entity.player.Player) getHandle()).attack(((CraftEntity) target).getHandle());
        } else {
            getHandle().doHurtTarget(((CraftEntity) target).getHandle());
        }
    }

    @Override
    public void swingMainHand() {
        Preconditions.checkState(!((EthyleneEntity) getHandle()).getGeneration(), "Cannot swing hand during world generation");

        getHandle().swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
    }

    @Override
    public void swingOffHand() {
        Preconditions.checkState(!((EthyleneEntity) getHandle()).getGeneration(), "Cannot swing hand during world generation");

        getHandle().swing(net.minecraft.world.InteractionHand.OFF_HAND, true);
    }

    @Override
    public void playHurtAnimation(float yaw) {
        if (getHandle().level() instanceof net.minecraft.server.level.ServerLevel world) {
            /*
             * Vanilla degrees state that 0 = left, 90 = front, 180 = right, and 270 = behind.
             * This makes no sense. We'll add 90 to it so that 0 = front, clockwise from there.
             */
            float actualYaw = yaw + 90;
            net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket packet = new net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket(getEntityId(), actualYaw);

            world.getChunkSource().broadcastAndSend(getHandle(), packet);
        }
    }

    @Override
    public void setCollidable(boolean collidable) {
        ((EthyleneLivingEntity) getHandle()).collides(collidable);
    }

    @Override
    public boolean isCollidable() {
        return ((EthyleneLivingEntity) getHandle()).collides();
    }

    @Override
    public Set<UUID> getCollidableExemptions() {
        return ((EthyleneLivingEntity) getHandle()).collidableExemptions();
    }

    @Override
    public <T> T getMemory(MemoryKey<T> memoryKey) {
        return (T) getHandle().getBrain().getMemory(CraftMemoryKey.bukkitToMinecraft(memoryKey)).map(CraftMemoryMapper::fromNms).orElse(null);
    }

    @Override
    public <T> void setMemory(MemoryKey<T> memoryKey, T t) {
        getHandle().getBrain().setMemory(CraftMemoryKey.bukkitToMinecraft(memoryKey), CraftMemoryMapper.toNms(t));
    }

    @Override
    public Sound getHurtSound() {
        net.minecraft.sounds.SoundEvent sound = getHandle().getHurtSound(getHandle().damageSources().generic());
        return (sound != null) ? CraftSound.minecraftToBukkit(sound) : null;
    }

    @Override
    public Sound getDeathSound() {
        net.minecraft.sounds.SoundEvent sound = getHandle().getDeathSound();
        return (sound != null) ? CraftSound.minecraftToBukkit(sound) : null;
    }

    @Override
    public Sound getFallDamageSound(int fallHeight) {
        return CraftSound.minecraftToBukkit(getHandle().getFallDamageSound(fallHeight));
    }

    @Override
    public Sound getFallDamageSoundSmall() {
        return CraftSound.minecraftToBukkit(getHandle().getFallSounds().small());
    }

    @Override
    public Sound getFallDamageSoundBig() {
        return CraftSound.minecraftToBukkit(getHandle().getFallSounds().big());
    }

    @Override
    public Sound getDrinkingSound(ItemStack itemStack) {
        Preconditions.checkArgument(itemStack != null, "itemStack must not be null");
        return CraftSound.minecraftToBukkit(getHandle().getDrinkingSound(CraftItemStack.asNMSCopy(itemStack)));
    }

    @Override
    public Sound getEatingSound(ItemStack itemStack) {
        Preconditions.checkArgument(itemStack != null, "itemStack must not be null");
        return CraftSound.minecraftToBukkit(getHandle().getEatingSound(CraftItemStack.asNMSCopy(itemStack)));
    }

    @Override
    public boolean canBreatheUnderwater() {
        return getHandle().canBreatheUnderwater();
    }

    @Override
    public EntityCategory getCategory() {
        throw new UnsupportedOperationException("Method no longer applicable. Use Tags instead.");
    }

    @Override
    public boolean isInvisible() {
        return getHandle().isInvisible();
    }

    @Override
    public void setInvisible(boolean invisible) {
        getHandle().persistentInvisibility = invisible;
        getHandle().setSharedFlag(5, invisible);
    }
}
