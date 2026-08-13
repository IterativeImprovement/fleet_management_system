package com.siyu.fleet_mgmt_sys.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpeedBandUtilsTest {

    // Equivalence partitions: each valid band 1-8 maps to its midpoint m/s.
    @ParameterizedTest
    @CsvSource({
            "1, 1.4",
            "2, 4.2",
            "3, 6.9",
            "4, 9.7",
            "5, 12.5",
            "6, 15.3",
            "7, 18.1",
            "8, 22.2",
    })
    void mapsEachBandToExpectedSpeed(int band, double expected) {
        assertEquals(expected, SpeedBandUtils.toMetresPerSecond(band));
    }

    @Test
    void obstructedBandIsImpassable() {
        assertEquals(0, SpeedBandUtils.toMetresPerSecond(0));
    }

    @ParameterizedTest
    @ValueSource(ints = {9, -1})
    void outOfRangeBandFallsBackToDefault(int band) {
        assertEquals(12.5, SpeedBandUtils.toMetresPerSecond(band));
    }
}
