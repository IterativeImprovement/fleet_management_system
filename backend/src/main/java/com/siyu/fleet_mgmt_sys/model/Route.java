package com.siyu.fleet_mgmt_sys.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "routes")
@Getter
@Setter
@NoArgsConstructor
public class Route {
    @Id
    private Long id;

    @OneToOne
    private Robot robot;

    @OneToOne
    private RouteGeometry routeGeo;
}