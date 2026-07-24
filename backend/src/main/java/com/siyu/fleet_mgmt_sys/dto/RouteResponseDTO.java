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
    public Long id;

    private String routeGeo;            // Google encoded polyline

    private Long taskId;

    private Integer totalDistance;      // metres

    private Double estimatedTime;
}
