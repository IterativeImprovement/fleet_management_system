package com.siyu.fleet_mgmt_sys.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteResponseDTO {
    public long id;

    private String routeGeo;            // Google encoded polyline

    private long taskId;

    private Integer totalDistance;      // metres

    private double estimatedTime;
}
