package com.siyu.fleet_mgmt_sys.service;

import com.siyu.fleet_mgmt_sys.model.Route;
import com.siyu.fleet_mgmt_sys.model.simulation.SimulationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WebsocketPublisherService {

    private final SimpMessagingTemplate template;

    // Push simulation events to frontend
    public void publishSimulationEvent(SimulationEvent event) {
        template.convertAndSend("/topic/simulation", event);
    }

    // Push reroute notification to frontend
    public void publishReroute(Long robotId, Route newRoute) {
        template.convertAndSend("/topic/robot/" + robotId + "/reroute", newRoute);
    }

    // Push obstruction notification
    public void publishObstruction(String linkId) {
        template.convertAndSend("/topic/obstruction", linkId);
    }

    public void publishObstructionCleared(String linkId) {
        template.convertAndSend("/topic/obstruction/cleared", linkId);
    }

    public void publishRerouteFailed(Long robotId, String linkId) {
        template.convertAndSend("/topic/robot/" + robotId + "/reroute/failed", linkId);
    }
}