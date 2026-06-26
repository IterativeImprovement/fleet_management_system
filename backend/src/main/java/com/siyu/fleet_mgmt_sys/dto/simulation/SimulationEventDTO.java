package com.siyu.fleet_mgmt_sys.dto.simulation;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulationEventDTO {

    private Long eventId;
    private String eventType;
    private double simTime;

    // TASK_CREATED
    private String taskName;
    private String taskDescription;
    private Integer taskPriority;
    private String taskType;
    private Long startWaypointId;
    private Long endWaypointId;
    private double completionDeadline;
    private List<Long> dependencyEventIds;

    // ROBOT_MALFUNCTION
    private Long robotId;

    // ROUTE_OBSTRUCTION
    private Long linkId;
}