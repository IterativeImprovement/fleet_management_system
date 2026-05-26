package com.siyu.fleet_mgmt_sys.controller;

import com.siyu.fleet_mgmt_sys.dto.RobotRequestDTO;
import com.siyu.fleet_mgmt_sys.dto.RobotSimulationDTO;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
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
        "name": "Robot1",
        "type": "Standard",
        "status": "IDLE",
        "tasksIdsToRemove": [],
        "tasksIdsToAdd": []
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

    /*
    PATCH http://localhost:8080/robots/1

    {
        "name": "Robot A",
        "taskIdsToAdd": [2, 3],
        "taskIdsToRemove": [1]
    }
     */

    @PatchMapping("/{id}")
    public ResponseEntity<Robot> updateRobot(@PathVariable Long id, @RequestBody RobotRequestDTO req) {
        return ResponseEntity.ok(robotService.updateRobot(id, req));
    }

    @GetMapping // GET localhost:8080/robots?taskIds=1&taskIds=2&taskIds=3
    public ResponseEntity<List<Robot>> filterRobots(
            @RequestParam(required = false) String status, // refer to RobotStatus for possible statuses
            @RequestParam(required = false) String type,
            @RequestParam(required = false) List<Long> taskIds // finds all robots that are assigned these tasks
    ) {
        return ResponseEntity.ok(robotService.filterRobots(status, type, taskIds));
    }

    @PatchMapping("/{id}/base")
    public ResponseEntity<Void> setToBase(@PathVariable Long id) { // sets the location's robot to the base defined
         robotService.setToBase(id);
         return ResponseEntity.ok().build();
         }

    // simulation logic, this connects to the websocket and it takes information about the robot's position from the frontend and updates it in the backend
    @MessageMapping("/robot/{id}/update") //
    public void updateStatusAndPosition(@DestinationVariable Long id, RobotSimulationDTO dto) {
        robotService.updateStatusAndPosition(id, dto.getStatus(), dto.getLat(), dto.getLng());
    }
}