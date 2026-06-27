package com.siyu.fleet_mgmt_sys.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class OneMapThemeDTO {
    @JsonProperty("QUERYNAME")
    private String queryName;
    @JsonProperty("THEMENAME")
    private String themeName;
}