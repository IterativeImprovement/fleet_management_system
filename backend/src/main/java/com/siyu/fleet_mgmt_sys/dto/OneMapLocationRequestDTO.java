package com.siyu.fleet_mgmt_sys.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OneMapLocationRequestDTO {
    private String name;
    private String address;
    private String postalCode;
    private Double latitude;
    private Double longitude;
    private String externalId;
}
