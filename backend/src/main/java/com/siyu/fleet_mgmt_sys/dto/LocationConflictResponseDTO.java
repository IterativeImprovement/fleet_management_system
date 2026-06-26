package com.siyu.fleet_mgmt_sys.dto;

import com.siyu.fleet_mgmt_sys.exception.ErrorResponse;
import lombok.Getter;

@Getter
public class LocationConflictResponseDTO extends ErrorResponse {
    private final LocationResponseDTO existingLocation;
    private final String proposedName;

    public LocationConflictResponseDTO(
            int status,
            String message,
            LocationResponseDTO existingLocation,
            String proposedName
    ) {
        super(status, message);
        this.existingLocation = existingLocation;
        this.proposedName = proposedName;
    }
}
