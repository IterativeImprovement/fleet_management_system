package com.siyu.fleet_mgmt_sys.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Wrapper matching the LTA DataMall OData response envelope:
 * {
 *   "odata.metadata": "...",
 *   "value": [ ... ]
 * }
 */
@Data
public class LtaApiResponseDTO {

    @JsonProperty("odata.metadata")
    private String metadata;

    @JsonProperty("value")
    private List<LtaTrafficSpeedBandResponseDTO> value;
}
