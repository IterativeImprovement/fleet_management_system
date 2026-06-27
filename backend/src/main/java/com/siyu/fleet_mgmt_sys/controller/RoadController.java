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

    @PatchMapping("/{id}")
    public ResponseEntity<RoadResponseDTO> updateRoadStatus(@PathVariable Long id, @RequestBody String newStatus) {
        return ResponseEntity.ok(roadService.updateRoadStatus(id, newStatus));
    }
}
