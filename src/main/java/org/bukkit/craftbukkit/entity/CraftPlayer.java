package org.bukkit.craftbukkit.entity;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.BaseEncoding;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.shorts.ShortArraySet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

import net.ethylenemc.interfaces.server.level.EthyleneServerPlayer;
import net.ethylenemc.interfaces.world.entity.EthyleneEntity;
import net.ethylenemc.interfaces.world.entity.EthyleneLivingEntity;
import net.ethylenemc.interfaces.world.inventory.EthyleneAbstractContainerMenu;
import net.ethylenemc.interfaces.world.level.EthyleneLevel;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Note;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.ServerLinks;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.WeatherType;
import org.bukkit.WorldBorder;
import org.bukkit.ban.IpBanList;
import org.bukkit.ban.ProfileBanList;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.serialization.DelegateDeserialization;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.conversations.ManuallyAbandonedConversationCanceller;
import org.bukkit.craftbukkit.CraftEffect;
import org.bukkit.craftbukkit.CraftEquipmentSlot;
import org.bukkit.craftbukkit.CraftOfflinePlayer;
import org.bukkit.craftbukkit.CraftParticle;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftServerLinks;
import org.bukkit.craftbukkit.CraftSound;
import org.bukkit.craftbukkit.CraftStatistic;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.CraftWorldBorder;
import org.bukkit.craftbukkit.advancement.CraftAdvancement;
import org.bukkit.craftbukkit.advancement.CraftAdvancementProgress;
import org.bukkit.craftbukkit.block.CraftBlockEntityState;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.block.CraftSign;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.conversations.ConversationTracker;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.map.CraftMapCursor;
import org.bukkit.craftbukkit.map.CraftMapView;
import org.bukkit.craftbukkit.map.RenderData;
import org.bukkit.craftbukkit.potion.CraftPotionEffectType;
import org.bukkit.craftbukkit.potion.CraftPotionUtil;
import org.bukkit.craftbukkit.profile.CraftPlayerProfile;
import org.bukkit.craftbukkit.scoreboard.CraftScoreboard;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerExpCooldownChangeEvent;
import org.bukkit.event.player.PlayerHideEntityEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerShowEntityEvent;
import org.bukkit.event.player.PlayerSpawnChangeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerUnregisterChannelEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView.Property;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapView;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.StandardMessenger;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.NotNull;

@DelegateDeserialization(CraftOfflinePlayer.class)
public class CraftPlayer extends CraftHumanEntity implements Player {
    private long firstPlayed = 0;
    private long lastPlayed = 0;
    private boolean hasPlayedBefore = false;
    private final ConversationTracker conversationTracker = new ConversationTracker();
    private final Set<String> channels = new HashSet<String>();
    private final Map<UUID, Set<WeakReference<Plugin>>> invertedVisibilityEntities = new HashMap<>();
    private static final WeakHashMap<Plugin, WeakReference<Plugin>> pluginWeakReferences = new WeakHashMap<>();
    private int hash = 0;
    private double health = 20;
    private boolean scaledHealth = false;
    private double healthScale = 20;
    private CraftWorldBorder clientWorldBorder = null;
    private net.minecraft.world.level.border.BorderChangeListener clientWorldBorderListener = createWorldBorderListener();

    public CraftPlayer(CraftServer server, net.minecraft.server.level.ServerPlayer entity) {
        super(server, entity);

        firstPlayed = System.currentTimeMillis();
    }

    public GameProfile getProfile() {
        return getHandle().getGameProfile();
    }

    @Override
    public void remove() {
        // Will lead to an inconsistent player state if we remove the player as any other entity.
        throw new UnsupportedOperationException(String.format("Cannot remove player %s, use Player#kickPlayer(String) instead.", getName()));
    }

    @Override
    public boolean isOp() {
        return server.getHandle().isOp(getProfile());
    }

    @Override
    public void setOp(boolean value) {
        if (value == isOp()) return;

        if (value) {
            server.getHandle().op(getProfile());
        } else {
            server.getHandle().deop(getProfile());
        }

        perm.recalculatePermissions();
    }

    @Override
    public boolean isOnline() {
        return server.getPlayer(getUniqueId()) != null;
    }

    @Override
    public PlayerProfile getPlayerProfile() {
        return new CraftPlayerProfile(getProfile());
    }

    @Override
    public InetSocketAddress getAddress() {
        if (getHandle().connection.protocol() == null) return null;

        SocketAddress addr = getHandle().connection.getRemoteAddress();
        if (addr instanceof InetSocketAddress) {
            return (InetSocketAddress) addr;
        } else {
            return null;
        }
    }

    public interface TransferCookieConnection {

        boolean isTransferred();

        net.minecraft.network.ConnectionProtocol getProtocol();

        void sendPacket(net.minecraft.network.protocol.Packet<?> packet);

        void kickPlayer(net.minecraft.network.chat.Component reason);
    }

    public record CookieFuture(net.minecraft.resources.ResourceLocation key, CompletableFuture<byte[]> future) {

    }
    private final Queue<CookieFuture> requestedCookies = new LinkedList<>();

    public boolean isAwaitingCookies() {
        return !requestedCookies.isEmpty();
    }

    public boolean handleCookieResponse(net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket response) {
        CookieFuture future = requestedCookies.peek();
        if (future != null) {
            if (future.key.equals(response.key())) {
                Preconditions.checkState(future == requestedCookies.poll(), "requestedCookies queue mismatch");

                future.future().complete(response.payload());
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isTransferred() {
        return ((EthyleneServerPlayer) getHandle()).getTransferCookieConnection().isTransferred();
    }

    @Override
    public CompletableFuture<byte[]> retrieveCookie(NamespacedKey key) {
        Preconditions.checkArgument(key != null, "Cookie key cannot be null");

        CompletableFuture<byte[]> future = new CompletableFuture<>();
        net.minecraft.resources.ResourceLocation nms = CraftNamespacedKey.toMinecraft(key);
        requestedCookies.add(new CookieFuture(nms, future));

        ((EthyleneServerPlayer) getHandle()).getTransferCookieConnection().sendPacket(new net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket(nms));

        return future;
    }

    @Override
    public void storeCookie(NamespacedKey key, byte[] value) {
        Preconditions.checkArgument(key != null, "Cookie key cannot be null");
        Preconditions.checkArgument(value != null, "Cookie value cannot be null");
        Preconditions.checkArgument(value.length <= 5120, "Cookie value too large, must be smaller than 5120 bytes");
        Preconditions.checkState(((EthyleneServerPlayer) getHandle()).getTransferCookieConnection().getProtocol() == net.minecraft.network.ConnectionProtocol.CONFIGURATION || ((EthyleneServerPlayer) getHandle()).getTransferCookieConnection().getProtocol() == net.minecraft.network.ConnectionProtocol.PLAY, "Can only store cookie in CONFIGURATION or PLAY protocol.");

        ((EthyleneServerPlayer) getHandle()).getTransferCookieConnection().sendPacket(new net.minecraft.network.protocol.common.ClientboundStoreCookiePacket(CraftNamespacedKey.toMinecraft(key), value));
    }

    @Override
    public void transfer(String host, int port) {
        Preconditions.checkArgument(host != null, "Host cannot be null");
        Preconditions.checkState(((EthyleneServerPlayer) getHandle()).getTransferCookieConnection().getProtocol() == net.minecraft.network.ConnectionProtocol.CONFIGURATION || ((EthyleneServerPlayer) getHandle()).getTransferCookieConnection().getProtocol() == net.minecraft.network.ConnectionProtocol.PLAY, "Can only transfer in CONFIGURATION or PLAY protocol.");

        ((EthyleneServerPlayer) getHandle()).getTransferCookieConnection().sendPacket(new net.minecraft.network.protocol.common.ClientboundTransferPacket(host, port));
    }

    @Override
    public double getEyeHeight(boolean ignorePose) {
        if (ignorePose) {
            return 1.62D;
        } else {
            return getEyeHeight();
        }
    }

    @Override
    public void sendRawMessage(String message) {
        this.sendRawMessage(null, message);
    }

    @Override
    public void sendRawMessage(UUID sender, String message) {
        Preconditions.checkArgument(message != null, "message cannot be null");

        if (getHandle().connection == null) return;

        for (net.minecraft.network.chat.Component component : CraftChatMessage.fromString(message)) {
            getHandle().sendSystemMessage(component);
        }
    }

    @Override
    public void sendMessage(String message) {
        if (!conversationTracker.isConversingModaly()) {
            this.sendRawMessage(message);
        }
    }

    @Override
    public void sendMessage(String... messages) {
        for (String message : messages) {
            sendMessage(message);
        }
    }

    @Override
    public void sendMessage(UUID sender, String message) {
        if (!conversationTracker.isConversingModaly()) {
            this.sendRawMessage(sender, message);
        }
    }

    @Override
    public void sendMessage(UUID sender, String... messages) {
        for (String message : messages) {
            sendMessage(sender, message);
        }
    }

    @Override
    public String getDisplayName() {
        return ((EthyleneServerPlayer) getHandle()).displayName();
    }

    @Override
    public void setDisplayName(final String name) {
        ((EthyleneServerPlayer) getHandle()).displayName(name == null ? getName() : name);
    }

    @Override
    public String getPlayerListName() {
        return ((EthyleneServerPlayer) getHandle()).listName() == null ? getName() : CraftChatMessage.fromComponent(((EthyleneServerPlayer) getHandle()).listName());
    }

    @Override
    public void setPlayerListName(String name) {
        if (name == null) {
            name = getName();
        }
        ((EthyleneServerPlayer) getHandle()).listName(name.equals(getName()) ? null : CraftChatMessage.fromStringOrNull(name));
        for (net.minecraft.server.level.ServerPlayer player : (List<net.minecraft.server.level.ServerPlayer>) server.getHandle().players) {
            if (((Player) ((EthyleneEntity) player).getBukkitEntity()).canSee(this)) {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, getHandle()));
            }
        }
    }

    private net.minecraft.network.chat.Component playerListHeader;
    private net.minecraft.network.chat.Component playerListFooter;

    @Override
    public String getPlayerListHeader() {
        return (playerListHeader == null) ? null : CraftChatMessage.fromComponent(playerListHeader);
    }

    @Override
    public String getPlayerListFooter() {
        return (playerListFooter == null) ? null : CraftChatMessage.fromComponent(playerListFooter);
    }

    @Override
    public void setPlayerListHeader(String header) {
        this.playerListHeader = CraftChatMessage.fromStringOrNull(header, true);
        updatePlayerListHeaderFooter();
    }

    @Override
    public void setPlayerListFooter(String footer) {
        this.playerListFooter = CraftChatMessage.fromStringOrNull(footer, true);
        updatePlayerListHeaderFooter();
    }

    @Override
    public void setPlayerListHeaderFooter(String header, String footer) {
        this.playerListHeader = CraftChatMessage.fromStringOrNull(header, true);
        this.playerListFooter = CraftChatMessage.fromStringOrNull(footer, true);
        updatePlayerListHeaderFooter();
    }

    private void updatePlayerListHeaderFooter() {
        if (getHandle().connection == null) return;

        net.minecraft.network.protocol.game.ClientboundTabListPacket packet = new net.minecraft.network.protocol.game.ClientboundTabListPacket((this.playerListHeader == null) ? net.minecraft.network.chat.Component.empty() : this.playerListHeader, (this.playerListFooter == null) ? net.minecraft.network.chat.Component.empty() : this.playerListFooter);
        getHandle().connection.send(packet);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof OfflinePlayer)) {
            return false;
        }
        OfflinePlayer other = (OfflinePlayer) obj;
        if ((this.getUniqueId() == null) || (other.getUniqueId() == null)) {
            return false;
        }

        boolean uuidEquals = this.getUniqueId().equals(other.getUniqueId());
        boolean idEquals = true;

        if (other instanceof CraftPlayer) {
            idEquals = this.getEntityId() == ((CraftPlayer) other).getEntityId();
        }

        return uuidEquals && idEquals;
    }

    @Override
    public void kickPlayer(String message) {
        ((EthyleneServerPlayer) getHandle()).getTransferCookieConnection().kickPlayer(CraftChatMessage.fromStringOrEmpty(message, true));
    }

    @Override
    public void setCompassTarget(Location loc) {
        Preconditions.checkArgument(loc != null, "Location cannot be null");

        if (getHandle().connection == null) return;

        // Do not directly assign here, from the packethandler we'll assign it.
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket(CraftLocation.toBlockPosition(loc), loc.getYaw()));
    }

    @Override
    public Location getCompassTarget() {
        return ((EthyleneServerPlayer) getHandle()).compassTarget();
    }

    @Override
    public void chat(String msg) {
        Preconditions.checkArgument(msg != null, "msg cannot be null");

        if (getHandle().connection == null) return;

        getHandle().connection.chat(msg, net.minecraft.network.chat.PlayerChatMessage.system(msg), false);
    }

    @Override
    public boolean performCommand(String command) {
        Preconditions.checkArgument(command != null, "command cannot be null");
        return server.dispatchCommand(this, command);
    }

    @Override
    public void playNote(Location loc, byte instrument, byte note) {
        playNote(loc, Instrument.getByType(instrument), new Note(note));
    }

    @Override
    public void playNote(Location loc, Instrument instrument, Note note) {
        Preconditions.checkArgument(loc != null, "Location cannot be null");
        Preconditions.checkArgument(instrument != null, "Instrument cannot be null");
        Preconditions.checkArgument(note != null, "Note cannot be null");

        if (getHandle().connection == null) return;

        Sound instrumentSound = instrument.getSound();
        if (instrumentSound == null) return;

        float pitch = note.getPitch();
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(CraftSound.bukkitToMinecraftHolder(instrumentSound), net.minecraft.sounds.SoundSource.RECORDS, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), 3.0f, pitch, getHandle().getRandom().nextLong()));
    }

    @Override
    public void playSound(Location loc, Sound sound, float volume, float pitch) {
        playSound(loc, sound, org.bukkit.SoundCategory.MASTER, volume, pitch);
    }

    @Override
    public void playSound(Location loc, String sound, float volume, float pitch) {
        playSound(loc, sound, org.bukkit.SoundCategory.MASTER, volume, pitch);
    }

    @Override
    public void playSound(Location loc, Sound sound, org.bukkit.SoundCategory category, float volume, float pitch) {
        playSound(loc, sound, category, volume, pitch, getHandle().random.nextLong());
    }

    @Override
    public void playSound(Location loc, String sound, org.bukkit.SoundCategory category, float volume, float pitch) {
        playSound(loc, sound, category, volume, pitch, getHandle().random.nextLong());
    }

    @Override
    public void playSound(Location loc, Sound sound, org.bukkit.SoundCategory category, float volume, float pitch, long seed) {
        if (loc == null || sound == null || category == null || getHandle().connection == null) return;

        playSound0(loc, CraftSound.bukkitToMinecraftHolder(sound), net.minecraft.sounds.SoundSource.valueOf(category.name()), volume, pitch, seed);
    }

    @Override
    public void playSound(Location loc, String sound, org.bukkit.SoundCategory category, float volume, float pitch, long seed) {
        if (loc == null || sound == null || category == null || getHandle().connection == null) return;

        playSound0(loc, net.minecraft.core.Holder.direct(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.parse(sound))), net.minecraft.sounds.SoundSource.valueOf(category.name()), volume, pitch, seed);
    }

    private void playSound0(Location loc, net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> soundEffectHolder, net.minecraft.sounds.SoundSource categoryNMS, float volume, float pitch, long seed) {
        Preconditions.checkArgument(loc != null, "Location cannot be null");

        if (getHandle().connection == null) return;

        net.minecraft.network.protocol.game.ClientboundSoundPacket packet = new net.minecraft.network.protocol.game.ClientboundSoundPacket(soundEffectHolder, categoryNMS, loc.getX(), loc.getY(), loc.getZ(), volume, pitch, seed);
        getHandle().connection.send(packet);
    }

    @Override
    public void playSound(org.bukkit.entity.Entity entity, Sound sound, float volume, float pitch) {
        playSound(entity, sound, org.bukkit.SoundCategory.MASTER, volume, pitch);
    }

    @Override
    public void playSound(org.bukkit.entity.Entity entity, String sound, float volume, float pitch) {
        playSound(entity, sound, org.bukkit.SoundCategory.MASTER, volume, pitch);
    }

    @Override
    public void playSound(org.bukkit.entity.Entity entity, Sound sound, org.bukkit.SoundCategory category, float volume, float pitch) {
        playSound(entity, sound, category, volume, pitch, getHandle().random.nextLong());
    }

    @Override
    public void playSound(org.bukkit.entity.Entity entity, String sound, org.bukkit.SoundCategory category, float volume, float pitch) {
        playSound(entity, sound, category, volume, pitch, getHandle().random.nextLong());
    }

    @Override
    public void playSound(org.bukkit.entity.Entity entity, Sound sound, org.bukkit.SoundCategory category, float volume, float pitch, long seed) {
        if (!(entity instanceof CraftEntity craftEntity) || sound == null || category == null || getHandle().connection == null) return;

        playSound0(entity, CraftSound.bukkitToMinecraftHolder(sound), net.minecraft.sounds.SoundSource.valueOf(category.name()), volume, pitch, seed);
    }

    @Override
    public void playSound(org.bukkit.entity.Entity entity, String sound, org.bukkit.SoundCategory category, float volume, float pitch, long seed) {
        if (!(entity instanceof CraftEntity craftEntity) || sound == null || category == null || getHandle().connection == null) return;

        playSound0(entity, net.minecraft.core.Holder.direct(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.parse(sound))), net.minecraft.sounds.SoundSource.valueOf(category.name()), volume, pitch, seed);
    }

    private void playSound0(org.bukkit.entity.Entity entity, net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> soundEffectHolder, net.minecraft.sounds.SoundSource categoryNMS, float volume, float pitch, long seed) {
        Preconditions.checkArgument(entity != null, "net.minecraft.world.entity.Entity cannot be null");
        Preconditions.checkArgument(soundEffectHolder != null, "net.minecraft.core.Holder of net.minecraft.sounds.SoundEvent cannot be null");
        Preconditions.checkArgument(categoryNMS != null, "SoundCategory cannot be null");

        if (getHandle().connection == null) return;
        if (!(entity instanceof CraftEntity craftEntity)) return;

        net.minecraft.network.protocol.game.ClientboundSoundEntityPacket packet = new net.minecraft.network.protocol.game.ClientboundSoundEntityPacket(soundEffectHolder, categoryNMS, craftEntity.getHandle(), volume, pitch, seed);
        getHandle().connection.send(packet);
    }

    @Override
    public void stopSound(Sound sound) {
        stopSound(sound, null);
    }

    @Override
    public void stopSound(String sound) {
        stopSound(sound, null);
    }

    @Override
    public void stopSound(Sound sound, org.bukkit.SoundCategory category) {
        stopSound(sound.getKey().getKey(), category);
    }

    @Override
    public void stopSound(String sound, org.bukkit.SoundCategory category) {
        if (getHandle().connection == null) return;

        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(net.minecraft.resources.ResourceLocation.parse(sound), category == null ? net.minecraft.sounds.SoundSource.MASTER : net.minecraft.sounds.SoundSource.valueOf(category.name())));
    }

    @Override
    public void stopSound(org.bukkit.SoundCategory category) {
        if (getHandle().connection == null) return;

        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(null, net.minecraft.sounds.SoundSource.valueOf(category.name())));
    }

    @Override
    public void stopAllSounds() {
        if (getHandle().connection == null) return;

        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(null, null));
    }

    @Override
    public void playEffect(Location loc, Effect effect, int data) {
        Preconditions.checkArgument(effect != null, "Effect cannot be null");
        Preconditions.checkArgument(loc != null, "Location cannot be null");

        if (getHandle().connection == null) return;

        int packetData = effect.getId();
        net.minecraft.network.protocol.game.ClientboundLevelEventPacket packet = new net.minecraft.network.protocol.game.ClientboundLevelEventPacket(packetData, CraftLocation.toBlockPosition(loc), data, false);
        getHandle().connection.send(packet);
    }

    @Override
    public <T> void playEffect(Location loc, Effect effect, T data) {
        Preconditions.checkArgument(effect != null, "Effect cannot be null");
        if (data != null) {
            Preconditions.checkArgument(effect.getData() != null, "Effect.%s does not have a valid Data", effect);
            Preconditions.checkArgument(effect.getData().isAssignableFrom(data.getClass()), "%s data cannot be used for the %s effect", data.getClass().getName(), effect);
        } else {
            // Special case: the axis is optional for ELECTRIC_SPARK
            Preconditions.checkArgument(effect.getData() == null || effect == Effect.ELECTRIC_SPARK, "Wrong kind of data for the %s effect", effect);
        }

        int datavalue = CraftEffect.getDataValue(effect, data);
        playEffect(loc, effect, datavalue);
    }

    @Override
    public boolean breakBlock(Block block) {
        Preconditions.checkArgument(block != null, "Block cannot be null");
        Preconditions.checkArgument(block.getWorld().equals(getWorld()), "Cannot break blocks across worlds");

        return getHandle().gameMode.destroyBlock(new net.minecraft.core.BlockPos(block.getX(), block.getY(), block.getZ()));
    }

    @Override
    public void sendBlockChange(Location loc, Material material, byte data) {
        if (getHandle().connection == null) return;

        net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket packet = new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(CraftLocation.toBlockPosition(loc), CraftMagicNumbers.getBlock(material, data));
        getHandle().connection.send(packet);
    }

    @Override
    public void sendBlockChange(Location loc, BlockData block) {
        if (getHandle().connection == null) return;

        net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket packet = new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(CraftLocation.toBlockPosition(loc), ((CraftBlockData) block).getState());
        getHandle().connection.send(packet);
    }

    @Override
    public void sendBlockChanges(Collection<BlockState> blocks) {
        Preconditions.checkArgument(blocks != null, "blocks must not be null");

        if (getHandle().connection == null || blocks.isEmpty()) {
            return;
        }

        Map<net.minecraft.core.SectionPos, ChunkSectionChanges> changes = new HashMap<>();
        for (BlockState state : blocks) {
            CraftBlockState cstate = (CraftBlockState) state;
            net.minecraft.core.BlockPos blockPosition = cstate.getPosition();

            // The coordinates of the chunk section in which the block is located, aka chunk x, y, and z
            net.minecraft.core.SectionPos sectionPosition = net.minecraft.core.SectionPos.of(blockPosition);

            // Push the block change position and block data to the final change map
            ChunkSectionChanges sectionChanges = changes.computeIfAbsent(sectionPosition, (ignore) -> new ChunkSectionChanges());

            sectionChanges.positions().add(net.minecraft.core.SectionPos.sectionRelativePos(blockPosition));
            sectionChanges.blockData().add(cstate.getHandle());
        }

        // Construct the packets using the data allocated above and send then to the players
        for (Map.Entry<net.minecraft.core.SectionPos, ChunkSectionChanges> entry : changes.entrySet()) {
            ChunkSectionChanges chunkChanges = entry.getValue();
            net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket packet = new net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket(entry.getKey(), chunkChanges.positions(), chunkChanges.blockData().toArray(net.minecraft.world.level.block.state.BlockState[]::new));
            getHandle().connection.send(packet);
        }
    }

    @Override
    public void sendBlockChanges(Collection<BlockState> blocks, boolean suppressLightUpdates) {
        this.sendBlockChanges(blocks);
    }

    private record ChunkSectionChanges(ShortSet positions, List<net.minecraft.world.level.block.state.BlockState> blockData) {

        public ChunkSectionChanges() {
            this(new ShortArraySet(), new ArrayList<>());
        }
    }

    @Override
    public void sendBlockDamage(Location loc, float progress) {
        this.sendBlockDamage(loc, progress, getEntityId());
    }

    @Override
    public void sendBlockDamage(Location loc, float progress, org.bukkit.entity.Entity source) {
        Preconditions.checkArgument(source != null, "source must not be null");
        this.sendBlockDamage(loc, progress, source.getEntityId());
    }

    @Override
    public void sendBlockDamage(Location loc, float progress, int sourceId) {
        Preconditions.checkArgument(loc != null, "loc must not be null");
        Preconditions.checkArgument(progress >= 0.0 && progress <= 1.0, "progress must be between 0.0 and 1.0 (inclusive)");

        if (getHandle().connection == null) return;

        int stage = (int) (9 * progress); // There are 0 - 9 damage states
        if (progress == 0.0F) {
            stage = -1; // The protocol states that any other value will reset the damage, which this API promises
        }

        net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket packet = new net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket(sourceId, CraftLocation.toBlockPosition(loc), stage);
        getHandle().connection.send(packet);
    }

    @Override
    public void sendSignChange(Location loc, String[] lines) {
        sendSignChange(loc, lines, DyeColor.BLACK);
    }

    @Override
    public void sendSignChange(Location loc, String[] lines, DyeColor dyeColor) {
        sendSignChange(loc, lines, dyeColor, false);
    }

    @Override
    public void sendSignChange(Location loc, String[] lines, DyeColor dyeColor, boolean hasGlowingText) {
        Preconditions.checkArgument(loc != null, "Location cannot be null");
        Preconditions.checkArgument(dyeColor != null, "DyeColor cannot be null");

        if (lines == null) {
            lines = new String[4];
        }
        Preconditions.checkArgument(lines.length >= 4, "Must have at least 4 lines (%s)", lines.length);

        if (getHandle().connection == null) return;

        net.minecraft.network.chat.Component[] components = CraftSign.sanitizeLines(lines);
        net.minecraft.world.level.block.entity.SignBlockEntity sign = new net.minecraft.world.level.block.entity.SignBlockEntity(CraftLocation.toBlockPosition(loc), net.minecraft.world.level.block.Blocks.OAK_SIGN.defaultBlockState());
        net.minecraft.world.level.block.entity.SignText text = sign.getFrontText();
        text = text.setColor(net.minecraft.world.item.DyeColor.byId(dyeColor.getWoolData()));
        text = text.setHasGlowingText(hasGlowingText);
        for (int i = 0; i < components.length; i++) {
            text = text.setMessage(i, components[i]);
        }
        sign.setText(text, true);

        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket(sign.getBlockPos(), sign.getType(), sign.getUpdateTag(getHandle().registryAccess())));
    }

    @Override
    public void sendBlockUpdate(@NotNull Location location, @NotNull TileState tileState) throws IllegalArgumentException {
        Preconditions.checkArgument(location != null, "Location can not be null");
        Preconditions.checkArgument(tileState != null, "TileState can not be null");

        if (getHandle().connection == null) return;

        CraftBlockEntityState<?> craftState = ((CraftBlockEntityState<?>) tileState);
        getHandle().connection.send(craftState.getUpdatePacket(location));
    }

    @Override
    public void sendEquipmentChange(LivingEntity entity, EquipmentSlot slot, ItemStack item) {
        this.sendEquipmentChange(entity, Map.of(slot, item));
    }

    @Override
    public void sendEquipmentChange(LivingEntity entity, Map<EquipmentSlot, ItemStack> items) {
        Preconditions.checkArgument(entity != null, "net.minecraft.world.entity.Entity cannot be null");
        Preconditions.checkArgument(items != null, "items cannot be null");

        if (getHandle().connection == null) {
            return;
        }

        List<Pair<net.minecraft.world.entity.EquipmentSlot, net.minecraft.world.item.ItemStack>> equipment = new ArrayList<>(items.size());
        for (Map.Entry<EquipmentSlot, ItemStack> entry : items.entrySet()) {
            EquipmentSlot slot = entry.getKey();
            Preconditions.checkArgument(slot != null, "Cannot set null EquipmentSlot");

            equipment.add(new Pair<>(CraftEquipmentSlot.getNMS(slot), CraftItemStack.asNMSCopy(entry.getValue())));
        }

        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket(entity.getEntityId(), equipment));
    }

    @Override
    public void sendPotionEffectChange(LivingEntity entity, PotionEffect effect) {
        Preconditions.checkArgument(entity != null, "net.minecraft.world.entity.Entity cannot be null");
        Preconditions.checkArgument(effect != null, "Effect cannot be null");

        if (getHandle().connection == null) {
            return;
        }

        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket(entity.getEntityId(), CraftPotionUtil.fromBukkit(effect), true));
    }

    @Override
    public void sendPotionEffectChangeRemove(LivingEntity entity, PotionEffectType type) {
        Preconditions.checkArgument(entity != null, "net.minecraft.world.entity.Entity cannot be null");
        Preconditions.checkArgument(type != null, "Type cannot be null");

        if (getHandle().connection == null) {
            return;
        }

        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket(entity.getEntityId(), CraftPotionEffectType.bukkitToMinecraftHolder(type)));
    }

    @Override
    public WorldBorder getWorldBorder() {
        return clientWorldBorder;
    }

    @Override
    public void setWorldBorder(WorldBorder border) {
        CraftWorldBorder craftBorder = (CraftWorldBorder) border;

        if (border != null && !craftBorder.isVirtual() && !craftBorder.getWorld().equals(getWorld())) {
            throw new UnsupportedOperationException("Cannot set player world border to that of another world");
        }

        // Nullify the old client-sided world border listeners so that calls to it will not affect this player
        if (clientWorldBorder != null) {
            this.clientWorldBorder.getHandle().removeListener(clientWorldBorderListener);
        }

        net.minecraft.world.level.border.WorldBorder newWorldBorder;
        if (craftBorder == null || !craftBorder.isVirtual()) {
            this.clientWorldBorder = null;
            newWorldBorder = ((CraftWorldBorder) getWorld().getWorldBorder()).getHandle();
        } else {
            this.clientWorldBorder = craftBorder;
            this.clientWorldBorder.getHandle().addListener(clientWorldBorderListener);
            newWorldBorder = clientWorldBorder.getHandle();
        }

        // Send all world border update packets to the player
        net.minecraft.server.network.ServerGamePacketListenerImpl connection = getHandle().connection;
        connection.send(new net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket(newWorldBorder));
        connection.send(new net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket(newWorldBorder));
        connection.send(new net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket(newWorldBorder));
        connection.send(new net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket(newWorldBorder));
        connection.send(new net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket(newWorldBorder));
    }

    private net.minecraft.world.level.border.BorderChangeListener createWorldBorderListener() {
        return new net.minecraft.world.level.border.BorderChangeListener() {
            @Override
            public void onBorderSizeSet(net.minecraft.world.level.border.WorldBorder border, double size) {
                getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket(border));
            }

            @Override
            public void onBorderSizeLerping(net.minecraft.world.level.border.WorldBorder border, double size, double newSize, long time) {
                getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket(border));
            }

            @Override
            public void onBorderCenterSet(net.minecraft.world.level.border.WorldBorder border, double x, double z) {
                getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket(border));
            }

            @Override
            public void onBorderSetWarningTime(net.minecraft.world.level.border.WorldBorder border, int warningTime) {
                getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket(border));
            }

            @Override
            public void onBorderSetWarningBlocks(net.minecraft.world.level.border.WorldBorder border, int warningBlocks) {
                getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket(border));
            }

            @Override
            public void onBorderSetDamagePerBlock(net.minecraft.world.level.border.WorldBorder border, double damage) {} // NO OP

            @Override
            public void onBorderSetDamageSafeZOne(net.minecraft.world.level.border.WorldBorder border, double blocks) {} // NO OP
        };
    }

    public boolean hasClientWorldBorder() {
        return clientWorldBorder != null;
    }

    @Override
    public void sendMap(MapView map) {
        if (getHandle().connection == null) return;

        RenderData data = ((CraftMapView) map).render(this);
        Collection<net.minecraft.world.level.saveddata.maps.MapDecoration> icons = new ArrayList<net.minecraft.world.level.saveddata.maps.MapDecoration>();
        for (MapCursor cursor : data.cursors) {
            if (cursor.isVisible()) {
                icons.add(new net.minecraft.world.level.saveddata.maps.MapDecoration(CraftMapCursor.CraftType.bukkitToMinecraftHolder(cursor.getType()), cursor.getX(), cursor.getY(), cursor.getDirection(), CraftChatMessage.fromStringOrOptional(cursor.getCaption())));
            }
        }

        net.minecraft.network.protocol.game.ClientboundMapItemDataPacket packet = new net.minecraft.network.protocol.game.ClientboundMapItemDataPacket(new net.minecraft.world.level.saveddata.maps.MapId(map.getId()), map.getScale().getValue(), map.isLocked(), icons, new net.minecraft.world.level.saveddata.maps.MapItemSavedData.MapPatch(0, 0, 128, 128, data.buffer));
        getHandle().connection.send(packet);
    }

    @Override
    public void sendHurtAnimation(float yaw) {
        if (getHandle().connection == null) {
            return;
        }

        /*
         * Vanilla degrees state that 0 = left, 90 = front, 180 = right, and 270 = behind.
         * This makes no sense. We'll add 90 to it so that 0 = front, clockwise from there.
         */
        float actualYaw = yaw + 90;
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket(getEntityId(), actualYaw));
    }

    @Override
    public void sendLinks(ServerLinks links) {
        if (getHandle().connection == null) {
            return;
        }
        Preconditions.checkArgument(links != null, "links cannot be null");

        net.minecraft.server.ServerLinks nms = ((CraftServerLinks) links).getServerLinks();
        getHandle().connection.send(new net.minecraft.network.protocol.common.ClientboundServerLinksPacket(nms.untrust()));
    }

    @Override
    public void addCustomChatCompletions(Collection<String> completions) {
        this.sendCustomChatCompletionPacket(completions, net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket.Action.ADD);
    }

    @Override
    public void removeCustomChatCompletions(Collection<String> completions) {
        this.sendCustomChatCompletionPacket(completions, net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket.Action.REMOVE);
    }

    @Override
    public void setCustomChatCompletions(Collection<String> completions) {
        this.sendCustomChatCompletionPacket(completions, net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket.Action.SET);
    }

    private void sendCustomChatCompletionPacket(Collection<String> completions, net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket.Action action) {
        if (getHandle().connection == null) return;

        net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket packet = new net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket(action, new ArrayList<>(completions));
        getHandle().connection.send(packet);
    }

    @Override
    public void setRotation(float yaw, float pitch) {
        throw new UnsupportedOperationException("Cannot set rotation of players. Consider teleporting instead.");
    }

    @Override
    public boolean teleport(Location location, PlayerTeleportEvent.TeleportCause cause) {
        Preconditions.checkArgument(location != null, "location");
        Preconditions.checkArgument(location.getWorld() != null, "location.world");
        location.checkFinite();

        net.minecraft.server.level.ServerPlayer entity = getHandle();

        if (getHealth() == 0 || entity.isRemoved()) {
            return false;
        }

        if (entity.connection == null) {
            return false;
        }

        if (entity.isVehicle()) {
            return false;
        }

        // From = Players current Location
        Location from = this.getLocation();
        // To = Players new Location if Teleport is Successful
        Location to = location;
        // Create & Call the Teleport Event.
        PlayerTeleportEvent event = new PlayerTeleportEvent(this, from, to, cause);
        server.getPluginManager().callEvent(event);

        // Return False to inform the Plugin that the Teleport was unsuccessful/cancelled.
        if (event.isCancelled()) {
            return false;
        }

        // If this player is riding another entity, we must dismount before teleporting.
        entity.stopRiding();

        // SPIGOT-5509: Wakeup, similar to riding
        if (this.isSleeping()) {
            this.wakeup(false);
        }

        // Update the From Location
        from = event.getFrom();
        // Grab the new To Location dependent on whether the event was cancelled.
        to = event.getTo();
        // Grab the To and From World Handles.
        net.minecraft.server.level.ServerLevel fromWorld = ((CraftWorld) from.getWorld()).getHandle();
        net.minecraft.server.level.ServerLevel toWorld = ((CraftWorld) to.getWorld()).getHandle();

        // Close any foreign inventory
        if (getHandle().containerMenu != getHandle().inventoryMenu) {
            getHandle().closeContainer();
        }

        // Check if the fromWorld and toWorld are the same.
        if (fromWorld == toWorld) {
            entity.connection.teleport(to);
        } else {
            entity.portalProcess = null; // SPIGOT-7785: there is no need to carry this over as it contains the old world/location and we might run into trouble if there is a portal in the same spot in both worlds
            // The respawn reason should never be used if the passed location is non null.
            server.getHandle().respawn(entity, true, net.minecraft.world.entity.Entity.RemovalReason.CHANGED_DIMENSION, null, to);
        }
        return true;
    }

    @Override
    public void setSneaking(boolean sneak) {
        getHandle().setShiftKeyDown(sneak);
    }

    @Override
    public boolean isSneaking() {
        return getHandle().isShiftKeyDown();
    }

    @Override
    public boolean isSprinting() {
        return getHandle().isSprinting();
    }

    @Override
    public void setSprinting(boolean sprinting) {
        getHandle().setSprinting(sprinting);
    }

    @Override
    public void loadData() {
        server.getHandle().playerIo.load(getHandle());
    }

    @Override
    public void saveData() {
        server.getHandle().playerIo.save(getHandle());
    }

    @Deprecated
    @Override
    public void updateInventory() {
        getHandle().containerMenu.sendAllDataToRemote();
    }

    @Override
    public void setSleepingIgnored(boolean isSleeping) {
        getHandle().fauxSleeping = isSleeping;
        ((CraftWorld) getWorld()).getHandle().updateSleepingPlayerList();
    }

    @Override
    public boolean isSleepingIgnored() {
        return getHandle().fauxSleeping;
    }

    @Override
    public Location getBedSpawnLocation() {
        return getRespawnLocation();
    }

    @Override
    public Location getRespawnLocation() {
        net.minecraft.server.level.ServerLevel world = getHandle().server.getLevel(getHandle().getRespawnDimension());
        net.minecraft.core.BlockPos bed = getHandle().getRespawnPosition();

        if (world != null && bed != null) {
            Optional<net.minecraft.server.level.ServerPlayer.RespawnPosAngle> spawnLoc = net.minecraft.server.level.ServerPlayer.findRespawnAndUseSpawnBlock(world, bed, getHandle().getRespawnAngle(), getHandle().isRespawnForced(), true);
            if (spawnLoc.isPresent()) {
                net.minecraft.server.level.ServerPlayer.RespawnPosAngle vec = spawnLoc.get();
                return CraftLocation.toBukkit(vec.position(), ((EthyleneLevel) world).getWorld(), vec.yaw(), 0);
            }
        }
        return null;
    }

    @Override
    public void setBedSpawnLocation(Location location) {
        setBedSpawnLocation(location, false);
    }

    @Override
    public void setRespawnLocation(Location location) {
        setRespawnLocation(location, false);
    }

    @Override
    public void setBedSpawnLocation(Location location, boolean override) {
        setRespawnLocation(location, override);
    }

    @Override
    public void setRespawnLocation(Location location, boolean override) {
        if (location == null) {
            ((EthyleneServerPlayer) getHandle()).setRespawnPosition(null, null, 0.0F, override, false, PlayerSpawnChangeEvent.Cause.PLUGIN);
        } else {
            ((EthyleneServerPlayer) getHandle()).setRespawnPosition(((CraftWorld) location.getWorld()).getHandle().dimension(), CraftLocation.toBlockPosition(location), location.getYaw(), override, false, PlayerSpawnChangeEvent.Cause.PLUGIN);
        }
    }

    @Override
    public Location getBedLocation() {
        Preconditions.checkState(isSleeping(), "Not sleeping");

        net.minecraft.core.BlockPos bed = getHandle().getRespawnPosition();
        return CraftLocation.toBukkit(bed, getWorld());
    }

    @Override
    public boolean hasDiscoveredRecipe(NamespacedKey recipe) {
        Preconditions.checkArgument(recipe != null, "recipe cannot be null");
        return getHandle().getRecipeBook().contains(CraftNamespacedKey.toMinecraft(recipe));
    }

    @Override
    public Set<NamespacedKey> getDiscoveredRecipes() {
        ImmutableSet.Builder<NamespacedKey> bukkitRecipeKeys = ImmutableSet.builder();
        getHandle().getRecipeBook().known.forEach(key -> bukkitRecipeKeys.add(CraftNamespacedKey.fromMinecraft(key)));
        return bukkitRecipeKeys.build();
    }

    @Override
    public void incrementStatistic(Statistic statistic) {
        CraftStatistic.incrementStatistic(getHandle().getStats(), statistic, getHandle());
    }

    @Override
    public void decrementStatistic(Statistic statistic) {
        CraftStatistic.decrementStatistic(getHandle().getStats(), statistic, getHandle());
    }

    @Override
    public int getStatistic(Statistic statistic) {
        return CraftStatistic.getStatistic(getHandle().getStats(), statistic);
    }

    @Override
    public void incrementStatistic(Statistic statistic, int amount) {
        CraftStatistic.incrementStatistic(getHandle().getStats(), statistic, amount, getHandle());
    }

    @Override
    public void decrementStatistic(Statistic statistic, int amount) {
        CraftStatistic.decrementStatistic(getHandle().getStats(), statistic, amount, getHandle());
    }

    @Override
    public void setStatistic(Statistic statistic, int newValue) {
        CraftStatistic.setStatistic(getHandle().getStats(), statistic, newValue, getHandle());
    }

    @Override
    public void incrementStatistic(Statistic statistic, Material material) {
        CraftStatistic.incrementStatistic(getHandle().getStats(), statistic, material, getHandle());
    }

    @Override
    public void decrementStatistic(Statistic statistic, Material material) {
        CraftStatistic.decrementStatistic(getHandle().getStats(), statistic, material, getHandle());
    }

    @Override
    public int getStatistic(Statistic statistic, Material material) {
        return CraftStatistic.getStatistic(getHandle().getStats(), statistic, material);
    }

    @Override
    public void incrementStatistic(Statistic statistic, Material material, int amount) {
        CraftStatistic.incrementStatistic(getHandle().getStats(), statistic, material, amount, getHandle());
    }

    @Override
    public void decrementStatistic(Statistic statistic, Material material, int amount) {
        CraftStatistic.decrementStatistic(getHandle().getStats(), statistic, material, amount, getHandle());
    }

    @Override
    public void setStatistic(Statistic statistic, Material material, int newValue) {
        CraftStatistic.setStatistic(getHandle().getStats(), statistic, material, newValue, getHandle());
    }

    @Override
    public void incrementStatistic(Statistic statistic, EntityType entityType) {
        CraftStatistic.incrementStatistic(getHandle().getStats(), statistic, entityType, getHandle());
    }

    @Override
    public void decrementStatistic(Statistic statistic, EntityType entityType) {
        CraftStatistic.decrementStatistic(getHandle().getStats(), statistic, entityType, getHandle());
    }

    @Override
    public int getStatistic(Statistic statistic, EntityType entityType) {
        return CraftStatistic.getStatistic(getHandle().getStats(), statistic, entityType);
    }

    @Override
    public void incrementStatistic(Statistic statistic, EntityType entityType, int amount) {
        CraftStatistic.incrementStatistic(getHandle().getStats(), statistic, entityType, amount, getHandle());
    }

    @Override
    public void decrementStatistic(Statistic statistic, EntityType entityType, int amount) {
        CraftStatistic.decrementStatistic(getHandle().getStats(), statistic, entityType, amount, getHandle());
    }

    @Override
    public void setStatistic(Statistic statistic, EntityType entityType, int newValue) {
        CraftStatistic.setStatistic(getHandle().getStats(), statistic, entityType, newValue, getHandle());
    }

    @Override
    public void setPlayerTime(long time, boolean relative) {
        ((EthyleneServerPlayer) getHandle()).timeOffset(time);
        ((EthyleneServerPlayer) getHandle()).relativeTime(relative);
    }

    @Override
    public long getPlayerTimeOffset() {
        return ((EthyleneServerPlayer) getHandle()).timeOffset();
    }

    @Override
    public long getPlayerTime() {
        return ((EthyleneServerPlayer) getHandle()).getPlayerTime();
    }

    @Override
    public boolean isPlayerTimeRelative() {
        return ((EthyleneServerPlayer) getHandle()).relativeTime();
    }

    @Override
    public void resetPlayerTime() {
        setPlayerTime(0, true);
    }

    @Override
    public void setPlayerWeather(WeatherType type) {
        ((EthyleneServerPlayer) getHandle()).setPlayerWeather(type, true);
    }

    @Override
    public WeatherType getPlayerWeather() {
        return ((EthyleneServerPlayer) getHandle()).getPlayerWeather();
    }

    @Override
    public int getExpCooldown() {
        return getHandle().takeXpDelay;
    }

    @Override
    public void setExpCooldown(int ticks) {
        getHandle().takeXpDelay = CraftEventFactory.callPlayerXpCooldownEvent(this.getHandle(), ticks, PlayerExpCooldownChangeEvent.ChangeReason.PLUGIN).getNewCooldown();
    }

    @Override
    public void resetPlayerWeather() {
        ((EthyleneServerPlayer) getHandle()).resetPlayerWeather();
    }

    @Override
    public boolean isBanned() {
        return ((ProfileBanList) server.getBanList(BanList.Type.PROFILE)).isBanned(getPlayerProfile());
    }

    @Override
    public BanEntry<PlayerProfile> ban(String reason, Date expires, String source) {
        return this.ban(reason, expires, source, true);
    }

    @Override
    public BanEntry<PlayerProfile> ban(String reason, Instant expires, String source) {
        return ban(reason, expires != null ? Date.from(expires) : null, source);
    }

    @Override
    public BanEntry<PlayerProfile> ban(String reason, Duration duration, String source) {
        return ban(reason, duration != null ? Instant.now().plus(duration) : null, source);
    }

    @Override
    public BanEntry<PlayerProfile> ban(String reason, Date expires, String source, boolean kickPlayer) {
        BanEntry<PlayerProfile> banEntry = ((ProfileBanList) server.getBanList(BanList.Type.PROFILE)).addBan(getPlayerProfile(), reason, expires, source);
        if (kickPlayer) {
            this.kickPlayer(reason);
        }
        return banEntry;
    }

    @Override
    public BanEntry<PlayerProfile> ban(String reason, Instant instant, String source, boolean kickPlayer) {
        return ban(reason, instant != null ? Date.from(instant) : null, source, kickPlayer);
    }

    @Override
    public BanEntry<PlayerProfile> ban(String reason, Duration duration, String source, boolean kickPlayer) {
        return ban(reason, duration != null ? Instant.now().plus(duration) : null, source, kickPlayer);
    }

    @Override
    public BanEntry<InetAddress> banIp(String reason, Date expires, String source, boolean kickPlayer) {
        Preconditions.checkArgument(getAddress() != null, "The Address of this Player is null");
        BanEntry<InetAddress> banEntry = ((IpBanList) server.getBanList(BanList.Type.IP)).addBan(getAddress().getAddress(), reason, expires, source);
        if (kickPlayer) {
            this.kickPlayer(reason);
        }
        return banEntry;
    }

    @Override
    public BanEntry<InetAddress> banIp(String reason, Instant instant, String source, boolean kickPlayer) {
        return banIp(reason, instant != null ? Date.from(instant) : null, source, kickPlayer);
    }

    @Override
    public BanEntry<InetAddress> banIp(String reason, Duration duration, String source, boolean kickPlayer) {
        return banIp(reason, duration != null ? Instant.now().plus(duration) : null, source, kickPlayer);
    }

    @Override
    public boolean isWhitelisted() {
        return server.getHandle().getWhiteList().isWhiteListed(getProfile());
    }

    @Override
    public void setWhitelisted(boolean value) {
        if (value) {
            server.getHandle().getWhiteList().add(new net.minecraft.server.players.UserWhiteListEntry(getProfile()));
        } else {
            server.getHandle().getWhiteList().remove(getProfile());
        }
    }

    @Override
    public void setGameMode(GameMode mode) {
        Preconditions.checkArgument(mode != null, "GameMode cannot be null");
        if (getHandle().connection == null) return;

        getHandle().setGameMode(net.minecraft.world.level.GameType.byId(mode.getValue()));
    }

    @Override
    public GameMode getGameMode() {
        return GameMode.getByValue(getHandle().gameMode.getGameModeForPlayer().getId());
    }

    @Override
    public GameMode getPreviousGameMode() {
        net.minecraft.world.level.GameType previousGameMode = getHandle().gameMode.getPreviousGameModeForPlayer();

        return (previousGameMode == null) ? null : GameMode.getByValue(previousGameMode.getId());
    }

    @Override
    public void giveExp(int exp) {
        getHandle().giveExperiencePoints(exp);
    }

    @Override
    public void giveExpLevels(int levels) {
        getHandle().giveExperienceLevels(levels);
    }

    @Override
    public float getExp() {
        return getHandle().experienceProgress;
    }

    @Override
    public void setExp(float exp) {
        Preconditions.checkArgument(exp >= 0.0 && exp <= 1.0, "Experience progress must be between 0.0 and 1.0 (%s)", exp);
        getHandle().experienceProgress = exp;
        getHandle().lastSentExp = -1;
    }

    @Override
    public int getLevel() {
        return getHandle().experienceLevel;
    }

    @Override
    public void setLevel(int level) {
        Preconditions.checkArgument(level >= 0, "Experience level must not be negative (%s)", level);
        getHandle().experienceLevel = level;
        getHandle().lastSentExp = -1;
    }

    @Override
    public int getTotalExperience() {
        return getHandle().totalExperience;
    }

    @Override
    public void setTotalExperience(int exp) {
        Preconditions.checkArgument(exp >= 0, "Total experience points must not be negative (%s)", exp);
        getHandle().totalExperience = exp;
    }

    @Override
    public void sendExperienceChange(float progress) {
        sendExperienceChange(progress, getLevel());
    }

    @Override
    public void sendExperienceChange(float progress, int level) {
        Preconditions.checkArgument(progress >= 0.0 && progress <= 1.0, "Experience progress must be between 0.0 and 1.0 (%s)", progress);
        Preconditions.checkArgument(level >= 0, "Experience level must not be negative (%s)", level);

        if (getHandle().connection == null) {
            return;
        }

        net.minecraft.network.protocol.game.ClientboundSetExperiencePacket packet = new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(progress, getTotalExperience(), level);
        getHandle().connection.send(packet);
    }

    @Nullable
    private static WeakReference<Plugin> getPluginWeakReference(@Nullable Plugin plugin) {
        return (plugin == null) ? null : pluginWeakReferences.computeIfAbsent(plugin, WeakReference::new);
    }

    @Override
    @Deprecated
    public void hidePlayer(Player player) {
        hideEntity0(null, player);
    }

    @Override
    public void hidePlayer(Plugin plugin, Player player) {
        hideEntity(plugin, player);
    }

    @Override
    public void hideEntity(Plugin plugin, org.bukkit.entity.Entity entity) {
        Preconditions.checkArgument(plugin != null, "Plugin cannot be null");
        Preconditions.checkArgument(plugin.isEnabled(), "Plugin (%s) cannot be disabled", plugin.getName());

        hideEntity0(plugin, entity);
    }

    private void hideEntity0(@Nullable Plugin plugin, org.bukkit.entity.Entity entity) {
        Preconditions.checkArgument(entity != null, "net.minecraft.world.entity.Entity hidden cannot be null");
        if (getHandle().connection == null) return;
        if (equals(entity)) return;

        boolean shouldHide;
        if (entity.isVisibleByDefault()) {
            shouldHide = addInvertedVisibility(plugin, entity);
        } else {
            shouldHide = removeInvertedVisibility(plugin, entity);
        }

        if (shouldHide) {
            untrackAndHideEntity(entity);
        }
    }

    private boolean addInvertedVisibility(@Nullable Plugin plugin, org.bukkit.entity.Entity entity) {
        Set<WeakReference<Plugin>> invertedPlugins = invertedVisibilityEntities.get(entity.getUniqueId());
        if (invertedPlugins != null) {
            // Some plugins are already inverting the entity. Just mark that this
            // plugin wants the entity inverted too and end.
            invertedPlugins.add(getPluginWeakReference(plugin));
            return false;
        }
        invertedPlugins = new HashSet<>();
        invertedPlugins.add(getPluginWeakReference(plugin));
        invertedVisibilityEntities.put(entity.getUniqueId(), invertedPlugins);

        return true;
    }

    private void untrackAndHideEntity(org.bukkit.entity.Entity entity) {
        // Remove this entity from the hidden player's EntityTrackerEntry
        net.minecraft.server.level.ChunkMap tracker = ((net.minecraft.server.level.ServerLevel) getHandle().level()).getChunkSource().chunkMap;
        net.minecraft.world.entity.Entity other = ((CraftEntity) entity).getHandle();
        net.minecraft.server.level.ChunkMap.TrackedEntity entry = tracker.entityMap.get(other.getId());
        if (entry != null) {
            entry.removePlayer(getHandle());
        }

        // Remove the hidden entity from this player user list, if they're on it
        if (other instanceof net.minecraft.server.level.ServerPlayer) {
            net.minecraft.server.level.ServerPlayer otherPlayer = (net.minecraft.server.level.ServerPlayer) other;
            if (otherPlayer.sentListPacket) {
                getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket(List.of(otherPlayer.getUUID())));
            }
        }

        server.getPluginManager().callEvent(new PlayerHideEntityEvent(this, entity));
    }

    void resetAndHideEntity(org.bukkit.entity.Entity entity) {
        // SPIGOT-7312: Can't show/hide self
        if (equals(entity)) {
            return;
        }

        if (invertedVisibilityEntities.remove(entity.getUniqueId()) == null) {
            untrackAndHideEntity(entity);
        }
    }

    @Override
    @Deprecated
    public void showPlayer(Player player) {
        showEntity0(null, player);
    }

    @Override
    public void showPlayer(Plugin plugin, Player player) {
        showEntity(plugin, player);
    }

    @Override
    public void showEntity(Plugin plugin, org.bukkit.entity.Entity entity) {
        Preconditions.checkArgument(plugin != null, "Plugin cannot be null");
        // Don't require that plugin be enabled. A plugin must be allowed to call
        // showPlayer during its onDisable() method.
        showEntity0(plugin, entity);
    }

    private void showEntity0(@Nullable Plugin plugin, org.bukkit.entity.Entity entity) {
        Preconditions.checkArgument(entity != null, "net.minecraft.world.entity.Entity show cannot be null");
        if (getHandle().connection == null) return;
        if (equals(entity)) return;

        boolean shouldShow;
        if (entity.isVisibleByDefault()) {
            shouldShow = removeInvertedVisibility(plugin, entity);
        } else {
            shouldShow = addInvertedVisibility(plugin, entity);
        }

        if (shouldShow) {
            trackAndShowEntity(entity);
        }
    }

    private boolean removeInvertedVisibility(@Nullable Plugin plugin, org.bukkit.entity.Entity entity) {
        Set<WeakReference<Plugin>> invertedPlugins = invertedVisibilityEntities.get(entity.getUniqueId());
        if (invertedPlugins == null) {
            return false; // net.minecraft.world.entity.Entity isn't inverted
        }
        invertedPlugins.remove(getPluginWeakReference(plugin));
        if (!invertedPlugins.isEmpty()) {
            return false; // Some other plugins still want the entity inverted
        }
        invertedVisibilityEntities.remove(entity.getUniqueId());

        return true;
    }

    private void trackAndShowEntity(org.bukkit.entity.Entity entity) {
        net.minecraft.server.level.ChunkMap tracker = ((net.minecraft.server.level.ServerLevel) getHandle().level()).getChunkSource().chunkMap;
        net.minecraft.world.entity.Entity other = ((CraftEntity) entity).getHandle();

        if (other instanceof net.minecraft.server.level.ServerPlayer) {
            net.minecraft.server.level.ServerPlayer otherPlayer = (net.minecraft.server.level.ServerPlayer) other;
            getHandle().connection.send(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(otherPlayer)));
        }

        net.minecraft.server.level.ChunkMap.TrackedEntity entry = tracker.entityMap.get(other.getId());
        if (entry != null && !entry.seenBy.contains(getHandle().connection)) {
            entry.updatePlayer(getHandle());
        }

        server.getPluginManager().callEvent(new PlayerShowEntityEvent(this, entity));
    }

    void resetAndShowEntity(org.bukkit.entity.Entity entity) {
        // SPIGOT-7312: Can't show/hide self
        if (equals(entity)) {
            return;
        }

        if (invertedVisibilityEntities.remove(entity.getUniqueId()) == null) {
            trackAndShowEntity(entity);
        }
    }

    public void onEntityRemove(net.minecraft.world.entity.Entity entity) {
        invertedVisibilityEntities.remove(entity.getUUID());
    }

    @Override
    public boolean canSee(Player player) {
        return canSee((org.bukkit.entity.Entity) player);
    }

    @Override
    public boolean canSee(org.bukkit.entity.Entity entity) {
        return equals(entity) || entity.isVisibleByDefault() ^ invertedVisibilityEntities.containsKey(entity.getUniqueId()); // SPIGOT-7312: Can always see self
    }

    public boolean canSeePlayer(UUID uuid) {
        org.bukkit.entity.Entity entity = getServer().getPlayer(uuid);

        return (entity != null) ? canSee(entity) : false; // If we can't find it, we can't see it
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();

        result.put("name", getName());

        return result;
    }

    @Override
    public Player getPlayer() {
        return this;
    }

    @Override
    public net.minecraft.server.level.ServerPlayer getHandle() {
        return (net.minecraft.server.level.ServerPlayer) entity;
    }

    public void setHandle(final net.minecraft.server.level.ServerPlayer entity) {
        super.setHandle(entity);
    }

    @Override
    public String toString() {
        return "CraftPlayer{" + "name=" + getName() + '}';
    }

    @Override
    public int hashCode() {
        if (hash == 0 || hash == 485) {
            hash = 97 * 5 + (this.getUniqueId() != null ? this.getUniqueId().hashCode() : 0);
        }
        return hash;
    }

    @Override
    public long getFirstPlayed() {
        return firstPlayed;
    }

    @Override
    public long getLastPlayed() {
        return lastPlayed;
    }

    @Override
    public boolean hasPlayedBefore() {
        return hasPlayedBefore;
    }

    public void setFirstPlayed(long firstPlayed) {
        this.firstPlayed = firstPlayed;
    }

    public void readExtraData(net.minecraft.nbt.CompoundTag nbttagcompound) {
        hasPlayedBefore = true;
        if (nbttagcompound.contains("bukkit")) {
            net.minecraft.nbt.CompoundTag data = nbttagcompound.getCompound("bukkit");

            if (data.contains("firstPlayed")) {
                firstPlayed = data.getLong("firstPlayed");
                lastPlayed = data.getLong("lastPlayed");
            }

            if (data.contains("newExp")) {
                net.minecraft.server.level.ServerPlayer handle = getHandle();
                ((EthyleneServerPlayer) handle).newExp(data.getInt("newExp"));
                ((EthyleneServerPlayer) handle).newTotalExp(data.getInt("newTotalExp"));
                ((EthyleneServerPlayer) handle).newLevel(data.getInt("newLevel"));
                ((EthyleneLivingEntity) handle).expToDrop(data.getInt("expToDrop"));
                ((EthyleneServerPlayer) handle).keepLevel(data.getBoolean("keepLevel"));
            }
        }
    }

    public void setExtraData(net.minecraft.nbt.CompoundTag nbttagcompound) {
        if (!nbttagcompound.contains("bukkit")) {
            nbttagcompound.put("bukkit", new net.minecraft.nbt.CompoundTag());
        }

        net.minecraft.nbt.CompoundTag data = nbttagcompound.getCompound("bukkit");
        net.minecraft.server.level.ServerPlayer handle = getHandle();
        data.putInt("newExp", ((EthyleneServerPlayer) handle).newExp());
        data.putInt("newTotalExp", ((EthyleneServerPlayer) handle).newTotalExp());
        data.putInt("newLevel", ((EthyleneServerPlayer) handle).newLevel());
        data.putInt("expToDrop", ((EthyleneLivingEntity) handle).expToDrop());
        data.putBoolean("keepLevel", ((EthyleneServerPlayer) handle).keepLevel());
        data.putLong("firstPlayed", getFirstPlayed());
        data.putLong("lastPlayed", System.currentTimeMillis());
        data.putString("lastKnownName", handle.getScoreboardName());
    }

    @Override
    public boolean beginConversation(Conversation conversation) {
        return conversationTracker.beginConversation(conversation);
    }

    @Override
    public void abandonConversation(Conversation conversation) {
        conversationTracker.abandonConversation(conversation, new ConversationAbandonedEvent(conversation, new ManuallyAbandonedConversationCanceller()));
    }

    @Override
    public void abandonConversation(Conversation conversation, ConversationAbandonedEvent details) {
        conversationTracker.abandonConversation(conversation, details);
    }

    @Override
    public void acceptConversationInput(String input) {
        conversationTracker.acceptConversationInput(input);
    }

    @Override
    public boolean isConversing() {
        return conversationTracker.isConversing();
    }

    @Override
    public void sendPluginMessage(Plugin source, String channel, byte[] message) {
        StandardMessenger.validatePluginMessage(server.getMessenger(), source, channel, message);
        if (getHandle().connection == null) return;

        if (channels.contains(channel)) {
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.parse(StandardMessenger.validateAndCorrectChannel(channel));
            sendCustomPayload(id, message);
        }
    }

    private void sendCustomPayload(net.minecraft.resources.ResourceLocation id, byte[] message) {
        net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket packet = new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(new net.minecraft.network.protocol.common.custom.DiscardedPayload(id, Unpooled.wrappedBuffer(message)));
        getHandle().connection.send(packet);
    }

    @Override
    public void setTexturePack(String url) {
        setResourcePack(url);
    }

    @Override
    public void setResourcePack(String url) {
        setResourcePack(url, null);
    }

    @Override
    public void setResourcePack(String url, byte[] hash) {
        setResourcePack(url, hash, false);
    }

    @Override
    public void setResourcePack(String url, byte[] hash, String prompt) {
        setResourcePack(url, hash, prompt, false);
    }

    @Override
    public void setResourcePack(String url, byte[] hash, boolean force) {
        setResourcePack(url, hash, null, force);
    }

    @Override
    public void setResourcePack(String url, byte[] hash, String prompt, boolean force) {
        Preconditions.checkArgument(url != null, "Resource pack URL cannot be null");

        setResourcePack(UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8)), url, hash, prompt, force);
    }

    @Override
    public void setResourcePack(UUID id, String url, byte[] hash, String prompt, boolean force) {
        Preconditions.checkArgument(id != null, "Resource pack ID cannot be null");
        Preconditions.checkArgument(url != null, "Resource pack URL cannot be null");

        String hashStr = "";
        if (hash != null) {
            Preconditions.checkArgument(hash.length == 20, "Resource pack hash should be 20 bytes long but was %s", hash.length);
            hashStr = BaseEncoding.base16().lowerCase().encode(hash);
        }

        this.handlePushResourcePack(new net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket(id, url, hashStr, force, CraftChatMessage.fromStringOrOptional(prompt, true)), true);
    }

    @Override
    public void addResourcePack(UUID id, String url, byte[] hash, String prompt, boolean force) {
        Preconditions.checkArgument(url != null, "Resource pack URL cannot be null");

        String hashStr = "";
        if (hash != null) {
            Preconditions.checkArgument(hash.length == 20, "Resource pack hash should be 20 bytes long but was %s", hash.length);
            hashStr = BaseEncoding.base16().lowerCase().encode(hash);
        }

        this.handlePushResourcePack(new net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket(id, url, hashStr, force, CraftChatMessage.fromStringOrOptional(prompt, true)), false);
    }

    @Override
    public void removeResourcePack(UUID id) {
        Preconditions.checkArgument(id != null, "Resource pack id cannot be null");
        if (getHandle().connection == null) return;
        getHandle().connection.send(new net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket(Optional.of(id)));
    }

    @Override
    public void removeResourcePacks() {
        if (getHandle().connection == null) return;
        getHandle().connection.send(new net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket(Optional.empty()));
    }

    private void handlePushResourcePack(net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket resourcePackPushPacket, boolean resetBeforePush) {
        if (getHandle().connection == null) return;

        if (resetBeforePush) {
            this.removeResourcePacks();
        }
        getHandle().connection.send(resourcePackPushPacket);
    }

    public void addChannel(String channel) {
        Preconditions.checkState(channels.size() < 128, "Cannot register channel '%s'. Too many channels registered!", channel);
        channel = StandardMessenger.validateAndCorrectChannel(channel);
        if (channels.add(channel)) {
            server.getPluginManager().callEvent(new PlayerRegisterChannelEvent(this, channel));
        }
    }

    public void removeChannel(String channel) {
        channel = StandardMessenger.validateAndCorrectChannel(channel);
        if (channels.remove(channel)) {
            server.getPluginManager().callEvent(new PlayerUnregisterChannelEvent(this, channel));
        }
    }

    @Override
    public Set<String> getListeningPluginChannels() {
        return ImmutableSet.copyOf(channels);
    }

    public void sendSupportedChannels() {
        if (getHandle().connection == null) return;
        Set<String> listening = server.getMessenger().getIncomingChannels();

        if (!listening.isEmpty()) {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();

            for (String channel : listening) {
                try {
                    stream.write(channel.getBytes("UTF8"));
                    stream.write((byte) 0);
                } catch (IOException ex) {
                    Logger.getLogger(CraftPlayer.class.getName()).log(Level.SEVERE, "Could not send Plugin Channel REGISTER to " + getName(), ex);
                }
            }

            sendCustomPayload(net.minecraft.resources.ResourceLocation.withDefaultNamespace("register"), stream.toByteArray());
        }
    }

    @Override
    public void setMetadata(String metadataKey, MetadataValue newMetadataValue) {
        server.getPlayerMetadata().setMetadata(this, metadataKey, newMetadataValue);
    }

    @Override
    public List<MetadataValue> getMetadata(String metadataKey) {
        return server.getPlayerMetadata().getMetadata(this, metadataKey);
    }

    @Override
    public boolean hasMetadata(String metadataKey) {
        return server.getPlayerMetadata().hasMetadata(this, metadataKey);
    }

    @Override
    public void removeMetadata(String metadataKey, Plugin owningPlugin) {
        server.getPlayerMetadata().removeMetadata(this, metadataKey, owningPlugin);
    }

    @Override
    public boolean setWindowProperty(Property prop, int value) {
        net.minecraft.world.inventory.AbstractContainerMenu container = getHandle().containerMenu;
        if (((EthyleneAbstractContainerMenu) container).getBukkitView().getType() != prop.getType()) {
            return false;
        }
        container.setData(prop.getId(), value);
        return true;
    }

    public void disconnect(String reason) {
        conversationTracker.abandonAllConversations();
        perm.clearPermissions();
    }

    @Override
    public boolean isFlying() {
        return getHandle().getAbilities().flying;
    }

    @Override
    public void setFlying(boolean value) {
        if (!getAllowFlight()) {
            Preconditions.checkArgument(!value, "Player is not allowed to fly (check #getAllowFlight())");
        }

        getHandle().getAbilities().flying = value;
        getHandle().onUpdateAbilities();
    }

    @Override
    public boolean getAllowFlight() {
        return getHandle().getAbilities().mayfly;
    }

    @Override
    public void setAllowFlight(boolean value) {
        if (isFlying() && !value) {
            getHandle().getAbilities().flying = false;
        }

        getHandle().getAbilities().mayfly = value;
        getHandle().onUpdateAbilities();
    }

    @Override
    public int getNoDamageTicks() {
        if (getHandle().spawnInvulnerableTime > 0) {
            return Math.max(getHandle().spawnInvulnerableTime, getHandle().invulnerableTime);
        } else {
            return getHandle().invulnerableTime;
        }
    }

    @Override
    public void setNoDamageTicks(int ticks) {
        super.setNoDamageTicks(ticks);
        getHandle().spawnInvulnerableTime = ticks; // SPIGOT-5921: Update both for players, like the getter above
    }

    @Override
    public void setFlySpeed(float value) {
        validateSpeed(value);
        net.minecraft.server.level.ServerPlayer player = getHandle();
        player.getAbilities().flyingSpeed = value / 2f;
        player.onUpdateAbilities();

    }

    @Override
    public void setWalkSpeed(float value) {
        validateSpeed(value);
        net.minecraft.server.level.ServerPlayer player = getHandle();
        player.getAbilities().walkingSpeed = value / 2f;
        player.onUpdateAbilities();
        getHandle().getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(player.getAbilities().walkingSpeed); // SPIGOT-5833: combination of the two in 1.16+
    }

    @Override
    public float getFlySpeed() {
        return (float) getHandle().getAbilities().flyingSpeed * 2f;
    }

    @Override
    public float getWalkSpeed() {
        return getHandle().getAbilities().walkingSpeed * 2f;
    }

    private void validateSpeed(float value) {
        Preconditions.checkArgument(value <= 1f && value >= -1f, "Speed value (%s) need to be between -1f and 1f", value);
    }

    @Override
    public void setMaxHealth(double amount) {
        super.setMaxHealth(amount);
        this.health = Math.min(this.health, amount);
        getHandle().resetSentInfo();
    }

    @Override
    public void resetMaxHealth() {
        super.resetMaxHealth();
        getHandle().resetSentInfo();
    }

    @Override
    public CraftScoreboard getScoreboard() {
        return this.server.getScoreboardManager().getPlayerBoard(this);
    }

    @Override
    public void setScoreboard(Scoreboard scoreboard) {
        Preconditions.checkArgument(scoreboard != null, "Scoreboard cannot be null");
        Preconditions.checkState(getHandle().connection != null, "Cannot set scoreboard yet (invalid player connection)");

        this.server.getScoreboardManager().setPlayerBoard(this, scoreboard);
    }

    @Override
    public void setHealthScale(double value) {
        Preconditions.checkArgument(value > 0F, "Health value (%s) must be greater than 0", value);
        healthScale = value;
        scaledHealth = true;
        updateScaledHealth();
    }

    @Override
    public double getHealthScale() {
        return healthScale;
    }

    @Override
    public void setHealthScaled(boolean scale) {
        if (scaledHealth != (scaledHealth = scale)) {
            updateScaledHealth();
        }
    }

    @Override
    public boolean isHealthScaled() {
        return scaledHealth;
    }

    public float getScaledHealth() {
        return (float) (isHealthScaled() ? getHealth() * getHealthScale() / getMaxHealth() : getHealth());
    }

    @Override
    public double getHealth() {
        return health;
    }

    public void setRealHealth(double health) {
        this.health = health;
    }

    public void updateScaledHealth() {
        updateScaledHealth(true);
    }

    public void updateScaledHealth(boolean sendHealth) {
        net.minecraft.world.entity.ai.attributes.AttributeMap attributemapserver = getHandle().getAttributes();
        Collection<net.minecraft.world.entity.ai.attributes.AttributeInstance> set = attributemapserver.getSyncableAttributes();

        injectScaledMaxHealth(set, true);

        // SPIGOT-3813: Attributes before health
        if (getHandle().connection != null) {
            getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket(getHandle().getId(), set));
            if (sendHealth) {
                sendHealthUpdate();
            }
        }
        getHandle().getEntityData().set(net.minecraft.world.entity.LivingEntity.DATA_HEALTH_ID, (float) getScaledHealth());

        ((EthyleneServerPlayer) getHandle()).maxHealthCache(getMaxHealth());
    }

    @Override
    public void sendHealthUpdate(double health, int foodLevel, float saturation) {
        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket((float) health, foodLevel, saturation));
    }

    @Override
    public void sendHealthUpdate() {
        net.minecraft.world.food.FoodData foodData = getHandle().getFoodData();
        sendHealthUpdate(getScaledHealth(), foodData.getFoodLevel(), foodData.getSaturationLevel());
    }

    public void injectScaledMaxHealth(Collection<net.minecraft.world.entity.ai.attributes.AttributeInstance> collection, boolean force) {
        if (!scaledHealth && !force) {
            return;
        }
        for (net.minecraft.world.entity.ai.attributes.AttributeInstance genericInstance : collection) {
            if (genericInstance.getAttribute() == net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH) {
                collection.remove(genericInstance);
                break;
            }
        }
        net.minecraft.world.entity.ai.attributes.AttributeInstance dummy = new net.minecraft.world.entity.ai.attributes.AttributeInstance(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, (attribute) -> { });
        dummy.setBaseValue(scaledHealth ? healthScale : getMaxHealth());
        collection.add(dummy);
    }

    @Override
    public org.bukkit.entity.Entity getSpectatorTarget() {
        net.minecraft.world.entity.Entity followed = getHandle().getCamera();
        return followed == getHandle() ? null : ((EthyleneEntity) followed).getBukkitEntity();
    }

    @Override
    public void setSpectatorTarget(org.bukkit.entity.Entity entity) {
        Preconditions.checkArgument(getGameMode() == GameMode.SPECTATOR, "Player must be in spectator mode");
        getHandle().setCamera((entity == null) ? null : ((CraftEntity) entity).getHandle());
    }

    @Override
    public void sendTitle(String title, String subtitle) {
        sendTitle(title, subtitle, 10, 70, 20);
    }

    @Override
    public void sendTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket times = new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut);
        getHandle().connection.send(times);

        if (title != null) {
            net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket packetTitle = new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(CraftChatMessage.fromString(title)[0]);
            getHandle().connection.send(packetTitle);
        }

        if (subtitle != null) {
            net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket packetSubtitle = new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(CraftChatMessage.fromString(subtitle)[0]);
            getHandle().connection.send(packetSubtitle);
        }
    }

    @Override
    public void resetTitle() {
        net.minecraft.network.protocol.game.ClientboundClearTitlesPacket packetReset = new net.minecraft.network.protocol.game.ClientboundClearTitlesPacket(true);
        getHandle().connection.send(packetReset);
    }

    @Override
    public void spawnParticle(Particle particle, Location location, int count) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count);
    }

    @Override
    public void spawnParticle(Particle particle, double x, double y, double z, int count) {
        spawnParticle(particle, x, y, z, count, null);
    }

    @Override
    public <T> void spawnParticle(Particle particle, Location location, int count, T data) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, data);
    }

    @Override
    public <T> void spawnParticle(Particle particle, double x, double y, double z, int count, T data) {
        spawnParticle(particle, x, y, z, count, 0, 0, 0, data);
    }

    @Override
    public void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ);
    }

    @Override
    public void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ) {
        spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, null);
    }

    @Override
    public <T> void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, T data) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, data);
    }

    @Override
    public <T> void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, T data) {
        spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, 1, data);
    }

    @Override
    public void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, extra);
    }

    @Override
    public void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, extra, null);
    }

    @Override
    public <T> void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, T data) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, extra, data);
    }

    @Override
    public <T> void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra, T data) {
        spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, extra, data, false);
    }

    @Override
    public <T> void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, T data, boolean force) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, extra, data, force);
    }

    @Override
    public <T> void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra, T data, boolean force) {
        net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket packetplayoutworldparticles = new net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket(CraftParticle.createParticleParam(particle, data), force, (float) x, (float) y, (float) z, (float) offsetX, (float) offsetY, (float) offsetZ, (float) extra, count);
        getHandle().connection.send(packetplayoutworldparticles);
    }

    @Override
    public org.bukkit.advancement.AdvancementProgress getAdvancementProgress(org.bukkit.advancement.Advancement advancement) {
        Preconditions.checkArgument(advancement != null, "advancement");

        CraftAdvancement craft = (CraftAdvancement) advancement;
        net.minecraft.server.PlayerAdvancements data = getHandle().getAdvancements();
        net.minecraft.advancements.AdvancementProgress progress = data.getOrStartProgress(craft.getHandle());

        return new CraftAdvancementProgress(craft, data, progress);
    }

    @Override
    public int getClientViewDistance() {
        return (getHandle().requestedViewDistance() == 0) ? Bukkit.getViewDistance() : getHandle().requestedViewDistance();
    }

    @Override
    public int getPing() {
        return getHandle().connection.latency();
    }

    @Override
    public String getLocale() {
        return getHandle().language;
    }

    @Override
    public void updateCommands() {
        if (getHandle().connection == null) return;

        getHandle().server.getCommands().sendCommands(getHandle());
    }

    @Override
    public void openBook(ItemStack book) {
        Preconditions.checkArgument(book != null, "ItemStack cannot be null");
        Preconditions.checkArgument(book.getType() == Material.WRITTEN_BOOK, "ItemStack Material (%s) must be Material.WRITTEN_BOOK", book.getType());

        ItemStack hand = getInventory().getItemInMainHand();
        getInventory().setItemInMainHand(book);
        getHandle().openItemGui(org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(book), net.minecraft.world.InteractionHand.MAIN_HAND);
        getInventory().setItemInMainHand(hand);
    }

    @Override
    public void openSign(Sign sign) {
        openSign(sign, Side.FRONT);
    }

    @Override
    public void openSign(@NotNull Sign sign, @NotNull Side side) {
        CraftSign.openSign(sign, this, side);
    }

    @Override
    public void showDemoScreen() {
        if (getHandle().connection == null) return;

        getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(net.minecraft.network.protocol.game.ClientboundGameEventPacket.DEMO_EVENT, net.minecraft.network.protocol.game.ClientboundGameEventPacket.DEMO_PARAM_INTRO));
    }

    @Override
    public boolean isAllowingServerListings() {
        return getHandle().allowsListing();
    }

    // Spigot start
    private final Player.Spigot spigot = new Player.Spigot()
    {
    };

    public Player.Spigot spigot()
    {
        return spigot;
    }
    // Spigot end
}
