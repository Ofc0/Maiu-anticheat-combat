package de.maiu.anticheat.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mouse-lattice analysis ("GCD analysis").
 *
 * A real mouse produces camera deltas that are integer multiples of one sensitivity step
 * (step = (sens*0.6+0.2)^3 * 8 * 0.15). Aim assist / aimbot code adds values that are NOT on that lattice.
 * We estimate the step directly from the data (no sensitivity needed) and measure how many deltas
 * fall off the lattice.
 */
public final class RotationAnalyzer {

    private RotationAnalyzer() {}

    public static final class Result {
        public final boolean valid;
        public final int samples;
        /** Estimated sensitivity step in degrees. */
        public final double step;
        /** Fraction (0..1) of rotation deltas that are NOT multiples of the step. Legit mouse ~0, aim assist high. */
        public final double offLattice;

        Result(boolean valid, int samples, double step, double offLattice) {
            this.valid = valid;
            this.samples = samples;
            this.step = step;
            this.offLattice = offLattice;
        }
    }

    public static final Result INVALID = new Result(false, 0, 0, 0);

    /**
     * @param deltas signed yaw and pitch deltas (degrees) as {dYaw, dPitch} pairs.
     */
    public static Result analyze(List<double[]> deltas) {
        List<Double> v = new ArrayList<Double>();
        for (double[] d : deltas) {
            double ay = Math.abs(d[0]);
            double ap = Math.abs(d[1]);
            if (ay > 0.0005 && ay < 60.0) v.add(ay);
            if (ap > 0.0005 && ap < 60.0) v.add(ap);
        }
        if (v.size() < 24) return INVALID;

        List<Double> sorted = new ArrayList<Double>(v);
        Collections.sort(sorted);

        // candidate steps: the smallest observed deltas (one mouse count) and their halves/thirds
        List<Double> cands = new ArrayList<Double>();
        int take = Math.min(24, sorted.size());
        for (int i = 0; i < take; i++) {
            double s = sorted.get(i);
            for (int k = 1; k <= 3; k++) {
                double c = s / k;
                if (c >= 0.004) cands.add(c);
            }
        }
        if (cands.isEmpty()) return INVALID;

        double bestScore = -1;
        double[] scores = new double[cands.size()];
        for (int i = 0; i < cands.size(); i++) {
            scores[i] = score(v, cands.get(i));
            if (scores[i] > bestScore) bestScore = scores[i];
        }

        // largest step that is (almost) as good as the best -> avoids picking sub-multiples
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

    private static double score(List<Double> v, double step) {
        int ok = 0;
        double tol = 0.08 + 1.0E-4 / step;
        for (double d : v) {
            double q = d / step;
            if (Math.abs(q - Math.rint(q)) < tol) ok++;
        }
        return ok / (double) v.size();
    }
}
