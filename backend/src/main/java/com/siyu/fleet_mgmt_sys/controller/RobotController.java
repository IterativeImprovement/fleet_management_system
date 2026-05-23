package com.siyu.fleet_mgmt_sys.controller;

import com.siyu.fleet_mgmt_sys.dto.RobotRequestDTO;
import com.siyu.fleet_mgmt_sys.dto.RobotSimulationDTO;
import com.siyu.fleet_mgmt_sys.model.Robot;
import com.siyu.fleet_mgmt_sys.service.RobotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/robot")
@RequiredArgsConstructor
public class RobotController {
    private final RobotService robotService;

    @PostMapping
    public ResponseEntity<Robot> createRobot(@RequestBody RobotRequestDTO req) {
        Robot result = robotService.createRobot(req);
        return ResponseEntity.created(URI.create("/robot/" + result.getId()))
                .body(result);
    }

    /*
    POST
    http://localhost:8080/robot

    //header
    Content-Type: application/json

    //body example (assuming DTO handles the specific subclass mapping via 'type')
    {
        "name": "RoboCarrier-01",
        "type": 1,
        "speed": 1.5
    }
    */

    @GetMapping("/{id}") // GET localhost:8080/robot/id
    public ResponseEntity<Robot> getRobot(@PathVariable Long id) {
        return ResponseEntity.ok(robotService.getRobot(id));
    }

    @DeleteMapping("/{id}") // DELETE localhost:8080/robot/id
    public ResponseEntity<Void> deleteRobot(@PathVariable Long id) {
        robotService.deleteRobot(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping // GET localhost:8080/robots?taskIds=1&taskIds=2&taskIds=3
    public ResponseEntity<List<Robot>> filterRobots(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) List<Long> taskIds
    ) {
        return ResponseEntity.ok(robotService.filterRobots(status, type, taskIds));
    }

    // simulation logic, this connects to the websocket and it takes information about the robot's position from the frontend and updates it in the backend
    @MessageMapping("/robots/{robotId}/position")
    public void updatePosition(@DestinationVariable Long robotId, RobotSimulationDTO dto) {
        robotService.updateStatusAndPosition(robotId, dto.getStatus(), dto.getLat(), dto.getLng());
    }
}