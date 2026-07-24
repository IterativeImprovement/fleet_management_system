package com.siyu.fleet_mgmt_sys.model;

import com.siyu.fleet_mgmt_sys.model.enums.RobotType;
import com.siyu.fleet_mgmt_sys.model.task.Task;
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

    @Column(columnDefinition = "TEXT")
    private String routeGeo;            // Google encoded polyline

    @OneToOne
    private Task task;

    private Integer totalDistance;      // metres

    private Double totalTime;           // seconds 

    @ElementCollection
    @MapKeyEnumerated(EnumType.STRING)
    private Map<RobotType, Double> estimatedTimes;  // per robot type, seconds
}