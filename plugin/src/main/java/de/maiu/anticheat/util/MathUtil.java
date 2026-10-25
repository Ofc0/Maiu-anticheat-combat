package de.maiu.anticheat.util;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import java.util.List;

public final class MathUtil {

    private MathUtil() {}

    public static float wrapAngleTo180(float angle) {
        angle = angle % 360.0F;
        if (angle >= 180.0F) angle -= 360.0F;
        if (angle < -180.0F) angle += 360.0F;
        return angle;
    }

    public static double wrap180(double angle) {
        angle = angle % 360.0;
        if (angle >= 180.0) angle -= 360.0;
        if (angle < -180.0) angle += 360.0;
        return angle;
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** Maps v from [lo,hi] to [0,1]. */
    public static double ramp(double v, double lo, double hi) {
        return clamp((v - lo) / (hi - lo), 0.0, 1.0);
    }

    /** Angle in degrees between two vectors. */
    public static double angleBetween(Vector a, Vector b) {
        double la = a.length();
        double lb = b.length();
        if (la < 1.0E-9 || lb < 1.0E-9) return 0.0;
        double dot = a.dot(b) / (la * lb);
        return Math.toDegrees(Math.acos(clamp(dot, -1.0, 1.0)));
    }

    /** Nearest point on an AABB to a position. */
    public static Vector nearestPoint(BoundingBox box, Vector p) {
        return new Vector(
                clamp(p.getX(), box.getMinX(), box.getMaxX()),
                clamp(p.getY(), box.getMinY(), box.getMaxY()),
                clamp(p.getZ(), box.getMinZ(), box.getMaxZ()));
    }

    public static double mean(List<Double> v) {
        if (v.isEmpty()) return 0.0;
        double s = 0;
        for (double d : v) s += d;
        return s / v.size();
    }

    public static double stdDev(List<Double> v) {
        if (v.size() < 2) return 0.0;
        double m = mean(v);
        double s = 0;
        for (double d : v) s += (d - m) * (d - m);
        return Math.sqrt(s / (v.size() - 1));
    }

    /**
     * True if a solid, occluding block lies between from and to (ignoring the first/last 0.3 blocks).
     * Only inspects loaded chunks so it can never trigger a synchronous chunk load.
     */
    @SuppressWarnings("deprecation")
    public static boolean isBlocked(World world, Vector from, Vector to) {
        Vector dir = to.clone().subtract(from);
        double len = dir.length();
        if (len < 0.6) return false;
        dir.multiply(1.0 / len);
        double step = 0.2;
        for (double d = 0.3; d < len - 0.3; d += step) {
            int x = (int) Math.floor(from.getX() + dir.getX() * d);
            int y = (int) Math.floor(from.getY() + dir.getY() * d);
            int z = (int) Math.floor(from.getZ() + dir.getZ() * d);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block b = world.getBlockAt(x, y, z);
            org.bukkit.Material m = b.getType();
            if (m.isSolid() && m.isOccluding()) {
                String n = m.name();
                if (!n.contains("LEAVES") && !n.contains("GLASS") && !n.contains("ICE")
                        && !n.contains("STAIRS") && !n.contains("STEP") && !n.contains("SLAB")
                        && !n.contains("FENCE") && !n.contains("DOOR") && !n.contains("TRAP")
                        && !n.contains("PANE") && !n.contains("WALL") && !n.contains("CHEST")
                        && !n.contains("ENCHANT") && !n.contains("BED") && !n.contains("CARPET")
                        && !n.contains("CAULDRON") && !n.contains("ANVIL") && !n.contains("BELL")
                        && !n.contains("GRINDSTONE") && !n.contains("LECTERN") && !n.contains("HOPPER")
                        && !n.contains("SCAFFOLDING") && !n.contains("CHAIN") && !n.contains("LANTERN")) {
                    return true;
                }
            }
        }
        return false;
    }
}
