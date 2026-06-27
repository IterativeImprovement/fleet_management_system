package com.siyu.fleet_mgmt_sys.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class OneMapLocationDTO {
    @JsonProperty("NAME")
    private String name;
    @JsonProperty("ADDRESSSTREETNAME")
    private String streetName;
    @JsonProperty("ADDRESSBLOCKHOUSENUMBER")
    private String blockNumber;
    @JsonProperty("ADDRESSPOSTALCODE")
    private String postalCode;
    @JsonProperty("Type")
    private String type;
    @JsonProperty("LatLng")
    private String latLng;
}