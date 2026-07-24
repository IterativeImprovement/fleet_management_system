package com.siyu.fleet_mgmt_sys.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RobotLocationNStatusDTO {
    String robotId;
    String status;
    double lat;
    double lng;
}
