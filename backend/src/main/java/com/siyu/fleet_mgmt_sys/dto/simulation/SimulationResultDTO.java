package com.siyu.fleet_mgmt_sys.dto.simulation;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulationResultDTO {
    private long seed;
    private Long simulationId;
    private List<SimulationEventDTO> events;
}