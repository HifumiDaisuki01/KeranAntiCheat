package com.keran.kac.util;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public final class CombatGeometry {
    private CombatGeometry() {}
    public static double distanceToBox(Vector point, BoundingBox box) {
        double x = Math.max(box.getMinX(), Math.min(box.getMaxX(), point.getX())) - point.getX();
        double y = Math.max(box.getMinY(), Math.min(box.getMaxY(), point.getY())) - point.getY();
        double z = Math.max(box.getMinZ(), Math.min(box.getMaxZ(), point.getZ())) - point.getZ();
        return Math.sqrt(x*x + y*y + z*z);
    }
}
