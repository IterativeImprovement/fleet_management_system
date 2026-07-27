package com.siyu.fleet_mgmt_sys.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "waypoints")
@Getter
@Setter
@NoArgsConstructor
public class WayPoint {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "waypoint_seq") // this is for efficiency purposes
    @SequenceGenerator(
            name = "waypoint_seq",
            sequenceName = "waypoint_seq",
            allocationSize = 50
    )
    private Long id;

    private double latitude;
    private double longitude;

    public WayPoint(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public WayPoint(String latlng) {
        if (latlng == null || latlng.isBlank()) {
            throw new IllegalArgumentException("must be in \"latitude,longitude\" format (got an empty value)");
        }

        String[] parts = latlng.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("must be in \"latitude,longitude\" format (got \"" + latlng + "\")");
        }

        this.latitude = parseCoordinate(parts[0], "latitude", -90, 90, latlng);
        this.longitude = parseCoordinate(parts[1], "longitude", -180, 180, latlng);
        System.out.println("WayPoint created: " + this.latitude + ", " + this.longitude);
    }

    // parses one half of a "lat,lng" string, rejecting anything that isn't a real coordinate
    private static double parseCoordinate(String raw, String label, double min, double max, String original) {
        double value;
        try {
            value = Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be a valid number (got \"" + original + "\")");
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(label + " must be a valid number (got \"" + original + "\")");
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + " must be between " + min + " and " + max + " (got " + value + ")");
        }
        return value;
    }

    @Override
    public String toString() {
        return this.latitude + "," + this.longitude;
    }
}
