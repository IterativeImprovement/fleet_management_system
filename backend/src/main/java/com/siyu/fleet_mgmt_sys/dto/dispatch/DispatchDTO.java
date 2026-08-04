package com.siyu.fleet_mgmt_sys.dto.dispatch;

import com.siyu.fleet_mgmt_sys.model.enums.DispatchPhase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A robot's current leg under backend-authoritative dispatch. Held in-memory (one per
 * robot) and pushed to the frontend, which animates {@code routeGeo} over {@code etaSeconds}
 * and acknowledges arrival with {@code revision}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchDTO {
    private Long simulationId;   // null for live (non-simulated) robots
    private Long robotId;
    private long revision;       // monotonic per robot; arrivals are gated on it
    private Long taskId;         // null for TO_BASE / IDLE
    private DispatchPhase phase;

    private String routeGeo;     // Google-encoded polyline of the leg (null for IDLE)
    private Integer distanceM;
    private Double etaSeconds;   // sim-seconds to travel the leg

    private double destLat;
    private double destLng;

    private boolean blocked;     // routing failed - robot holds position, alert the user
}
