package com.siyu.fleet_mgmt_sys.model.simulation;


import com.siyu.fleet_mgmt_sys.model.enums.SimulationEventType;
import com.siyu.fleet_mgmt_sys.model.enums.TaskType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Each event is discrete.
 *
 * The simulation is only responsible for generating task creation events and road blockage events.
 */

@NoArgsConstructor
@Setter
@Getter
public class SimulationEvent {

    private Long eventId;
    private SimulationEventType eventType;
    private double simTime;

    // TASK_CREATED only
    private String taskName;
    private String taskDescription;
    private Integer taskPriority;
    private TaskType taskType;
    private Long startWaypointId;
    private Long endWaypointId;
    private String startWaypointName;
    private String endWaypointName;

    // ROBOT_MALFUNCTION only
    private Long robotId;
    private String robotName;

    // ROUTE_OBSTRUCTION only
    private Long roadSegmentId; // this is based on LTA's definition and id of a road
    private String roadSegmentName;

    private List<Long> dependencyEventIds = new ArrayList<>();
    private double completionDeadline;

    public void setRoadS(String unknownRoad) {
    }
}