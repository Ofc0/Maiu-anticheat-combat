package de.maiu.anticheat.decoy;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import de.maiu.anticheat.MaiuAntiCheat;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Packet-only fake player ("AuraBot"). Only the suspect's client ever sees it.
 *
 * Satisfies the usual client side AntiBot checks:
 *  - ping:      listed TabList entry with a believable latency
 *  - name:      believable username, unique, no special characters
 *  - ticks:     entity lives > 5s on the client, cheat clients require ~25 client ticks
 *  - ground:    teleport packets with onGround=true on real floor height
 *  - sleeping:  standing pose (default metadata)
 *  - dead:      client entity health > 0 (default)
 *  - swing:     periodic arm swing animation
 *  - movement:  continuously moves (follows + drifts)
 *  - sound:     footstep / sweep sounds at the bot position
 * The bot stays in the blind spot behind the player, so a legit player can never see or hit it.
 */
public class DecoyManager {

    private static final String[] NAMES = {
            "Alex_PvP", "Shadow_99", "KevFighter", "Viper_X", "ProGamer21", "GhostRider", "SniperElite",
            "NeonBlade", "WolfGamer", "Mika_x3", "Lukas_HD", "Nico2k7", "TimoPlays", "Jonas_MC", "FelixGG"
    };

    public interface HitListener {
        void onDecoyHit(Player suspect);
    }

    private final MaiuAntiCheat plugin;
    private final Map<UUID, Decoy> byPlayer = new ConcurrentHashMap<UUID, Decoy>();
    private final Map<Integer, UUID> byEntityId = new ConcurrentHashMap<Integer, UUID>();
    private final Random random = new Random();
    private HitListener hitListener;

    private Sound stepSound;
    private Sound sweepSound;

    public DecoyManager(MaiuAntiCheat plugin) {
        this.plugin = plugin;
        this.stepSound = findSound("BLOCK_GRASS_STEP", "STEP_GRASS");
        this.sweepSound = findSound("ENTITY_PLAYER_ATTACK_SWEEP", "ENTITY_PLAYER_ATTACK_STRONG", "HURT_FLESH");
    }

    public void setHitListener(HitListener l) {
        this.hitListener = l;
    }

    private static Sound findSound(String... names) {
        for (String n : names) {
            try {
                return Sound.valueOf(n);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private final class Decoy {
        final Player player;
        final int entityId = SpigotReflectionUtil.generateEntityId();
        final UUID uuid = UUID.randomUUID();
        final String name;
        final UserProfile profile;
        final boolean visibleMode;
        final int side = random.nextBoolean() ? 1 : -1;
        final int durationTicks;
        final boolean legacyInfo;
        final boolean legacySpawn;
        BukkitTask task;
        int ticks;
        int nextSwing = 12;
        double lastX, lastZ;
        boolean removed;

        Decoy(Player player, String name, int seconds, boolean visibleMode) {
            this.player = player;
            this.name = name;
            this.profile = new UserProfile(uuid, name);
            this.visibleMode = visibleMode;
            this.durationTicks = seconds * 20;
            ClientVersion cv = PacketEvents.getAPI().getPlayerManager().getClientVersion(player);
            this.legacyInfo = cv != null && cv.isOlderThan(ClientVersion.V_1_19_3);
            this.legacySpawn = cv != null && cv.isOlderThan(ClientVersion.V_1_20_2);
        }
    }

    // ------------------------------------------------------------------ public API

    public boolean hasDecoy(Player p) {
        return byPlayer.containsKey(p.getUniqueId());
    }

    public boolean isDecoyEntity(int entityId) {
        return byEntityId.containsKey(entityId);
    }

    /** Netty thread entry point. */
    public UUID suspectOf(int entityId) {
        return byEntityId.get(entityId);
    }

    public long ageMs(Player p) {
        Decoy d = byPlayer.get(p.getUniqueId());
        return d == null ? 0 : d.ticks * 50L;
    }

    public boolean spawn(final Player player, int seconds, boolean visibleMode) {
        if (player == null || !player.isOnline()) return false;
        remove(player);
        try {
            final Decoy d = new Decoy(player, pickName(), seconds, visibleMode);
            Location spot = position(d, 0);
            byPlayer.put(player.getUniqueId(), d);
            byEntityId.put(d.entityId, player.getUniqueId());

            sendInfoAdd(d);
            sendSpawn(d, spot);
            send(player, new WrapperPlayServerEntityHeadLook(d.entityId, spot.getYaw()));
            sendEquipment(d);
            d.lastX = spot.getX();
            d.lastZ = spot.getZ();

            d.task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
                @Override
                public void run() {
                    tick(d);
                }
            }, 1L, 1L);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("Decoy spawn failed: " + t);
            t.printStackTrace();
            remove(player);
            return false;
        }
    }

    public void remove(Player player) {
        Decoy d = byPlayer.remove(player.getUniqueId());
        if (d == null) return;
        byEntityId.remove(d.entityId);
        destroy(d);
    }

    public void removeAll() {
        for (Decoy d : new ArrayList<Decoy>(byPlayer.values())) {
            byPlayer.remove(d.player.getUniqueId());
            byEntityId.remove(d.entityId);
            destroy(d);
        }
    }

    /** Called from the netty thread when the suspect's client attacks the decoy. */
    public void handleAttack(final UUID suspect) {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                Player p = Bukkit.getPlayer(suspect);
                if (p == null) return;
                Decoy d = byPlayer.get(suspect);
                // ignore the first 10 ticks (spawn jitter) so we never punish on a race
                if (d == null || d.ticks < 10) return;
                if (hitListener != null) hitListener.onDecoyHit(p);
                remove(p);
            }
        });
    }

    // ------------------------------------------------------------------ internals

    private String pickName() {
        for (int i = 0; i < 30; i++) {
            String n = NAMES[random.nextInt(NAMES.length)];
            if (random.nextInt(3) == 0) n = n + random.nextInt(10);
            if (Bukkit.getPlayerExact(n) != null) continue;
            boolean used = false;
            for (Decoy d : byPlayer.values()) if (d.name.equals(n)) used = true;
            if (!used) return n;
        }
        return "Player" + (1000 + random.nextInt(8999));
    }

    private void tick(Decoy d) {
        if (d.removed) return;
        Player p = d.player;
        if (!p.isOnline() || p.isDead() || d.ticks >= d.durationTicks) {
            remove(p);
            return;
        }
        d.ticks++;
        Location loc = position(d, d.ticks);
        send(p, new WrapperPlayServerEntityTeleport(d.entityId, new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                loc.getYaw(), 0f, true));
        send(p, new WrapperPlayServerEntityHeadLook(d.entityId, loc.getYaw()));

        double moved = Math.hypot(loc.getX() - d.lastX, loc.getZ() - d.lastZ);
        if (moved > 0.05 && d.ticks % 7 == 0 && stepSound != null) {
            p.playSound(loc, stepSound, 0.35f, 1.0f);
        }
        d.lastX = loc.getX();
        d.lastZ = loc.getZ();

        if (d.ticks >= d.nextSwing) {
            send(p, new WrapperPlayServerEntityAnimation(d.entityId,
                    WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM));
            if (sweepSound != null) p.playSound(loc, sweepSound, 0.5f, 1.0f);
            d.nextSwing = d.ticks + 12 + random.nextInt(14);
        }
    }

    /** Position in the blind spot behind the player (or in front for the visible debug mode). */
    private Location position(Decoy d, int ticks) {
        Location base = d.player.getLocation();
        double yaw = base.getYaw();
        double offset;
        double dist;
        if (d.visibleMode) {
            offset = 25.0 * Math.sin(ticks / 14.0);
            dist = 2.6;
        } else {
            // 180 deg = straight behind. Drift up to 38 deg to one side: still far outside any human field of view.
            offset = 180.0 + d.side * 38.0 * (0.5 + 0.5 * Math.sin(ticks / 17.0));
            dist = 2.3 + 0.25 * Math.sin(ticks / 9.0);
        }
        double a = Math.toRadians(yaw + offset);
        double x = base.getX() - Math.sin(a) * dist;
        double z = base.getZ() + Math.cos(a) * dist;
        double y = groundY(base.getWorld(), x, base.getY(), z);

        double dx = base.getX() - x;
        double dz = base.getZ() - z;
        float botYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        return new Location(base.getWorld(), x, y, z, botYaw, 0f);
    }

    private double groundY(World w, double x, double playerY, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return playerY;
        int start = (int) Math.floor(playerY) + 1;
        for (int y = start; y >= start - 8; y--) {
            Material m = w.getBlockAt(bx, y, bz).getType();
            if (m.isSolid()) {
                return y + 1.0;
            }
        }
        return playerY;
    }

    private void send(Player p, PacketWrapper<?> w) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(p, w);
    }

    private void sendInfoAdd(Decoy d) {
        int latency = 18 + random.nextInt(60);
        if (d.legacyInfo) {
            WrapperPlayServerPlayerInfo.PlayerData pd = new WrapperPlayServerPlayerInfo.PlayerData(
                    null, d.profile, GameMode.SURVIVAL, latency);
            send(d.player, new WrapperPlayServerPlayerInfo(WrapperPlayServerPlayerInfo.Action.ADD_PLAYER, pd));
        } else {
            WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                    d.profile, true, latency, GameMode.SURVIVAL, null, null);
            send(d.player, new WrapperPlayServerPlayerInfoUpdate(
                    EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
                            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY,
                            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE),
                    info));
        }
    }

    private void sendSpawn(Decoy d, Location l) {
        if (d.legacySpawn) {
            send(d.player, new WrapperPlayServerSpawnPlayer(d.entityId, d.uuid,
                    new Vector3d(l.getX(), l.getY(), l.getZ()), l.getYaw(), 0f, new ArrayList<EntityData<?>>()));
        } else {
            send(d.player, new WrapperPlayServerSpawnEntity(d.entityId, Optional.of(d.uuid), EntityTypes.PLAYER,
                    new Vector3d(l.getX(), l.getY(), l.getZ()), 0f, l.getYaw(), l.getYaw(), 0,
                    Optional.<Vector3d>empty()));
        }
    }

    private void sendEquipment(Decoy d) {
        try {
            List<Equipment> eq = new ArrayList<Equipment>();
            eq.add(new Equipment(EquipmentSlot.MAIN_HAND, item("DIAMOND_SWORD")));
            eq.add(new Equipment(EquipmentSlot.HELMET, item("IRON_HELMET")));
            eq.add(new Equipment(EquipmentSlot.CHEST_PLATE, item("IRON_CHESTPLATE")));
            eq.add(new Equipment(EquipmentSlot.LEGGINGS, item("IRON_LEGGINGS")));
            eq.add(new Equipment(EquipmentSlot.BOOTS, item("IRON_BOOTS")));
            send(d.player, new WrapperPlayServerEntityEquipment(d.entityId, eq));
        } catch (Throwable ignored) {
        }
    }

    private com.github.retrooper.packetevents.protocol.item.ItemStack item(String material) {
        return SpigotConversionUtil.fromBukkitItemStack(new ItemStack(Material.valueOf(material)));
    }

    private void destroy(Decoy d) {
        d.removed = true;
        if (d.task != null) d.task.cancel();
        Player p = d.player;
        if (!p.isOnline()) return;
        try {
            send(p, new WrapperPlayServerDestroyEntities(d.entityId));
            if (d.legacyInfo) {
                WrapperPlayServerPlayerInfo.PlayerData pd = new WrapperPlayServerPlayerInfo.PlayerData(
                        null, d.profile, GameMode.SURVIVAL, 0);
                send(p, new WrapperPlayServerPlayerInfo(WrapperPlayServerPlayerInfo.Action.REMOVE_PLAYER, pd));
            } else {
                send(p, new WrapperPlayServerPlayerInfoRemove(Arrays.asList(d.uuid)));
            }
        } catch (Throwable ignored) {
        }
    }
}
