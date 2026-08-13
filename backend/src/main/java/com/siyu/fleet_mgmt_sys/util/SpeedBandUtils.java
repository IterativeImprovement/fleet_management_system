package com.siyu.fleet_mgmt_sys.util;

public class SpeedBandUtils {

    private SpeedBandUtils() {}

    public static double toMetresPerSecond(int speedBand) {
        // Uses the approximate midpoint of each LTA km/h range
        return switch (speedBand) {
            case 0 -> 0;
            case 1 -> 1.4;    // ~5 km/h
            case 2 -> 4.2;    // ~15 km/h
            case 3 -> 6.9;    // ~25 km/h
            case 4 -> 9.7;    // ~35 km/h
            case 5 -> 12.5;   // ~45 km/h
            case 6 -> 15.3;   // ~55 km/h
            case 7 -> 18.1;   // ~65 km/h
            case 8 -> 22.2;   // ~80 km/h (70+ km/h range)
            default -> 12.5;  // Default fallback (~45 km/h)
        };
    }
}