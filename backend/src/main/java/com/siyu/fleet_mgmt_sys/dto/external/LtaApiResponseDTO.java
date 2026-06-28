package com.siyu.fleet_mgmt_sys.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Wrapper matching the LTA ODataMall Data response envelope:
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
