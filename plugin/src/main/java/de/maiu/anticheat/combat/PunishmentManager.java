package de.maiu.anticheat.combat;

import de.maiu.anticheat.MaiuAntiCheat;
import de.maiu.anticheat.Settings;
import de.maiu.anticheat.engine.Check;
import de.maiu.anticheat.engine.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Alerts + graded response: damage reduction/cancel -> kick -> ban. */
public class PunishmentManager {

    private final MaiuAntiCheat plugin;
    private Settings s;
    private final Set<UUID> muted = ConcurrentHashMap.newKeySet();

    public PunishmentManager(MaiuAntiCheat plugin, Settings s) {
        this.plugin = plugin;
        this.s = s;
    }

    public void reload(Settings s) {
        this.s = s;
    }

    public boolean toggleMute(UUID u) {
        if (muted.remove(u)) return false;
        muted.add(u);
        return true;
    }

    public void staffNotice(String msg) {
        String line = ChatColor.DARK_GRAY + "[" + ChatColor.AQUA + "MaiuAC" + ChatColor.DARK_GRAY + "] " + msg;
        for (Player st : Bukkit.getOnlinePlayers()) {
            if ((st.hasPermission("maiu.alerts") || st.isOp()) && !muted.contains(st.getUniqueId())) {
                st.sendMessage(line);
            }
        }
        Bukkit.getConsoleSender().sendMessage(ChatColor.stripColor(line));
    }

    /** Multiplier applied to damage a flagged player deals. 1.0 = normal. */
    public double damageMultiplier(PlayerData d, long now) {
        return now < d.flaggedUntil ? s.damageMultiplier : 1.0;
    }

    public void flag(Player p, PlayerData d, Check c, double vl, String evidence, boolean certain) {
        long now = System.currentTimeMillis();
        // ONLY enable damage penalty if it is a 100% CONFIRMED detection (e.g. attacked the AuraBot decoy)
        // Never apply damage multiplier on soft / heuristic flags to prevent "invincible" players on false flags!
        if (certain) {
            d.flaggedUntil = now + s.punishSeconds * 1000L;
        }

        String tag = certain ? ChatColor.DARK_RED + "" + ChatColor.BOLD + "CONFIRMED " : "";
        staffNotice(tag + ChatColor.WHITE + p.getName() + ChatColor.GRAY + " flagged "
                + ChatColor.RED + c.display() + ChatColor.DARK_GRAY + " (" + ChatColor.GRAY
                + String.format(Locale.ROOT, "vl %.1f", vl) + ChatColor.DARK_GRAY + ", ai " + ChatColor.GRAY
                + d.lastAiVerdict + ChatColor.DARK_GRAY + ") " + ChatColor.DARK_GRAY + "» " + ChatColor.GRAY + evidence);

        // aim related flags: deploy the decoy to confirm (never for reach/wall which are pure math)
        if (!certain && (c == Check.KILLAURA || c == Check.AIMASSIST || c == Check.TRIGGERBOT)
                && s.decoyEnabled && !plugin.getDecoys().hasDecoy(p)
                && now - d.lastDecoyTime >= s.decoyCooldownSeconds * 1000L) {
            d.lastDecoyTime = now;
            plugin.getDecoys().spawn(p, s.decoySeconds, false);
        }

        int total = 0;
        for (int n : d.flagCount) total += n;

        if (s.banEnabled && (certain || total >= s.banAfterFlags)) {
            String cmd = s.banCommand.replace("%player%", p.getName()).replace("%cheat%", c.display());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        } else if (s.kickEnabled && certain) {
            p.kickPlayer(ChatColor.translateAlternateColorCodes('&', s.kickMessage));
        }
    }
}
