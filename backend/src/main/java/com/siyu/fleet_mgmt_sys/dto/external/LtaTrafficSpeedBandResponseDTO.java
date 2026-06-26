package com.siyu.fleet_mgmt_sys.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Represents a single road segment's traffic speed band from LTA DataMall.
 *
 * Speed bands:
 *   1 = > 90 km/h
 *   2 = 71–90 km/h
 *   3 = 51–70 km/h
 *   4 = 41–50 km/h
 *   5 = 31–40 km/h
 *   6 = 21–30 km/h
 *   7 = 10–20 km/h
 *   8 = < 10 km/h
 */
@Data
public class LtaTrafficSpeedBandResponseDTO {

    // Road name/description
    @JsonProperty("RoadName")
    private String roadName;

    // Road category: A=Expressway, B=Major arterial, C=Arterial, D=Minor, E=Small
    @JsonProperty("RoadCategory")
    private String roadCategory;

    // Speed band (1–8), where 1 is fastest and 8 is slowest
    @JsonProperty("SpeedBand")
    private int speedBand;

    // Minimum speed of the band (km/h)
    @JsonProperty("MinimumSpeed")
    private String minimumSpeed;

    // Maximum speed of the band (km/h)
    @JsonProperty("MaximumSpeed")
    private String maximumSpeed;

    // WGS84 start latitude of the road link
    @JsonProperty("StartLat")
    private double startLat;

    // WGS84 start longitude of the road link
    @JsonProperty("StartLon")
    private double startLon;

    // WGS84 end latitude of the road link
    @JsonProperty("EndLat")
    private double endLat;

    // WGS84 end longitude of the road link
    @JsonProperty("EndLon")
    private double endLon;

    // Link ID of the road segment
    @JsonProperty("LinkID")
    private String linkId;
}