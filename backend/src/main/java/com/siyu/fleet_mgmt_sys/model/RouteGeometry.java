package com.siyu.fleet_mgmt_sys.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "routegeo")
@Getter
@Setter
@NoArgsConstructor
public class RouteGeometry {

    @Id
    @GeneratedValue
    private Long id;

    @OneToMany
    private List<WayPoint> wayPoints;

    private String routeGeoString;

}
