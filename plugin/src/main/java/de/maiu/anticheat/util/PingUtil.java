package de.maiu.anticheat.util;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Version independent helpers (Bukkit 1.8 - 1.21) that are not in the 1.8 API we compile against. */
public final class PingUtil {

    private PingUtil() {}

    private static Method getPing;
    private static boolean getPingChecked;
    private static Method getHandle;
    private static Field pingField;
    private static boolean fieldChecked;

    public static int ping(Player p) {
        try {
            if (!getPingChecked) {
                getPingChecked = true;
                try {
                    getPing = Player.class.getMethod("getPing");
                } catch (Throwable ignored) {
                    getPing = null;
                }
            }
            if (getPing != null) {
                return (Integer) getPing.invoke(p);
            }
            if (!fieldChecked) {
                fieldChecked = true;
                getHandle = p.getClass().getMethod("getHandle");
                Object handle = getHandle.invoke(p);
                try {
                    pingField = handle.getClass().getField("ping");
                } catch (Throwable ignored) {
                    pingField = null;
                }
            }
            if (pingField != null && getHandle != null) {
                return pingField.getInt(getHandle.invoke(p));
            }
        } catch (Throwable ignored) {
        }
        return 50;
    }

    private static Method getWidth;
    private static Method getHeight;
    private static boolean sizeChecked;

    /** @return {width, height} of an entity's hitbox. */
    public static double[] size(Entity e) {
        try {
            if (!sizeChecked) {
                sizeChecked = true;
                try {
                    getWidth = Entity.class.getMethod("getWidth");
                    getHeight = Entity.class.getMethod("getHeight");
                } catch (Throwable ignored) {
                    getWidth = null;
                    getHeight = null;
                }
            }
            if (getWidth != null && getHeight != null) {
                return new double[]{(Double) getWidth.invoke(e), (Double) getHeight.invoke(e)};
            }
        } catch (Throwable ignored) {
        }
        if (e instanceof Player) {
            return new double[]{0.6, ((Player) e).isSneaking() ? 1.65 : 1.8};
        }
        return new double[]{0.6, 1.8};
    }
}
