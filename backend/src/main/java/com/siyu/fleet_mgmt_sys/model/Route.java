package com.siyu.fleet_mgmt_sys.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "routes")
public class Route {
    @Id
    private Long id;

    @OneToOne
    private Robot robot;

}