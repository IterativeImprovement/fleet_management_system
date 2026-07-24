package com.siyu.fleet_mgmt_sys.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObstructionEventDTO {
    String id;
    Integer restoredSpeedBand;
}
