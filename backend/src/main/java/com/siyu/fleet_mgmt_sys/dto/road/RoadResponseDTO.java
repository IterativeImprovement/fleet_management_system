package com.siyu.fleet_mgmt_sys.dto.road;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadResponseDTO {
    private String id;
    private String roadName;
    private String roadCategory;

    private double startLat;
    private double startLon;
    private double endLat;
    private double endLon;

    private String status;
}