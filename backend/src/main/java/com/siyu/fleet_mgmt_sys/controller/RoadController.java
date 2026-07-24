package com.siyu.fleet_mgmt_sys.controller;

import com.siyu.fleet_mgmt_sys.dto.road.RoadResponseDTO;
import com.siyu.fleet_mgmt_sys.service.RoadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/road")
@RequiredArgsConstructor
public class RoadController {
    private final RoadService roadService;

    @GetMapping("/{id}")
    public ResponseEntity<RoadResponseDTO> getRoad(@PathVariable String id) {
        return ResponseEntity.ok(roadService.getRoad(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<RoadResponseDTO> updateRoadStatus(@PathVariable String id, @RequestBody String newStatus) {
        return ResponseEntity.ok(roadService.updateRoadStatus(id, newStatus));
    }

    // Obstruction over WebSocket now lives in WebsocketController (routing branch owns /app/obstruction).
}
