package com.siyu.fleet_mgmt_sys.controller;

import com.siyu.fleet_mgmt_sys.dto.simulation.SimulationConfig;
import com.siyu.fleet_mgmt_sys.dto.simulation.SimulationResultDTO;
import com.siyu.fleet_mgmt_sys.model.simulation.SimulationResult;
import com.siyu.fleet_mgmt_sys.service.simulation.SimulationEngine;
import com.siyu.fleet_mgmt_sys.service.simulation.SimulationMapper;
import com.siyu.fleet_mgmt_sys.service.simulation.SimulationResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/simulation")
@RequiredArgsConstructor
public class SimulationController {
    private final SimulationEngine simulationEngine;

    /*
     * POST /simulations HTTP/1.1
     * Host: localhost:8080
     * Content-Type: application/json
     * Content-Length: 27
     * 
     * { "seed": 8472910394}
     */

    private final SimulationMapper simulationMapper;
    private final SimulationResetService simulationResetService;

    @PostMapping("/generate")
    public ResponseEntity<SimulationResultDTO> generate(@RequestBody(required = false) SimulationConfig config) {
        // clear any leftover simulation robots/tasks from previous runs so they
        // do not accumulate in the database on each new Start
        simulationResetService.resetAll();

        // No body → run the preset defaults. No seed in the body → pick a random one.
        if (config == null) {
            config = new SimulationConfig();
        }
        if (config.getSeed() == null) {
            config.setRandomSeed();
        }

        SimulationResult result = simulationEngine.generate(config);
        return ResponseEntity.ok(simulationMapper.toDTO(result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> reset(@PathVariable Long id) {
        simulationResetService.reset(id);
        return ResponseEntity.noContent().build();
    }

}
