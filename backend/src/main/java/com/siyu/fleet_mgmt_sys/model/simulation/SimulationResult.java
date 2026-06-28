package com.siyu.fleet_mgmt_sys.model.simulation;

import com.siyu.fleet_mgmt_sys.dto.simulation.SimulationConfig;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SimulationResult {
    private long seed;
    private SimulationConfig config;
    private List<SimulationEvent> events;
    private Long simulationId;
}