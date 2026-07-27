package com.siyu.fleet_mgmt_sys.controller;

import com.siyu.fleet_mgmt_sys.dto.robot.RobotRequestDTO;
import com.siyu.fleet_mgmt_sys.dto.robot.RobotResponseDTO;
import com.siyu.fleet_mgmt_sys.model.robot.Robot;
import com.siyu.fleet_mgmt_sys.service.dispatch.DispatchService;
import com.siyu.fleet_mgmt_sys.service.robot.RobotMapper;
import com.siyu.fleet_mgmt_sys.service.robot.RobotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;


@RestController
@RequestMapping("/robot")
@RequiredArgsConstructor
public class RobotController {
    private final RobotService robotService;
    private final RobotMapper robotMapper;
    private final DispatchService dispatchService;

    @PostMapping
    public ResponseEntity<RobotResponseDTO> createRobot(@RequestBody RobotRequestDTO req) {
        Robot result = robotService.createRobot(req);
        return ResponseEntity.created(URI.create("/robot/" + result.getId()))
                .body(robotMapper.toDTO(result));
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
    public ResponseEntity<RobotResponseDTO> getRobot(@PathVariable Long id) {
        return ResponseEntity.ok(robotMapper.toDTO(robotService.getRobot(id)));
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
    public ResponseEntity<RobotResponseDTO> updateRobot(@PathVariable Long id, @RequestBody RobotRequestDTO req) {
        return ResponseEntity.ok(robotMapper.toDTO(robotService.updateRobot(id, req)));
    }

    @GetMapping // GET localhost:8080/robots?taskIds=1&taskIds=2&taskIds=3
    public ResponseEntity<List<RobotResponseDTO>> filterRobots(
            @RequestParam(required = false) String status, // refer to RobotStatus for possible statuses
            @RequestParam(required = false) String type,
            @RequestParam(required = false) List<Long> taskIds // finds all robots that are assigned these tasks
    ) {
        return ResponseEntity.ok(robotService.filterRobots(status, type, taskIds)
                .stream()
                .map(robotMapper::toDTO)
                .toList());
    }

    @PatchMapping("/{id}/base")
    public ResponseEntity<Void> setToBase(@PathVariable Long id) { // sets the location's robot to the base defined
         robotService.setToBase(id);
         return ResponseEntity.ok().build();
         }

    // drops the robot's current task(s) back to the pool and sends it home
    @PostMapping("/{id}/send-to-base")
    public ResponseEntity<Void> sendToBase(@PathVariable Long id) {
        dispatchService.userSendToBase(id);
        return ResponseEntity.ok().build();
    }

    // same, but to the repair/servicing location
    @PostMapping("/{id}/send-to-servicing")
    public ResponseEntity<Void> sendToServicing(@PathVariable Long id) {
        dispatchService.userSendToServicing(id);
        return ResponseEntity.ok().build();
    }

    // Robot position/status over WebSocket now lives in WebsocketController (routing branch owns /app/robot/{id}/update).
}