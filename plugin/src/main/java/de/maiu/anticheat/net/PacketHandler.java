package de.maiu.anticheat.net;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import de.maiu.anticheat.MaiuAntiCheat;
import de.maiu.anticheat.engine.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Hey there! This class is our low-level Network Packet Interceptor.
 *
 * In standard Minecraft plugins, people usually listen to `PlayerMoveEvent` or `EntityDamageByEntityEvent`.
 * But there is a catch: those Bukkit events run on the server's main tick loop (every 50 milliseconds).
 *
 * Cheats, however, send raw network packets through Netty threads at 100+ packets per second!
 * By hooking into PacketEvents here, we intercept packets the very microsecond they arrive from
 * the player's internet connection, BEFORE Minecraft even calculates a game tick.
 */
public class PacketHandler extends PacketListenerAbstract {

    private final MaiuAntiCheat plugin;

    public PacketHandler(MaiuAntiCheat plugin) {
        // Priority LOW means we get to see the packet very early in the pipeline
        super(PacketListenerPriority.LOW);
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        User user = event.getUser();
        if (user == null) return;
        UUID uuid = user.getUUID();
        if (uuid == null) return;
        PacketTypeCommon type = event.getPacketType();

        try {
            // Case 1: The player is sending movement/look packets
            if (WrapperPlayClientPlayerFlying.isFlying(type)) {
                WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
                if (flying.hasRotationChanged()) {
                    float yaw = flying.getLocation().getYaw();
                    float pitch = flying.getLocation().getPitch();
                    
                    // Send rotation data directly into our GCD and math analyzer
                    PlayerData d = plugin.getAnalyzer().peek(uuid);
                    if (d != null) {
                        d.onRotation(yaw, pitch, System.currentTimeMillis());
                    }
                    
                    // Reposition our AuraBot decoy immediately in the same network tick
                    // so the bot never peeks into view when someone spins rapidly!
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        plugin.getDecoys().updateSuspectRotation(p, yaw, pitch);
                    }
                }
            } 
            // Case 2: The player clicked or attacked an entity
            else if (type == PacketType.Play.Client.INTERACT_ENTITY) {
                WrapperPlayClientInteractEntity w = new WrapperPlayClientInteractEntity(event);
                UUID suspect = plugin.getDecoys().suspectOf(w.getEntityId());
                if (suspect != null) {
                    // IMPORTANT: The decoy bot is 100% fake and client-side!
                    // It does not exist in Minecraft's server world, so we must CANCEL
                    // this packet immediately so the server doesn't crash looking for a non-existent mob.
                    event.setCancelled(true);
                    
                    // If the suspect clicked the decoy that was hidden in their blind spot,
                    // they are 100% using an automated cheat!
                    if (suspect.equals(uuid)
                            && w.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                        plugin.getDecoys().handleAttack(uuid);
                    }
                }
            } 
            // Case 3: The player swung their arm (left click in air or at a block)
            else if (type == PacketType.Play.Client.ANIMATION) {
                PlayerData d = plugin.getAnalyzer().peek(uuid);
                if (d != null) d.onSwing(System.currentTimeMillis());
            }
        } catch (Throwable t) {
            // Golden rule in network programming: Never crash the connection if something fails in analysis!
            plugin.getLogger().fine("packet handler: " + t);
        }
    }
}
