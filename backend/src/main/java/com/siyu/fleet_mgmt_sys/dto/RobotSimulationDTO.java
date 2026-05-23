package com.siyu.fleet_mgmt_sys.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RobotSimulationDTO {
    private Long robotId;

    private Integer status;

    private double lat;

    private double lng;
}
