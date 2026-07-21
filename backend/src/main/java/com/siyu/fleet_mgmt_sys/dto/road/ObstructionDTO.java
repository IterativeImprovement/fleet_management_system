package com.siyu.fleet_mgmt_sys.dto.road;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/* Obstruction reported from the frontend over WebSocket during simulation */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ObstructionDTO {
    private String linkId;   // LTA LinkID == Road.id, sent as a string from the frontend
}
