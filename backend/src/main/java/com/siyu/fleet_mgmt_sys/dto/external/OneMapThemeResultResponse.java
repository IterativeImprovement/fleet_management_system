package com.siyu.fleet_mgmt_sys.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class OneMapThemeResultResponse {
    @JsonProperty("SrchResults")
    private List<OneMapLocationDTO> srchResults;
}
