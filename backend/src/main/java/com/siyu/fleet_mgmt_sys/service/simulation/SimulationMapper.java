package com.siyu.fleet_mgmt_sys.service.simulation;

import com.siyu.fleet_mgmt_sys.dto.simulation.SimulationEventDTO;
import com.siyu.fleet_mgmt_sys.dto.simulation.SimulationResultDTO;
import com.siyu.fleet_mgmt_sys.model.simulation.SimulationEvent;
import com.siyu.fleet_mgmt_sys.model.simulation.SimulationResult;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class SimulationMapper {

    public SimulationResultDTO toDTO(SimulationResult result) {
        return SimulationResultDTO.builder()
                .seed(result.getSeed())
                .simulationId(result.getSimulationId())
                .events(result.getEvents().stream()
                        .map(this::toEventDTO)
                        .collect(Collectors.toList()))
                .build();
    }

    private SimulationEventDTO toEventDTO(SimulationEvent event) {
        return SimulationEventDTO.builder()
                .eventId(event.getEventId())
                .eventType(event.getEventType().name())
                .simTime(event.getSimTime())
                .taskName(event.getTaskName())
                .taskDescription(event.getTaskDescription())
                .taskPriority(event.getTaskPriority())
                .taskType(event.getTaskType() != null ? event.getTaskType().name() : null)
                .startWaypointId(event.getStartWaypointId())
                .endWaypointId(event.getEndWaypointId())
                .completionDeadline(event.getCompletionDeadline())
                .dependencyEventIds(event.getDependencyEventIds())
                .robotId(event.getRobotId())
                .linkId(event.getRoadSegmentId())
                .build();
    }
}