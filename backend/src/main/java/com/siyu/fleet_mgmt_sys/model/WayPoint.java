package com.siyu.fleet_mgmt_sys.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "WayPoint")
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
    private long id;

    private double latitude;
    private double longitude;

    private String routeGeo;

    public WayPoint(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public WayPoint(String latlng) {
        String[] parts = latlng.split(",");
        this.latitude = Double.parseDouble(parts[0].trim());
        this.longitude = Double.parseDouble(parts[1].trim());
        //System.out.println("WayPoint created: " + this.latitude + ", " + this.longitude);
    }

    @Override
    public String toString() {
        return this.latitude + "," + this.longitude;
    }
}
