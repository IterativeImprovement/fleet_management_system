package com.siyu.fleet_mgmt_sys.exception;

import com.siyu.fleet_mgmt_sys.dto.location.LocationResponseDTO;
import lombok.Getter;

@Getter
public class CustomLocationRenameRequiredException extends RuntimeException {
    private final LocationResponseDTO existingLocation;
    private final String proposedName;

    public CustomLocationRenameRequiredException(LocationResponseDTO existingLocation, String proposedName) {
        super("A custom location already exists at these coordinates.");
        this.existingLocation = existingLocation;
        this.proposedName = proposedName;
    }
}
