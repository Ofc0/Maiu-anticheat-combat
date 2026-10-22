package de.maiu.anticheat;

import com.github.retrooper.packetevents.PacketEvents;
import de.maiu.anticheat.ai.OllamaJudge;
import de.maiu.anticheat.combat.CombatListener;
import de.maiu.anticheat.combat.PunishmentManager;
import de.maiu.anticheat.decoy.DecoyManager;
import de.maiu.anticheat.engine.CombatAnalyzer;
import de.maiu.anticheat.net.PacketHandler;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class MaiuAntiCheat extends JavaPlugin {

    private Settings settings;
    private CombatAnalyzer analyzer;
    private DecoyManager decoys;
    private PunishmentManager punishments;
    private OllamaJudge judge;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings().reEncodeByDefault(false).checkForUpdates(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.load(getConfig());

        analyzer = new CombatAnalyzer(this, settings);
        decoys = new DecoyManager(this);
        punishments = new PunishmentManager(this, settings);
        judge = new OllamaJudge(this, settings.ollamaUrl, settings.aiModel, settings.aiTimeoutMs);

        decoys.setHitListener(new DecoyManager.HitListener() {
            @Override
            public void onDecoyHit(Player suspect) {
                analyzer.onDecoyHit(suspect);
            }
        });

        PacketEvents.getAPI().getEventManager().registerListener(new PacketHandler(this));
        PacketEvents.getAPI().init();

        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        MaiuCommand cmd = new MaiuCommand(this);
        getCommand("maiu").setExecutor(cmd);
        getCommand("maiu").setTabCompleter(cmd);

        analyzer.start();

        if (settings.aiEnabled) {
            judge.warmup(new OllamaJudge.Callback() {
                @Override
                public void done(OllamaJudge.Verdict v, String error) {
                    if (v != null) {
                        getLogger().info("AI judge ready (model " + settings.aiModel + ", warm-up " + v.latencyMs + "ms)");
                    } else {
                        getLogger().warning("AI judge offline (" + error + "). Rule engine runs standalone.");
                    }
                }
            });
        }
        getLogger().info("MaiuAC " + getDescription().getVersion() + " enabled (PacketEvents "
                + PacketEvents.getAPI().getVersion() + ").");
    }

    @Override
    public void onDisable() {
        if (decoys != null) decoys.removeAll();
        if (judge != null) judge.shutdown();
        if (PacketEvents.getAPI() != null) PacketEvents.getAPI().terminate();
    }

    public void reloadSettings() {
        reloadConfig();
        settings = Settings.load(getConfig());
        analyzer.reload(settings);
        punishments.reload(settings);
        judge.update(settings.ollamaUrl, settings.aiModel, settings.aiTimeoutMs);
    }

    public Settings getSettings() { return settings; }
    public CombatAnalyzer getAnalyzer() { return analyzer; }
    public DecoyManager getDecoys() { return decoys; }
    public PunishmentManager getPunishments() { return punishments; }
    public OllamaJudge getJudge() { return judge; }
}
