package com.siyu.fleet_mgmt_sys.util;

public class SpeedBandUtils {

    private SpeedBandUtils() {}

    public static double toMetresPerSecond(int speedBand) {
        return switch (speedBand) {
            case 1 -> 25.0;   // 90 km/h
            case 2 -> 22.2;   // 80 km/h
            case 3 -> 16.7;   // 60 km/h
            case 4 -> 12.5;   // 45 km/h
            case 5 -> 9.7;    // 35 km/h
            case 6 -> 6.9;    // 25 km/h
            case 7 -> 4.2;    // 15 km/h
            case 8 -> 2.8;    // 10 km/h
            default -> 6.9;
        };
    }
}