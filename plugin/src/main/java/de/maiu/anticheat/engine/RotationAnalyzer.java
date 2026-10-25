package de.maiu.anticheat.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hi there! Welcome to the RotationAnalyzer.
 *
 * If you are learning Java or how game engines work, here is a cool secret:
 * In Minecraft, your mouse does NOT move in smooth infinite decimals.
 * Your physical mouse sends discrete integer counts ("dots"). The Minecraft client takes that
 * count and multiplies it by a formula: (sensitivity * 0.6 + 0.2)^3 * 8 * 0.15.
 *
 * This means every genuine physical mouse turn is ALWAYS an exact multiple of that "step"!
 * Cheat clients (like AimAssist or smoothing algorithms) calculate smooth angles using float math,
 * which results in numbers that do NOT land on this physical grid ("off-lattice").
 *
 * What we do here is mathematically estimate what the player's mouse step is,
 * and count how many camera movements break this physical rule!
 */
public final class RotationAnalyzer {

    // Private constructor so nobody accidentally does `new RotationAnalyzer()`.
    // It is just a utility class filled with static helper methods!
    private RotationAnalyzer() {}

    /**
     * A clean, immutable result object holding our analysis findings.
     * In Java, making fields `public final` is a great design pattern when an object
     * is purely used to return multiple values from a function without setters.
     */
    public static final class Result {
        public final boolean valid;       // True if we had enough data to make an accurate call
        public final int samples;         // How many camera movement ticks we checked
        public final double step;         // The estimated physical mouse step in degrees
        public final double offLattice;   // The percentage (0.0 to 1.0) of moves that violate mouse physics

        Result(boolean valid, int samples, double step, double offLattice) {
            this.valid = valid;
            this.samples = samples;
            this.step = step;
            this.offLattice = offLattice;
        }
    }

    // A handy fallback object to return when there isn't enough data yet (e.g. player just joined)
    public static final Result INVALID = new Result(false, 0, 0, 0);

    /**
     * Main analysis method:
     * Takes a list of raw camera differences: {deltaYaw, deltaPitch}.
     */
    public static Result analyze(List<double[]> deltas) {
        // Step 1: Filter out noise and impossible values
        List<Double> v = new ArrayList<Double>();
        for (double[] d : deltas) {
            double ay = Math.abs(d[0]);
            double ap = Math.abs(d[1]);
            // If delta is smaller than 0.0005, the mouse didn't really move (sensor noise).
            // If delta is bigger than 60 degrees, it's a huge teleport or spin, not normal aiming.
            if (ay > 0.0005 && ay < 60.0) v.add(ay);
            if (ap > 0.0005 && ap < 60.0) v.add(ap);
        }
        // Need at least 24 movements so we don't jump to conclusions on a tiny sample size!
        if (v.size() < 24) return INVALID;

        // Step 2: Sort all movements from smallest to largest
        List<Double> sorted = new ArrayList<Double>(v);
        Collections.sort(sorted);

        // Step 3: Find potential candidate steps.
        // The smallest movements are usually 1 single mouse count (or 2 or 3 counts).
        List<Double> cands = new ArrayList<Double>();
        int take = Math.min(24, sorted.size());
        for (int i = 0; i < take; i++) {
            double s = sorted.get(i);
            for (int k = 1; k <= 3; k++) {
                double c = s / k;
                // Minecraft sensitivity step never goes below ~0.004 degrees in practice
                if (c >= 0.004) cands.add(c);
            }
        }
        if (cands.isEmpty()) return INVALID;

        // Step 4: Test each candidate step against all player movements and see which one fits best
        double bestScore = -1;
        double[] scores = new double[cands.size()];
        for (int i = 0; i < cands.size(); i++) {
            scores[i] = score(v, cands.get(i));
            if (scores[i] > bestScore) bestScore = scores[i];
        }

        // Step 5: Pick the largest step that explains the movements well.
        // This prevents picking half-steps or quarter-steps!
        double chosen = 0;
        double chosenScore = bestScore;
        for (int i = 0; i < cands.size(); i++) {
            if (scores[i] >= bestScore - 0.03 && cands.get(i) > chosen) {
                chosen = cands.get(i);
                chosenScore = scores[i];
            }
        }
        return new Result(true, v.size(), chosen, 1.0 - chosenScore);
    }

    /**
     * Scores how well a candidate step explains the observed rotation deltas.
     * Uses IEEE-754 epsilon scaling and integer rounding tolerance to handle
     * floating-point drift and packet quantization jitter.
     */
    private static double score(List<Double> v, double step) {
        int ok = 0;
        // Dynamic epsilon: larger step has tighter tolerance; smaller step has slight padding
        // to avoid floating-point drift false flags on ultra-low sensitivities.
        double tol = Math.max(0.06, Math.min(0.12, 0.05 + 2.0E-4 / step));
        for (double d : v) {
            double q = d / step;
            double nearestInteger = Math.rint(q);
            if (Math.abs(q - nearestInteger) < tol) {
                ok++;
            }
        }
        return ok / (double) v.size();
    }
}
