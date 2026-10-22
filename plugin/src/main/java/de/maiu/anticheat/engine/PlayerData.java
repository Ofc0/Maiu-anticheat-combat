package de.maiu.anticheat.engine;

import de.maiu.anticheat.util.MathUtil;
import org.bukkit.entity.Entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * All per-player combat state. Rotation data is written from the netty thread, everything else
 * is main-thread only.
 */
public class PlayerData {

    public static final class AttackRecord {
        public long time;
        public int targetId;
        public double reach;          // lag-compensated eye->hitbox distance
        public double hitboxAngle;    // degrees between crosshair and nearest hitbox point (0 = crosshair on target)
        public double centerAngle;    // degrees between crosshair and target torso centre
        public boolean wall;
        public double rotBefore;      // total camera movement (deg) within 120ms before the hit
        public double yawBefore;      // yaw movement (deg) within 120ms before the hit
        public boolean reacquire;     // crosshair re-entered the hitbox right before the click
        public boolean instant;       // ... and the click followed within ~1 tick
        public org.bukkit.util.Vector dir; // normalized eye -> target torso
    }

    public static final class AimSample {
        public long time;
        public double err;            // angle crosshair -> target torso
        public double errYaw;         // signed
        public double targetVel;      // target angular velocity (deg / tick)
        public double playerYaw;      // players yaw delta this tick (signed)
        public boolean onTarget;
    }

    public final UUID uuid;
    public final String name;

    // ---- rotation (netty thread writer, main reader) ----
    private final Object rotLock = new Object();
    private float lastYaw, lastPitch;
    private boolean hasLastRot;
    private long ignoreRotUntil;
    private final ArrayDeque<double[]> rot = new ArrayDeque<double[]>(); // {time, dYaw, dPitch}
    private double accYaw, accPitch;
    private static final int MAX_ROT = 160;

    // ---- swings (netty thread writer) ----
    private final ArrayDeque<Long> swings = new ArrayDeque<Long>();

    // ---- main thread state ----
    public final ArrayDeque<AttackRecord> attacks = new ArrayDeque<AttackRecord>();
    public final ArrayDeque<AimSample> aimSamples = new ArrayDeque<AimSample>();
    public final ArrayDeque<double[]> posHistory = new ArrayDeque<double[]>(); // {time,x,y,z}
    public Entity lastTarget;
    public long lastAttackTime;
    public double prevBearingYaw = Double.NaN;
    public long offTargetSince;      // when crosshair left the hitbox (ms), 0 if currently on target
    public long onTargetSince;       // when crosshair entered the hitbox (ms)
    public boolean wasOnTarget;

    public final double[] vl = new double[Check.values().length];
    public final long[] lastFlag = new long[Check.values().length];
    public final int[] flagCount = new int[Check.values().length];

    public long flaggedUntil;        // punishment window (damage reduction)
    public long lastDecoyTime;
    public long decoyStartTime;
    public long lastJudgeTime;
    public boolean debug;
    public String lastAiVerdict = "-";

    // cached evaluation results (for /maiu inspect)
    public RotationAnalyzer.Result lastRot = RotationAnalyzer.INVALID;
    public double lastLock;
    public double lastAimScore;
    public double lastPerfect;
    public double lastIntervalStd;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    // ------------------------------------------------------------------ rotation

    public void onRotation(float yaw, float pitch, long now) {
        synchronized (rotLock) {
            if (!hasLastRot) {
                lastYaw = yaw;
                lastPitch = pitch;
                hasLastRot = true;
                return;
            }
            double dy = MathUtil.wrapAngleTo180(yaw - lastYaw);
            double dp = pitch - lastPitch;
            lastYaw = yaw;
            lastPitch = pitch;
            if (now < ignoreRotUntil) return;
            if (dy == 0.0 && dp == 0.0) return;
            accYaw += dy;
            accPitch += dp;
            rot.addLast(new double[]{now, dy, dp});
            if (rot.size() > MAX_ROT) rot.pollFirst();
        }
    }

    public void ignoreRotation(long untilMs) {
        synchronized (rotLock) {
            ignoreRotUntil = Math.max(ignoreRotUntil, untilMs);
            hasLastRot = false;
        }
    }

    public void resetRotation() {
        synchronized (rotLock) {
            rot.clear();
            accYaw = 0;
            accPitch = 0;
        }
    }

    /** {yaw, pitch} accumulated since last call. */
    public double[] drainRotation() {
        synchronized (rotLock) {
            double[] r = new double[]{accYaw, accPitch};
            accYaw = 0;
            accPitch = 0;
            return r;
        }
    }

    /** {sumAbsYaw, sumAbsTotal} of camera movement in the last windowMs. */
    public double[] rotationWithin(long now, long windowMs) {
        double yaw = 0, total = 0;
        synchronized (rotLock) {
            for (double[] e : rot) {
                if (now - (long) e[0] <= windowMs) {
                    yaw += Math.abs(e[1]);
                    total += Math.abs(e[1]) + Math.abs(e[2]);
                }
            }
        }
        return new double[]{yaw, total};
    }

    public RotationAnalyzer.Result analyzeRotation() {
        List<double[]> copy;
        synchronized (rotLock) {
            copy = new ArrayList<double[]>(rot.size());
            for (double[] e : rot) copy.add(new double[]{e[1], e[2]});
        }
        lastRot = RotationAnalyzer.analyze(copy);
        return lastRot;
    }

    // ------------------------------------------------------------------ swings

    public void onSwing(long now) {
        synchronized (swings) {
            swings.addLast(now);
            while (!swings.isEmpty() && now - swings.peekFirst() > 1000L) swings.pollFirst();
        }
    }

    public int cps(long now) {
        synchronized (swings) {
            while (!swings.isEmpty() && now - swings.peekFirst() > 1000L) swings.pollFirst();
            return swings.size();
        }
    }

    // ------------------------------------------------------------------ attacks

    public void addAttack(AttackRecord r) {
        attacks.addLast(r);
        while (attacks.size() > 40) attacks.pollFirst();
    }

    public List<AttackRecord> recentAttacks(long now, long windowMs) {
        List<AttackRecord> out = new ArrayList<AttackRecord>();
        for (AttackRecord a : attacks) {
            if (now - a.time <= windowMs) out.add(a);
        }
        return out;
    }

    public void addAimSample(AimSample s) {
        aimSamples.addLast(s);
        while (aimSamples.size() > 80) aimSamples.pollFirst();
    }

    public void clearCombat() {
        attacks.clear();
        aimSamples.clear();
        lastTarget = null;
        resetRotation();
        for (int i = 0; i < vl.length; i++) {
            vl[i] = 0;
            flagCount[i] = 0;
        }
        flaggedUntil = 0;
    }
}
