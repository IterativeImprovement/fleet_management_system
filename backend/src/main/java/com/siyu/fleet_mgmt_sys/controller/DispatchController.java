package com.siyu.fleet_mgmt_sys.controller;

import com.siyu.fleet_mgmt_sys.dto.dispatch.DispatchDTO;
import com.siyu.fleet_mgmt_sys.service.dispatch.DispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DispatchController {

    private final DispatchService dispatchService;

    /** Startup/reconnect snapshot of every robot's current leg for a run. */
    @GetMapping("/simulation/{simulationId}/dispatches")
    public ResponseEntity<List<DispatchDTO>> getDispatches(@PathVariable Long simulationId) {
        return ResponseEntity.ok(dispatchService.snapshot(simulationId));
    }

    /** Frontend reports a robot finished the leg it was animating (revision-gated). */
    @PostMapping("/robot/{robotId}/dispatch/{revision}/arrive")
    public ResponseEntity<Void> arrive(@PathVariable Long robotId, @PathVariable long revision) {
        dispatchService.onArrive(robotId, revision);
        return ResponseEntity.ok().build();
    }
}
