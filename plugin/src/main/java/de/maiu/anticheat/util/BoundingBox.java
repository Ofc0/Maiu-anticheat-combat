package de.maiu.anticheat.util;

import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * Version-independent 3D Axis-Aligned Bounding Box (AABB) implementation.
 * Compatible with Minecraft 1.8.9 through 1.21+ without NMS dependencies.
 */
public class BoundingBox {

    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;

    public BoundingBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    /**
     * Create bounding box for standard entities like Player (width 0.6, height 1.8)
     */
    public static BoundingBox fromEntity(Location loc, double width, double height) {
        double halfWidth = width / 2.0;
        return new BoundingBox(
                loc.getX() - halfWidth,
                loc.getY(),
                loc.getZ() - halfWidth,
                loc.getX() + halfWidth,
                loc.getY() + height,
                loc.getZ() + halfWidth
        );
    }

    public BoundingBox expand(double expansion) {
        return new BoundingBox(
                minX - expansion, minY - expansion, minZ - expansion,
                maxX + expansion, maxY + expansion, maxZ + expansion
        );
    }

    /**
     * Calculates the minimum ray distance from eye origin along direction vector to the AABB.
     * Returns -1 if no intersection occurs within maxDistance.
     */
    public double calculateRayDistance(Vector origin, Vector direction, double maxDistance) {
        double dirX = direction.getX();
        double dirY = direction.getY();
        double dirZ = direction.getZ();

        double tMinX = (dirX != 0.0) ? (minX - origin.getX()) / dirX : (minX - origin.getX() >= 0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
        double tMaxX = (dirX != 0.0) ? (maxX - origin.getX()) / dirX : (maxX - origin.getX() >= 0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);

        if (tMinX > tMaxX) {
            double temp = tMinX;
            tMinX = tMaxX;
            tMaxX = temp;
        }

        double tMinY = (dirY != 0.0) ? (minY - origin.getY()) / dirY : (minY - origin.getY() >= 0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
        double tMaxY = (dirY != 0.0) ? (maxY - origin.getY()) / dirY : (maxY - origin.getY() >= 0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);

        if (tMinY > tMaxY) {
            double temp = tMinY;
            tMinY = tMaxY;
            tMaxY = temp;
        }

        if (tMinX > tMaxY || tMinY > tMaxX) {
            return -1.0;
        }

        double tMin = Math.max(tMinX, tMinY);
        double tMax = Math.min(tMaxX, tMaxY);

        double tMinZ = (dirZ != 0.0) ? (minZ - origin.getZ()) / dirZ : (minZ - origin.getZ() >= 0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
        double tMaxZ = (dirZ != 0.0) ? (maxZ - origin.getZ()) / dirZ : (maxZ - origin.getZ() >= 0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);

        if (tMinZ > tMaxZ) {
            double temp = tMinZ;
            tMinZ = tMaxZ;
            tMaxZ = temp;
        }

        if (tMin > tMaxZ || tMinZ > tMax) {
            return -1.0;
        }

        tMin = Math.max(tMin, tMinZ);
        tMax = Math.min(tMax, tMaxZ);

        if (tMax < 0.0) {
            return -1.0;
        }

        double hitDist = tMin >= 0.0 ? tMin : tMax;
        return hitDist <= maxDistance ? hitDist : -1.0;
    }

    public double getCenterX() { return (minX + maxX) / 2.0; }
    public double getCenterY() { return (minY + maxY) / 2.0; }
    public double getCenterZ() { return (minZ + maxZ) / 2.0; }

    public Vector getCenter() {
        return new Vector(getCenterX(), getCenterY(), getCenterZ());
    }

    public double getMinX() { return minX; }
    public double getMinY() { return minY; }
    public double getMinZ() { return minZ; }
    public double getMaxX() { return maxX; }
    public double getMaxY() { return maxY; }
    public double getMaxZ() { return maxZ; }
}
