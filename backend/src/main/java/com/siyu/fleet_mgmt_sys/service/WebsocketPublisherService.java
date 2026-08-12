package com.siyu.fleet_mgmt_sys.service;

import com.siyu.fleet_mgmt_sys.dto.dispatch.DispatchDTO;
import com.siyu.fleet_mgmt_sys.model.Route;
import com.siyu.fleet_mgmt_sys.model.enums.RobotStatus;
import com.siyu.fleet_mgmt_sys.model.simulation.SimulationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class WebsocketPublisherService {

    private final SimpMessagingTemplate template;

    public void publishSimulationEvent(SimulationEvent event) {
        template.convertAndSend("/topic/simulation", event);
    }

    public void publishDispatch(Long simulationId, DispatchDTO dispatch) {
        template.convertAndSend("/topic/simulation/" + simulationId + "/dispatch", dispatch);
    }

     public void publishReroute(Long robotId, Route newRoute) {
        template.convertAndSend("/topic/robot/" + robotId + "/reroute", newRoute);
    }

     public void publishObstruction(String linkId) {
        template.convertAndSend("/topic/obstruction", linkId);
    }

    public void publishObstructionCleared(String linkId) {
        template.convertAndSend("/topic/obstruction/cleared", linkId);
    }

    public void publishRerouteFailed(Long robotId, String linkId) {
        template.convertAndSend("/topic/robot/" + robotId + "/reroute/failed", linkId);
    }

    public void publishRobotStatus(Long robotId, RobotStatus status) {
        template.convertAndSend("/topic/robot/" + robotId + "/status", status);
    }

    public void publishRepairComplete(Long robotId) {
        template.convertAndSend("/topic/robot/" + robotId + "/repair/complete", robotId);
    }

    public void publishRobotStatus(Long robotId, Long simulationId, RobotStatus status) {
        template.convertAndSend(
                "/topic/simulation/" + simulationId + "/repair",
                (Object) Map.of("robotId", robotId, "status", status.name())
        );
    }
}