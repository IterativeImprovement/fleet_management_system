package com.siyu.fleet_mgmt_sys.controller;


import com.siyu.fleet_mgmt_sys.dto.simulation.SimulationConfig;
import com.siyu.fleet_mgmt_sys.dto.simulation.SimulationResultDTO;
import com.siyu.fleet_mgmt_sys.model.simulation.SimulationResult;
import com.siyu.fleet_mgmt_sys.service.simulation.SimulationEngine;
import com.siyu.fleet_mgmt_sys.service.simulation.SimulationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/simulation")
@RequiredArgsConstructor
public class SimulationController {
    private final SimulationEngine simulationEngine;


    /*
    POST /simulations HTTP/1.1
    Host: localhost:8080
    Content-Type: application/json
    Content-Length: 27

    {    "seed": 8472910394}
     */

    private final SimulationMapper simulationMapper;

    @PostMapping("/generate")
    public ResponseEntity<SimulationResultDTO> generate(@RequestParam(required = false) Long seed) {
        SimulationConfig config = new SimulationConfig();

        if (seed != null) {
            config.setSeed(seed);
        } else {
            config.setRandomSeed();
        }

        SimulationResult result = simulationEngine.generate(config);
        return ResponseEntity.ok(simulationMapper.toDTO(result));
    }

}
