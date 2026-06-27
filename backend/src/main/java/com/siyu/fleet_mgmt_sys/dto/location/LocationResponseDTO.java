package com.siyu.fleet_mgmt_sys.dto.location;

import com.siyu.fleet_mgmt_sys.model.Location;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LocationResponseDTO {
    private Long id;
    private String name;
    private String address;
    private String postalCode;
    private double latitude;
    private double longitude;
    private String source;

    public LocationResponseDTO(Location location) {
        this.id = location.getId();
        this.name = location.getName();
        this.address = location.getAddress();
        this.postalCode = location.getPostalCode();
        this.latitude = location.getLatitude();
        this.longitude = location.getLongitude();
        this.source = location.getSource() != null ? location.getSource().name() : null;
    }
}
