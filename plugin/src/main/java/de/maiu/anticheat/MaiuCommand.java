package de.maiu.anticheat;

import de.maiu.anticheat.ai.OllamaJudge;
import de.maiu.anticheat.engine.Check;
import de.maiu.anticheat.engine.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MaiuCommand implements CommandExecutor, TabCompleter {

    private final MaiuAntiCheat plugin;

    public MaiuCommand(MaiuAntiCheat plugin) {
        this.plugin = plugin;
    }

    private static final List<String> SUBS = Arrays.asList("status", "alerts", "inspect", "debug", "bot", "reset", "reload");

    @Override
    public boolean onCommand(final CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("maiu.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission (maiu.admin).");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.DARK_GRAY + "--------------------------------------------------");
            sender.sendMessage(ChatColor.AQUA + "🛡️  " + ChatColor.BOLD + "MaiuAntiCheat " + ChatColor.GRAY + "v" + plugin.getDescription().getVersion() + ChatColor.DARK_GRAY + " | " + ChatColor.GREEN + "AI Active");
            sender.sendMessage(ChatColor.DARK_GRAY + "--------------------------------------------------");
            sender.sendMessage(ChatColor.YELLOW + "  /maiu status" + ChatColor.GRAY + " » Check neural engine & GPU model latency");
            sender.sendMessage(ChatColor.YELLOW + "  /maiu alerts" + ChatColor.GRAY + " » Toggle staff cheat notifications on/off");
            sender.sendMessage(ChatColor.YELLOW + "  /maiu inspect <player>" + ChatColor.GRAY + " » Detailed combat telemetry & AI arbiter verdict");
            sender.sendMessage(ChatColor.YELLOW + "  /maiu debug <player>" + ChatColor.GRAY + " » Live second-by-second actionbar HUD");
            sender.sendMessage(ChatColor.YELLOW + "  /maiu bot <player> [sec]" + ChatColor.GRAY + " » Deploy 9-check AntiBot decoy trap");
            sender.sendMessage(ChatColor.YELLOW + "  /maiu bot <player> [sec] front" + ChatColor.GRAY + " » Visible decoy (visual testing)");
            sender.sendMessage(ChatColor.YELLOW + "  /maiu bot stop <player>" + ChatColor.GRAY + " » Remove active decoy trap");
            sender.sendMessage(ChatColor.YELLOW + "  /maiu reset <player>" + ChatColor.GRAY + " » Clear violation levels & combat state");
            sender.sendMessage(ChatColor.YELLOW + "  /maiu reload" + ChatColor.GRAY + " » Hot-reload config.yml");
            sender.sendMessage(ChatColor.DARK_GRAY + "--------------------------------------------------");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("status")) {
            sender.sendMessage(ChatColor.AQUA + "[MaiuAC] " + ChatColor.GRAY + "Math Engine: " + ChatColor.GREEN + "ONLINE"
                    + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "Tracked Players: " + ChatColor.WHITE + Bukkit.getOnlinePlayers().size());
            sender.sendMessage(ChatColor.GRAY + "Pinging local Ollama neural service...");
            plugin.getJudge().warmup(new OllamaJudge.Callback() {
                @Override
                public void done(final OllamaJudge.Verdict v, final String error) {
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            if (v != null) {
                                sender.sendMessage(ChatColor.AQUA + "[MaiuAC] " + ChatColor.GREEN + "AI Judge ONLINE "
                                        + ChatColor.DARK_GRAY + "(" + ChatColor.YELLOW + v.latencyMs + "ms"
                                        + ChatColor.DARK_GRAY + " | Model: " + ChatColor.WHITE + plugin.getSettings().aiModel + ChatColor.DARK_GRAY + ")");
                            } else {
                                sender.sendMessage(ChatColor.AQUA + "[MaiuAC] " + ChatColor.RED + "AI Judge OFFLINE: " + error
                                        + ChatColor.GRAY + " (deterministic rule engine active)");
                            }
                        }
                    });
                }
            });
            return true;
        }

        if (sub.equals("alerts")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            boolean muted = plugin.getPunishments().toggleMute(((Player) sender).getUniqueId());
            sender.sendMessage(ChatColor.AQUA + "[MaiuAC] " + ChatColor.GRAY + "Alerts " + (muted ? ChatColor.RED + "muted" : ChatColor.GREEN + "enabled"));
            return true;
        }

        if (sub.equals("reload")) {
            plugin.reloadSettings();
            sender.sendMessage(ChatColor.GREEN + "[MaiuAC] config reloaded.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /maiu " + sub + " <player>");
            return true;
        }

        if (sub.equals("bot") && args[1].equalsIgnoreCase("stop")) {
            Player t = args.length > 2 ? Bukkit.getPlayer(args[2]) : null;
            if (t == null) {
                sender.sendMessage(ChatColor.RED + "Player not online.");
                return true;
            }
            plugin.getDecoys().remove(t);
            sender.sendMessage(ChatColor.YELLOW + "[MaiuAC] decoy removed.");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not online.");
            return true;
        }
        PlayerData d = plugin.getAnalyzer().get(target);

        if (sub.equals("bot")) {
            int sec = plugin.getSettings().decoySeconds;
            boolean front = false;
            for (int i = 2; i < args.length; i++) {
                if (args[i].equalsIgnoreCase("front")) front = true;
                else {
                    try {
                        sec = Math.max(1, Math.min(60, Integer.parseInt(args[i])));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            boolean ok = plugin.getDecoys().spawn(target, sec, front);
            sender.sendMessage(ok
                    ? ChatColor.GREEN + "[MaiuAC] decoy spawned for " + target.getName() + " (" + sec + "s, "
                    + (front ? "FRONT test mode - you can see it" : "hidden behind the player") + ")"
                    : ChatColor.RED + "[MaiuAC] decoy spawn failed, see console.");
            return true;
        }

        if (sub.equals("reset")) {
            d.clearCombat();
            plugin.getDecoys().remove(target);
            sender.sendMessage(ChatColor.GREEN + "[MaiuAC] reset " + target.getName());
            return true;
        }

        if (sub.equals("debug")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            plugin.getAnalyzer().toggleDebug(((Player) sender).getUniqueId(), target.getUniqueId());
            boolean on = plugin.getAnalyzer().isDebugging(((Player) sender).getUniqueId());
            sender.sendMessage(ChatColor.AQUA + "[MaiuAC] debug " + (on ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF")
                    + ChatColor.GRAY + " for " + target.getName());
            return true;
        }

        if (sub.equals("inspect")) {
            sender.sendMessage(ChatColor.DARK_GRAY + "--------------------------------------------------");
            sender.sendMessage(ChatColor.AQUA + "📊 " + ChatColor.BOLD + "Combat Telemetry: " + ChatColor.WHITE + target.getName());
            sender.sendMessage(ChatColor.DARK_GRAY + "--------------------------------------------------");
            sender.sendMessage(plugin.getAnalyzer().debugLine(d));
            StringBuilder flags = new StringBuilder();
            for (Check c : Check.values()) {
                int count = d.flagCount[c.ordinal()];
                ChatColor color = count > 0 ? ChatColor.RED : ChatColor.GRAY;
                flags.append(color).append(c.display()).append(": ").append(ChatColor.WHITE).append(count).append("  ");
            }
            sender.sendMessage(ChatColor.YELLOW + "Violation Levels: " + flags);
            sender.sendMessage(ChatColor.YELLOW + "AI Verdict: " + ChatColor.WHITE + d.lastAiVerdict);
            sender.sendMessage(ChatColor.YELLOW + "Decoy Status: " + (plugin.getDecoys().hasDecoy(target) ? ChatColor.GREEN + "ACTIVE" : ChatColor.DARK_GRAY + "IDLE")
                    + ChatColor.DARK_GRAY + " | " + ChatColor.YELLOW + "Real-time CPS: " + ChatColor.WHITE + d.cps(System.currentTimeMillis()));
            sender.sendMessage(ChatColor.DARK_GRAY + "--------------------------------------------------");
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Unknown sub command.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length >= 2) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[args.length - 1].toLowerCase(Locale.ROOT))) out.add(p.getName());
            }
            if (args[0].equalsIgnoreCase("bot") && args.length == 2) out.add("stop");
        }
        return out;
    }
}
