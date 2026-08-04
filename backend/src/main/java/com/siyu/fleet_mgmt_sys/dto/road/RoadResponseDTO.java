package com.siyu.fleet_mgmt_sys.dto.road;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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

    // Every graph sub-edge sharing this road's linkId, as [[fromLat,fromLon],[toLat,toLon]] pairs.
    // A road that crosses others gets split into multiple sub-edges at those junctions, so this can
    // contain more than one segment - the frontend uses it to draw the road's true geometry (e.g.
    // when marking it obstructed) instead of just the straight line between startLat/Lon and
    // endLat/Lon, which only reflects the original, pre-split road.
    private List<List<List<Double>>> segments;
}