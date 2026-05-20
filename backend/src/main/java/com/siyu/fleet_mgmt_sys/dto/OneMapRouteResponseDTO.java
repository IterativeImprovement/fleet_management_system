package com.siyu.fleet_mgmt_sys.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OneMapRouteResponseDTO {
    @JsonProperty("route_geometry")
    private String routeGeometry;

    @JsonProperty("route_summary")
    private RouteSummary routeSummary;

    @JsonProperty("route_name")
    private List<String> routeName;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RouteSummary {
        @JsonProperty("start_point")
        private String startPoint;
        @JsonProperty("end_point")
        private String endPoint;
        @JsonProperty("total_time")
        private int totalTime;
        @JsonProperty("total_distance")
        private int totalDistance;
    }
}