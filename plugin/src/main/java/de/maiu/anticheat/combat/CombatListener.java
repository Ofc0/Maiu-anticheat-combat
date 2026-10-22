package de.maiu.anticheat.combat;

import de.maiu.anticheat.MaiuAntiCheat;
import de.maiu.anticheat.engine.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class CombatListener implements Listener {

    private final MaiuAntiCheat plugin;

    public CombatListener(MaiuAntiCheat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        Entity target = e.getEntity();
        if (!(target instanceof LivingEntity)) return;
        if (target.getType().name().equals("ARMOR_STAND")) return;

        Player attacker = (Player) e.getDamager();
        GameMode gm = attacker.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;
        if (attacker.hasPermission("maiu.bypass")) return;

        plugin.getAnalyzer().onAttack(attacker, target, e);
        if (e.isCancelled()) return;

        PlayerData d = plugin.getAnalyzer().get(attacker);
        double mult = plugin.getPunishments().damageMultiplier(d, System.currentTimeMillis());
        if (mult <= 0.001) {
            e.setCancelled(true);
        } else if (mult < 0.999) {
            e.setDamage(e.getDamage() * mult);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        PlayerData d = plugin.getAnalyzer().get(e.getPlayer());
        d.ignoreRotation(System.currentTimeMillis() + 2500L);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        PlayerData d = plugin.getAnalyzer().get(e.getPlayer());
        d.ignoreRotation(System.currentTimeMillis() + 1500L);
        d.resetRotation();
        plugin.getDecoys().remove(e.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        PlayerData d = plugin.getAnalyzer().get(e.getPlayer());
        d.ignoreRotation(System.currentTimeMillis() + 2000L);
        d.resetRotation();
        plugin.getDecoys().remove(e.getPlayer());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        plugin.getAnalyzer().get(e.getPlayer()).ignoreRotation(System.currentTimeMillis() + 2000L);
        plugin.getDecoys().remove(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.getDecoys().remove(e.getPlayer());
        plugin.getAnalyzer().remove(e.getPlayer().getUniqueId());
    }
}
