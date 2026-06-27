package com.siyu.fleet_mgmt_sys.dto.robot;

import com.siyu.fleet_mgmt_sys.model.enums.RobotStatus;
import com.siyu.fleet_mgmt_sys.model.enums.RobotType;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RobotResponseDTO {

    private Long id;
    private String name;
    private RobotStatus status;
    private RobotType type;
    private double speed;
    private double baseLatitude;
    private double baseLongitude;
    private double latitude;
    private double longitude;
    private List<Long> taskIds;
    private Long currentClusterId;
}