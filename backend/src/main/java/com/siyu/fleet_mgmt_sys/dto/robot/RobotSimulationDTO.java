package com.siyu.fleet_mgmt_sys.dto.robot;

import com.siyu.fleet_mgmt_sys.model.enums.RobotStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/* Data passed from front end during simulation */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RobotSimulationDTO {
    private Long robotId;

    private RobotStatus status;

    private double lat;

    private double lng;
}
