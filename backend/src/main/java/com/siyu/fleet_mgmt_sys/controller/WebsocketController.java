package com.siyu.fleet_mgmt_sys.controller;

import com.siyu.fleet_mgmt_sys.dto.simulation.SimulationConfig;
import com.siyu.fleet_mgmt_sys.dto.websocket.ObstructionEventDTO;
import com.siyu.fleet_mgmt_sys.dto.websocket.RobotLocationNStatusDTO;
import com.siyu.fleet_mgmt_sys.exception.notfoundexception.RobotNotFoundException;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.repository.RobotRepository;
import com.siyu.fleet_mgmt_sys.service.graph.RouteGraphService;
import com.siyu.fleet_mgmt_sys.service.route.RouteObstructionService;
import com.siyu.fleet_mgmt_sys.service.simulation.SimulationEngine;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class WebsocketController {

    private RobotRepository robotRepository;
    private RouteGraphService routeGraphService;
    private RouteObstructionService routeObstructionService;

    private final SimulationEngine simulationEngine;

    // Frontend sends to /app/simulation/start
    @MessageMapping("/simulation/start")
    public void startSimulation(SimulationConfig config) {
        simulationEngine.generate(config);
    }

    // Frontend sends obstruction events to /app/obstruction
    @MessageMapping("/obstruction")
    public void handleObstruction(ObstructionEventDTO event) {
        routeGraphService.obstructLink(event.getId());
        routeObstructionService.handleObstruction(event.getId()); // trigger rerouting for affected robots
    }

    @MessageMapping("/obstruction/cleared")
    public void handleObstructionCleared(ObstructionEventDTO event) {
        routeObstructionService.handleObstructionCleared(
                event.getId(), event.getRestoredSpeedBand());
    }

    @MessageMapping("/robot/{robotId}/update")
    public void updateRobotPosition(RobotLocationNStatusDTO dto) {
        Robot robot = robotRepository.findById(Long.parseLong(dto.getRobotId()))
                .orElseThrow(() -> new RobotNotFoundException(Long.parseLong(dto.getRobotId())));
        robot.setPosition(dto.getLat(), dto.getLng());
        robotRepository.save(robot);
    }


}