package com.siyu.fleet_mgmt_sys.model;

import com.siyu.fleet_mgmt_sys.model.enums.RobotType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Entity
@Table(name = "routes")
@Getter
@Setter
@NoArgsConstructor
public class Route {
    @Id
    @GeneratedValue
    private Long id;

    private String routeGeo;

    @OneToOne
    private Task task;

    private int totalDistance; // in metres

    @ElementCollection
    @MapKeyEnumerated(EnumType.STRING)
    private Map<RobotType, Double> estimatedTimes; // estimated time of in seconds, array of different robots' estimated times
}