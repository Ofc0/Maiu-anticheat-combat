package de.maiu.anticheat;

import org.bukkit.configuration.file.FileConfiguration;

/** Typed snapshot of config.yml. */
public class Settings {

    // reach
    public double reachLimit;
    public double reachHard;
    public boolean cancelImpossibleReach;
    // wall
    public boolean cancelWallHits;
    // killaura
    public double auraAngleSoft;
    public double auraAngleHard;
    public long auraSwitchMs;
    public double auraSwitchAngle;
    // aim
    public double aimSuspect;
    // flag thresholds (vl)
    public double flagReach, flagWall, flagAura, flagAim, flagTrigger;
    // decoy
    public boolean decoyEnabled;
    public int decoySeconds;
    public int decoyCooldownSeconds;
    public boolean decoyOnSuspicion;
    // punishments
    public double damageMultiplier;
    public int punishSeconds;
    public boolean kickEnabled;
    public String kickMessage;
    public boolean banEnabled;
    public String banCommand;
    public int banAfterFlags;
    // ai
    public boolean aiEnabled;
    public String ollamaUrl;
    public String aiModel;
    public int aiTimeoutMs;
    public int aiCooldownSeconds;

    public static Settings load(FileConfiguration c) {
        Settings s = new Settings();
        s.reachLimit = c.getDouble("checks.reach.limit", 3.41);
        s.reachHard = c.getDouble("checks.reach.hard_limit", 3.9);
        s.cancelImpossibleReach = c.getBoolean("checks.reach.cancel_impossible", true);
        s.cancelWallHits = c.getBoolean("checks.wallhit.cancel", true);
        s.auraAngleSoft = c.getDouble("checks.killaura.angle_soft", 20.0);
        s.auraAngleHard = c.getDouble("checks.killaura.angle_hard", 38.0);
        s.auraSwitchMs = c.getLong("checks.killaura.switch_ms", 250L);
        s.auraSwitchAngle = c.getDouble("checks.killaura.switch_angle", 40.0);
        s.aimSuspect = c.getDouble("checks.aim.suspect_score", 0.30);
        s.flagReach = c.getDouble("flag_vl.reach", 3.0);
        s.flagWall = c.getDouble("flag_vl.wallhit", 3.0);
        s.flagAura = c.getDouble("flag_vl.killaura", 4.0);
        s.flagAim = c.getDouble("flag_vl.aimassist", 5.0);
        s.flagTrigger = c.getDouble("flag_vl.triggerbot", 5.0);
        s.decoyEnabled = c.getBoolean("decoy.enabled", true);
        s.decoySeconds = c.getInt("decoy.duration_seconds", 5);
        s.decoyCooldownSeconds = c.getInt("decoy.cooldown_seconds", 10);
        s.decoyOnSuspicion = c.getBoolean("decoy.auto_on_suspicion", true);
        s.damageMultiplier = c.getDouble("punishments.damage_multiplier", 0.0);
        s.punishSeconds = c.getInt("punishments.duration_seconds", 60);
        s.kickEnabled = c.getBoolean("punishments.kick.enabled", false);
        s.kickMessage = c.getString("punishments.kick.message", "&c[MaiuAC] Unfair combat advantage detected.");
        s.banEnabled = c.getBoolean("punishments.ban.enabled", false);
        s.banCommand = c.getString("punishments.ban.command", "ban %player% [MaiuAC] %cheat%");
        s.banAfterFlags = c.getInt("punishments.ban.after_flags", 3);
        s.aiEnabled = c.getBoolean("ai.enabled", true);
        s.ollamaUrl = c.getString("ai.ollama_url", "http://localhost:11434");
        s.aiModel = c.getString("ai.model", "maiu-combat-ai");
        s.aiTimeoutMs = c.getInt("ai.timeout_ms", 8000);
        s.aiCooldownSeconds = c.getInt("ai.judge_cooldown_seconds", 6);
        return s;
    }
}
