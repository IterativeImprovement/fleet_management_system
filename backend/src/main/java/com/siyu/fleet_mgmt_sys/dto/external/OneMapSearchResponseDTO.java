package com.siyu.fleet_mgmt_sys.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class OneMapSearchResponseDTO {
    private String error;
    private Integer found;
    private Integer totalNumPages;
    private Integer pageNum;
    private List<SearchResult> results = new ArrayList<>();

    @Data
    @NoArgsConstructor
    public static class SearchResult {
        @JsonProperty("SEARCHVAL")
        private String searchVal;

        @JsonProperty("BLK_NO")
        private String blockNumber;

        @JsonProperty("ROAD_NAME")
        private String roadName;

        @JsonProperty("BUILDING")
        private String building;

        @JsonProperty("ADDRESS")
        private String address;

        @JsonProperty("POSTAL")
        private String postal;

        @JsonProperty("LATITUDE")
        private String latitude;

        @JsonProperty("LONGITUDE")
        private String longitude;
    }
}
