package com.siyu.fleet_mgmt_sys.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OneMapAuthResponseDTO {

    @JsonProperty("access_token")
    private String accessToken;

    // epoch seconds
    @JsonProperty("expiry_timestamp")
    private String expiryTimestamp;
}
