package com.siyu.fleet_mgmt_sys.controller;

import com.siyu.fleet_mgmt_sys.dto.simulation.SimulationConfig;
import com.siyu.fleet_mgmt_sys.dto.websocket.ObstructionEventDTO;
import com.siyu.fleet_mgmt_sys.dto.websocket.RobotLocationNStatusDTO;
import com.siyu.fleet_mgmt_sys.service.RoadService;
import com.siyu.fleet_mgmt_sys.service.RobotBreakdownService;
import com.siyu.fleet_mgmt_sys.service.robot.RobotService;
import com.siyu.fleet_mgmt_sys.service.route.RouteObstructionService;
import com.siyu.fleet_mgmt_sys.service.simulation.SimulationEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebsocketController {

    private final RobotService robotService;
    private final RoadService roadService;
    private final RouteObstructionService routeObstructionService;
    private final RobotBreakdownService robotBreakdownService;

    private final SimulationEngine simulationEngine;

    // Frontend sends to /app/simulation/start
    @MessageMapping("/simulation/start")
    public void startSimulation(SimulationConfig config) {
        simulationEngine.generate(config);
    }

    // Frontend sends obstruction events to /app/obstruction
    @MessageMapping("/obstruction")
    public void handleObstruction(ObstructionEventDTO event) {
        routeObstructionService.handleObstruction(event.getId()); // obstructs the link, then reroutes affected robots
        roadService.updateRoadStatus(event.getId(), "obstructed"); // last: throws if the road isn't in the DB, must not block rerouting
    }

    @MessageMapping("/obstruction/cleared")
    public void handleObstructionCleared(ObstructionEventDTO event) {
        routeObstructionService.handleObstructionCleared(
                event.getId(), event.getRestoredSpeedBand());
    }

    @MessageMapping("/robot/{robotId}/breakdown")
    public void handleRobotBreakdown(@DestinationVariable Long robotId) {
        log.info("Breakdown message received for robot {}", robotId);
        robotBreakdownService.handleRobotBreakdown(robotId);
    }

    @MessageMapping("/robot/{robotId}/update")
    public void updateRobotPosition(@DestinationVariable Long robotId, RobotLocationNStatusDTO dto) {
        // position only - the backend owns robot status via dispatch
        robotService.updatePosition(robotId, dto.getLat(), dto.getLng());
    }

}