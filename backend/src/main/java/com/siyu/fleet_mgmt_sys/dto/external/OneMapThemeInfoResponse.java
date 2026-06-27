package com.siyu.fleet_mgmt_sys.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class OneMapThemeInfoResponse {
    @JsonProperty("Theme_Names")
    private List<OneMapThemeDTO> themeNames;
}