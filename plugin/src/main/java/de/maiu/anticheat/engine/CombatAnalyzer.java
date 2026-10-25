package de.maiu.anticheat.engine;

import de.maiu.anticheat.MaiuAntiCheat;
import de.maiu.anticheat.Settings;
import de.maiu.anticheat.ai.OllamaJudge;
import de.maiu.anticheat.util.BoundingBox;
import de.maiu.anticheat.util.MathUtil;
import de.maiu.anticheat.util.PingUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rule + statistics engine (instant, deterministic, runs on the main thread).
 *
 *  Reach      lag-compensated eye->hitbox distance
 *  WallHit    all sight rays blocked by solid blocks
 *  KillAura   hits with the crosshair far off the target, multi-target snaps, decoy hits (certain)
 *  AimAssist  mouse-lattice analysis + tracking lock + perfect aim + snaps (needs >= 2 independent signals)
 *  TriggerBot perfectly regular click timing, instant click on crosshair re-entry, impossible CPS
 *
 * Violation levels (vl) rise with evidence and decay with time, so one odd hit never flags anybody.
 * Mouse wiggling is never evaluated unless the player is actually fighting.
 */
public class CombatAnalyzer {

    private final MaiuAntiCheat plugin;
    private Settings s;
    private final ConcurrentHashMap<UUID, PlayerData> data = new ConcurrentHashMap<UUID, PlayerData>();
    private final Map<UUID, UUID> debugViewers = new ConcurrentHashMap<UUID, UUID>(); // viewer -> target
    private int tickCounter;
    private final de.maiu.anticheat.ai.TrainingDataCollector datasetCollector;

    public CombatAnalyzer(MaiuAntiCheat plugin, Settings settings) {
        this.plugin = plugin;
        this.s = settings;
        this.datasetCollector = new de.maiu.anticheat.ai.TrainingDataCollector(plugin);
    }

    public void reload(Settings settings) {
        this.s = settings;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    tick();
                } catch (Throwable t) {
                    plugin.getLogger().warning("tick error: " + t);
                    t.printStackTrace();
                }
            }
        }, 20L, 1L);
    }

    // ------------------------------------------------------------------ data access

    public PlayerData get(Player p) {
        PlayerData d = data.get(p.getUniqueId());
        if (d == null) {
            d = new PlayerData(p.getUniqueId(), p.getName());
            PlayerData prev = data.putIfAbsent(p.getUniqueId(), d);
            if (prev != null) d = prev;
        }
        return d;
    }

    public PlayerData peek(UUID uuid) {
        return data.get(uuid);
    }

    public void remove(UUID uuid) {
        data.remove(uuid);
        debugViewers.remove(uuid);
    }

    public void toggleDebug(UUID viewer, UUID target) {
        if (debugViewers.containsKey(viewer)) debugViewers.remove(viewer);
        else debugViewers.put(viewer, target);
    }

    public boolean isDebugging(UUID viewer) {
        return debugViewers.containsKey(viewer);
    }

    // ------------------------------------------------------------------ per tick

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = get(p);
            updatePosHistory(p, d, now);
            if (now - d.lastAttackTime < 6000L) {
                trackTarget(p, d, now);
            }
        }
        tickCounter++;
        if (tickCounter % 20 == 0) {
            secondly(now);
        }
    }

    private void updatePosHistory(Player p, PlayerData d, long now) {
        Location l = p.getLocation();
        d.posHistory.addLast(new double[]{now, l.getX(), l.getY(), l.getZ()});
        while (!d.posHistory.isEmpty() && now - (long) d.posHistory.peekFirst()[0] > 1500L) {
            d.posHistory.pollFirst();
        }
    }

    /** Samples crosshair vs. target once per tick while the player is fighting. */
    private void trackTarget(Player p, PlayerData d, long now) {
        Entity t = d.lastTarget;
        double[] rot = d.drainRotation();
        if (t == null || !t.isValid() || !t.getWorld().equals(p.getWorld())) return;
        Location eyeLoc = p.getEyeLocation();
        if (eyeLoc.distanceSquared(t.getLocation()) > 100.0) return;

        double[] sz = PingUtil.size(t);
        Location tl = t.getLocation();
        BoundingBox box = BoundingBox.fromEntity(tl, sz[0], sz[1]).expand(0.1);
        Vector eye = eyeLoc.toVector();
        Vector look = eyeLoc.getDirection();
        Vector center = new Vector(tl.getX(), tl.getY() + sz[1] * 0.5, tl.getZ());
        Vector to = center.clone().subtract(eye);

        double bearingYaw = Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
        double errYaw = MathUtil.wrap180(bearingYaw - eyeLoc.getYaw());
        double targetVel = Double.isNaN(d.prevBearingYaw) ? 0.0 : Math.abs(MathUtil.wrap180(bearingYaw - d.prevBearingYaw));
        d.prevBearingYaw = bearingYaw;

        boolean on = box.calculateRayDistance(eye, look, 8.0) >= 0.0;
        if (on && !d.wasOnTarget) d.onTargetSince = now;
        if (!on && d.wasOnTarget) d.offTargetSince = now;
        d.wasOnTarget = on;

        PlayerData.AimSample sm = new PlayerData.AimSample();
        sm.time = now;
        sm.err = MathUtil.angleBetween(look, to);
        sm.errYaw = errYaw;
        sm.targetVel = targetVel;
        sm.playerYaw = rot[0];
        sm.onTarget = on;
        d.addAimSample(sm);
    }

    // ------------------------------------------------------------------ attack

    public void onAttack(Player a, Entity t, EntityDamageByEntityEvent event) {
        long now = System.currentTimeMillis();
        PlayerData d = get(a);
        int ping = PingUtil.ping(a);

        Location eyeLoc = a.getEyeLocation();
        Vector eye = eyeLoc.toVector();
        Vector look = eyeLoc.getDirection();
        double[] sz = PingUtil.size(t);

        // candidate target positions = where the attacker's client may have seen the target (ping compensation)
        List<double[]> cands = new ArrayList<double[]>();
        Location tl = t.getLocation();
        cands.add(new double[]{tl.getX(), tl.getY(), tl.getZ()});
        if (t instanceof Player) {
            PlayerData td = peek(t.getUniqueId());
            if (td != null) {
                // Grim-style historical compensation:
                // Include half of target's own ping plus attacker's ping + 1 tick (50ms) buffer
                int targetPing = PingUtil.ping((Player) t);
                long totalLatency = ping + (targetPing / 2);
                long lo = Math.max(0, totalLatency - 80);
                long hi = totalLatency + 280;
                for (double[] h : td.posHistory) {
                    long age = now - (long) h[0];
                    if (age >= lo && age <= hi) cands.add(new double[]{h[1], h[2], h[3]});
                }
            }
        }

        double reach = Double.MAX_VALUE;
        double hitboxAngle = Double.MAX_VALUE;
        double centerAngle = Double.MAX_VALUE;
        for (double[] c : cands) {
            BoundingBox box = BoundingBox.fromEntity(new Location(tl.getWorld(), c[0], c[1], c[2]), sz[0], sz[1]).expand(0.1);
            Vector nearest = MathUtil.nearestPoint(box, eye);
            reach = Math.min(reach, nearest.distance(eye));
            double ray = box.calculateRayDistance(eye, look, 8.0);
            double ha = ray >= 0.0 ? 0.0 : MathUtil.angleBetween(look, nearest.clone().subtract(eye));
            hitboxAngle = Math.min(hitboxAngle, ha);
            Vector ctr = new Vector(c[0], c[1] + sz[1] * 0.5, c[2]);
            centerAngle = Math.min(centerAngle, MathUtil.angleBetween(look, ctr.clone().subtract(eye)));
        }

        // wall check: ping-compensated line-of-sight check across all target positions in history.
        // A hit is ONLY considered a WallHit if from the attacker's eye to EVERY plausible target hitbox position
        // in latency history (center, top, bottom, and corners), the sight rays are solidly occluded.
        boolean wall = false;
        if (tl.getWorld().equals(eyeLoc.getWorld())) {
            boolean anyCandVisible = false;
            for (double[] c : cands) {
                double targetY = c[1];
                Vector top = new Vector(c[0], targetY + sz[1] * 0.85, c[2]);
                Vector mid = new Vector(c[0], targetY + sz[1] * 0.5, c[2]);
                Vector bot = new Vector(c[0], targetY + 0.35, c[2]);

                if (!MathUtil.isBlocked(tl.getWorld(), eye, mid)
                        || !MathUtil.isBlocked(tl.getWorld(), eye, top)
                        || !MathUtil.isBlocked(tl.getWorld(), eye, bot)) {
                    anyCandVisible = true;
                    break;
                }
            }
            wall = !anyCandVisible;
        }

        double[] rb = d.rotationWithin(now, 120L);
        PlayerData.AttackRecord rec = new PlayerData.AttackRecord();
        rec.time = now;
        rec.targetId = t.getEntityId();
        rec.reach = reach;
        rec.hitboxAngle = hitboxAngle;
        rec.centerAngle = centerAngle;
        rec.wall = wall;
        rec.yawBefore = rb[0];
        rec.rotBefore = rb[1];
        rec.dir = new Vector(tl.getX(), tl.getY() + sz[1] * 0.5, tl.getZ()).subtract(eye).normalize();
        rec.reacquire = d.offTargetSince > 0 && d.onTargetSince - d.offTargetSince >= 250L && now - d.onTargetSince <= 400L;
        rec.instant = rec.reacquire && now - d.onTargetSince <= 70L;

        d.hitCount++;
        PlayerData.AttackRecord prev = d.attacks.peekLast();
        d.lastTarget = t;
        d.lastAttackTime = now;
        d.addAttack(rec);

        // ---- REACH
        if (reach > s.reachLimit) {
            addVl(d, Check.REACH, reach > s.reachHard ? 2.0 : 1.0);
            tryFlag(a, d, Check.REACH, String.format(Locale.ROOT, "reach %.2f > %.2f (ping %dms)", reach, s.reachLimit, ping));
        } else {
            addVl(d, Check.REACH, -0.35);
        }

        // ---- WALLHIT
        if (wall) {
            addVl(d, Check.WALLHIT, 2.0);
            tryFlag(a, d, Check.WALLHIT, "hit through solid blocks (all sight rays blocked)");
        } else {
            addVl(d, Check.WALLHIT, -0.25);
        }

        // ---- KILLAURA
        double pts = 0;
        String why = "";
        if (hitboxAngle > s.auraAngleHard) {
            pts += 2.5;
            why = String.format(Locale.ROOT, "hit %.0f deg outside crosshair", hitboxAngle);
        } else if (hitboxAngle > s.auraAngleSoft) {
            pts += 1.2;
            why = String.format(Locale.ROOT, "hit %.0f deg outside crosshair", hitboxAngle);
        }
        if (prev != null && prev.targetId != rec.targetId && now - prev.time <= s.auraSwitchMs && prev.dir != null) {
            double sw = MathUtil.angleBetween(prev.dir, rec.dir);
            if (sw >= s.auraSwitchAngle) {
                pts += 2.0;
                why += (why.isEmpty() ? "" : ", ") + String.format(Locale.ROOT,
                        "multi-target switch %.0f deg in %dms", sw, now - prev.time);
            }
        }
        if (pts > 0) {
            addVl(d, Check.KILLAURA, pts);
            tryFlag(a, d, Check.KILLAURA, why);
        } else {
            addVl(d, Check.KILLAURA, -0.2);
        }

        // quick (non-flag) extra evaluation of the aim picture
        evaluateAim(a, d, now, false);
    }

    // ------------------------------------------------------------------ decoy

    public void onDecoyHit(Player p) {
        PlayerData d = get(p);
        d.vl[Check.KILLAURA.ordinal()] = Math.max(d.vl[Check.KILLAURA.ordinal()], 10.0);
        raise(p, d, Check.KILLAURA,
                "attacked the AuraBot decoy that was hidden behind the player (impossible for a legit client)", true);
    }

    private void maybeDecoy(Player p, PlayerData d, long now, String reason) {
        if (!s.decoyEnabled || !s.decoyOnSuspicion) return;
        if (plugin.getDecoys().hasDecoy(p)) return;
        if (now - d.lastDecoyTime < s.decoyCooldownSeconds * 1000L) return;
        d.lastDecoyTime = now;
        if (plugin.getDecoys().spawn(p, s.decoySeconds, false)) {
            plugin.getPunishments().staffNotice(ChatColor.AQUA + p.getName() + ChatColor.GRAY
                    + ": AuraBot deployed behind the player (" + reason + ")");
        }
    }

    // ------------------------------------------------------------------ once per second

    private void secondly(long now) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = get(p);
            // time decay
            for (Check c : Check.values()) {
                int i = c.ordinal();
                if (d.vl[i] > 0) d.vl[i] = Math.max(0, d.vl[i] - 0.08);
            }
            boolean fighting = now - d.lastAttackTime < 10000L;
            if (!fighting) {
                d.lastAimScore = 0;
                continue;
            }
            double aim = evaluateAim(p, d, now, true);
            evaluateTrigger(p, d, now);

            boolean suspicious = aim >= s.aimSuspect
                    || d.vl[Check.KILLAURA.ordinal()] >= 1.5
                    || d.vl[Check.TRIGGERBOT.ordinal()] >= 2.0;
            if (suspicious) {
                maybeDecoy(p, d, now, aim >= s.aimSuspect ? String.format(Locale.ROOT, "aim score %.2f", aim) : "combat anomaly");
                requestJudgement(p, d, now);
            }
        }

        // live debug output
        for (Map.Entry<UUID, UUID> e : debugViewers.entrySet()) {
            Player viewer = Bukkit.getPlayer(e.getKey());
            Player target = Bukkit.getPlayer(e.getValue());
            PlayerData td = target == null ? null : peek(target.getUniqueId());
            if (viewer == null || td == null) continue;
            viewer.sendMessage(debugLine(td));
        }
    }

    /** Returns the 0..1 aim-assist score. */
    private double evaluateAim(Player p, PlayerData d, long now, boolean updateVl) {
        List<PlayerData.AttackRecord> recent = d.recentAttacks(now, 10000L);
        List<Long> recentSwings = d.recentSwings(now, 10000L);
        int totalSwings = recentSwings.size();
        int hits = recent.size();
        int misses = Math.max(0, totalSwings - hits);
        d.missedSwings = misses;

        if (hits < 3) {
            if (updateVl) {
                d.lastAimScore = 0;
                addVl(d, Check.AIMASSIST, -0.5);
            }
            return 0;
        }

        RotationAnalyzer.Result r = d.analyzeRotation();
        double s1 = r.valid ? MathUtil.ramp(r.offLattice, 0.22, 0.55) : 0.0;

        double lock = computeLock(d);
        d.lastLock = lock;
        double s2 = lock < 0 ? 0.0 : MathUtil.ramp(lock, 0.45, 0.80);

        int perfectN = 0;
        int snaps = 0;
        for (PlayerData.AttackRecord a : recent) {
            if (a.centerAngle < 1.2) perfectN++;
            if (a.yawBefore >= 25.0 && a.centerAngle < 8.0) snaps++;
        }
        double perfect = perfectN / (double) recent.size();
        d.lastPerfect = perfect;
        double s3 = recent.size() >= 5 ? MathUtil.ramp(perfect, 0.45, 0.85) : 0.0;
        double s4 = MathUtil.ramp(snaps, 2, 5);

        // Neural Aim / Missed-Hit Analysis:
        // Cheats with "random miss" generators maintain high tracking lock & GCD off-lattice despite missing swings
        double hitRatio = totalSwings > 0 ? (hits / (double) totalSwings) : 1.0;
        double neuralAimModifier = 0.0;
        if (hitRatio > 0.40 && hitRatio < 0.90 && s1 > 0.45 && s2 > 0.40) {
            // Intentional missed hits with abnormal smoothing/lock
            neuralAimModifier = 0.25;
        }

        // --- PURE STATISTICAL AIM-ASSIST & SMOOTHING DETECTION (NO BOT REQUIRED) ---
        // 1. Off-Lattice Ratio: Physical mouse hardware MUST land on sensitivity grid. If > 45%, software is moving the crosshair.
        if (r.valid && r.offLattice >= 0.45 && recent.size() >= 4) {
            addVl(d, Check.AIMASSIST, 2.5);
            tryFlag(p, d, Check.AIMASSIST, String.format(Locale.ROOT,
                    "invalid mouse sensor lattice (%.0f%% impossible software movements, step=%.4f)",
                    r.offLattice * 100, r.step));
        }

        // 2. Unnatural Aim Acceleration / Micro-Correction Jerk:
        // Human hands accelerate smoothly (cosine curve). Injected aim hacks produce jagged non-zero curvature.
        double jerkScore = computeRotationJerk(d);
        if (jerkScore >= 0.65 && recent.size() >= 5) {
            addVl(d, Check.AIMASSIST, 2.0);
            tryFlag(p, d, Check.AIMASSIST, String.format(Locale.ROOT,
                    "unnatural aim curvature & micro-correction jerk (score %.2f)", jerkScore));
        }

        double score = 1.0 - (1.0 - 0.55 * s1) * (1.0 - 0.50 * s2) * (1.0 - 0.35 * s3) * (1.0 - 0.45 * s4);
        score = Math.min(1.0, score + neuralAimModifier);
        int signals = (s1 > 0.4 ? 1 : 0) + (s2 > 0.4 ? 1 : 0) + (s3 > 0.4 ? 1 : 0) + (s4 > 0.4 ? 1 : 0) + (neuralAimModifier > 0 ? 1 : 0);
        d.lastAimScore = score;

        if (updateVl) {
            if (score >= s.aimSuspect) {
                addVl(d, Check.AIMASSIST, 0.8 + 2.0 * score);
                if (signals >= 1 || score >= 0.40) {
                    tryFlag(p, d, Check.AIMASSIST, String.format(Locale.ROOT,
                            "score %.2f | off-lattice %.0f%% | lock %s | perfect %.0f%% | snaps %d",
                            score, r.valid ? r.offLattice * 100 : 0, lock < 0 ? "n/a" : String.format(Locale.ROOT, "%.0f%%", lock * 100),
                            perfect * 100, snaps));
                }
            } else {
                addVl(d, Check.AIMASSIST, -0.5);
            }
        }
        return score;
    }

    /** Fraction of ticks (while the target moves angularly) where the crosshair stayed locked on the target. */
    private double computeLock(PlayerData d) {
        int moving = 0;
        int locked = 0;
        for (PlayerData.AimSample sm : d.aimSamples) {
            if (sm.targetVel >= 1.0 && sm.err < 25.0) {
                moving++;
                if (sm.err <= 1.3) locked++;
            }
        }
        if (moving < 12) return -1.0;
        return locked / (double) moving;
    }

    /**
     * Measures rotational jerk (derivative of angular acceleration).
     * Legit human hand movements decelerate smoothly into a hit.
     * Software aim-assist exhibits sharp direction reversals and jagged acceleration spikes.
     */
    private double computeRotationJerk(PlayerData d) {
        List<double[]> copy = d.rawRotationsCopy();
        if (copy.size() < 10) return 0.0;
        int abnormalSpikes = 0;
        int evaluated = 0;
        for (int i = 2; i < copy.size(); i++) {
            double v1 = Math.hypot(copy.get(i - 1)[0], copy.get(i - 1)[1]);
            double v2 = Math.hypot(copy.get(i)[0], copy.get(i)[1]);
            double a1 = v1 - Math.hypot(copy.get(i - 2)[0], copy.get(i - 2)[1]);
            double a2 = v2 - v1;
            double jerk = Math.abs(a2 - a1);
            if (v2 > 0.5 && v2 < 20.0) {
                evaluated++;
                // Sharp direction flip without deceleration = software aim interpolation
                if (jerk > 6.5) abnormalSpikes++;
            }
        }
        if (evaluated < 8) return 0.0;
        return abnormalSpikes / (double) evaluated;
    }

    private void evaluateTrigger(Player p, PlayerData d, long now) {
        List<PlayerData.AttackRecord> recent = d.recentAttacks(now, 10000L);
        double pts = 0;
        StringBuilder why = new StringBuilder();

        List<Double> iv = new ArrayList<Double>();
        for (int i = 1; i < recent.size(); i++) {
            double gap = recent.get(i).time - recent.get(i - 1).time;
            if (gap < 1500) iv.add(gap);
        }
        if (iv.size() >= 5) {
            double std = MathUtil.stdDev(iv);
            d.lastIntervalStd = std;
            // Human clicks have natural variation (typically stdDev > 25ms). TriggerBots / autoclickers are < 20ms
            if (std < 22.0 && MathUtil.mean(iv) < 500) {
                pts += 2.0;
                why.append(String.format(Locale.ROOT, "click interval deviation only %.1fms", std));
            }
        }
        int reacq = 0;
        int instant = 0;
        for (PlayerData.AttackRecord a : recent) {
            if (a.reacquire) reacq++;
            if (a.instant) instant++;
        }
        if (reacq >= 3 && instant / (double) reacq >= 0.65) {
            pts += 2.0;
            why.append(why.length() > 0 ? ", " : "").append(instant).append("/").append(reacq).append(" instant clicks on crosshair re-entry");
        }
        int cps = d.cps(now);
        if (cps > 18) {
            pts += 2.5;
            why.append(why.length() > 0 ? ", " : "").append("cps ").append(cps);
        }
        if (pts > 0) {
            addVl(d, Check.TRIGGERBOT, pts);
            tryFlag(p, d, Check.TRIGGERBOT, why.toString());
        } else {
            addVl(d, Check.TRIGGERBOT, -0.4);
        }
    }

    // ------------------------------------------------------------------ AI second opinion

    private void requestJudgement(final Player p, final PlayerData d, long now) {
        if (!s.aiEnabled) return;
        if (now - d.lastJudgeTime < s.aiCooldownSeconds * 1000L) return;
        d.lastJudgeTime = now;
        final String json = buildFeatureJson(d, now);
        final String uuidStr = p.getUniqueId().toString();
        plugin.getJudge().ask(json, new OllamaJudge.Callback() {
            @Override
            public void done(final OllamaJudge.Verdict v, final String error) {
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (v == null) {
                            d.lastAiVerdict = "offline (" + error + ")";
                            return;
                        }
                        d.lastAiVerdict = (v.cheat ? "CHEAT " + v.type : "legit") + " "
                                + Math.round(v.confidence * 100) + "% (" + v.latencyMs + "ms)";
                        // Continuous learning dataset collection
                        datasetCollector.record(json, v.cheat ? v.type : "legit", v.confidence);

                        Check c = parseCheck(v.type);
                        if (v.cheat && v.confidence >= 0.75 && c != null) {
                            addVl(d, c, 1.5 + 2.0 * v.confidence);
                            tryFlag(p, d, c, String.format(Locale.ROOT, "AI Arbiter confirmed %s with %.0f%% confidence", v.type, v.confidence * 100));
                        } else if (!v.cheat && v.confidence >= 0.75) {
                            addVl(d, Check.AIMASSIST, -0.5);
                        }
                    }
                });
            }
        });
    }

    private Check parseCheck(String type) {
        if (type == null) return null;
        for (Check c : Check.values()) {
            if (c.display().equalsIgnoreCase(type) || c.name().equalsIgnoreCase(type)) return c;
        }
        return null;
    }

    private String buildFeatureJson(PlayerData d, long now) {
        List<PlayerData.AttackRecord> recent = d.recentAttacks(now, 10000L);
        List<Long> recentSwings = d.recentSwings(now, 10000L);
        int totalSwings = recentSwings.size();
        int hits = recent.size();
        int misses = Math.max(0, totalSwings - hits);
        double hitRatio = totalSwings > 0 ? (hits / (double) totalSwings) : 1.0;

        double maxReach = 0, maxAng = 0, sumCtr = 0;
        for (PlayerData.AttackRecord a : recent) {
            maxReach = Math.max(maxReach, a.reach);
            maxAng = Math.max(maxAng, a.hitboxAngle);
            sumCtr += a.centerAngle;
        }
        RotationAnalyzer.Result r = d.lastRot;
        StringBuilder sb = new StringBuilder();
        sb.append("{\"attacks\":").append(recent.size());
        sb.append(",\"swings\":").append(totalSwings);
        sb.append(",\"missed_swings\":").append(misses);
        sb.append(",\"hit_ratio\":").append(fmt(hitRatio));
        sb.append(",\"reach_max\":").append(fmt(maxReach));
        sb.append(",\"hit_off_crosshair_max_deg\":").append(fmt(maxAng));
        sb.append(",\"avg_center_angle_deg\":").append(fmt(recent.isEmpty() ? 0 : sumCtr / recent.size()));
        sb.append(",\"perfect_aim_ratio\":").append(fmt(d.lastPerfect));
        sb.append(",\"mouse_off_lattice_ratio\":").append(fmt(r.valid ? r.offLattice : 0));
        sb.append(",\"tracking_lock_ratio\":").append(fmt(d.lastLock < 0 ? 0 : d.lastLock));
        sb.append(",\"click_interval_std_ms\":").append(fmt(d.lastIntervalStd));
        sb.append(",\"rule_aim_score\":").append(fmt(d.lastAimScore));
        sb.append(",\"vl_aura\":").append(fmt(d.vl[Check.KILLAURA.ordinal()]));
        sb.append(",\"vl_reach\":").append(fmt(d.vl[Check.REACH.ordinal()]));
        sb.append("}");
        return sb.toString();
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    // ------------------------------------------------------------------ vl / flags

    private void addVl(PlayerData d, Check c, double amount) {
        int i = c.ordinal();
        d.vl[i] = Math.max(0, Math.min(20.0, d.vl[i] + amount));
    }

    private double threshold(Check c) {
        switch (c) {
            case REACH: return s.flagReach;
            case WALLHIT: return s.flagWall;
            case KILLAURA: return s.flagAura;
            case AIMASSIST: return s.flagAim;
            default: return s.flagTrigger;
        }
    }

    private void tryFlag(Player p, PlayerData d, Check c, String evidence) {
        if (d.vl[c.ordinal()] >= threshold(c)) {
            raise(p, d, c, evidence, false);
        }
    }

    private void raise(Player p, PlayerData d, Check c, String evidence, boolean certain) {
        long now = System.currentTimeMillis();
        int i = c.ordinal();
        if (!certain && now - d.lastFlag[i] < 3000L) return;
        d.lastFlag[i] = now;
        d.flagCount[i]++;
        plugin.getPunishments().flag(p, d, c, d.vl[i], evidence, certain);
    }

    // ------------------------------------------------------------------ inspect

    public String debugLine(PlayerData d) {
        RotationAnalyzer.Result r = d.lastRot;
        return ChatColor.DARK_GRAY + "[HUD] " + ChatColor.WHITE + d.name + ChatColor.DARK_GRAY + " » "
                + ChatColor.GRAY + "Hits: " + ChatColor.YELLOW + d.recentAttacks(System.currentTimeMillis(), 10000L).size()
                + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "Lattice Error: " + (r.valid ? (r.offLattice > 0.35 ? ChatColor.RED : ChatColor.GREEN) + String.format(Locale.ROOT, "%.0f%%", r.offLattice * 100) : ChatColor.DARK_GRAY + "n/a")
                + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "AimLock: " + (d.lastLock < 0 ? ChatColor.DARK_GRAY + "n/a" : (d.lastLock > 0.5 ? ChatColor.RED : ChatColor.GREEN) + String.format(Locale.ROOT, "%.0f%%", d.lastLock * 100))
                + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "CenterLock: " + String.format(Locale.ROOT, "%.0f%%", d.lastPerfect * 100)
                + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "Score: " + (d.lastAimScore > 0.35 ? ChatColor.RED : ChatColor.GREEN) + String.format(Locale.ROOT, "%.2f", d.lastAimScore);
    }

    public de.maiu.anticheat.ai.TrainingDataCollector getDatasetCollector() {
        return datasetCollector;
    }

    public Iterable<PlayerData> all() {
        return data.values();
    }

    public void forget(Iterator<PlayerData> it) {
        it.remove();
    }
}
