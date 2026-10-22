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

import java.util.UUID;

/** Netty-thread packet intake: camera rotation stream, swings and decoy attacks. */
public class PacketHandler extends PacketListenerAbstract {

    private final MaiuAntiCheat plugin;

    public PacketHandler(MaiuAntiCheat plugin) {
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
            if (WrapperPlayClientPlayerFlying.isFlying(type)) {
                WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
                if (flying.hasRotationChanged()) {
                    PlayerData d = plugin.getAnalyzer().peek(uuid);
                    if (d != null) {
                        d.onRotation(flying.getLocation().getYaw(), flying.getLocation().getPitch(),
                                System.currentTimeMillis());
                    }
                }
            } else if (type == PacketType.Play.Client.INTERACT_ENTITY) {
                WrapperPlayClientInteractEntity w = new WrapperPlayClientInteractEntity(event);
                UUID suspect = plugin.getDecoys().suspectOf(w.getEntityId());
                if (suspect != null) {
                    // the decoy is client-side only: never let this packet reach the server
                    event.setCancelled(true);
                    if (suspect.equals(uuid)
                            && w.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                        plugin.getDecoys().handleAttack(uuid);
                    }
                }
            } else if (type == PacketType.Play.Client.ANIMATION) {
                PlayerData d = plugin.getAnalyzer().peek(uuid);
                if (d != null) d.onSwing(System.currentTimeMillis());
            }
        } catch (Throwable t) {
            // never break the connection because of an analysis error
            plugin.getLogger().fine("packet handler: " + t);
        }
    }
}
