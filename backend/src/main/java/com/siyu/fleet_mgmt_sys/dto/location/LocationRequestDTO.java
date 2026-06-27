package com.siyu.fleet_mgmt_sys.dto.location;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LocationRequestDTO {
    private String name;
    private String address;
    private String postalCode;
    private Double latitude;
    private Double longitude;
    private Boolean confirmRename;
}
