package com.siyu.fleet_mgmt_sys.controller;

import com.siyu.fleet_mgmt_sys.dto.road.ObstructionDTO;
import com.siyu.fleet_mgmt_sys.dto.road.RoadResponseDTO;
import com.siyu.fleet_mgmt_sys.service.RoadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/road")
@RequiredArgsConstructor
public class RoadController {
    private final RoadService roadService;

    @GetMapping("/{id}")
    public ResponseEntity<RoadResponseDTO> getRoad(@PathVariable Long id) {
        return ResponseEntity.ok(roadService.getRoad(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<RoadResponseDTO> updateRoadStatus(@PathVariable Long id, @RequestBody String newStatus) {
        return ResponseEntity.ok(roadService.updateRoadStatus(id, newStatus));
    }

    // Obstruction reported from the frontend over WebSocket during simulation.
    // ponytail: fire-and-forget, no validation — matches the robot WS handler. A bad/unknown
    // linkId throws (NumberFormatException / RoadNotFoundException) into Spring's STOMP error
    // log without crashing. Add a guard only if sim scripts start emitting junk link ids.
    @MessageMapping("/obstruction")   // = /app/obstruction
    public void obstructRoad(ObstructionDTO dto) {
        roadService.updateRoadStatus(Long.parseLong(dto.getLinkId()), "obstructed");
    }
}
